package es.fabi.escudo.filter

/**
 * Hash FNV-1a de 64 bits sobre un dominio en ASCII minúsculas.
 *
 * Guardamos los dominios como hashes de 64 bits en un LongArray ordenado:
 * 1 millón de dominios ocupa ~8 MB en vez de ~100 MB como HashSet<String>.
 * La probabilidad de falso positivo es despreciable (~1e-13 por consulta).
 */
object DomainHash {
    private const val OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    private const val PRIME = 0x100000001b3L

    fun hash(s: CharSequence, start: Int = 0, end: Int = s.length): Long {
        var h = OFFSET
        for (i in start until end) {
            h = h xor (s[i].code.toLong() and 0xff)
            h *= PRIME
        }
        return h
    }
}

/** Array dinámico de longs, para no crear millones de objetos Long. */
class LongArrayBuilder(initial: Int = 1024) {
    private var data = LongArray(initial)
    var size = 0
        private set

    fun add(v: Long) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }

    fun addAll(arr: LongArray) {
        if (size + arr.size > data.size) data = data.copyOf(maxOf(data.size * 2, size + arr.size))
        System.arraycopy(arr, 0, data, size, arr.size)
        size += arr.size
    }

    /** Devuelve los valores ordenados y sin duplicados. */
    fun toSortedUnique(): LongArray {
        if (size == 0) return LongArray(0)
        val a = data.copyOf(size)
        a.sort()
        var w = 1
        for (r in 1 until a.size) if (a[r] != a[w - 1]) a[w++] = a[r]
        return if (w == a.size) a else a.copyOf(w)
    }
}
