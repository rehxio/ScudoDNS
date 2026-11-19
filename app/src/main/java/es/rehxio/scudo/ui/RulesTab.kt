package es.rehxio.scudo.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun RulesTab(vm: MainViewModel, modifier: Modifier) {
    val rules by vm.rules.collectAsState()
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun addRule(block: Boolean) {
        val d = vm.cleanDomain(input)
        if (d == null) { error = true; return }
        if (block) vm.block(d) else vm.allow(d)
        input = ""
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                border = CardDefaults.outlinedCardBorder()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Añadir regla", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Tus reglas mandan sobre las listas y afectan también a los subdominios.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        input, { input = it; error = false }, singleLine = true, isError = error,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        placeholder = { Text("ejemplo.com") },
                        supportingText = if (error) {{ Text("Escribe un dominio válido, por ejemplo ads.ejemplo.com") }} else null,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { addRule(true) }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError)) {
                            Icon(Icons.Filled.Block, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Bloquear")
                        }
                        FilledTonalButton(onClick = { addRule(false) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Permitir")
                        }
                    }
                }
            }
        }
        item {
            RuleSection("Bloqueados por ti", rules.block.sorted(), Icons.Filled.Block,
                StatusColors.blocked, StatusColors.blockedBg, "Nada bloqueado a mano todavía.", vm::removeRule)
        }
        item {
            RuleSection("Permitidos por ti", rules.allow.sorted(), Icons.Filled.CheckCircle,
                StatusColors.allowed, StatusColors.allowedBg,
                "Si una app o web deja de funcionar, permite su dominio desde el registro.", vm::removeRule)
        }
    }
}

@Composable
private fun RuleSection(
    title: String, domains: List<String>, icon: ImageVector, fg: Color, bg: Color,
    empty: String, onRemove: (String) -> Unit,
) {
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                Text("${domains.size}", style = MaterialTheme.typography.labelLarge, color = fg,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 10.dp, vertical = 2.dp))
            }
            if (domains.isEmpty()) {
                Text(empty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            domains.forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(18.dp), tint = fg)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(d, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemove(d) }) { Icon(Icons.Filled.Close, "Quitar regla") }
                }
            }
        }
    }
}
