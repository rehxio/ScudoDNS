package es.rehxio.scudo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.rehxio.scudo.update.ReleaseInfo
import es.rehxio.scudo.update.UpdateState

/** Tarjeta en Inicio cuando hay una versión nueva: novedades y enlace directo al APK. */
@Composable
fun UpdateCard(vm: MainViewModel) {
    val state by vm.update.collectAsState()
    val info: ReleaseInfo = (state as? UpdateState.Available)?.info ?: return
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var showNotes by remember { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SystemUpdate, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Versión ${info.version} disponible", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
            }
            if (info.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    info.notes,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (showNotes) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { showNotes = !showNotes }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (showNotes) "Ver menos" else "Ver novedades")
                }
            }
            Text(
                "Se descarga con el navegador. Al terminar, toca la descarga para instalarla encima: se conservan tus listas y reglas.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { vm.downloadUpdate(ctx) }) {
                    Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Descargar")
                }
                TextButton(onClick = vm::dismissUpdate) { Text("Ahora no") }
            }
        }
    }
}

/** Apartado de "Acerca de": comprobar ahora y activar o no la comprobación automática. */
@Composable
fun UpdateSettings(vm: MainViewModel) {
    val state by vm.update.collectAsState()
    val auto by vm.autoUpdate.collectAsState()
    val cs = MaterialTheme.colorScheme
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Buscar versiones nuevas", style = MaterialTheme.typography.bodyLarge)
                Text("Una vez al día consulta GitHub. No envía ningún dato tuyo.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Switch(checked = auto, onCheckedChange = vm::setAutoUpdate)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (val s = state) {
                    UpdateState.Checking -> "Comprobando…"
                    UpdateState.UpToDate -> "Tienes la última versión."
                    is UpdateState.Available -> "Hay una versión nueva: la tienes en Inicio."
                    is UpdateState.Failed -> s.message
                    UpdateState.Idle -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state is UpdateState.Failed) cs.error else cs.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::checkUpdate, enabled = state != UpdateState.Checking) { Text("Comprobar ahora") }
        }
    }
}
