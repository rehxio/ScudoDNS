package es.fabi.escudo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import es.fabi.escudo.filter.ListParser
import es.fabi.escudo.lists.ListRepository
import es.fabi.escudo.log.LogFilter
import es.fabi.escudo.log.LogPage
import es.fabi.escudo.log.QueryLog
import es.fabi.escudo.vpn.EscudoVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado de la pestaña Registro. */
data class LogState(
    val filter: LogFilter = LogFilter.ALL,
    val search: String = "",
    val pageSize: Int = 50,
    val page: Int = 0,
    val live: Boolean = true,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ListRepository.get(app)

    val lists = repo.lists
    val busy = repo.busy
    val totalDomains = repo.totalDomains
    val rules = repo.rules
    val running = EscudoVpnService.running

    /** Consultas, bloqueadas y sin respuesta del DNS. */
    private val _stats = MutableStateFlow(Triple(0L, 0L, 0L))
    val stats: StateFlow<Triple<Long, Long, Long>> = _stats

    private val _diag = MutableStateFlow<List<String>>(emptyList())
    val diag: StateFlow<List<String>> = _diag

    private val _upstreams = MutableStateFlow(repo.upstreams.joinToString(", "))
    val upstreams: StateFlow<String> = _upstreams

    private val _logState = MutableStateFlow(LogState())
    val logState: StateFlow<LogState> = _logState

    private val _logPage = MutableStateFlow(LogPage(emptyList(), 0, 1, 0, 0, 0))
    val logPage: StateFlow<LogPage> = _logPage

    init {
        // Primera ejecución (o listas que fallaron): las descargamos.
        viewModelScope.launch { repo.updateMissing() }
        viewModelScope.launch {
            while (true) {
                _stats.value = Triple(EscudoVpnService.queries.get(), EscudoVpnService.blocked.get(),
                    EscudoVpnService.failures.get())
                _diag.value = es.fabi.escudo.vpn.Diag.summary()
                if (_logState.value.live) refreshLog()
                delay(2000)
            }
        }
    }

    // ---------- Listas ----------

    fun updateAll() = viewModelScope.launch { repo.updateAll() }
    fun add(name: String, url: String) = viewModelScope.launch { repo.add(name, url) }
    fun remove(id: String) = viewModelScope.launch { repo.remove(id) }
    fun setEnabled(id: String, enabled: Boolean) = viewModelScope.launch { repo.setEnabled(id, enabled) }

    // ---------- Reglas ----------

    fun block(domain: String) { repo.blockDomain(domain); viewModelScope.launch { refreshLog() } }
    fun allow(domain: String) { repo.allowDomain(domain); viewModelScope.launch { refreshLog() } }
    fun removeRule(domain: String) = repo.removeRule(domain)

    /** Valida un dominio escrito a mano. Devuelve el dominio limpio o null. */
    fun cleanDomain(input: String): String? = ListParser.normalize(
        input.trim().removePrefix("https://").removePrefix("http://").substringBefore('/')
    )

    // ---------- Registro ----------

    fun setFilter(f: LogFilter) = updateLog { it.copy(filter = f, page = 0) }
    fun setSearch(s: String) = updateLog { it.copy(search = s, page = 0) }
    fun setPageSize(n: Int) = updateLog { it.copy(pageSize = n, page = 0) }
    fun setPage(p: Int) = updateLog { it.copy(page = p) }
    fun setLive(on: Boolean) = updateLog { it.copy(live = on) }
    fun clearLog() { QueryLog.clear(); updateLog { it.copy(page = 0) } }

    private fun updateLog(f: (LogState) -> LogState) {
        _logState.value = f(_logState.value)
        viewModelScope.launch { refreshLog() }
    }

    private suspend fun refreshLog() {
        val s = _logState.value
        _logPage.value = withContext(Dispatchers.Default) {
            QueryLog.page(QueryLog.filter(QueryLog.snapshot(), s.filter, s.search), s.page, s.pageSize)
        }
    }

    // ---------- DNS ----------

    /** Guarda los DNS de salida. Devuelve false si alguno no es una IP válida. */
    fun saveUpstreams(text: String): Boolean {
        val ips = text.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (ips.isEmpty() || !ips.all(::isIpLiteral)) return false
        repo.upstreams = ips
        _upstreams.value = ips.joinToString(", ")
        if (running.value) EscudoVpnService.start(getApplication<Application>()) // recarga los DNS sin cortar
        return true
    }

    private fun isIpLiteral(s: String): Boolean {
        val v4 = s.split('.')
        if (v4.size == 4 && v4.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }) return true
        return s.contains(':') && s.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' }
    }
}
