package es.rehxio.scudo.lists

import android.content.Context
import android.content.SharedPreferences
import es.rehxio.scudo.filter.Blocklist
import es.rehxio.scudo.filter.ListParser
import es.rehxio.scudo.filter.LongArrayBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class UserRules(val block: Set<String> = emptySet(), val allow: Set<String> = emptySet())

data class FilterList(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean,
    val lastUpdated: Long = 0,
    val domains: Int = 0,
    val error: String? = null,
)

class ListRepository private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("scudo", Context.MODE_PRIVATE)
    val dir: File = File(context.filesDir, "lists").apply { mkdirs() }
    private val mutex = Mutex()

    private val _lists = MutableStateFlow(loadLists())
    val lists: StateFlow<List<FilterList>> = _lists

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _totalDomains = MutableStateFlow(0)
    val totalDomains: StateFlow<Int> = _totalDomains

    private val _rules = MutableStateFlow(
        UserRules(prefs.getStringSet("userBlock", emptySet())!!.toSet(), prefs.getStringSet("userAllow", emptySet())!!.toSet())
    )
    val rules: StateFlow<UserRules> = _rules

    var upstreams: List<String>
        get() = (prefs.getString("upstreams", null) ?: "9.9.9.9,1.1.1.1").split(',').filter { it.isNotBlank() }
        set(value) = prefs.edit().putString("upstreams", value.joinToString(",")).apply()

    init {
        Blocklist.setUserRules(_rules.value.block, _rules.value.allow)
        Blocklist.ensureLoaded(dir)
        _totalDomains.value = Blocklist.size
    }

    // ---------- Reglas manuales ----------

    /** Bloquea [domain] y sus subdominios, quitándolo de permitidos si estaba. */
    fun blockDomain(domain: String) = editRules { r -> UserRules(r.block + domain, r.allow - domain) }

    /** Permite [domain] y sus subdominios aunque esté en alguna lista. */
    fun allowDomain(domain: String) = editRules { r -> UserRules(r.block - domain, r.allow + domain) }

    fun removeRule(domain: String) = editRules { r -> UserRules(r.block - domain, r.allow - domain) }

    @Synchronized
    private fun editRules(f: (UserRules) -> UserRules) {
        val next = f(_rules.value)
        _rules.value = next
        prefs.edit().putStringSet("userBlock", next.block).putStringSet("userAllow", next.allow).apply()
        Blocklist.setUserRules(next.block, next.allow) // efecto inmediato en la VPN
    }

    // ---------- Gestión de listas ----------

    suspend fun add(name: String, url: String) {
        val clean = url.trim()
        if (!isHttpsUrl(clean)) return // la interfaz ya lo impide; aquí no se aceptan URL que no sean https
        val list = FilterList(UUID.randomUUID().toString(), name.ifBlank { clean }, clean, enabled = true)
        mutate { it + list }
        update(listOf(list))
    }

    suspend fun remove(id: String) {
        mutate { all -> all.filterNot { it.id == id } }
        File(dir, "$id.block").delete()
        File(dir, "$id.allow").delete()
        prefs.edit().remove("etag_$id").remove("lastmod_$id").apply()
        rebuild()
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        mutate { all -> all.map { if (it.id == id) it.copy(enabled = enabled) else it } }
        val l = _lists.value.first { it.id == id }
        if (enabled && !File(dir, "$id.block").exists()) update(listOf(l)) else rebuild()
    }

    /** Descarga las listas activadas que aún no se han descargado nunca (primera vez o tras un error). */
    suspend fun updateMissing() {
        val missing = _lists.value.filter { it.enabled && !File(dir, "${it.id}.block").exists() }
        if (missing.isNotEmpty()) update(missing)
    }

    /** Descarga de nuevo todas las listas activadas y recompone el filtro. */
    suspend fun updateAll() = update(_lists.value.filter { it.enabled })

    private suspend fun update(targets: List<FilterList>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _busy.value = true
            try {
                for (l in targets) {
                    val result = runCatching { download(l) }
                    val updated = result.fold(
                        onSuccess = { r ->
                            if (r == null) { // 304: la lista no ha cambiado, no se descarga nada
                                val count = (File(dir, "${l.id}.block").length() / 8).toInt()
                                l.copy(lastUpdated = System.currentTimeMillis(), domains = count, error = null)
                            } else {
                                Blocklist.writeLongs(File(dir, "${l.id}.block"), r.block)
                                Blocklist.writeLongs(File(dir, "${l.id}.allow"), r.allow)
                                l.copy(lastUpdated = System.currentTimeMillis(), domains = r.block.size, error = null)
                            }
                        },
                        onFailure = { e -> l.copy(error = e.message ?: e.javaClass.simpleName) },
                    )
                    mutate { all -> all.map { if (it.id == l.id) updated.copy(enabled = it.enabled) else it } }
                }
                rebuildLocked()
            } finally {
                _busy.value = false
            }
        }
    }

    /** Descarga y procesa la lista. Devuelve null si el servidor dice que no ha cambiado. */
    private fun download(l: FilterList): es.rehxio.scudo.filter.ParseResult? {
        var target = URL(l.url)
        val haveFile = File(dir, "${l.id}.block").exists()
        repeat(5) { // seguimos redirecciones a mano para poder comprobar cada destino
            // Solo HTTPS: una lista por HTTP podría ser alterada por cualquiera en la red.
            if (target.protocol != "https") throw IOException("Solo se admiten listas por https://")
            val conn = target.openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "ScudoDNS (Android)")
            if (haveFile) {
                prefs.getString("etag_${l.id}", null)?.let { conn.setRequestProperty("If-None-Match", it) }
                prefs.getString("lastmod_${l.id}", null)?.let { conn.setRequestProperty("If-Modified-Since", it) }
            }
            try {
                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_NOT_MODIFIED) return null
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: throw IOException("Redirección sin destino")
                    target = URL(target, loc)
                    return@repeat
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                val declared = conn.getHeaderField("Content-Length")?.toLongOrNull()
                if (declared != null && declared > MAX_LIST_BYTES) throw IOException("La lista supera ${MAX_LIST_BYTES / 1_000_000} MB")
                val result = LimitedInputStream(conn.inputStream, MAX_LIST_BYTES).reader().use { ListParser.parse(it) }
                if (result.block.isEmpty() && result.allow.isEmpty()) throw IOException("La lista no contiene dominios válidos")
                prefs.edit()
                    .putString("etag_${l.id}", conn.getHeaderField("ETag"))
                    .putString("lastmod_${l.id}", conn.getHeaderField("Last-Modified"))
                    .apply()
                return result
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Demasiadas redirecciones")
    }

    private suspend fun rebuild() = withContext(Dispatchers.IO) { mutex.withLock { rebuildLocked() } }

    /** Une todas las listas activadas en un único array ordenado y lo publica a la VPN. */
    private fun rebuildLocked() {
        val block = LongArrayBuilder(256 * 1024)
        val allow = LongArrayBuilder(1024)
        val skipped = mutableSetOf<String>()
        for (l in _lists.value.filter { it.enabled }) {
            val lb = Blocklist.readLongs(File(dir, "${l.id}.block"))
            val la = Blocklist.readLongs(File(dir, "${l.id}.allow"))
            // Tope global para no agotar la memoria al combinar muchas listas grandes.
            if (block.size + allow.size + lb.size + la.size > MAX_TOTAL_RULES) {
                skipped += l.id
                continue
            }
            block.addAll(lb)
            allow.addAll(la)
        }
        val limitMsg = "No se aplica: se supera el máximo de ${MAX_TOTAL_RULES / 1_000_000} millones de dominios entre todas las listas"
        if (skipped.isNotEmpty() || _lists.value.any { it.error == limitMsg }) mutate { all ->
            all.map {
                when {
                    it.id in skipped -> it.copy(error = limitMsg)
                    it.error == limitMsg -> it.copy(error = null)
                    else -> it
                }
            }
        }
        val b = block.toSortedUnique()
        val a = allow.toSortedUnique()
        Blocklist.writeLongs(File(dir, Blocklist.COMBINED_BLOCK), b)
        Blocklist.writeLongs(File(dir, Blocklist.COMBINED_ALLOW), a)
        Blocklist.set(b, a)
        _totalDomains.value = b.size
    }

    // ---------- Persistencia ----------

    @Synchronized
    private fun mutate(f: (List<FilterList>) -> List<FilterList>) {
        val next = f(_lists.value)
        _lists.value = next
        saveLists(next)
    }

    private fun loadLists(): List<FilterList> {
        val json = prefs.getString("lists", null) ?: return DEFAULT_LISTS.also { saveLists(it) }
        val arr = JSONArray(json)
        val loaded = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            FilterList(
                id = o.getString("id"),
                name = o.getString("name"),
                url = o.getString("url"),
                enabled = o.optBoolean("enabled", true),
                lastUpdated = o.optLong("lastUpdated", 0),
                domains = o.optInt("domains", 0),
                error = if (o.isNull("error")) null else o.optString("error"),
            )
        }
        // La URL antigua de HaGeZi dejó de existir (404): la corregimos en instalaciones previas.
        if (loaded.none { it.url == HAGEZI_OLD_URL }) return loaded
        val fixed = loaded.map { if (it.url == HAGEZI_OLD_URL) it.copy(url = HAGEZI_PRO_URL, error = null) else it }
        saveLists(fixed)
        return fixed
    }

    private fun saveLists(lists: List<FilterList>) {
        val arr = JSONArray()
        lists.forEach { l ->
            arr.put(JSONObject().apply {
                put("id", l.id); put("name", l.name); put("url", l.url)
                put("enabled", l.enabled); put("lastUpdated", l.lastUpdated)
                put("domains", l.domains); put("error", l.error ?: JSONObject.NULL)
            })
        }
        prefs.edit().putString("lists", arr.toString()).apply()
    }

    companion object {
        /** Tamaño máximo de una lista ya descomprimida (las más grandes rondan 30-60 MB). */
        const val MAX_LIST_BYTES = 150_000_000L

        /** Máximo de reglas sumando todas las listas activas (~80 MB al combinarlas). */
        const val MAX_TOTAL_RULES = 10_000_000

        fun isHttpsUrl(url: String): Boolean =
            runCatching { URL(url).let { it.protocol == "https" && !it.host.isNullOrBlank() } }.getOrDefault(false)

        const val HAGEZI_PRO_URL = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/pro-onlydomains.txt"
        private const val HAGEZI_OLD_URL = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/pro.txt"

        val DEFAULT_LISTS = listOf(
            FilterList("hagezi-pro", "HaGeZi Multi PRO",
                HAGEZI_PRO_URL, true),
            FilterList("stevenblack", "StevenBlack Unified hosts",
                "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", true),
            FilterList("adguard-dns", "AdGuard DNS filter",
                "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt", false),
        )

        @Volatile private var instance: ListRepository? = null
        fun get(context: Context): ListRepository =
            instance ?: synchronized(this) {
                instance ?: ListRepository(context.applicationContext).also { instance = it }
            }
    }
}

/** Corta la lectura con un error si se superan [limit] bytes (protege frente a descargas gigantes). */
private class LimitedInputStream(private val inner: InputStream, private val limit: Long) : FilterInputStream(inner) {
    private var count = 0L

    private fun add(n: Long) {
        if (n > 0) {
            count += n
            if (count > limit) throw IOException("La lista supera ${limit / 1_000_000} MB")
        }
    }

    override fun read(): Int = super.read().also { if (it >= 0) add(1) }
    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { add(it.toLong()) }
    override fun skip(n: Long): Long = super.skip(n).also { add(it) }
}
