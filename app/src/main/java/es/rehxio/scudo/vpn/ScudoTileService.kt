package es.rehxio.scudo.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import es.rehxio.scudo.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Botón de los ajustes rápidos: pausa o reanuda la protección sin abrir la app.
 * Solo está activo mientras el panel está abierto, así que no consume nada el resto del tiempo.
 */
class ScudoTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        watcher?.cancel()
        watcher = scope.launch { ScudoVpnService.running.collect { show(it) } }
    }

    override fun onStopListening() {
        watcher?.cancel()
        watcher = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        if (ScudoVpnService.running.value) {
            runCatching { ScudoVpnService.stop(this) }
            return
        }
        // Sin permiso de VPN todavía (o si Android no deja arrancar desde aquí): se abre la app,
        // que pide lo que haga falta y enciende la protección.
        val started = VpnService.prepare(this) == null && runCatching { ScudoVpnService.start(this) }
            .onFailure { Log.w("ScudoTile", "No se pudo encender desde el ajuste rápido", it) }.isSuccess
        if (!started) {
            if (isLocked) unlockAndRun { openApp() } else openApp()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_START, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 2, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun show(on: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "ScudoDNS"
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (on) "Protegiendo" else "En pausa"
        tile.updateTile()
    }
}
