package es.rehxio.scudo

import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import es.rehxio.scudo.ui.ScudoTheme
import es.rehxio.scudo.ui.MainScreen
import es.rehxio.scudo.ui.MainViewModel
import es.rehxio.scudo.vpn.ScudoVpnService

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) ScudoVpnService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScudoTheme {
                MainScreen(vm, onToggle = { on -> if (on) startProtection() else ScudoVpnService.stop(this) })
            }
        }
    }

    private fun startProtection() {
        val intent = VpnService.prepare(this) // null = ya tenemos permiso
        if (intent != null) vpnPermission.launch(intent) else ScudoVpnService.start(this)
    }
}
