package es.fabi.escudo.filter

import java.io.BufferedReader

class ParseResult(val block: LongArray, val allow: LongArray)

/**
 * Entiende los formatos habituales de listas de bloqueo:
 *  - hosts:      0.0.0.0 ads.example.com   /   127.0.0.1 ads.example.com
 *  - dominios:   ads.example.com   /   *.ads.example.com
 *  - AdBlock/AdGuard DNS:  ||ads.example.com^   @@||permitido.com^   ||x.com^$important
 *
 * Todas las reglas bloquean también los subdominios (como hacen AdGuard Home o RethinkDNS).
 * Se ignoran comentarios, reglas cosméticas (##) y reglas que no son de dominio (rutas, regex).
 */
object ListParser {

    private val IGNORED = setOf(
        "localhost", "localhost.localdomain", "local", "broadcasthost",
        "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix",
        "ip6-allnodes", "ip6-allrouters", "ip6-allhosts", "0.0.0.0",
    )
    private val ALLOWED_MODIFIERS = setOf("important", "all", "document", "doc")

    fun parse(reader: BufferedReader): ParseResult {
        val block = LongArrayBuilder(64 * 1024)
        val allow = LongArrayBuilder(256)
        reader.forEachLine { line ->
            parseLine(line) { domain, isAllow ->
                val h = DomainHash.hash(domain)
                if (isAllow) allow.add(h) else block.add(h)
            }
        }
        return ParseResult(block.toSortedUnique(), allow.toSortedUnique())
    }

    /** Llama a [emit] con (dominio, esPermitido) por cada regla válida de la línea. */
    fun parseLine(raw: String, emit: (String, Boolean) -> Unit) {
        var line = raw.trim()
        if (line.isEmpty()) return
        val c = line[0]
        if (c == '#' || c == '!' || c == '[' || c == '/') return
        if (line.contains("##") || line.contains("#@#") || line.contains("#?#") || line.contains("#$#")) return

        if (line.startsWith("@@||")) { adblock(line.substring(4))?.let { emit(it, true) }; return }
        if (line.startsWith("||")) { adblock(line.substring(2))?.let { emit(it, false) }; return }
        if (line.startsWith("@@") || line.startsWith("|")) return

        val hash = line.indexOf('#')
        if (hash >= 0) line = line.substring(0, hash).trim()
        if (line.isEmpty()) return

        val parts = line.split(' ', '\t').filter { it.isNotEmpty() }
        when {
            parts.size >= 2 && isIp(parts[0]) ->
                for (i in 1 until parts.size) normalize(parts[i])?.let { emit(it, false) }
            parts.size == 1 -> normalize(parts[0])?.let { emit(it, false) }
        }
    }

    private fun adblock(rest: String): String? {
        val caret = rest.indexOf('^')
        val domain: String
        if (caret >= 0) {
            domain = rest.substring(0, caret)
            val tail = rest.substring(caret + 1)
            if (tail.isNotEmpty() && tail != "|") {
                if (!tail.startsWith("$")) return null
                val mods = tail.substring(1).split(',').map { it.trim().lowercase() }
                if (!mods.all { it in ALLOWED_MODIFIERS }) return null
            }
        } else {
            if (rest.contains('$')) return null
            domain = rest
        }
        if (domain.contains('/') || domain.contains('*') && !domain.startsWith("*.")) return null
        return normalize(domain)
    }

    private fun isIp(s: String): Boolean {
        if (s.contains(':')) return true
        var dots = 0
        for (ch in s) {
            if (ch == '.') dots++ else if (ch !in '0'..'9') return false
        }
        return dots == 3
    }

    /** Devuelve el dominio en minúsculas y validado, o null si no es un dominio. */
    fun normalize(input: String): String? {
        var d = input.lowercase()
        if (d.startsWith("*.")) d = d.substring(2)
        d = d.trimStart('.').trimEnd('.')
        if (d.length < 3 || d.length > 253 || d in IGNORED) return null
        if (!d.contains('.') || d.contains("..")) return null
        var allDigits = true
        for (ch in d) {
            val ok = ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_'
            if (!ok) return null
            if (ch != '.' && ch !in '0'..'9') allDigits = false
        }
        if (allDigits) return null // es una IP, no un dominio
        return d
    }
}
