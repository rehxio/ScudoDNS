package es.fabi.escudo.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import es.fabi.escudo.filter.Blocklist
import es.fabi.escudo.filter.Verdict
import es.fabi.escudo.lists.ListRepository
import es.fabi.escudo.log.AppResolver
import es.fabi.escudo.log.LogEntry
import es.fabi.escudo.log.QueryLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Inet4Address
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong

/**
 * VPN local que solo captura el tráfico DNS.
 *
 * Android usa como DNS una IP falsa (10.111.222.2) que solo existe dentro del túnel.
 * Leemos cada consulta: si el dominio está en las listas respondemos 0.0.0.0 al instante;
 * si no, la reenviamos al DNS real con un socket "protegido" (fuera de la VPN).
 * El resto del tráfico del móvil no entra en el túnel: no hay pérdida de velocidad.
 */
class EscudoVpnService : VpnService() {

    @Volatile private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private var pool: ExecutorService? = null
    private var out: FileOutputStream? = null
    private val writeLock = Any()
    /** DNS de respaldo elegidos por el usuario (Quad9, Cloudflare...). */
    @Volatile private var upstreams: List<InetAddress> = emptyList()

    /** DNS de cada red real (Wi-Fi, datos): router, Pi-hole o el del operador. */
    private val networkDns = ConcurrentHashMap<Network, List<InetAddress>>()
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    /** Último servidor que respondió: se prueba primero. */
    @Volatile private var preferred: Upstream? = null

    private data class Upstream(val address: InetAddress, val network: Network?)
    private val apps by lazy { AppResolver(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    @Synchronized
    private fun startVpn() {
        val repo = ListRepository.get(this)
        upstreams = repo.upstreams.mapNotNull { runCatching { InetAddress.getByName(it.trim()) }.getOrNull() }
        if (tun != null) return // ya en marcha: solo se recargan los DNS
        Blocklist.ensureLoaded(repo.dir)
        watchNetworks()

        val builder = Builder()
            .setSession("Escudo DNS")
            .setMtu(1500)
            .addAddress(TUN_ADDR4, 32)
            .addDnsServer(DNS_ADDR4)
            .addRoute(DNS_ADDR4, 32)
            .addAddress(TUN_ADDR6, 128)
            .addDnsServer(DNS_ADDR6)
            .addRoute(DNS_ADDR6, 128)
        // Las descargas de listas y el reenvío DNS de esta app no pasan por la VPN.
        runCatching { builder.addDisallowedApplication(packageName) }

        val fd = try { builder.establish() } catch (e: Exception) {
            Log.e(TAG, "No se pudo crear la VPN", e); null
        }
        if (fd == null) { stopSelf(); return }
        tun = fd
        Diag.reset()
        out = FileOutputStream(fd.fileDescriptor)
        pool = Executors.newFixedThreadPool(32)
        reader = Thread({ readLoop(fd) }, "escudo-tun").apply { start() }
        _running.value = true
    }

    /**
     * Lectura no bloqueante con poll(): así podemos parar el hilo de forma fiable
     * (en algunos Android cerrar el descriptor no desbloquea un read() bloqueante).
     */
    private fun readLoop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        val pollFd = StructPollfd().apply {
            this.fd = fd.fileDescriptor
            events = OsConstants.POLLIN.toShort()
        }
        try {
            while (tun === fd) {
                val ready = try { Os.poll(arrayOf(pollFd), 1000) } catch (e: ErrnoException) {
                    if (e.errno == OsConstants.EINTR) continue else throw IOException(e)
                }
                if (ready <= 0) continue
                if (pollFd.revents.toInt() and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL) != 0) break
                val n = try { input.read(buf) } catch (e: IOException) {
                    if (tun !== fd) break else continue // EAGAIN u otro error transitorio
                }
                if (n < 0) break
                if (n > 0) {
                    try { handlePacket(buf, n) } catch (e: Exception) { Log.w(TAG, "Paquete descartado", e) }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Fin de la lectura del túnel", e)
        } finally {
            if (tun === fd) stopVpn()
        }
    }

    private fun handlePacket(buf: ByteArray, n: Int) {
        Diag.packetsIn.incrementAndGet()
        val pkt = Packets.parse(buf, n)
        if (pkt == null || pkt.dstPort != 53) {
            Diag.other.incrementAndGet()
            Diag.lastOther = Packets.describe(buf, n)
            // Conexiones TCP al DNS (DNS sobre TLS en el 853 o DNS por TCP): las rechazamos al momento.
            Packets.parseTcpSyn(buf, n)?.let { write(Packets.buildTcpRst(it)) }
            return
        }
        queries.incrementAndGet()

        // El buffer se reutiliza en la siguiente lectura: copiamos la consulta.
        val query = buf.copyOfRange(pkt.payloadOffset, pkt.payloadOffset + pkt.payloadLength)
        try {
            pool?.execute { process(pkt, query) }
        } catch (_: RejectedExecutionException) { }
    }

    private fun process(pkt: UdpPacket, query: ByteArray) {
        val q = Dns.parseQuery(query, 0, query.size)
        val verdict = q?.let { Blocklist.decide(it.name) } ?: Verdict.ALLOWED
        // La app se consulta antes de responder: después el sistema ya habría cerrado el socket.
        val app = if (q != null) apps.appFor(pkt.src, pkt.srcPort, pkt.dst, pkt.dstPort) else null

        if (q != null && verdict.isBlocked) {
            blocked.incrementAndGet()
            val resp = Dns.blockedResponse(query, 0, q)
            write(Packets.build(pkt.ipVersion, pkt.dst, pkt.src, 53, pkt.srcPort, resp, 0, resp.size))
            QueryLog.add(LogEntry(System.currentTimeMillis(), q.name, q.qtype, verdict, app))
        } else {
            val ok = forward(pkt, query, q)
            if (q != null) QueryLog.add(LogEntry(System.currentTimeMillis(), q.name, q.qtype, verdict, app, failed = !ok))
        }
    }

    /** Vigila las redes reales (no VPN) para saber qué DNS da cada una. */
    private fun watchNetworks() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                // IPv4 primero: los DNS IPv6 de enlace local (fe80::) a veces no responden.
                networkDns[network] = lp.dnsServers.sortedBy { if (it is Inet4Address) 0 else 1 }
                Diag.privateDns = if (Build.VERSION.SDK_INT >= 28 && lp.isPrivateDnsActive)
                    (lp.privateDnsServerName ?: "automático") else null
            }
            override fun onLost(network: Network) {
                networkDns.remove(network)
                if (preferred?.network == network) preferred = null
            }
        }
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        try { cm.registerNetworkCallback(req, cb); netCallback = cb } catch (e: Exception) { Log.w(TAG, "Sin callback de red", e) }
    }

    /** Orden de prueba: el último que funcionó, los DNS de la red (Wi-Fi antes que datos) y los de respaldo. */
    private fun candidates(): List<Upstream> {
        val cm = getSystemService(ConnectivityManager::class.java)
        val nets = networkDns.entries.sortedBy { (net, _) ->
            val caps = cm.getNetworkCapabilities(net)
            when {
                caps == null -> 3
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 0
                else -> 1
            }
        }
        val list = ArrayList<Upstream>()
        preferred?.let { list += it }
        for ((net, servers) in nets) for (a in servers) list += Upstream(a, net)
        for (a in upstreams) list += Upstream(a, null)
        val result = list.distinct().take(4)
        Diag.dnsInUse = result.joinToString(", ") { it.address.hostAddress ?: "?" }.ifEmpty { "ninguno" }
        return result
    }

    /** Reenvía la consulta. Devuelve false si ningún servidor respondió (y entonces contesta SERVFAIL). */
    private fun forward(pkt: UdpPacket, query: ByteArray, q: DnsQuery?): Boolean {
        val queryId = Dns.id(query)
        for (up in candidates()) {
            try {
                DatagramSocket().use { s ->
                    if (up.network != null) up.network.bindSocket(s) else protect(s)
                    s.soTimeout = 2000
                    s.send(DatagramPacket(query, query.size, up.address, 53))
                    val rb = ByteArray(4096)
                    while (true) { // ignoramos respuestas que no son de esta consulta
                        val rp = DatagramPacket(rb, rb.size)
                        s.receive(rp)
                        if (rp.length >= 12 && Dns.id(rb) == queryId) {
                            write(Packets.build(pkt.ipVersion, pkt.dst, pkt.src, 53, pkt.srcPort, rb, 0, rp.length))
                            preferred = up
                            Diag.answered.incrementAndGet()
                            Diag.lastOk = up.address.hostAddress
                            return true
                        }
                    }
                }
            } catch (e: SocketTimeoutException) {
                if (preferred == up) preferred = null
                Diag.lastError = "${up.address.hostAddress}: sin respuesta en 2 s"
            } catch (e: Exception) {
                if (preferred == up) preferred = null
                Diag.lastError = "${up.address.hostAddress}: ${e.javaClass.simpleName} ${e.message ?: ""}"
                Log.d(TAG, "DNS ${up.address} falló: ${e.message}")
            }
        }
        failures.incrementAndGet()
        if (q != null) {
            val resp = Dns.servfail(query, 0, q)
            write(Packets.build(pkt.ipVersion, pkt.dst, pkt.src, 53, pkt.srcPort, resp, 0, resp.size))
        }
        return false
    }

    private fun write(packet: ByteArray) {
        synchronized(writeLock) {
            try {
                val o = out
                if (o == null) Diag.writeErrors.incrementAndGet() else o.write(packet)
            } catch (e: IOException) {
                Diag.writeErrors.incrementAndGet()
                Diag.lastError = "Escribir en el túnel: ${e.message}"
            }
        }
    }

    @Synchronized
    private fun stopVpn() {
        val fd = tun ?: return
        tun = null // el hilo lector lo ve en menos de 1 s y termina
        _running.value = false
        reader = null
        pool?.shutdownNow()
        pool = null
        netCallback?.let { cb -> runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb) } }
        netCallback = null
        networkDns.clear()
        preferred = null
        synchronized(writeLock) { out = null }
        runCatching { fd.close() }
    }

    override fun onRevoke() { // otra VPN tomó el control o el usuario la quitó
        stopVpn()
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "EscudoVpn"
        const val ACTION_START = "es.fabi.escudo.START"
        const val ACTION_STOP = "es.fabi.escudo.STOP"

        private const val TUN_ADDR4 = "10.111.222.1"
        private const val DNS_ADDR4 = "10.111.222.2"
        private const val TUN_ADDR6 = "fd00:6573:6375::1"
        private const val DNS_ADDR6 = "fd00:6573:6375::2"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running
        val queries = AtomicLong()
        val blocked = AtomicLong()
        val failures = AtomicLong()

        fun start(ctx: Context) =
            ctx.startService(Intent(ctx, EscudoVpnService::class.java).setAction(ACTION_START))

        fun stop(ctx: Context) =
            ctx.startService(Intent(ctx, EscudoVpnService::class.java).setAction(ACTION_STOP))
    }
}

/** Contadores para la sección "Diagnóstico" de la pantalla de inicio. */
object Diag {
    val packetsIn = AtomicLong()
    val other = AtomicLong()
    val answered = AtomicLong()
    val writeErrors = AtomicLong()
    @Volatile var lastOther: String? = null
    @Volatile var lastError: String? = null
    @Volatile var lastOk: String? = null
    @Volatile var dnsInUse: String = "aún sin consultas"
    @Volatile var privateDns: String? = null

    fun reset() {
        packetsIn.set(0); other.set(0); answered.set(0); writeErrors.set(0)
        lastOther = null; lastError = null; lastOk = null; dnsInUse = "aún sin consultas"
    }

    fun summary(): List<String> = listOfNotNull(
        "Paquetes leídos: ${packetsIn.get()} · otros: ${other.get()}" + (lastOther?.let { " (último $it)" } ?: ""),
        "Respondidas por el DNS: ${answered.get()}" + (lastOk?.let { " (último $it)" } ?: ""),
        "Sin respuesta: ${EscudoVpnService.failures.get()} · errores al escribir: ${writeErrors.get()}",
        "DNS en uso: $dnsInUse",
        privateDns?.let { "⚠ DNS privado activo: $it. Desactívalo en Ajustes → Red." },
        lastError?.let { "Último error: $it" },
    )
}
