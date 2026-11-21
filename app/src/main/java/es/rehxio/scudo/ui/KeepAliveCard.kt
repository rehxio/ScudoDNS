package es.rehxio.scudo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Pasos para que la protección no se cierre nunca. Plegada por defecto en una sola línea con el
 * progreso; al tocarla se despliegan los pasos. Se puede ocultar para siempre (vuelve desde Acerca de).
 */
@Composable
fun KeepAliveCard(state: KeepAlive, onConfirm: (String) -> Unit, onHide: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable { open = !open },
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BatteryChargingFull, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("Que no se cierre nunca", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${state.doneCount} de ${state.totalCount}", style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text(if (open) "Ocultar" else "Ver", style = MaterialTheme.typography.labelMedium, color = cs.primary)
            }
            if (open) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Android puede cerrar ScudoDNS para ahorrar batería. Con estos ajustes se mantiene siempre encendida." +
                        (OemSettings.brand.label.takeIf { it.isNotEmpty() }
                            ?.let { " $it añade ajustes propios: no dejan comprobarlos, así que márcalos al terminar." } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
                if (!OemSettings.replacesAndroidBattery) Step(
                    done = state.androidBattery,
                    title = "Sin ahorro de batería",
                    hint = "Acepta el aviso para que ScudoDNS pueda funcionar en segundo plano.",
                    action = "Permitir",
                    onAction = { OemSettings.androidBattery(ctx) },
                )
                for (step in OemSettings.steps) Step(
                    done = state.oemDone[step.prefKey] == true,
                    title = step.title,
                    hint = step.hint,
                    action = "Abrir",
                    onAction = { OemSettings.open(ctx, step) },
                    extra = "Ya lo he hecho" to { onConfirm(step.prefKey) },
                )
                Step(
                    done = state.vpnAlwaysOn == true,
                    title = "VPN siempre activada",
                    hint = "Toca el engranaje ⚙ junto a ScudoDNS y activa \"VPN siempre activada\".",
                    action = "Abrir",
                    onAction = { OemSettings.vpn(ctx) },
                    unknown = state.vpnAlwaysOn == null,
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onHide, contentPadding = PaddingValues(0.dp)) {
                    Text("No mostrar más (vuelve desde Acerca de)", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun Step(
    done: Boolean,
    title: String,
    hint: String,
    action: String,
    onAction: () -> Unit,
    extra: Pair<String, () -> Unit>? = null,
    unknown: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Outlined.Circle, null,
            Modifier.size(20.dp).padding(top = 1.dp),
            tint = if (done) cs.primary else cs.onSurfaceVariant.copy(alpha = if (unknown) 0.5f else 0.9f),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (!done) {
                Text(hint, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onAction) { Text(action) }
                    extra?.let { (label, f) -> TextButton(onClick = f) { Text(label) } }
                }
            }
        }
    }
}
