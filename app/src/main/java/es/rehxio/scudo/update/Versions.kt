package es.rehxio.scudo.update

/** Comparación de versiones "1.2.3" (admite una "v" delante y sufijos como "-beta", que se ignoran). */
object Versions {
    fun parse(v: String): List<Int>? {
        val core = v.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        if (core.isEmpty()) return null
        val parts = core.split('.')
        if (parts.size > 4) return null
        return parts.map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
    }

    /** true si [remote] es estrictamente posterior a [current]. Si alguna no se entiende, false. */
    fun isNewer(remote: String, current: String): Boolean {
        val r = parse(remote) ?: return false
        val c = parse(current) ?: return false
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Pasa las notas de la release (Markdown) a texto sencillo para mostrarlas en la app. */
    fun plainNotes(markdown: String, maxLines: Int = 12): String =
        markdown.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("<!--") }
            .map { line ->
                line.replace(Regex("^#{1,6}\\s*"), "")
                    .replace(Regex("^[-*]\\s+"), "• ")
                    .replace("**", "").replace("`", "")
                    .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
            }
            .take(maxLines)
            .joinToString("\n")
}
