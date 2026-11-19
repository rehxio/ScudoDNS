package es.rehxio.scudo.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsCacheTest {
    private val name = byteArrayOf(3) + "www".toByteArray() + byteArrayOf(7) + "example".toByteArray() +
        byteArrayOf(3) + "com".toByteArray() + byteArrayOf(0)

    private fun query(id: Int = 0x1234, upper: Boolean = false): ByteArray {
        val n = name.copyOf().also { if (upper) it[1] = 'W'.code.toByte() }
        return byteArrayOf((id ushr 8).toByte(), id.toByte(), 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0) + n + byteArrayOf(0, 1, 0, 1)
    }

    private fun u32(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    /** Respuesta A con TTL [ttl] (y opcionalmente un registro OPT). */
    private fun answer(ttl: Int, id: Int = 0x1234, opt: Boolean = false): ByteArray {
        val header = byteArrayOf((id ushr 8).toByte(), id.toByte(), 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 1, 0, 0, 0, if (opt) 1 else 0)
        val rr = byteArrayOf(0xC0.toByte(), 0x0C, 0, 1, 0, 1) + u32(ttl) + byteArrayOf(0, 4, 93, 184.toByte(), 216.toByte(), 34)
        val optRr = byteArrayOf(0, 0, 41, 0x10, 0) + u32(0x00008000) + byteArrayOf(0, 0)
        return header + name + byteArrayOf(0, 1, 0, 1) + rr + (if (opt) optRr else ByteArray(0))
    }

    private fun nxdomain(soaTtl: Int, soaMin: Int): ByteArray {
        val header = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x83.toByte(), 0, 1, 0, 0, 0, 1, 0, 0)
        val rdata = byteArrayOf(0xC0.toByte(), 0x10, 0xC0.toByte(), 0x10) + u32(1) + u32(2) + u32(3) + u32(4) + u32(soaMin)
        val soa = byteArrayOf(0xC0.toByte(), 0x10, 0, 6, 0, 1) + u32(soaTtl) + byteArrayOf(0, rdata.size.toByte()) + rdata
        return header + name + byteArrayOf(0, 1, 0, 1) + soa
    }

    private fun ttlOfAnswer(r: ByteArray): Int {
        var t = -1
        DnsRecords.forEachRecord(r, r.size) { pos, type, sec, _ -> if (sec == 0 && type == 1) t = DnsRecords.readTtl(r, pos) }
        return t
    }

    @Test fun freshHitDecrementsTtlAndRewritesIdAndCase() {
        val c = DnsCache()
        val q1 = query()
        val p1 = Dns.parseQuery(q1, 0, q1.size)!!
        val r = answer(300)
        assertTrue(c.put(DnsCache.key(q1, p1), r, r.size, now = 0))

        val q2 = query(id = 0x5555, upper = true)
        val p2 = Dns.parseQuery(q2, 0, q2.size)!!
        assertEquals(DnsCache.key(q1, p1), DnsCache.key(q2, p2)) // mismas claves sin importar mayúsculas
        val hit = c.get(DnsCache.key(q2, p2), q2, p2, now = 100_000)!!
        assertFalse(hit.stale)
        assertEquals(0x5555, Dns.id(hit.response))
        assertEquals('W'.code.toByte(), hit.response[13])
        assertEquals(200, ttlOfAnswer(hit.response))
        assertTrue(Dns.isResponseTo(hit.response, hit.response.size, q2, p2))
    }

    @Test fun staleThenExpired() {
        val c = DnsCache(maxStaleMs = 60_000)
        val q = query(); val p = Dns.parseQuery(q, 0, q.size)!!
        val r = answer(10)
        c.put(DnsCache.key(q, p), r, r.size, now = 0)
        val stale = c.get(DnsCache.key(q, p), q, p, now = 20_000)!!
        assertTrue(stale.stale)
        assertEquals(DnsCache.STALE_TTL, ttlOfAnswer(stale.response))
        assertNull(c.get(DnsCache.key(q, p), q, p, now = 80_000))
        assertEquals(0, c.size)
    }

    @Test fun optRecordTtlIsNotTouched() {
        val c = DnsCache()
        val q = query(); val p = Dns.parseQuery(q, 0, q.size)!!
        val r = answer(300, opt = true)
        c.put(DnsCache.key(q, p), r, r.size, now = 0)
        val hit = c.get(DnsCache.key(q, p), q, p, now = 5_000)!!
        var optTtl = -1
        DnsRecords.forEachRecord(hit.response, hit.response.size) { pos, type, _, _ -> if (type == 41) optTtl = DnsRecords.readTtl(hit.response, pos) }
        assertEquals(0x8000, optTtl)
        assertEquals(295, ttlOfAnswer(hit.response))
    }

    @Test fun negativeAnswersUseSoaMinimum() {
        assertEquals(30, DnsCache.cacheTtl(nxdomain(soaTtl = 900, soaMin = 30).let { it }, nxdomain(900, 30).size))
        assertEquals(20, DnsCache.cacheTtl(nxdomain(20, 600), nxdomain(20, 600).size))
    }

    @Test fun notCacheable() {
        val truncated = answer(300).also { it[2] = (it[2].toInt() or 0x02).toByte() }
        assertNull(DnsCache.cacheTtl(truncated, truncated.size))
        val servfail = answer(300).also { it[3] = 0x82.toByte() }
        assertNull(DnsCache.cacheTtl(servfail, servfail.size))
        val query = query()
        assertNull(DnsCache.cacheTtl(query, query.size)) // no es respuesta
        val cut = answer(300)
        assertNull(DnsCache.cacheTtl(cut, cut.size - 3)) // registro cortado
        val zero = answer(0)
        assertFalse(DnsCache().put("k", zero, zero.size, 0))
    }

    @Test fun boundedSize() {
        val c = DnsCache(maxEntries = 3)
        val r = answer(300)
        for (i in 0 until 10) c.put("k$i", r, r.size, 0)
        assertEquals(3, c.size)
        val q = query(); val p = Dns.parseQuery(q, 0, q.size)!!
        assertNotNull(c.get("k9", q, p, 0))
        assertNull(c.get("k0", q, p, 0))
    }
}
