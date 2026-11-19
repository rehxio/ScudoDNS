package es.rehxio.scudo.vpn

import es.rehxio.scudo.vpn.Packets.u16

/** Recorrido seguro de los registros de un mensaje DNS (con comprobación de límites). */
internal object DnsRecords {
    const val TYPE_SOA = 6
    const val TYPE_OPT = 41

    /** Salta un nombre (etiquetas o puntero de compresión). Devuelve la posición siguiente o -1 si está mal formado. */
    fun skipName(b: ByteArray, start: Int, end: Int): Int {
        var p = start
        var labels = 0
        while (p < end) {
            val l = b[p].toInt() and 0xFF
            when {
                l == 0 -> return p + 1
                l and 0xC0 == 0xC0 -> return if (p + 2 <= end) p + 2 else -1
                l and 0xC0 != 0 -> return -1
                else -> {
                    p += 1 + l
                    if (++labels > 127) return -1
                }
            }
        }
        return -1
    }

    /**
     * Llama a [f] con (posición del TTL, tipo, sección 0=respuesta 1=autoridad 2=adicional, fin de los datos)
     * por cada registro. Devuelve false si el mensaje está mal formado.
     */
    fun forEachRecord(b: ByteArray, len: Int, f: (ttlPos: Int, type: Int, section: Int, rdataEnd: Int) -> Unit): Boolean {
        if (len < 12 || len > b.size) return false
        var p = 12
        for (i in 0 until u16(b, 4)) {
            p = skipName(b, p, len)
            if (p < 0 || p + 4 > len) return false
            p += 4
        }
        val counts = intArrayOf(u16(b, 6), u16(b, 8), u16(b, 10))
        for (sec in 0..2) {
            for (i in 0 until counts[sec]) {
                p = skipName(b, p, len)
                if (p < 0 || p + 10 > len) return false
                val type = u16(b, p)
                val rdEnd = p + 10 + u16(b, p + 8)
                if (rdEnd > len) return false
                f(p + 4, type, sec, rdEnd)
                p = rdEnd
            }
        }
        return true
    }

    fun readTtl(b: ByteArray, p: Int): Int {
        val v = ((b[p].toLong() and 0xFF) shl 24) or ((b[p + 1].toLong() and 0xFF) shl 16) or
            ((b[p + 2].toLong() and 0xFF) shl 8) or (b[p + 3].toLong() and 0xFF)
        return if (v > Int.MAX_VALUE) 0 else v.toInt() // RFC 2181: TTL con el bit alto = 0
    }

    fun writeTtl(b: ByteArray, p: Int, v: Int) {
        b[p] = (v ushr 24).toByte(); b[p + 1] = (v ushr 16).toByte()
        b[p + 2] = (v ushr 8).toByte(); b[p + 3] = v.toByte()
    }
}

/**
 * Caché de respuestas DNS en memoria.
 *
 * - Respeta el TTL de cada respuesta y lo descuenta al servirla.
 * - Guarda también respuestas negativas (NXDOMAIN o sin datos) según su SOA.
 * - "Serve-stale" (RFC 8767): si una respuesta acaba de caducar se entrega al instante con un TTL corto
 *   mientras se actualiza en segundo plano, para que el usuario no espere.
 * - Tamaño acotado: se descartan las entradas menos usadas.
 */
class DnsCache(private val maxEntries: Int = 4096, private val maxStaleMs: Long = 3_600_000L) {

    class Hit(val response: ByteArray, val stale: Boolean)

    private class Entry(val data: ByteArray, val storedAt: Long, val ttlSec: Int)

    private val map = object : LinkedHashMap<String, Entry>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > maxEntries
    }

    val size: Int @Synchronized get() = map.size

    /** Guarda la respuesta si es cacheable. Devuelve true si se guardó. */
    @Synchronized
    fun put(key: String, resp: ByteArray, len: Int, now: Long): Boolean {
        val ttl = cacheTtl(resp, len) ?: return false
        if (ttl <= 0) return false
        map[key] = Entry(resp.copyOf(len), now, ttl)
        return true
    }

    /** Devuelve la respuesta lista para enviar (ID, pregunta y TTL ajustados), o null si no hay. */
    @Synchronized
    fun get(key: String, query: ByteArray, q: DnsQuery, now: Long): Hit? {
        val e = map[key] ?: return null
        val age = (now - e.storedAt).coerceAtLeast(0)
        val ttlMs = e.ttlSec * 1000L
        val fresh = age < ttlMs
        if (!fresh && age > ttlMs + maxStaleMs) {
            map.remove(key)
            return null
        }
        if (e.data.size < q.questionEnd || query.size < q.questionEnd) return null
        val out = e.data.copyOf()
        out[0] = query[0]; out[1] = query[1]                                  // ID de esta consulta
        out[2] = ((out[2].toInt() and 0xFE) or (query[2].toInt() and 0x01)).toByte() // RD como lo pidió
        System.arraycopy(query, 12, out, 12, q.questionEnd - 12)                // pregunta tal cual (mayúsculas)
        val elapsed = (age / 1000).toInt()
        val ok = DnsRecords.forEachRecord(out, out.size) { pos, type, _, _ ->
            if (type != DnsRecords.TYPE_OPT) {
                val t = if (fresh) maxOf(DnsRecords.readTtl(out, pos) - elapsed, 1) else STALE_TTL
                DnsRecords.writeTtl(out, pos, t)
            }
        }
        if (!ok) {
            map.remove(key)
            return null
        }
        return Hit(out, !fresh)
    }

    @Synchronized
    fun clear() = map.clear()

    companion object {
        /** TTL que se anuncia al servir una respuesta caducada mientras se actualiza. */
        const val STALE_TTL = 30
        const val MAX_TTL = 86_400
        const val NEG_TTL_DEFAULT = 60
        const val NEG_TTL_MAX = 3_600

        /** Clave: nombre en minúsculas, tipo, clase y si la consulta lleva EDNS. */
        fun key(query: ByteArray, q: DnsQuery): String {
            val cls = u16(query, q.questionEnd - 2)
            val edns = u16(query, 10) > 0
            return "${q.name}|${q.qtype}|$cls|${if (edns) 1 else 0}"
        }

        /** Segundos que se puede guardar la respuesta, o null si no se debe guardar. */
        fun cacheTtl(r: ByteArray, len: Int): Int? {
            if (len < 12 || len > r.size) return null
            val flags = u16(r, 2)
            if (flags and 0x8000 == 0) return null // no es una respuesta
            if (flags and 0x0200 != 0) return null // truncada
            val rcode = flags and 0x0F
            if (rcode != 0 && rcode != 3) return null // solo NOERROR y NXDOMAIN
            if (u16(r, 4) != 1) return null
            var minAns = Int.MAX_VALUE
            var minNeg = Int.MAX_VALUE
            val ok = DnsRecords.forEachRecord(r, len) { pos, type, sec, rdEnd ->
                if (type == DnsRecords.TYPE_OPT) return@forEachRecord
                val ttl = DnsRecords.readTtl(r, pos)
                if (sec == 0) minAns = minOf(minAns, ttl)
                if (sec == 1 && type == DnsRecords.TYPE_SOA) {
                    // RFC 2308: TTL negativo = mínimo entre el TTL del SOA y su campo MINIMUM.
                    val soaMin = if (rdEnd - 4 >= pos + 6) DnsRecords.readTtl(r, rdEnd - 4) else ttl
                    minNeg = minOf(minNeg, ttl, soaMin)
                }
            }
            if (!ok) return null
            return if (rcode == 0 && u16(r, 6) > 0) {
                minOf(minAns, MAX_TTL)
            } else {
                minOf(if (minNeg == Int.MAX_VALUE) NEG_TTL_DEFAULT else minNeg, NEG_TTL_MAX)
            }
        }
    }
}
