package es.rehxio.scudo.log

import es.rehxio.scudo.filter.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

class QueryLogTest {
    private fun e(i: Int, v: Verdict = Verdict.ALLOWED, app: String? = null) =
        LogEntry(i.toLong(), "d$i.example.com", 1, v, app)

    @Test fun ringBufferKeepsNewestFirst() {
        QueryLog.clear()
        repeat(QueryLog.CAPACITY + 5) { QueryLog.add(e(it)) }
        val s = QueryLog.snapshot()
        assertEquals(QueryLog.CAPACITY, s.size)
        assertEquals((QueryLog.CAPACITY + 4).toLong(), s.first().time)
        assertEquals(5L, s.last().time)
        QueryLog.clear()
    }

    @Test fun pagination() {
        val list = (1..120).map { e(it) }
        val p0 = QueryLog.page(list, 0, 50)
        assertEquals(3, p0.pages); assertEquals(1, p0.from); assertEquals(50, p0.to); assertEquals(50, p0.items.size)
        val p2 = QueryLog.page(list, 2, 50)
        assertEquals(101, p2.from); assertEquals(120, p2.to); assertEquals(20, p2.items.size)
        val clamped = QueryLog.page(list, 9, 50)
        assertEquals(2, clamped.page)
        val empty = QueryLog.page(emptyList(), 0, 25)
        assertEquals(1, empty.pages); assertEquals(0, empty.from); assertEquals(0, empty.total)
    }

    @Test fun filterAndSearch() {
        val list = listOf(e(1, Verdict.BLOCKED), e(2, Verdict.ALLOWED, "Chrome"), e(3, Verdict.BLOCKED_USER), e(4, Verdict.ALLOWED_USER))
        assertEquals(2, QueryLog.filter(list, LogFilter.BLOCKED, "").size)
        assertEquals(2, QueryLog.filter(list, LogFilter.ALLOWED, "").size)
        assertEquals(1, QueryLog.filter(list, LogFilter.ALL, "chrome").size)
        assertEquals(1, QueryLog.filter(list, LogFilter.ALL, "D3.EXAMPLE").size)
    }
}
