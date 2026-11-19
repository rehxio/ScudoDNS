package es.rehxio.scudo.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

/**
 * Vuelve a encender la protección al reiniciar el móvil o al actualizar la app,
 * si el usuario la tenía encendida y el permiso de VPN sigue concedido.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        if (!ScudoVpnService.isWanted(context)) return
        if (VpnService.prepare(context) != null) return // permiso retirado: no se puede sin el usuario
        runCatching { ScudoVpnService.start(context) }
    }
}
