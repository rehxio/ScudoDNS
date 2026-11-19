package es.fabi.escudo

import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import es.fabi.escudo.ui.EscudoTheme
import es.fabi.escudo.ui.MainScreen
import es.fabi.escudo.ui.MainViewModel
import es.fabi.escudo.vpn.EscudoVpnService

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) EscudoVpnService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EscudoTheme {
                MainScreen(vm, onToggle = { on -> if (on) startProtection() else EscudoVpnService.stop(this) })
            }
        }
    }

    private fun startProtection() {
        val intent = VpnService.prepare(this) // null = ya tenemos permiso
        if (intent != null) vpnPermission.launch(intent) else EscudoVpnService.start(this)
    }
}
