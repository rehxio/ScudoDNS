package es.rehxio.scudo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.os.Build
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.platform.LocalContext
import es.rehxio.scudo.lists.FilterList
import es.rehxio.scudo.lists.ListRepository
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

internal val nf: NumberFormat = NumberFormat.getIntegerInstance()

@Composable
fun HomeTab(vm: MainViewModel, onToggle: (Boolean) -> Unit, modifier: Modifier, onAbout: () -> Unit) {
    val lists by vm.lists.collectAsState()
    val running by vm.running.collectAsState()
    val total by vm.totalDomains.collectAsState()
    val stats by vm.stats.collectAsState()
    val diag by vm.diag.collectAsState()
    val privateDns by vm.privateDns.collectAsState()
    val keepAlive by vm.keepAlive.collectAsState()
    var toDelete by remember { mutableStateOf<FilterList?>(null) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { StatusCard(running, total, stats, onToggle) }
        item { UpdateCard(vm) }
        privateDns?.let { name -> if (running) item { PrivateDnsWarning(name) } }
        if (running && !keepAlive.complete) item { KeepAliveCard(keepAlive, onAutostartDone = vm::confirmAutostart) }
        if (running) item { DiagCard(diag) }
        item {
            Row(Modifier.padding(top = 14.dp, start = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
                Text("Listas de bloqueo", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${lists.count { it.enabled }} de ${lists.size} activas",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(lists, key = { it.id }) { l ->
            ListCard(l, onEnabled = { vm.setEnabled(l.id, it) }, onDelete = { toDelete = l })
        }
        item {
            Text(
                "ScudoDNS es gratis y de código abierto · Acerca de",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onAbout).padding(vertical = 16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }

    toDelete?.let { l ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Eliminar lista?") },
            text = { Text(l.name) },
            confirmButton = { TextButton(onClick = { vm.remove(l.id); toDelete = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun StatusCard(running: Boolean, total: Int, stats: Triple<Long, Long, Long>, onToggle: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (running) cs.primary else cs.surfaceContainerHighest, label = "bg")
    val fg by animateColorAsState(if (running) cs.onPrimary else cs.onSurface, label = "fg")
    val (q, b, failed) = stats
    val fraction by animateFloatAsState(if (q > 0) (b.toFloat() / q).coerceIn(0f, 1f) else 0f, label = "pct")

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(fg.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (running) Icons.Filled.VerifiedUser else Icons.Filled.GppBad, null, Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (running) "Protegido" else "Sin protección",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${nf.format(total)} dominios bloqueados",
                        style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.8f))
                }
                Switch(
                    checked = running, onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = cs.primary, checkedTrackColor = cs.onPrimary,
                        checkedBorderColor = Color.Transparent,
                    ),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Consultas", nf.format(q), fg, Modifier.weight(1f))
                StatTile("Bloqueadas", nf.format(b), fg, Modifier.weight(1f))
                StatTile("Bloqueado", String.format("%.1f %%", fraction * 100), fg, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = fg, trackColor = fg.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
            )
            if (running && failed > 0) {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ErrorOutline, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${nf.format(failed)} consultas sin respuesta del DNS. Revisa los servidores en el botón DNS.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Aviso cuando el DNS privado de Android está activo: con él no hay Internet mientras la VPN está encendida. */
@Composable
private fun PrivateDnsWarning(name: String) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.errorContainer, contentColor = cs.onErrorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Desactiva el DNS privado", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text("Android tiene activo el DNS privado ($name). Mientras esté así, las webs y apps no cargarán. " +
                "Ve a los ajustes de red de Android → DNS privado → Desactivado.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(
                onClick = {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Abrir ajustes", color = cs.onErrorContainer, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun DiagCard(lines: List<String>) {
    var open by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable { open = !open },
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("Diagnóstico", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(if (open) "Ocultar" else "Ver", style = MaterialTheme.typography.labelMedium, color = cs.primary)
            }
            if (open) {
                Spacer(Modifier.height(8.dp))
                lines.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                        color = if (it.startsWith("⚠")) cs.error else cs.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, fg: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(fg.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.75f))
    }
}

@Composable
private fun ListCard(l: FilterList, onEnabled: (Boolean) -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLowest),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(l.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(l.url.removePrefix("https://").removePrefix("http://"), style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                if (l.error != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, null, Modifier.size(14.dp), tint = cs.error)
                        Spacer(Modifier.width(4.dp))
                        Text(l.error, style = MaterialTheme.typography.labelSmall, color = cs.error,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    val info = if (l.lastUpdated == 0L) "Sin descargar" else "${nf.format(l.domains)} dominios · " +
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(l.lastUpdated))
                    Text(info, style = MaterialTheme.typography.labelSmall,
                        color = if (l.enabled) cs.primary else cs.onSurfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .background(if (l.enabled) cs.secondaryContainer else cs.surfaceContainerHigh)
                            .padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            Switch(checked = l.enabled, onCheckedChange = onEnabled)
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Eliminar", tint = cs.onSurfaceVariant) }
        }
    }
}

@Composable
internal fun AddListDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val valid = ListRepository.isHttpsUrl(url)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir lista") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre (opcional)") }, singleLine = true)
                OutlinedTextField(url, { url = it.trim() }, label = { Text("URL de la lista") }, singleLine = true,
                    placeholder = { Text("https://…/lista.txt") },
                    isError = url.isNotEmpty() && !valid,
                    supportingText = if (url.isNotEmpty() && !valid) {{ Text("Tiene que ser una dirección https://") }} else null)
                Text("Formatos: hosts, un dominio por línea o AdBlock (||dominio^). Las reglas @@|| se usan como lista blanca.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onAdd(name.trim(), url) }) { Text("Añadir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
internal fun DnsDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Boolean) {
    var text by remember { mutableStateOf(current) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Servidores DNS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it; error = false }, singleLine = true, isError = error,
                    label = { Text("DNS de respaldo (IPs separadas por comas)") },
                    supportingText = if (error) {{ Text("Escribe IPs válidas, por ejemplo 9.9.9.9") }} else null)
                Text("Las consultas no bloqueadas van primero al DNS de tu red (el router, tu Pi-hole o el de tu " +
                    "operador). Estos servidores se usan si ese no responde.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { if (onSave(text)) onDismiss() else error = true }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
