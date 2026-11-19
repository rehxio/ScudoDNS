package es.rehxio.scudo.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Accesos directos a los ajustes del sistema que impiden que Android cierre la protección. */
object SystemShortcuts {
    val isXiaomi: Boolean
        get() = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
            Build.BRAND.lowercase() in setOf("xiaomi", "redmi", "poco")

    /** Abre el primero de los ajustes que exista en este móvil. */
    private fun openFirst(ctx: Context, vararg intents: Intent): Boolean {
        for (i in intents) {
            if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return true
        }
        return false
    }

    private fun appDetails(ctx: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    /** Diálogo de Android "¿Permitir que ScudoDNS se ejecute en segundo plano?", solo para esta app. */
    fun battery(ctx: Context) = openFirst(
        ctx,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
        appDetails(ctx),
    )

    /** Ahorro de batería propio de Xiaomi (HyperOS/MIUI), directamente en la ficha de ScudoDNS. */
    fun xiaomiBattery(ctx: Context) = openFirst(
        ctx,
        Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
            .putExtra("package_name", ctx.packageName)
            .putExtra("package_label", "ScudoDNS"),
        appDetails(ctx),
    )

    /** En Xiaomi, la ficha de la app tiene el interruptor "Inicio automático" arriba del todo. */
    fun xiaomiAutostart(ctx: Context) = openFirst(
        ctx,
        appDetails(ctx),
        Intent().setComponent(ComponentName("com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity")),
    )

    /** Lista de VPN de Android: Android no deja abrir directamente la ficha de una VPN concreta. */
    fun vpn(ctx: Context) = openFirst(ctx, Intent(Settings.ACTION_VPN_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
}

/**
 * Lista de pasos para que la protección no se cierre nunca. Cada paso abre su ajuste
 * y se marca como hecho en cuanto Android lo confirma (al volver a la app).
 */
@Composable
fun KeepAliveCard(state: KeepAlive, onAutostartDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer, contentColor = cs.onSecondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Que no se cierre nunca", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text("Android puede cerrar ScudoDNS para ahorrar batería. Con estos ajustes se mantiene siempre encendida.",
                style = MaterialTheme.typography.bodySmall)

            Step(
                done = state.batteryUnrestricted,
                title = "Sin ahorro de batería",
                hint = "Acepta el aviso para que ScudoDNS pueda funcionar en segundo plano.",
                action = "Permitir",
                onAction = { SystemShortcuts.battery(ctx) },
                extra = if (state.isXiaomi) ("Ajuste de Xiaomi" to { SystemShortcuts.xiaomiBattery(ctx); Unit }) else null,
            )
            if (state.isXiaomi) Step(
                done = state.autostartDone,
                title = "Inicio automático",
                hint = "Activa \"Inicio automático\" en la ficha de ScudoDNS y vuelve.",
                action = "Abrir",
                onAction = { SystemShortcuts.xiaomiAutostart(ctx) },
                extra = "Ya lo he hecho" to onAutostartDone,
            )
            if (state.vpnAlwaysOn != null) Step(
                done = state.vpnAlwaysOn,
                title = "VPN siempre activada",
                hint = "Toca el engranaje ⚙ junto a ScudoDNS y activa \"VPN siempre activada\".",
                action = "Abrir",
                onAction = { SystemShortcuts.vpn(ctx) },
            ) else Step(
                done = false,
                title = "VPN siempre activada (recomendado)",
                hint = "Toca el engranaje ⚙ junto a ScudoDNS y activa \"VPN siempre activada\".",
                action = "Abrir",
                onAction = { SystemShortcuts.vpn(ctx) },
                unknown = true,
            )
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
            tint = if (done) cs.primary else cs.onSecondaryContainer.copy(alpha = if (unknown) 0.4f else 0.7f),
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
