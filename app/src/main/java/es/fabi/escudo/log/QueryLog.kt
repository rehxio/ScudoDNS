package es.fabi.escudo.log

import es.fabi.escudo.filter.Verdict

class LogEntry(
    val time: Long,
    val domain: String,
    val qtype: Int,
    val verdict: Verdict,
    val app: String?,
    /** true si ningún servidor DNS respondió a esta consulta. */
    val failed: Boolean = false,
) {
    val typeName: String get() = when (qtype) {
        1 -> "A"; 28 -> "AAAA"; 5 -> "CNAME"; 12 -> "PTR"; 15 -> "MX"; 16 -> "TXT"
        33 -> "SRV"; 64 -> "SVCB"; 65 -> "HTTPS"; else -> "TIPO $qtype"
    }
}

enum class LogFilter { ALL, BLOCKED, ALLOWED }

class LogPage(val items: List<LogEntry>, val page: Int, val pages: Int, val total: Int, val from: Int, val to: Int)

/**
 * Registro de las últimas consultas en un buffer circular de tamaño fijo:
 * la memoria no crece aunque la VPN esté semanas encendida (~1,5 MB con 10.000 entradas).
 */
object QueryLog {
    const val CAPACITY = 10_000

    private val buf = arrayOfNulls<LogEntry>(CAPACITY)
    private var head = 0
    private var count = 0

    @Synchronized
    fun add(e: LogEntry) {
        buf[head] = e
        head = (head + 1) % CAPACITY
        if (count < CAPACITY) count++
    }

    @Synchronized
    fun clear() {
        buf.fill(null); head = 0; count = 0
    }

    /** Copia de las entradas, de la más reciente a la más antigua. */
    @Synchronized
    fun snapshot(): List<LogEntry> {
        val out = ArrayList<LogEntry>(count)
        for (i in 1..count) out += buf[(head - i + CAPACITY) % CAPACITY]!!
        return out
    }

    fun filter(entries: List<LogEntry>, filter: LogFilter, search: String): List<LogEntry> {
        val s = search.trim().lowercase()
        return entries.filter { e ->
            when (filter) {
                LogFilter.ALL -> true
                LogFilter.BLOCKED -> e.verdict.isBlocked
                LogFilter.ALLOWED -> !e.verdict.isBlocked
            } && (s.isEmpty() || e.domain.contains(s) || e.app?.lowercase()?.contains(s) == true)
        }
    }

    /** Página [page] (empezando en 0) de [size] elementos. Se ajusta si la página no existe. */
    fun page(entries: List<LogEntry>, page: Int, size: Int): LogPage {
        val total = entries.size
        val pages = maxOf(1, (total + size - 1) / size)
        val p = page.coerceIn(0, pages - 1)
        val from = p * size
        val to = minOf(total, from + size)
        return LogPage(entries.subList(from, to).toList(), p, pages, total, if (total == 0) 0 else from + 1, to)
    }
}
