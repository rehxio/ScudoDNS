package es.rehxio.scudo.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private class Tab(val title: String, val on: ImageVector, val off: ImageVector)

private val TABS = listOf(
    Tab("Inicio", Icons.Filled.Shield, Icons.Outlined.Shield),
    Tab("Registro", Icons.AutoMirrored.Filled.ListAlt, Icons.AutoMirrored.Outlined.ListAlt),
    Tab("Reglas", Icons.Filled.Tune, Icons.Outlined.Tune),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel, onToggle: (Boolean) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val busy by vm.busy.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var showDns by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Text(if (tab == 0) "ScudoDNS" else TABS[tab].title, fontWeight = FontWeight.SemiBold)
                },
                actions = {
                    when (tab) {
                        0 -> {
                            IconButton(onClick = { showAbout = true }) { Icon(Icons.Outlined.Info, "Acerca de ScudoDNS") }
                            IconButton(onClick = { showDns = true }) { Icon(Icons.Filled.Dns, "Servidores DNS") }
                            if (busy) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                            else IconButton(onClick = vm::updateAll) { Icon(Icons.Filled.Refresh, "Actualizar listas") }
                        }
                        1 -> IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, "Vaciar registro") }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(if (tab == i) t.on else t.off, null) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Añadir lista") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (tab) {
            0 -> HomeTab(vm, onToggle, m, onAbout = { showAbout = true })
            1 -> LogTab(vm, m)
            else -> RulesTab(vm, m)
        }
    }

    if (showAdd) AddListDialog(onDismiss = { showAdd = false }) { name, url -> vm.add(name, url); showAdd = false }
    if (showDns) DnsDialog(vm.upstreams.collectAsState().value, onDismiss = { showDns = false }, onSave = vm::saveUpstreams)
    if (showAbout) AboutSheet(onDismiss = { showAbout = false })
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("¿Vaciar el registro?") },
        text = { Text("Se borran las consultas guardadas. Tus reglas y listas no cambian.") },
        confirmButton = { TextButton(onClick = { vm.clearLog(); confirmClear = false }) { Text("Vaciar") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
    )
}
