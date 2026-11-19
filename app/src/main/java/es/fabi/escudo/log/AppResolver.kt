package es.fabi.escudo.log

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Averigua qué app hizo cada consulta DNS (lo que en Pi-hole sería el "cliente").
 * Solo funciona en Android 10 o superior; en versiones anteriores se muestra sin app.
 */
class AppResolver(context: Context) {
    private val ctx = context.applicationContext
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private val names = ConcurrentHashMap<Int, String>()

    fun appFor(src: ByteArray, srcPort: Int, dst: ByteArray, dstPort: Int): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        val uid = try {
            cm.getConnectionOwnerUid(
                IPPROTO_UDP,
                InetSocketAddress(InetAddress.getByAddress(src), srcPort),
                InetSocketAddress(InetAddress.getByAddress(dst), dstPort),
            )
        } catch (_: Exception) { return null }
        if (uid == Process.INVALID_UID) return null
        return names.getOrPut(uid) { label(uid) }
    }

    private fun label(uid: Int): String {
        when (uid) {
            0 -> return "Sistema (root)"
            1000 -> return "Sistema Android"
            1051 -> return "Resolución DNS del sistema"
        }
        val pm = ctx.packageManager
        val pkgs = pm.getPackagesForUid(uid) ?: return "App $uid"
        val pkg = pkgs.first()
        return try {
            val label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            if (pkgs.size > 1) "$label (+${pkgs.size - 1})" else label
        } catch (_: Exception) { pkg }
    }

    private companion object {
        const val IPPROTO_UDP = 17 // IPPROTO_UDP
    }
}
