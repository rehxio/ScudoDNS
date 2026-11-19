package es.rehxio.scudo.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Una versión publicada en GitHub Releases. */
data class ReleaseInfo(
    val version: String,
    val notes: String,
    val pageUrl: String,
    /** Enlace directo al APK: se abre en el navegador, que lo descarga. */
    val apkUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Busca versiones nuevas en GitHub Releases y avisa. La descarga la hace el navegador y la instalación
 * el usuario, como con cualquier APK: la app no pide permiso para instalar otras apps, así Play Protect
 * no la confunde con las apps maliciosas que descargan e instalan software por su cuenta.
 */
object AppUpdater {
    private const val TAG = "ScudoUpdate"
    private const val PREFS = "scudo"
    private const val KEY_AUTO = "updateAutoCheck"
    private const val KEY_LAST = "updateLastCheck"
    private const val KEY_NOTIFIED = "updateNotified"
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
            _state.value = UpdateState.Checking
            _state.value = try {
                val info = fetchLatest()
                prefs(ctx).edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
                if (info != null && Versions.isNewer(info.version, BuildConfig.VERSION_NAME)) {
                    if (notify) notifyOnce(ctx, info)
                    UpdateState.Available(info)
                } else {
                    UpdateState.UpToDate
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo comprobar si hay versión nueva", e)
                UpdateState.Failed("No se pudo comprobar: ${e.message ?: e.javaClass.simpleName}")
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
            return ReleaseInfo(
                version = tag.removePrefix("v"),
                notes = Versions.plainNotes(o.optString("body")),
                pageUrl = o.optString("html_url").ifBlank { "${BuildConfig.REPO_URL}/releases/latest" },
                apkUrl = a.optString("browser_download_url").takeIf { it.startsWith("https://github.com/") }
                    ?: o.optString("html_url"),
            )
        }
        return null
    }

    /** Abre la descarga del APK en el navegador. Al terminar, se instala tocando la descarga. */
    fun openDownload(ctx: Context, info: ReleaseInfo) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Oculta el aviso hasta la próxima comprobación. */
    fun dismiss() {
        _state.value = UpdateState.Idle
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
            c.setRequestProperty("Accept", "application/vnd.github+json")
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
}
