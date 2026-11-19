package es.rehxio.scudo.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import es.rehxio.scudo.MainActivity
import es.rehxio.scudo.R
import java.io.FileDescriptor
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import es.rehxio.scudo.filter.Blocklist
import es.rehxio.scudo.filter.Verdict
import es.rehxio.scudo.lists.ListRepository
import es.rehxio.scudo.log.AppResolver
import es.rehxio.scudo.log.LogEntry
import es.rehxio.scudo.log.QueryLog
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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
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
class ScudoVpnService : VpnService() {

    @Volatile private var tun: ParcelFileDescriptor? = null
    /** Extremo de escritura de una tubería que despierta al hilo lector para que termine. */
    @Volatile private var wakeWrite: FileDescriptor? = null
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

    /** Respuestas recientes: las repetidas se contestan al instante. */
    private val cache = DnsCache()
    /** Claves que se están actualizando en segundo plano (para no repetir el trabajo). */
    private val refreshing: MutableSet<String> = ConcurrentHashMap.newKeySet()
    /** Intentos contra cada servidor DNS (se lanzan escalonados). */
    private var attempts: ScheduledThreadPoolExecutor? = null
    /** Tiempo de respuesta medio (ms) y fallos seguidos de cada servidor. */
    private val srtt = ConcurrentHashMap<InetAddress, Double>()
    private val failStreak = ConcurrentHashMap<InetAddress, Int>()

    private data class Upstream(val address: InetAddress, val network: Network?)
    private val apps by lazy { AppResolver(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            setWanted(this, false) // el usuario la ha apagado: no se vuelve a encender sola
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }
        // Servicio en primer plano desde el primer momento: Android no lo cierra para ahorrar
        // memoria y, si se arrancó con startForegroundService (arranque del móvil), cumple el plazo.
        goForeground()
        startVpn()
        if (tun == null) { // sin permiso de VPN u otro error: no nos quedamos a medias
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        setWanted(this, true)
        // START_STICKY: si el sistema mata el proceso, lo vuelve a arrancar en cuanto puede.
        return START_STICKY
    }

    /** Notificación mínima y silenciosa: sin sonido, sin icono en la barra de estado. */
    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Protección activa", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Aviso permanente mientras ScudoDNS filtra el DNS"
                    setShowBadge(false)
                }
            )
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags)
        val pause = PendingIntent.getService(this, 1,
            Intent(this, ScudoVpnService::class.java).setAction(ACTION_STOP), flags)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("ScudoDNS protege este móvil")
            .setContentText("Bloqueando anuncios y rastreadores")
            .setContentIntent(open)
            .addAction(0, "Pausar", pause)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (e: Exception) {
            // Algunos reinicios en segundo plano no lo permiten (Android 12+). La VPN sigue funcionando:
            // el sistema la mantiene con prioridad alta mientras el túnel está abierto.
            Log.w(TAG, "No se pudo pasar a primer plano", e)
        }
    }

    @Synchronized
    private fun startVpn() {
        val repo = ListRepository.get(this)
        upstreams = repo.upstreams.mapNotNull { runCatching { InetAddress.getByName(it.trim()) }.getOrNull() }
        cache.clear() // los DNS pueden haber cambiado
        if (tun != null) return // ya en marcha: solo se recargan los DNS
        Blocklist.ensureLoaded(repo.dir)
        watchNetworks()

        val builder = Builder()
            .setSession("ScudoDNS")
            .setMtu(1500)
            .addAddress(TUN_ADDR4, 32)
            .addDnsServer(DNS_ADDR4)
            .addRoute(DNS_ADDR4, 32)
            .addAddress(TUN_ADDR6, 128)
            .addDnsServer(DNS_ADDR6)
            .addRoute(DNS_ADDR6, 128)
        // Las descargas de listas y el reenvío DNS de esta app no pasan por la VPN.
        runCatching { builder.addDisallowedApplication(packageName) }
        // Android 10+ trata cualquier VPN como conexión "de pago" salvo que se diga lo contrario:
        // las apps dejarían de hacer copias de seguridad o descargas grandes por Wi-Fi.
        // Así la VPN hereda lo que sea la red real (Wi-Fi sin límite, datos medidos).
        if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false)

        val fd = try { builder.establish() } catch (e: Exception) {
            Log.e(TAG, "No se pudo crear la VPN", e); null
        }
        if (fd == null) { stopSelf(); return }
        tun = fd
        Diag.reset()
        out = FileOutputStream(fd.fileDescriptor)
        // Cola acotada: si una app inunda de consultas, se descartan las que sobran
        // en vez de acumularlas en memoria hasta agotarla.
        pool = ThreadPoolExecutor(
            WORKERS, WORKERS, 30, TimeUnit.SECONDS,
            ArrayBlockingQueue(QUEUE_CAPACITY), ThreadPoolExecutor.AbortPolicy(),
        ).apply { allowCoreThreadTimeOut(true) } // sin consultas, los hilos se liberan
        attempts = ScheduledThreadPoolExecutor(ATTEMPT_THREADS).apply {
            removeOnCancelPolicy = true
            setKeepAliveTime(30, TimeUnit.SECONDS)
            allowCoreThreadTimeOut(true)
        }
        val (wakeRead, wakeW) = Os.pipe()
        wakeWrite = wakeW
        reader = Thread({ readLoop(fd, wakeRead) }, "scudo-tun").apply { start() }
        _running.value = true
    }

    /**
     * Lectura con poll() sin tiempo de espera: el hilo duerme hasta que llega un paquete o hasta que
     * stopVpn() escribe en la tubería de aviso. Así no despierta la CPU cada segundo para nada.
     */
    private fun readLoop(fd: ParcelFileDescriptor, wakeRead: FileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        val tunPoll = StructPollfd().apply {
            this.fd = fd.fileDescriptor
            events = OsConstants.POLLIN.toShort()
        }
        val wakePoll = StructPollfd().apply {
            this.fd = wakeRead
            events = OsConstants.POLLIN.toShort()
        }
        val fds = arrayOf(tunPoll, wakePoll)
        try {
            while (tun === fd) {
                try { Os.poll(fds, -1) } catch (e: ErrnoException) {
                    if (e.errno == OsConstants.EINTR) continue else throw IOException(e)
                }
                if (wakePoll.revents.toInt() != 0) break // nos piden parar
                val ev = tunPoll.revents.toInt()
                if (ev and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL) != 0) break
                if (ev and OsConstants.POLLIN == 0) continue
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
            runCatching { Os.close(wakeRead) }
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
        } catch (_: RejectedExecutionException) {
            Diag.dropped.incrementAndGet()
        }
    }

    private fun process(pkt: UdpPacket, query: ByteArray) {
        val q = Dns.parseQuery(query, 0, query.size)
        if (q == null) { // consulta que no sabemos leer: se reenvía tal cual, sin caché ni registro
            resolve(query, null)?.let { reply(pkt, it) } ?: failures.incrementAndGet()
            return
        }
        val verdict = Blocklist.decide(q.name)
        val now = System.currentTimeMillis()

        if (verdict.isBlocked) {
            // Respuesta local: averiguar la app (~1 ms) antes de responder no se nota.
            val app = apps.appFor(pkt.src, pkt.srcPort, pkt.dst, pkt.dstPort)
            blocked.incrementAndGet()
            reply(pkt, Dns.blockedResponse(query, 0, q))
            QueryLog.add(LogEntry(now, q.name, q.qtype, verdict, app))
            return
        }

        val key = DnsCache.key(query, q)
        val hit = cache.get(key, query, q, now)
        if (hit != null) {
            val app = apps.appFor(pkt.src, pkt.srcPort, pkt.dst, pkt.dstPort)
            reply(pkt, hit.response)
            Diag.cacheHits.incrementAndGet()
            if (hit.stale) refreshInBackground(key, query, q)
            QueryLog.add(LogEntry(now, q.name, q.qtype, verdict, app, cached = true))
            return
        }

        // Hay que preguntar fuera: la app se averigua en paralelo para no retrasar la respuesta.
        val appTask = attempts?.let { ex ->
            runCatching { ex.submit(Callable<String?> { apps.appFor(pkt.src, pkt.srcPort, pkt.dst, pkt.dstPort) }) }.getOrNull()
        }
        val resp = resolve(query, q)
        if (resp != null) {
            reply(pkt, resp)
            cache.put(key, resp, resp.size, System.currentTimeMillis())
        } else {
            failures.incrementAndGet()
            reply(pkt, Dns.servfail(query, 0, q))
        }
        val app = runCatching { appTask?.get(300, TimeUnit.MILLISECONDS) }.getOrNull()
        QueryLog.add(LogEntry(now, q.name, q.qtype, verdict, app, failed = resp == null))
    }

    private fun reply(pkt: UdpPacket, payload: ByteArray) =
        write(Packets.build(pkt.ipVersion, pkt.dst, pkt.src, 53, pkt.srcPort, payload, 0, payload.size))

    /** Actualiza en segundo plano una respuesta caducada que ya se ha servido desde la caché. */
    private fun refreshInBackground(key: String, query: ByteArray, q: DnsQuery) {
        if (!refreshing.add(key)) return
        try {
            pool?.execute {
                try {
                    resolve(query, q)?.let { cache.put(key, it, it.size, System.currentTimeMillis()) }
                } finally {
                    refreshing.remove(key)
                }
            } ?: refreshing.remove(key)
        } catch (_: RejectedExecutionException) {
            refreshing.remove(key)
        }
    }

    /** Vigila las redes reales (no VPN) para saber qué DNS da cada una. */
    private fun watchNetworks() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                // IPv4 primero: los DNS IPv6 de enlace local (fe80::) a veces no responden.
                val servers = lp.dnsServers.sortedBy { if (it is Inet4Address) 0 else 1 }
                if (networkDns.put(network, servers) != servers) cache.clear() // red o DNS nuevos
                Diag.privateDns = if (Build.VERSION.SDK_INT >= 28 && lp.isPrivateDnsActive)
                    (lp.privateDnsServerName ?: "automático") else null
            }
            override fun onLost(network: Network) {
                networkDns.remove(network)
                cache.clear()
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
        // Los que han fallado varias veces seguidas pasan al final (se siguen probando).
        val result = list.distinct().sortedBy { if ((failStreak[it.address] ?: 0) >= 3) 1 else 0 }.take(4)
        Diag.dnsInUse = result.joinToString(", ") { it.address.hostAddress ?: "?" }.ifEmpty { "ninguno" }
        return result
    }

    /**
     * Pregunta a los servidores DNS de forma escalonada: se lanza el primero y, si no ha contestado
     * en un tiempo algo mayor de lo que suele tardar, se lanza también el siguiente. Gana la primera
     * respuesta válida. Así un servidor lento o un paquete perdido no hacen esperar 2 segundos.
     */
    private fun resolve(query: ByteArray, q: DnsQuery?): ByteArray? {
        val exec = attempts ?: return null
        val cands = candidates()
        if (cands.isEmpty()) return null
        val result = AtomicReference<ByteArray?>(null)
        val done = CountDownLatch(1)
        val pending = AtomicInteger(cands.size)
        val sockets = ConcurrentLinkedQueue<DatagramSocket>()
        val hedge = hedgeDelayMs(cands.first())
        val scheduled = cands.mapIndexed { i, up ->
            try {
                exec.schedule(Runnable {
                    try {
                        if (result.get() == null) attempt(up, query, q, result, done, sockets)
                    } finally {
                        if (pending.decrementAndGet() == 0) done.countDown()
                    }
                }, i * hedge, TimeUnit.MILLISECONDS)
            } catch (_: RejectedExecutionException) {
                if (pending.decrementAndGet() == 0) done.countDown()
                null
            }
        }
        try {
            done.await(UPSTREAM_TIMEOUT_MS + hedge * (cands.size - 1) + 250, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        scheduled.forEach { it?.cancel(false) }         // los intentos que aún no habían empezado
        sockets.forEach { runCatching { it.close() } }  // y los que siguen esperando
        return result.get()
    }

    private fun attempt(
        up: Upstream, query: ByteArray, q: DnsQuery?,
        result: AtomicReference<ByteArray?>, done: CountDownLatch, sockets: ConcurrentLinkedQueue<DatagramSocket>,
    ) {
        val start = System.nanoTime()
        try {
            DatagramSocket().use { s ->
                sockets += s
                if (up.network != null) up.network.bindSocket(s) else protect(s)
                // Socket "conectado": el sistema descarta cualquier datagrama que no venga
                // de este servidor y puerto, lo que impide colar respuestas falsas desde otra IP.
                s.connect(up.address, 53)
                if (result.get() != null) return
                s.send(DatagramPacket(query, query.size))
                val rb = ByteArray(4096)
                val deadline = System.currentTimeMillis() + UPSTREAM_TIMEOUT_MS
                while (true) {
                    val left = deadline - System.currentTimeMillis()
                    if (left <= 0) throw SocketTimeoutException()
                    s.soTimeout = left.toInt()
                    val rp = DatagramPacket(rb, rb.size)
                    s.receive(rp)
                    // Solo aceptamos la respuesta a ESTA pregunta: mismo ID, marcada como
                    // respuesta y con la misma sección de pregunta.
                    if (Dns.isResponseTo(rb, rp.length, query, q)) {
                        recordSuccess(up, (System.nanoTime() - start) / 1_000_000.0)
                        if (result.compareAndSet(null, rb.copyOf(rp.length))) {
                            preferred = up
                            Diag.answered.incrementAndGet()
                            Diag.lastOk = up.address.hostAddress
                            done.countDown()
                        }
                        return
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            recordFailure(up, "sin respuesta en ${UPSTREAM_TIMEOUT_MS / 1000} s")
        } catch (e: Exception) {
            // Si ya contestó otro, este socket lo hemos cerrado nosotros: no es un fallo.
            if (result.get() == null) recordFailure(up, "${e.javaClass.simpleName} ${e.message ?: ""}")
        }
    }

    /** Espera antes de lanzar el siguiente servidor: algo más que lo que suele tardar el primero. */
    private fun hedgeDelayMs(first: Upstream): Long {
        val rtt = srtt[first.address] ?: 100.0
        return (rtt * 2.5 + 20).toLong().coerceIn(HEDGE_MIN_MS, HEDGE_MAX_MS)
    }

    private fun recordSuccess(up: Upstream, ms: Double) {
        srtt.merge(up.address, ms) { old, new -> old * 0.8 + new * 0.2 }
        failStreak.remove(up.address)
    }

    private fun recordFailure(up: Upstream, why: String) {
        failStreak.merge(up.address, 1) { a, b -> a + b }
        if (preferred == up) preferred = null
        Diag.lastError = "${up.address.hostAddress}: $why"
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
        tun = null
        wakeWrite?.let { w -> // despierta al hilo lector para que termine
            runCatching { Os.write(w, byteArrayOf(1), 0, 1) }
            runCatching { Os.close(w) }
        }
        wakeWrite = null
        _running.value = false
        reader = null
        pool?.shutdownNow()
        pool = null
        attempts?.shutdownNow()
        attempts = null
        cache.clear()
        netCallback?.let { cb -> runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb) } }
        netCallback = null
        networkDns.clear()
        preferred = null
        synchronized(writeLock) { out = null }
        runCatching { fd.close() }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onRevoke() { // otra VPN tomó el control o el usuario la quitó
        setWanted(this, false) // no peleamos con la otra VPN volviendo a encendernos
        stopVpn()
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScudoVpn"
        private const val WORKERS = 32
        private const val QUEUE_CAPACITY = 512
        private const val UPSTREAM_TIMEOUT_MS = 2000L
        private const val ATTEMPT_THREADS = 64
        private const val HEDGE_MIN_MS = 60L
        private const val HEDGE_MAX_MS = 400L
        const val ACTION_START = "es.rehxio.scudo.START"
        const val ACTION_STOP = "es.rehxio.scudo.STOP"

        private const val TUN_ADDR4 = "10.111.222.1"
        private const val DNS_ADDR4 = "10.111.222.2"
        private const val TUN_ADDR6 = "fd00:6573:6375::1"
        private const val DNS_ADDR6 = "fd00:6573:6375::2"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running
        val queries = AtomicLong()
        val blocked = AtomicLong()
        val failures = AtomicLong()

        private const val CHANNEL_ID = "proteccion"
        private const val NOTIFICATION_ID = 1
        private const val PREFS = "scudo"
        private const val KEY_WANTED = "vpnWanted"

        /** Recuerda si el usuario quiere la protección encendida (para volver a encenderla tras reiniciar o actualizar). */
        fun setWanted(ctx: Context, on: Boolean) =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_WANTED, on).apply()

        fun isWanted(ctx: Context) =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WANTED, false)

        fun start(ctx: Context) =
            ContextCompat.startForegroundService(ctx, Intent(ctx, ScudoVpnService::class.java).setAction(ACTION_START))

        fun stop(ctx: Context) =
            ctx.startService(Intent(ctx, ScudoVpnService::class.java).setAction(ACTION_STOP))
    }
}

/** Contadores para la sección "Diagnóstico" de la pantalla de inicio. */
object Diag {
    val packetsIn = AtomicLong()
    val other = AtomicLong()
    val answered = AtomicLong()
    val writeErrors = AtomicLong()
    val dropped = AtomicLong()
    val cacheHits = AtomicLong()
    @Volatile var lastOther: String? = null
    @Volatile var lastError: String? = null
    @Volatile var lastOk: String? = null
    @Volatile var dnsInUse: String = "aún sin consultas"
    @Volatile var privateDns: String? = null

    fun reset() {
        packetsIn.set(0); other.set(0); answered.set(0); writeErrors.set(0); dropped.set(0); cacheHits.set(0)
        lastOther = null; lastError = null; lastOk = null; dnsInUse = "aún sin consultas"
    }

    fun summary(): List<String> = listOfNotNull(
        "Paquetes leídos: ${packetsIn.get()} · otros: ${other.get()}" + (lastOther?.let { " (último $it)" } ?: ""),
        "Respondidas por el DNS: ${answered.get()}" + (lastOk?.let { " (último $it)" } ?: "") +
            " · desde caché: ${cacheHits.get()}",
        "Sin respuesta: ${ScudoVpnService.failures.get()} · errores al escribir: ${writeErrors.get()}" +
            (if (dropped.get() > 0) " · descartadas por saturación: ${dropped.get()}" else ""),
        "DNS en uso: $dnsInUse",
        privateDns?.let { "⚠ DNS privado activo: $it. Desactívalo en Ajustes → Red." },
        lastError?.let { "Último error: $it" },
    )
}
