package es.rehxio.scudo.vpn

import es.rehxio.scudo.vpn.Packets.put16
import es.rehxio.scudo.vpn.Packets.u16

class DnsQuery(val name: String, val qtype: Int, val questionEnd: Int)

object Dns {
    const val TYPE_A = 1
    const val TYPE_AAAA = 28

    /** Lee la pregunta de una consulta DNS. Devuelve null si no es una consulta normal. */
    fun parseQuery(b: ByteArray, off: Int, len: Int): DnsQuery? {
        if (len < 17) return null
        val end = off + len
        if (b[off + 2].toInt() and 0x80 != 0) return null // es una respuesta, no una consulta
        if (u16(b, off + 4) != 1) return null             // exactamente una pregunta
        var p = off + 12
        val sb = StringBuilder(64)
        var labels = 0
        while (true) {
            if (p >= end) return null
            val l = b[p].toInt() and 0xFF
            if (l == 0) { p++; break }
            if (l and 0xC0 != 0 || p + 1 + l > end || ++labels > 127) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in p + 1..p + l) {
                var c = (b[i].toInt() and 0xFF).toChar()
                if (c in 'A'..'Z') c += 32
                sb.append(c)
            }
            p += 1 + l
        }
        if (p + 4 > end) return null
        return DnsQuery(sb.toString(), u16(b, p), p + 4 - off)
    }

    /** ID de la consulta/respuesta (para no aceptar respuestas que no corresponden). */
    fun id(b: ByteArray, off: Int = 0): Int = u16(b, off)

    /** Respuesta SERVFAIL: el móvil sabe al instante que falló y reintenta, en vez de quedarse colgado. */
    fun servfail(b: ByteArray, off: Int, q: DnsQuery): ByteArray {
        val out = ByteArray(q.questionEnd)
        System.arraycopy(b, off, out, 0, q.questionEnd)
        out[2] = (0x80 or (b[off + 2].toInt() and 0x79)).toByte()
        out[3] = 0x82.toByte() // RA=1, RCODE=2 (SERVFAIL)
        put16(out, 4, 1); put16(out, 6, 0); put16(out, 8, 0); put16(out, 10, 0)
        return out
    }

    /**
     * Respuesta para un dominio bloqueado, como el modo NULL de Pi-hole:
     * A → 0.0.0.0, AAAA → ::, cualquier otro tipo → respuesta vacía (NODATA).
     */
    fun blockedResponse(b: ByteArray, off: Int, q: DnsQuery): ByteArray {
        val qLen = q.questionEnd
        val rdLen = when (q.qtype) { TYPE_A -> 4; TYPE_AAAA -> 16; else -> -1 }
        val out = ByteArray(qLen + if (rdLen > 0) 12 + rdLen else 0)
        System.arraycopy(b, off, out, 0, qLen)
        out[2] = (0x80 or (b[off + 2].toInt() and 0x79)).toByte() // QR=1, conserva opcode y RD
        out[3] = 0x80.toByte()                                    // RA=1, RCODE=0
        put16(out, 4, 1)
        put16(out, 6, if (rdLen > 0) 1 else 0)
        put16(out, 8, 0)
        put16(out, 10, 0)
        if (rdLen > 0) {
            var p = qLen
            out[p++] = 0xC0.toByte(); out[p++] = 0x0C // puntero al nombre de la pregunta
            put16(out, p, q.qtype); p += 2
            put16(out, p, 1); p += 2                 // clase IN
            out[p + 3] = 60; p += 4                  // TTL 60 s
            put16(out, p, rdLen)                     // los datos ya son ceros
        }
        return out
    }
}
