package es.rehxio.scudo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import es.rehxio.scudo.ui.MainScreen
import es.rehxio.scudo.ui.MainViewModel
import es.rehxio.scudo.ui.ScudoTheme
import es.rehxio.scudo.vpn.ScudoVpnService

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) ScudoVpnService.start(this)
    }

    // Opcional: sin él la protección funciona igual, solo que la notificación no se ve.
    // Se pide antes que el permiso de VPN y, conteste lo que conteste, se sigue con la VPN:
    // así nunca aparecen los dos diálogos a la vez.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestVpn()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScudoTheme {
                MainScreen(vm, onToggle = { on -> if (on) startProtection() else ScudoVpnService.stop(this) })
            }
        }
        if (savedInstanceState == null) handleStartRequest(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStartRequest(intent)
    }

    /** El ajuste rápido abre la app con esta marca cuando necesita que se conceda algún permiso. */
    private fun handleStartRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START, false) == true) {
            intent.removeExtra(EXTRA_START)
            if (!ScudoVpnService.running.value) startProtection()
        }
    }

    // La pantalla solo se actualiza mientras está visible: en segundo plano no gasta nada.
    override fun onStart() {
        super.onStart()
        vm.setVisible(true)
    }

    override fun onStop() {
        vm.setVisible(false)
        super.onStop()
    }

    private fun startProtection() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) // luego requestVpn()
        } else {
            requestVpn()
        }
    }

    private fun requestVpn() {
        val intent = VpnService.prepare(this) // null = ya tenemos permiso
        if (intent != null) vpnPermission.launch(intent) else ScudoVpnService.start(this)
    }

    companion object {
        const val EXTRA_START = "es.rehxio.scudo.START_PROTECTION"
    }
}
