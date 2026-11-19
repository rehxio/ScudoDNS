package es.rehxio.scudo.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test fun parsea() {
        assertEquals(listOf(0, 7, 0), Versions.parse("v0.7.0"))
        assertEquals(listOf(1, 2), Versions.parse("1.2-beta"))
        assertNull(Versions.parse("latest"))
        assertNull(Versions.parse(""))
        assertNull(Versions.parse("1..2"))
    }

    @Test fun compara() {
        assertTrue(Versions.isNewer("v0.7.0", "0.6.0"))
        assertTrue(Versions.isNewer("0.10.0", "0.9.9"))
        assertTrue(Versions.isNewer("1.0", "0.99.99"))
        assertTrue(Versions.isNewer("0.6.1", "0.6"))
        assertFalse(Versions.isNewer("0.6.0", "0.6.0"))
        assertFalse(Versions.isNewer("0.6", "0.6.0"))
        assertFalse(Versions.isNewer("0.5.9", "0.6.0"))
        assertFalse(Versions.isNewer("basura", "0.6.0"))
    }

    @Test fun notasEnTextoPlano() {
        val md = "## Novedades\n- **Botón** nuevo\n\n<!-- oculto -->\n* Ver [la guía](https://x.y)\n`código`"
        assertEquals("Novedades\n• Botón nuevo\n• Ver la guía\ncódigo", Versions.plainNotes(md))
    }

    @Test fun soloDominiosDeGitHub() {
        assertTrue(AppUpdater.isAllowedHost("api.github.com"))
        assertTrue(AppUpdater.isAllowedHost("release-assets.githubusercontent.com"))
        assertTrue(AppUpdater.isAllowedHost("objects.githubusercontent.com"))
        assertFalse(AppUpdater.isAllowedHost("github.com.evil.com"))
        assertFalse(AppUpdater.isAllowedHost("evilgithubusercontent.com"))
        assertFalse(AppUpdater.isAllowedHost(null))
    }
}
