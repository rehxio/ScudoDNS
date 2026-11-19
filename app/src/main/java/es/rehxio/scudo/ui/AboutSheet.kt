package es.rehxio.scudo.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.rehxio.scudo.BuildConfig

/**
 * "Acerca de": versión, licencia, enlaces y, si está configurado, un apartado discreto de donativos.
 * Solo aparece cuando el usuario lo abre: la app nunca pide donaciones por su cuenta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    fun open(url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.VerifiedUser, null, Modifier.size(28.dp), tint = cs.onPrimary)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("ScudoDNS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Versión ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Software libre bajo licencia GPL-3.0. Gratis, sin anuncios, sin rastreo y sin servidores propios: " +
                    "todo el filtrado ocurre en tu móvil.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(16.dp))
            UpdateSettings(vm)
            HorizontalDivider(color = cs.outlineVariant)
            LinkRow(Icons.Filled.Code, "Código fuente") { open(BuildConfig.REPO_URL) }
            HorizontalDivider(color = cs.outlineVariant)
            LinkRow(Icons.AutoMirrored.Filled.MenuBook, "Guía y ayuda") { open("${BuildConfig.REPO_URL}/wiki") }
            HorizontalDivider(color = cs.outlineVariant)
            LinkRow(Icons.Filled.BugReport, "Reportar un fallo") {
                open("${BuildConfig.REPO_URL}/issues/new?template=fallo.yml")
            }

            if (BuildConfig.DONATE_URL.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer,
                        contentColor = cs.onSecondaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("¿Te resulta útil?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "ScudoDNS es y será siempre gratis. Si quieres apoyar su desarrollo, puedes invitarme a un café. " +
                                "Es totalmente opcional y no cambia nada en la app.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(onClick = { open(BuildConfig.DONATE_URL) }) {
                            Icon(Icons.Filled.Coffee, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Invitar a un café")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
