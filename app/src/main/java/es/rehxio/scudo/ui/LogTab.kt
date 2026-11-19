package es.rehxio.scudo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.rehxio.scudo.filter.Verdict
import es.rehxio.scudo.lists.UserRules
import es.rehxio.scudo.log.LogEntry
import es.rehxio.scudo.log.LogFilter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val PAGE_SIZES = listOf(25, 50, 100, 200)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogTab(vm: MainViewModel, modifier: Modifier) {
    val state by vm.logState.collectAsState()
    val page by vm.logPage.collectAsState()
    val rules by vm.rules.collectAsState()
    var selected by remember { mutableStateOf<LogEntry?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.page, state.pageSize, state.filter) { listState.scrollToItem(0) }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.search, onValueChange = vm::setSearch,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Buscar dominio o app") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (state.search.isNotEmpty()) IconButton(onClick = { vm.setSearch("") }) { Icon(Icons.Filled.Clear, "Borrar búsqueda") }
            },
            singleLine = true, shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(state.filter == LogFilter.ALL, { vm.setFilter(LogFilter.ALL) }, { Text("Todas") })
            FilterChip(state.filter == LogFilter.BLOCKED, { vm.setFilter(LogFilter.BLOCKED) }, { Text("Bloqueadas") },
                leadingIcon = { Icon(Icons.Filled.Block, null, Modifier.size(16.dp)) })
            FilterChip(state.filter == LogFilter.ALLOWED, { vm.setFilter(LogFilter.ALLOWED) }, { Text("Permitidas") },
                leadingIcon = { Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp)) })
            FilterChip(state.live, { vm.setLive(!state.live) }, { Text(if (state.live) "En directo" else "En pausa") },
                leadingIcon = {
                    Icon(Icons.Filled.FiberManualRecord, null, Modifier.size(12.dp),
                        tint = if (state.live) StatusColors.blocked else MaterialTheme.colorScheme.outline)
                })
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (page.items.isEmpty()) {
                EmptyLog(state.search.isNotEmpty() || state.filter != LogFilter.ALL)
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(page.items, key = { i, e -> "${e.time}-$i-${e.domain}" }) { i, e ->
                        LogRow(e, rules) { selected = e }
                        if (i < page.items.lastIndex) HorizontalDivider(
                            Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }

        PaginationBar(
            pageSize = state.pageSize, onPageSize = vm::setPageSize,
            from = page.from, to = page.to, total = page.total,
            page = page.page, pages = page.pages, onPage = vm::setPage,
        )
    }

    selected?.let { e ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            EntryActions(e, rules,
                onBlock = { vm.block(it); selected = null },
                onAllow = { vm.allow(it); selected = null },
                onRemove = { vm.removeRule(it); selected = null })
        }
    }
}

@Composable
private fun LogRow(e: LogEntry, rules: UserRules, onClick: () -> Unit) {
    val blocked = e.verdict.isBlocked
    val fg = when { e.failed -> StatusColors.failed; blocked -> StatusColors.blocked; else -> StatusColors.allowed }
    val bg = when { e.failed -> StatusColors.failedBg; blocked -> StatusColors.blockedBg; else -> StatusColors.allowedBg }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(when { e.failed -> Icons.Filled.ErrorOutline; blocked -> Icons.Filled.Block; else -> Icons.Filled.CheckCircle },
                null, Modifier.size(20.dp), tint = fg)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(e.domain, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(formatTime(e.time), e.typeName, e.app, if (e.failed) "sin respuesta" else null,
                if (e.cached) "caché" else null).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (e.verdict == Verdict.BLOCKED_USER || e.verdict == Verdict.ALLOWED_USER) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Person, "Tu regla", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyLog(filtered: Boolean) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.Inbox, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(if (filtered) "No hay consultas que coincidan" else "Aún no hay consultas",
            style = MaterialTheme.typography.titleMedium)
        Text(if (filtered) "Prueba con otro filtro o búsqueda." else "Activa la protección y aparecerán aquí en directo.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaginationBar(
    pageSize: Int, onPageSize: (Int) -> Unit,
    from: Int, to: Int, total: Int,
    page: Int, pages: Int, onPage: (Int) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Por página", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f).height(36.dp)) {
                    PAGE_SIZES.forEachIndexed { i, n ->
                        SegmentedButton(
                            selected = pageSize == n, onClick = { onPageSize(n) },
                            shape = SegmentedButtonDefaults.itemShape(i, PAGE_SIZES.size),
                            icon = {},
                        ) { Text("$n", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (total == 0) "0 consultas" else "${nf.format(from)}–${nf.format(to)} de ${nf.format(total)}",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { onPage(page - 1) }, enabled = page > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Página anterior")
                }
                Text("${page + 1} / $pages", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { onPage(page + 1) }, enabled = page < pages - 1) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Página siguiente")
                }
            }
        }
    }
}

/** Hoja inferior con las acciones de un dominio: elegir nivel (sub.ejemplo.com / ejemplo.com) y bloquear o permitir. */
@Composable
private fun EntryActions(
    e: LogEntry, rules: UserRules,
    onBlock: (String) -> Unit, onAllow: (String) -> Unit, onRemove: (String) -> Unit,
) {
    val levels = remember(e.domain) { domainLevels(e.domain) }
    var target by remember(e.domain) { mutableStateOf(levels.first()) }
    val clipboard = LocalClipboardManager.current
    val inBlock = target in rules.block
    val inAllow = target in rules.allow

    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.domain, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { clipboard.setText(AnnotatedString(e.domain)) }) { Icon(Icons.Filled.ContentCopy, "Copiar") }
        }
        Text(
            listOfNotNull(
                when (e.verdict) {
                    Verdict.BLOCKED -> "Bloqueada por una lista"
                    Verdict.BLOCKED_USER -> "Bloqueada por tu regla"
                    Verdict.ALLOWED_USER -> "Permitida por tu regla"
                    Verdict.ALLOWED -> if (e.failed) "Permitida, pero el DNS no respondió" else "Permitida"
                },
                e.app, formatTime(e.time), e.typeName,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (levels.size > 1) {
            Text("Aplicar a", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                levels.forEach { d -> FilterChip(target == d, { target = d }, { Text(d) }) }
            }
            Text("Se aplica también a todos sus subdominios.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onBlock(target) }, enabled = !inBlock, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError),
            ) {
                Icon(Icons.Filled.Block, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Bloquear")
            }
            FilledTonalButton(onClick = { onAllow(target) }, enabled = !inAllow, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Permitir")
            }
        }
        if (inBlock || inAllow) {
            OutlinedButton(onClick = { onRemove(target) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Quitar mi regla para $target")
            }
        }
        Text("Algunas apps guardan la respuesta un minuto; el cambio puede tardar ese tiempo en notarse.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp))
    }
}

/** "a.b.ejemplo.com" → ["a.b.ejemplo.com", "b.ejemplo.com", "ejemplo.com"] (sin el TLD solo). */
internal fun domainLevels(domain: String): List<String> {
    val parts = domain.split('.')
    if (parts.size < 2) return listOf(domain)
    return (0 until parts.size - 1).map { parts.drop(it).joinToString(".") }
}

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
private val dayFmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

private fun formatTime(t: Long): String {
    val today = Calendar.getInstance()
    val c = Calendar.getInstance().apply { timeInMillis = t }
    val sameDay = today.get(Calendar.YEAR) == c.get(Calendar.YEAR) &&
        today.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
    return (if (sameDay) timeFmt else dayFmt).format(Date(t))
}
