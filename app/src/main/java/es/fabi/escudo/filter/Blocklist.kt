package es.fabi.escudo.filter

import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

/** Qué se decidió con una consulta y por qué. */
enum class Verdict(val isBlocked: Boolean) {
    ALLOWED(false),       // no está en ninguna lista
    BLOCKED(true),        // bloqueado por una lista
    BLOCKED_USER(true),   // bloqueado por una regla tuya
    ALLOWED_USER(false),  // permitido por una regla tuya
}

/**
 * Lista combinada en memoria que consulta la VPN. Se sustituye entera de forma atómica
 * cuando se actualizan o activan/desactivan listas, sin cortar la VPN.
 *
 * Prioridad: tus reglas (la más específica gana) > excepciones @@ de las listas > listas.
 */
object Blocklist {
    @Volatile private var blocked = LongArray(0)
    @Volatile private var allowed = LongArray(0)
    @Volatile private var userBlock: Set<String> = emptySet()
    @Volatile private var userAllow: Set<String> = emptySet()
    @Volatile private var loaded = false

    val size: Int get() = blocked.size

    fun setUserRules(block: Set<String>, allow: Set<String>) {
        userBlock = block.toHashSet()
        userAllow = allow.toHashSet()
    }

    fun decide(rawName: String): Verdict {
        val name = rawName.trimEnd('.')
        if (name.isEmpty()) return Verdict.ALLOWED
        val ub = userBlock
        val ua = userAllow
        if (ub.isNotEmpty() || ua.isNotEmpty()) {
            var start = 0
            while (true) { // de lo más específico a lo más general
                val suffix = if (start == 0) name else name.substring(start)
                if (suffix in ua) return Verdict.ALLOWED_USER
                if (suffix in ub) return Verdict.BLOCKED_USER
                val dot = name.indexOf('.', start)
                if (dot < 0) break
                start = dot + 1
            }
        }
        val b = blocked
        if (b.isEmpty()) return Verdict.ALLOWED
        val a = allowed
        if (a.isNotEmpty() && matches(name, a)) return Verdict.ALLOWED
        return if (matches(name, b)) Verdict.BLOCKED else Verdict.ALLOWED
    }

    fun set(block: LongArray, allow: LongArray) {
        allowed = allow
        blocked = block
        loaded = true
    }

    fun ensureLoaded(dir: File) {
        if (loaded) return
        set(readLongs(File(dir, COMBINED_BLOCK)), readLongs(File(dir, COMBINED_ALLOW)))
    }

    /** true si hay que bloquear el dominio (o alguno de sus dominios padre). */
    fun isBlocked(name: String): Boolean = decide(name).isBlocked

    /** Comprueba "a.b.ejemplo.com", "b.ejemplo.com" y "ejemplo.com" (nunca el TLD solo). */
    private fun matches(name: String, set: LongArray): Boolean {
        var start = 0
        val end = if (name.endsWith('.')) name.length - 1 else name.length
        while (true) {
            val nextDot = name.indexOf('.', start)
            if (nextDot < 0 || nextDot >= end) return false // queda solo el TLD
            if (set.binarySearch(DomainHash.hash(name, start, end)) >= 0) return true
            start = nextDot + 1
        }
    }

    const val COMBINED_BLOCK = "combined.block"
    const val COMBINED_ALLOW = "combined.allow"

    fun writeLongs(file: File, values: LongArray) {
        val tmp = File(file.path + ".tmp")
        DataOutputStream(BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use { out ->
            for (v in values) out.writeLong(v)
        }
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    fun readLongs(file: File): LongArray {
        if (!file.exists() || file.length() < 8) return LongArray(0)
        RandomAccessFile(file, "r").use { raf ->
            val buf = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
            val out = LongArray((raf.length() / 8).toInt())
            buf.asLongBuffer().get(out)
            return out
        }
    }
}
