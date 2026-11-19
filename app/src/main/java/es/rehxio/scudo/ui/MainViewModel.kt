package es.rehxio.scudo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import es.rehxio.scudo.filter.ListParser
import es.rehxio.scudo.lists.ListRepository
import es.rehxio.scudo.log.LogFilter
import es.rehxio.scudo.log.LogPage
import es.rehxio.scudo.log.QueryLog
import es.rehxio.scudo.vpn.ScudoVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import android.os.PowerManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import es.rehxio.scudo.update.AppUpdater
import es.rehxio.scudo.update.UpdateState

/** Lo que hace falta para que Android no cierre la protección. */
data class KeepAlive(val batteryUnrestricted: Boolean)

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
    val running = ScudoVpnService.running

    /** Consultas, bloqueadas y sin respuesta del DNS. */
    private val _stats = MutableStateFlow(Triple(0L, 0L, 0L))
    val stats: StateFlow<Triple<Long, Long, Long>> = _stats

    private val _diag = MutableStateFlow<List<String>>(emptyList())
    val diag: StateFlow<List<String>> = _diag

    /** Nombre del DNS privado de Android si está activo (rompe la resolución con la VPN). */
    private val _privateDns = MutableStateFlow<String?>(null)
    val privateDns: StateFlow<String?> = _privateDns

    private val _upstreams = MutableStateFlow(repo.upstreams.joinToString(", "))
    val upstreams: StateFlow<String> = _upstreams

    private val visible = MutableStateFlow(false)
    fun setVisible(v: Boolean) { visible.value = v }

    /** Si Android puede cerrar la app para ahorrar batería (null = no se sabe). */
    private val _keepAlive = MutableStateFlow(KeepAlive(batteryUnrestricted = true))
    val keepAlive: StateFlow<KeepAlive> = _keepAlive

    private fun keepAliveStatus(): KeepAlive {
        val app = getApplication<Application>()
        val pm = app.getSystemService(PowerManager::class.java)
        return KeepAlive(batteryUnrestricted = pm?.isIgnoringBatteryOptimizations(app.packageName) ?: true)
    }

    private val _logState = MutableStateFlow(LogState())
    val logState: StateFlow<LogState> = _logState

    private val _logPage = MutableStateFlow(LogPage(emptyList(), 0, 1, 0, 0, 0))
    val logPage: StateFlow<LogPage> = _logPage

    // ---------- Versión nueva ----------

    val update: StateFlow<UpdateState> = AppUpdater.state
    private val _autoUpdate = MutableStateFlow(AppUpdater.isAutoCheck(app))
    val autoUpdate: StateFlow<Boolean> = _autoUpdate

    fun setAutoUpdate(on: Boolean) {
        AppUpdater.setAutoCheck(getApplication(), on)
        _autoUpdate.value = on
    }
    fun checkUpdate() = viewModelScope.launch { AppUpdater.check(getApplication(), notify = false) }
    fun downloadUpdate(ctx: Context) {
        val s = update.value as? UpdateState.Available ?: return
        AppUpdater.openDownload(ctx, s.info)
    }
    fun dismissUpdate() = AppUpdater.dismiss()

    init {
        // Primera ejecución (o listas que fallaron): las descargamos.
        viewModelScope.launch { repo.updateMissing() }
        viewModelScope.launch { AppUpdater.checkIfDue(getApplication()) }
        viewModelScope.launch {
            while (true) {
                visible.first { it } // en segundo plano se queda dormido sin consumir
                _keepAlive.value = keepAliveStatus()
                _stats.value = Triple(ScudoVpnService.queries.get(), ScudoVpnService.blocked.get(),
                    ScudoVpnService.failures.get())
                _diag.value = es.rehxio.scudo.vpn.Diag.summary()
                _privateDns.value = es.rehxio.scudo.vpn.Diag.privateDns
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
        if (running.value) ScudoVpnService.start(getApplication<Application>()) // recarga los DNS sin cortar
        return true
    }

    private fun isIpLiteral(s: String): Boolean {
        val v4 = s.split('.')
        if (v4.size == 4 && v4.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }) return true
        return s.contains(':') && s.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' }
    }
}
