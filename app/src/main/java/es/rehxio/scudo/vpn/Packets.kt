package es.rehxio.scudo.vpn

/** Un paquete UDP leído del túnel (IPv4 o IPv6). El payload apunta dentro de [buf]. */
class UdpPacket(
    val ipVersion: Int,
    val src: ByteArray,
    val dst: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val buf: ByteArray,
    val payloadOffset: Int,
    val payloadLength: Int,
)

object Packets {
    private const val UDP = 17

    fun parse(b: ByteArray, len: Int): UdpPacket? {
        if (len < 1) return null
        return when (b[0].toInt() ushr 4) {
            4 -> parseV4(b, len)
            6 -> parseV6(b, len)
            else -> null
        }
    }

    private fun parseV4(b: ByteArray, len: Int): UdpPacket? {
        if (len < 28) return null
        val ihl = (b[0].toInt() and 0x0F) * 4
        if (ihl < 20 || b[9].toInt() != UDP) return null
        if ((u16(b, 6) and 0x3FFF) != 0) return null // fragmentado: no lo tratamos
        val total = minOf(u16(b, 2), len)
        if (total < ihl + 8) return null
        val udpLen = minOf(u16(b, ihl + 4), total - ihl)
        if (udpLen < 8) return null
        return UdpPacket(
            4, b.copyOfRange(12, 16), b.copyOfRange(16, 20),
            u16(b, ihl), u16(b, ihl + 2), b, ihl + 8, udpLen - 8,
        )
    }

    private fun parseV6(b: ByteArray, len: Int): UdpPacket? {
        if (len < 48 || b[6].toInt() != UDP) return null // no seguimos cabeceras de extensión
        val udpLen = minOf(u16(b, 44), len - 40)
        if (udpLen < 8) return null
        return UdpPacket(
            6, b.copyOfRange(8, 24), b.copyOfRange(24, 40),
            u16(b, 40), u16(b, 42), b, 48, udpLen - 8,
        )
    }

    /** Construye un paquete IP+UDP completo con checksums. */
    fun build(
        ipVersion: Int, src: ByteArray, dst: ByteArray, srcPort: Int, dstPort: Int,
        payload: ByteArray, off: Int, len: Int,
    ): ByteArray {
        val ipLen = if (ipVersion == 4) 20 else 40
        val udpLen = 8 + len
        val out = ByteArray(ipLen + udpLen)
        if (ipVersion == 4) {
            out[0] = 0x45
            put16(out, 2, ipLen + udpLen)
            out[6] = 0x40 // Don't Fragment
            out[8] = 64   // TTL
            out[9] = UDP.toByte()
            System.arraycopy(src, 0, out, 12, 4)
            System.arraycopy(dst, 0, out, 16, 4)
            put16(out, 10, fold(sum(out, 0, 20)))
        } else {
            out[0] = 0x60
            put16(out, 4, udpLen)
            out[6] = UDP.toByte()
            out[7] = 64 // hop limit
            System.arraycopy(src, 0, out, 8, 16)
            System.arraycopy(dst, 0, out, 24, 16)
        }
        put16(out, ipLen, srcPort)
        put16(out, ipLen + 2, dstPort)
        put16(out, ipLen + 4, udpLen)
        System.arraycopy(payload, off, out, ipLen + 8, len)

        var pseudo = sum(src, 0, src.size) + sum(dst, 0, dst.size) + UDP + udpLen
        var c = fold(sum(out, ipLen, udpLen, pseudo))
        if (c == 0) c = 0xFFFF
        put16(out, ipLen + 6, c)
        return out
    }

    /** Una conexión TCP entrante (SYN) que no vamos a atender: DNS sobre TLS (853) o DNS por TCP (53). */
    class TcpSyn(val ipVersion: Int, val src: ByteArray, val dst: ByteArray, val srcPort: Int, val dstPort: Int, val seq: Long)

    fun parseTcpSyn(b: ByteArray, len: Int): TcpSyn? {
        if (len < 1) return null
        val v = b[0].toInt() ushr 4
        val off: Int
        val src: ByteArray
        val dst: ByteArray
        if (v == 4) {
            if (len < 40 || b[9].toInt() != 6) return null
            off = (b[0].toInt() and 0x0F) * 4
            src = b.copyOfRange(12, 16); dst = b.copyOfRange(16, 20)
        } else if (v == 6) {
            if (len < 60 || b[6].toInt() != 6) return null
            off = 40
            src = b.copyOfRange(8, 24); dst = b.copyOfRange(24, 40)
        } else return null
        if (len < off + 20) return null
        val flags = b[off + 13].toInt()
        if (flags and 0x02 == 0 || flags and 0x04 != 0) return null // solo SYN sin RST
        val seq = ((b[off + 4].toLong() and 0xFF) shl 24) or ((b[off + 5].toLong() and 0xFF) shl 16) or
            ((b[off + 6].toLong() and 0xFF) shl 8) or (b[off + 7].toLong() and 0xFF)
        return TcpSyn(v, src, dst, u16(b, off), u16(b, off + 2), seq)
    }

    /** RST+ACK para rechazar la conexión al instante (en vez de dejar que espere hasta agotar el tiempo). */
    fun buildTcpRst(s: TcpSyn): ByteArray {
        val ipLen = if (s.ipVersion == 4) 20 else 40
        val out = ByteArray(ipLen + 20)
        if (s.ipVersion == 4) {
            out[0] = 0x45; put16(out, 2, ipLen + 20); out[6] = 0x40; out[8] = 64; out[9] = 6
            System.arraycopy(s.dst, 0, out, 12, 4); System.arraycopy(s.src, 0, out, 16, 4)
            put16(out, 10, fold(sum(out, 0, 20)))
        } else {
            out[0] = 0x60; put16(out, 4, 20); out[6] = 6; out[7] = 64
            System.arraycopy(s.dst, 0, out, 8, 16); System.arraycopy(s.src, 0, out, 24, 16)
        }
        val t = ipLen
        put16(out, t, s.dstPort); put16(out, t + 2, s.srcPort)
        val ack = (s.seq + 1) and 0xFFFFFFFFL
        out[t + 8] = (ack ushr 24).toByte(); out[t + 9] = (ack ushr 16).toByte()
        out[t + 10] = (ack ushr 8).toByte(); out[t + 11] = ack.toByte()
        out[t + 12] = 0x50          // cabecera de 20 bytes
        out[t + 13] = 0x14          // RST + ACK
        val pseudo = sum(s.dst, 0, s.dst.size) + sum(s.src, 0, s.src.size) + 6 + 20
        put16(out, t + 16, fold(sum(out, t, 20, pseudo)))
        return out
    }

    /** Describe un paquete que no es DNS por UDP, para el diagnóstico (p. ej. "TCP→853"). */
    fun describe(b: ByteArray, len: Int): String {
        if (len < 1) return "vacío"
        val v = b[0].toInt() ushr 4
        val (proto, off) = when (v) {
            4 -> if (len >= 20) (b[9].toInt() and 0xFF) to (b[0].toInt() and 0x0F) * 4 else return "IPv4 corto"
            6 -> if (len >= 40) (b[6].toInt() and 0xFF) to 40 else return "IPv6 corto"
            else -> return "IP v$v"
        }
        val name = when (proto) { 6 -> "TCP"; 17 -> "UDP"; 1 -> "ICMP"; 58 -> "ICMPv6"; else -> "proto $proto" }
        return if ((proto == 6 || proto == 17) && len >= off + 4) "$name→${u16(b, off + 2)} (IPv$v)" else "$name (IPv$v)"
    }

    internal fun sum(b: ByteArray, off: Int, len: Int, start: Long = 0): Long {
        var s = start
        var i = off
        val end = off + len
        while (i + 1 < end) {
            s += ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) s += (b[i].toInt() and 0xFF) shl 8
        return s
    }

    internal fun fold(s0: Long): Int {
        var s = s0
        while (s ushr 16 != 0L) s = (s and 0xFFFF) + (s ushr 16)
        return (s.inv() and 0xFFFF).toInt()
    }

    fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
    fun put16(b: ByteArray, i: Int, v: Int) { b[i] = (v ushr 8).toByte(); b[i + 1] = v.toByte() }
}
