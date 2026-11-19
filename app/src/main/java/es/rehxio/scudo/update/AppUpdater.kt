package es.rehxio.scudo.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import es.rehxio.scudo.BuildConfig
import es.rehxio.scudo.MainActivity
import es.rehxio.scudo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Una versión publicada en GitHub Releases. */
data class ReleaseInfo(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val size: Long,
    /** Huella SHA-256 que publica GitHub para el APK (puede faltar en releases antiguas). */
    val sha256: String?,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState
    data class Ready(val info: ReleaseInfo, val file: File) : UpdateState
    data class Failed(val info: ReleaseInfo?, val message: String) : UpdateState
}

/**
 * Busca versiones nuevas en GitHub Releases, descarga el APK y se lo pasa al instalador de Android.
 *
 * Medidas de seguridad: solo HTTPS y solo dominios de GitHub; tamaño máximo; la huella SHA-256 del
 * archivo debe coincidir con la que publica GitHub; y el APK debe ser de esta misma app, de una
 * versión mayor y firmado con la misma clave. Aun así, Android pide confirmación antes de instalar
 * y rechaza por su cuenta cualquier actualización firmada con otra clave.
 */
object AppUpdater {
    private const val TAG = "ScudoUpdate"
    private const val PREFS = "scudo"
    private const val KEY_AUTO = "updateAutoCheck"
    private const val KEY_LAST = "updateLastCheck"
    private const val KEY_NOTIFIED = "updateNotified"
    private const val MAX_APK_BYTES = 60_000_000L
    private const val CHANNEL_ID = "actualizaciones"
    private const val NOTIFICATION_ID = 2
    private const val AUTO_INTERVAL_MS = 6 * 60 * 60 * 1000L

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state
    private val mutex = Mutex()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAutoCheck(ctx: Context) = prefs(ctx).getBoolean(KEY_AUTO, true)
    fun setAutoCheck(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(KEY_AUTO, on).apply()

    /** Comprobación automática al abrir la app, como mucho cada 6 horas. */
    suspend fun checkIfDue(ctx: Context) {
        if (!isAutoCheck(ctx)) return
        if (System.currentTimeMillis() - prefs(ctx).getLong(KEY_LAST, 0) < AUTO_INTERVAL_MS) return
        check(ctx, notify = false)
    }

    /** Pregunta a GitHub por la última versión. Con [notify], avisa con una notificación (una vez por versión). */
    suspend fun check(ctx: Context, notify: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _state.value
            if (current is UpdateState.Downloading || current is UpdateState.Ready) return@withLock
            _state.value = UpdateState.Checking
            _state.value = try {
                val info = fetchLatest()
                prefs(ctx).edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
                if (info != null && Versions.isNewer(info.version, BuildConfig.VERSION_NAME)) {
                    if (notify) notifyOnce(ctx, info)
                    UpdateState.Available(info)
                } else {
                    cleanDownloads(ctx)
                    UpdateState.UpToDate
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo comprobar si hay versión nueva", e)
                UpdateState.Failed(null, "No se pudo comprobar: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun fetchLatest(): ReleaseInfo? {
        val repo = BuildConfig.REPO_URL.removePrefix("https://github.com/").trim('/')
        val json = httpGetText("https://api.github.com/repos/$repo/releases/latest")
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val tag = o.optString("tag_name").ifBlank { return null }
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val digest = a.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase()
            return ReleaseInfo(
                version = tag.removePrefix("v"),
                notes = Versions.plainNotes(o.optString("body")),
                pageUrl = o.optString("html_url").ifBlank { "${BuildConfig.REPO_URL}/releases/latest" },
                apkUrl = a.optString("browser_download_url"),
                size = a.optLong("size"),
                sha256 = digest,
            )
        }
        return null
    }

    /** Descarga el APK, lo comprueba y deja el estado en Ready (o Failed). */
    suspend fun download(ctx: Context, info: ReleaseInfo) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _state.value = UpdateState.Downloading(info, 0f)
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "ScudoDNS-${info.version.replace(Regex("[^0-9A-Za-z.]"), "")}.apk")
            _state.value = try {
                if (info.size <= 0 || info.size > MAX_APK_BYTES) throw IOException("Tamaño del archivo no válido")
                val hash = httpDownload(info.apkUrl, file, info.size) { p -> _state.value = UpdateState.Downloading(info, p) }
                if (file.length() != info.size) throw IOException("El archivo descargado está incompleto")
                if (info.sha256 != null && info.sha256 != hash) throw IOException("La huella del archivo no coincide con la publicada")
                verifyApk(ctx, file)
                UpdateState.Ready(info, file)
            } catch (e: Exception) {
                Log.w(TAG, "Descarga de la actualización fallida", e)
                file.delete()
                UpdateState.Failed(info, e.message ?: "Error al descargar")
            }
        }
    }

    /** Comprueba que el APK es de esta app, más nuevo y firmado con la misma clave. */
    private fun verifyApk(ctx: Context, file: File) {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.path, flags) ?: throw IOException("El archivo no es un APK válido")
        if (archive.packageName != ctx.packageName) throw IOException("El APK no es de ScudoDNS")
        val installed = pm.getPackageInfo(ctx.packageName, flags)
        if (versionCode(archive) <= versionCode(installed)) throw IOException("El APK no es más nuevo que la versión instalada")
        val a = certHashes(archive)
        val b = certHashes(installed)
        // Si Android no deja leer la firma del archivo (pasa en algunas versiones antiguas), lo comprueba
        // igualmente el sistema al instalar: nunca acepta una actualización firmada con otra clave.
        if (a.isNotEmpty() && b.isNotEmpty() && a != b) throw IOException("El APK está firmado con otra clave")
    }

    @Suppress("DEPRECATION")
    private fun versionCode(p: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun certHashes(p: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            val si = p.signingInfo ?: return emptySet()
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else p.signatures
        return sigs.orEmpty().map { sha256Hex(it.toByteArray()) }.toSet()
    }

    /**
     * Abre el instalador de Android. La primera vez hay que permitir "Instalar apps desconocidas"
     * para ScudoDNS: entonces se abre ese ajuste y se devuelve false (el usuario vuelve y pulsa de nuevo).
     */
    fun install(ctx: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(intent) }.isSuccess
    }

    /** Oculta el aviso hasta la próxima comprobación. */
    fun dismiss() {
        val s = _state.value
        if (s !is UpdateState.Downloading) _state.value = UpdateState.Idle
    }

    private fun cleanDownloads(ctx: Context) {
        File(ctx.cacheDir, "updates").listFiles()?.forEach { it.delete() }
    }

    private fun notifyOnce(ctx: Context, info: ReleaseInfo) {
        val p = prefs(ctx)
        if (p.getString(KEY_NOTIFIED, null) == info.version) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Versiones nuevas", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("ScudoDNS ${info.version} disponible")
            .setContentText("Toca para ver las novedades e instalarla")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) } // sin permiso de notificaciones no pasa nada
        p.edit().putString(KEY_NOTIFIED, info.version).apply()
    }

    // ---------- Red ----------

    /** Solo se habla con GitHub: la API, la página de descarga y sus servidores de archivos. */
    internal fun isAllowedHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return h == "api.github.com" || h == "github.com" || h.endsWith(".githubusercontent.com")
    }

    private fun open(url: String): HttpURLConnection {
        var target = URL(url)
        repeat(5) {
            if (target.protocol != "https" || !isAllowedHost(target.host)) throw IOException("Destino no permitido: ${target.host}")
            val c = target.openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "ScudoDNS/${BuildConfig.VERSION_NAME} (Android)")
            c.setRequestProperty("Accept", if (target.host == "api.github.com") "application/vnd.github+json" else "application/octet-stream")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IOException("Redirección sin destino")
                c.disconnect()
                target = URL(target, loc)
                return@repeat
            }
            if (code !in 200..299) { c.disconnect(); throw IOException("HTTP $code") }
            return c
        }
        throw IOException("Demasiadas redirecciones")
    }

    private fun httpGetText(url: String): String {
        val c = open(url)
        try {
            val bytes = c.inputStream.use { it.readNBytesCompat(1_000_000) }
            return String(bytes, Charsets.UTF_8)
        } finally { c.disconnect() }
    }

    /** Descarga a [file] y devuelve el SHA-256 en hexadecimal. */
    private fun httpDownload(url: String, file: File, expected: Long, progress: (Float) -> Unit): String {
        val c = open(url)
        val md = MessageDigest.getInstance("SHA-256")
        try {
            var total = 0L
            var lastReport = 0L
            c.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > expected || total > MAX_APK_BYTES) throw IOException("El archivo es mayor de lo anunciado")
                        md.update(buf, 0, n)
                        out.write(buf, 0, n)
                        if (total - lastReport > 128 * 1024) { lastReport = total; progress(total.toFloat() / expected) }
                    }
                }
            }
            progress(1f)
        } finally { c.disconnect() }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            if (out.size() + n > limit) throw IOException("Respuesta demasiado grande")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun sha256Hex(b: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
