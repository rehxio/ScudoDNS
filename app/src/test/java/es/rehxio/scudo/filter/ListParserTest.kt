package es.rehxio.scudo.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListParserTest {

    private fun rules(line: String): List<Pair<String, Boolean>> {
        val out = mutableListOf<Pair<String, Boolean>>()
        ListParser.parseLine(line) { d, allow -> out += d to allow }
        return out
    }

    @Test fun hostsFormat() {
        assertEquals(listOf("ads.example.com" to false), rules("0.0.0.0 ads.example.com"))
        assertEquals(listOf("ads.example.com" to false), rules("127.0.0.1\tAds.Example.com  # comentario"))
        assertEquals(listOf("a.com" to false, "b.com" to false), rules("0.0.0.0 a.com b.com"))
        assertTrue(rules("127.0.0.1 localhost").isEmpty())
        assertTrue(rules("::1 ip6-localhost").isEmpty())
        assertTrue(rules("0.0.0.0 0.0.0.0").isEmpty())
    }

    @Test fun plainDomains() {
        assertEquals(listOf("tracker.net" to false), rules("tracker.net"))
        assertEquals(listOf("tracker.net" to false), rules("*.tracker.net"))
        assertTrue(rules("# comentario").isEmpty())
        assertTrue(rules("").isEmpty())
        assertTrue(rules("sin-punto").isEmpty())
        assertTrue(rules("1.2.3.4").isEmpty())
    }

    @Test fun adblockFormat() {
        assertEquals(listOf("ads.com" to false), rules("||ads.com^"))
        assertEquals(listOf("ads.com" to false), rules("||ads.com^\$important"))
        assertEquals(listOf("ok.com" to true), rules("@@||ok.com^"))
        assertTrue(rules("||ads.com/path^").isEmpty())
        assertTrue(rules("||ads.com^\$third-party").isEmpty())
        assertTrue(rules("example.com##.banner").isEmpty())
        assertTrue(rules("! Title: lista").isEmpty())
        assertTrue(rules("[Adblock Plus 2.0]").isEmpty())
        assertTrue(rules("/^ad[0-9]+\\./").isEmpty())
    }

    @Test fun blocklistMatchesSubdomainsAndRespectsAllow() {
        val r = ListParser.parse(
            """
            ||doubleclick.net^
            0.0.0.0 tracker.example.org
            @@||good.doubleclick.net^
            """.trimIndent().reader().buffered()
        )
        Blocklist.set(r.block, r.allow)
        assertTrue(Blocklist.isBlocked("doubleclick.net"))
        assertTrue(Blocklist.isBlocked("ad.g.doubleclick.net"))
        assertTrue(Blocklist.isBlocked("tracker.example.org."))
        assertFalse(Blocklist.isBlocked("example.org"))
        assertFalse(Blocklist.isBlocked("good.doubleclick.net"))
        assertFalse(Blocklist.isBlocked("x.good.doubleclick.net"))
        assertFalse(Blocklist.isBlocked("net"))
        assertFalse(Blocklist.isBlocked("google.com"))
    }

    @Test fun sortedUnique() {
        val b = LongArrayBuilder(2)
        listOf(5L, 1L, 5L, 3L, 1L).forEach(b::add)
        assertEquals(listOf(1L, 3L, 5L), b.toSortedUnique().toList())
    }
}

class UserRulesTest {
    @Test fun userRulesBeatListsAndMostSpecificWins() {
        val r = ListParser.parse("||ads.com^\n||tracker.net^".reader().buffered())
        Blocklist.set(r.block, r.allow)
        Blocklist.setUserRules(block = setOf("bad.example.org", "x.safe.com"), allow = setOf("ads.com", "safe.com"))
        assertEquals(Verdict.ALLOWED_USER, Blocklist.decide("cdn.ads.com"))
        assertEquals(Verdict.BLOCKED, Blocklist.decide("tracker.net"))
        assertEquals(Verdict.BLOCKED_USER, Blocklist.decide("bad.example.org"))
        assertEquals(Verdict.BLOCKED_USER, Blocklist.decide("a.x.safe.com"))   // la regla más específica gana
        assertEquals(Verdict.ALLOWED_USER, Blocklist.decide("y.safe.com"))
        assertEquals(Verdict.ALLOWED, Blocklist.decide("example.org"))
        Blocklist.setUserRules(emptySet(), emptySet())
    }
}

class ListLimitsTest {
    @Test fun overlongLinesAreIgnoredWithoutBreakingTheRest() {
        val huge = "a".repeat(ListParser.MAX_LINE * 10)
        val r = ListParser.parse("$huge\nads.example.com\n${huge}.com\n||tracker.net^".reader())
        assertEquals(2, r.block.size)
    }

    @Test fun tooManyRulesIsRejected() {
        val many = object : java.io.Reader() {
            var i = 0
            var pending = ""
            override fun read(cbuf: CharArray, off: Int, len: Int): Int {
                if (pending.isEmpty()) {
                    if (i > ListParser.MAX_RULES + 10) return -1
                    pending = "d${i++}.example.com\n"
                }
                val n = minOf(len, pending.length)
                pending.toCharArray(cbuf, off, 0, n)
                pending = pending.substring(n)
                return n
            }
            override fun close() {}
        }
        var thrown = false
        try { ListParser.parse(many) } catch (e: ListTooLargeException) { thrown = true }
        assertTrue(thrown)
    }
}
