package es.rehxio.scudo.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DnsPacketTest {

    /** Consulta DNS de tipo A para "Ads.Example.com" con un registro OPT (EDNS) al final. */
    private fun query(qtype: Int = Dns.TYPE_A): ByteArray {
        val name = byteArrayOf(3, 'A'.code.toByte(), 'd'.code.toByte(), 's'.code.toByte(),
            7) + "example".toByteArray() + byteArrayOf(3) + "com".toByteArray() + byteArrayOf(0)
        val header = byteArrayOf(0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 1)
        val question = name + byteArrayOf(0, qtype.toByte(), 0, 1)
        val opt = byteArrayOf(0, 0, 41, 0x10, 0, 0, 0, 0, 0, 0, 0)
        return header + question + opt
    }

    @Test fun parsesQuestion() {
        val q = query()
        val parsed = Dns.parseQuery(q, 0, q.size)
        assertNotNull(parsed)
        assertEquals("ads.example.com", parsed!!.name)
        assertEquals(Dns.TYPE_A, parsed.qtype)
        assertEquals(12 + 17 + 4, parsed.questionEnd)
    }

    @Test fun blockedAnswerIsZeroAddress() {
        val q = query()
        val parsed = Dns.parseQuery(q, 0, q.size)!!
        val r = Dns.blockedResponse(q, 0, parsed)
        assertEquals(0x12, r[0].toInt()); assertEquals(0x34, r[1].toInt())
        assertEquals(0x81, r[2].toInt() and 0xFF) // QR + RD
        assertEquals(0x80, r[3].toInt() and 0xFF) // RA, NOERROR
        assertEquals(1, Packets.u16(r, 6))        // 1 respuesta
        assertEquals(0, Packets.u16(r, 10))       // sin OPT
        val ans = parsed.questionEnd
        assertEquals(0xC00C, Packets.u16(r, ans))
        assertEquals(4, Packets.u16(r, ans + 10))
        assertArrayEquals(ByteArray(4), r.copyOfRange(ans + 12, ans + 16))
        assertEquals(ans + 16, r.size)
    }

    @Test fun otherTypesGetNoData() {
        val q = query(65) // HTTPS
        val parsed = Dns.parseQuery(q, 0, q.size)!!
        val r = Dns.blockedResponse(q, 0, parsed)
        assertEquals(0, Packets.u16(r, 6))
        assertEquals(parsed.questionEnd, r.size)
    }

    @Test fun ipv4RoundTripAndChecksum() {
        val payload = query()
        val src = byteArrayOf(10, 111, 222.toByte(), 1)
        val dst = byteArrayOf(10, 111, 222.toByte(), 2)
        val pkt = Packets.build(4, src, dst, 40000, 53, payload, 0, payload.size)
        // Un checksum correcto hace que la suma de la cabecera IP dé 0
        assertEquals(0, Packets.fold(Packets.sum(pkt, 0, 20)))
        val udpLen = pkt.size - 20
        val pseudo = Packets.sum(src, 0, 4) + Packets.sum(dst, 0, 4) + 17 + udpLen
        assertEquals(0, Packets.fold(Packets.sum(pkt, 20, udpLen, pseudo)))

        val p = Packets.parse(pkt, pkt.size)!!
        assertEquals(4, p.ipVersion)
        assertEquals(40000, p.srcPort)
        assertEquals(53, p.dstPort)
        assertArrayEquals(payload, pkt.copyOfRange(p.payloadOffset, p.payloadOffset + p.payloadLength))
    }

    @Test fun ipv6RoundTripAndChecksum() {
        val payload = query()
        val src = ByteArray(16).also { it[0] = 0xfd.toByte(); it[15] = 1 }
        val dst = ByteArray(16).also { it[0] = 0xfd.toByte(); it[15] = 2 }
        val pkt = Packets.build(6, src, dst, 50000, 53, payload, 0, payload.size)
        val udpLen = pkt.size - 40
        val pseudo = Packets.sum(src, 0, 16) + Packets.sum(dst, 0, 16) + 17 + udpLen
        assertEquals(0, Packets.fold(Packets.sum(pkt, 40, udpLen, pseudo)))
        val p = Packets.parse(pkt, pkt.size)!!
        assertEquals(6, p.ipVersion)
        assertEquals(53, p.dstPort)
        assertEquals(payload.size, p.payloadLength)
    }

    @Test fun servfailKeepsIdAndQuestion() {
        val q = query()
        val parsed = Dns.parseQuery(q, 0, q.size)!!
        val r = Dns.servfail(q, 0, parsed)
        assertEquals(0x1234, Dns.id(r))
        assertEquals(2, r[3].toInt() and 0x0F)    // RCODE SERVFAIL
        assertEquals(0x80, r[2].toInt() and 0x80) // es respuesta
        assertEquals(0, Packets.u16(r, 6))
        assertEquals(parsed.questionEnd, r.size)
    }
}

class TcpRstTest {
    @Test fun rstForSynHasValidChecksums() {
        val syn = ByteArray(40)
        syn[0] = 0x45; Packets.put16(syn, 2, 40); syn[8] = 64; syn[9] = 6
        byteArrayOf(10, 111, 222.toByte(), 1).copyInto(syn, 12); byteArrayOf(10, 111, 222.toByte(), 2).copyInto(syn, 16)
        Packets.put16(syn, 20, 41000); Packets.put16(syn, 22, 853)
        syn[24] = 0; syn[25] = 0; syn[26] = 0x10; syn[27] = 0  // seq = 0x1000
        syn[32] = 0x50; syn[33] = 0x02                       // SYN
        val s = Packets.parseTcpSyn(syn, syn.size)!!
        assertEquals(853, s.dstPort)
        val rst = Packets.buildTcpRst(s)
        assertEquals(0, Packets.fold(Packets.sum(rst, 0, 20)))
        val pseudo = Packets.sum(rst, 12, 4) + Packets.sum(rst, 16, 4) + 6 + 20
        assertEquals(0, Packets.fold(Packets.sum(rst, 20, 20, pseudo)))
        assertEquals(853, Packets.u16(rst, 20)); assertEquals(41000, Packets.u16(rst, 22))
        assertEquals(0x1001, Packets.u16(rst, 30))  // ack = seq + 1
        assertEquals(0x14, rst[33].toInt())
        assertEquals("TCP→853 (IPv4)", Packets.describe(syn, syn.size))
    }
}
