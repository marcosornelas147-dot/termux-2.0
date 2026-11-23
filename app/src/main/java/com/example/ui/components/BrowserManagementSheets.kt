package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.browser.BrowserTabState
import com.example.browser.MemoryOverview
import com.example.data.local.BookmarkEntity
import com.example.data.local.BrowserSettings
import com.example.data.local.DownloadEntryEntity
import com.example.data.local.HistoryEntryEntity
import com.example.data.local.SitePermissionEntity
import com.example.ui.theme.JetBrainsMonoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class BrowserSheetType {
    NONE,
    HISTORY,
    BOOKMARKS,
    DOWNLOADS,
    MEMORY,
    SECURITY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryModalSheet(
    history: List<HistoryEntryEntity>,
    onOpenUrl: (String) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = remember(history, query) {
        if (query.isBlank()) history
        else history.filter {
            it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Histórico de Navegação", style = MaterialTheme.typography.titleLarge)
                }
                if (history.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("Limpar tudo")
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Filtrar histórico...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (filtered.isEmpty()) {
                Text(
                    text = "Nenhum registro encontrado no histórico.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { item ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOpenUrl(item.url)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.url,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onDeleteItem(item.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remover do histórico")
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksModalSheet(
    bookmarks: List<BookmarkEntity>,
    currentUrl: String,
    currentTitle: String,
    onAddCurrent: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Favoritos", style = MaterialTheme.typography.titleLarge)
                }
                if (!currentUrl.startsWith("termux://")) {
                    TextButton(onClick = onAddCurrent) {
                        Text("Salvar atual")
                    }
                }
            }

            if (bookmarks.isEmpty()) {
                Text(
                    text = "Sem favoritos salvos ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(bookmarks, key = { it.id }) { bm ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOpenUrl(bm.url)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bm.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = bm.url,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Excluir favorito")
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsModalSheet(
    downloads: List<DownloadEntryEntity>,
    onDeleteDownload: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DownloadDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Downloads", style = MaterialTheme.typography.titleLarge)
                }
                if (downloads.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("Limpar lista")
                    }
                }
            }

            if (downloads.isEmpty()) {
                Text(
                    text = "Nenhum arquivo baixado ainda. Downloads feitos no navegador aparecem aqui.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(downloads, key = { it.id }) { dl ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dl.fileName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontFamily = JetBrainsMonoFamily,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${dl.status} • ${dl.bytesDownloaded / 1024} KB • ${dateFormatter.format(Date(dl.createdAt))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onDeleteDownload(dl.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remover download")
                                    }
                                }
                                if (dl.status == "DOWNLOADING") {
                                    if (dl.bytesTotal > 0) {
                                        LinearProgressIndicator(
                                            progress = { (dl.bytesDownloaded.toFloat() / dl.bytesTotal.toFloat()).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryManagerSheet(
    tabs: List<BrowserTabState>,
    memoryOverview: MemoryOverview,
    settings: BrowserSettings,
    onMaxActiveTabsChange: (Int) -> Unit,
    onAutoSuspendChange: (Boolean) -> Unit,
    onSuspendInactive: () -> Unit,
    onClearCache: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Gerenciamento de Memória", style = MaterialTheme.typography.titleLarge)
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Consumo estimado das abas: ~${memoryOverview.totalBrowserEstimatedMb} MB",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Heap JVM: ${memoryOverview.appHeapUsedMb} MB • RAM livre no aparelho: ${memoryOverview.deviceAvailableMb} / ${memoryOverview.deviceTotalMb} MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Ativas na RAM: ${memoryOverview.activeTabsCount} • Suspensas (estado salvo): ${memoryOverview.suspendedTabsCount}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Limite de abas simultâneas na RAM", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${settings.maxActiveTabs}",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = settings.maxActiveTabs.toFloat(),
                        onValueChange = { onMaxActiveTabsChange(it.roundToInt()) },
                        valueRange = 2f..12f,
                        steps = 9
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Suspender abas inativas automaticamente", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Preserva o estado e libera memória em celulares com pouca RAM",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = settings.autoSuspendEnabled, onCheckedChange = onAutoSuspendChange)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(onClick = onSuspendInactive, modifier = Modifier.weight(1f)) {
                        Text("Suspender Inativas")
                    }
                    OutlinedButton(onClick = { onClearCache(false) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Limpar Cache")
                    }
                }
            }

            item {
                HorizontalDivider()
                Text("Consumo por aba", style = MaterialTheme.typography.titleMedium)
            }

            items(tabs, key = { it.id }) { tab ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = if (tab.isSuspended) "Suspensa (Bundle preservado)" else tab.url,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "~${tab.estimatedMemoryMb} MB",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (tab.isSuspended) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsSheet(
    settings: BrowserSettings,
    sitePermissions: List<SitePermissionEntity>,
    onToggleHttpsOnly: (Boolean) -> Unit,
    onToggleJavascript: (Boolean) -> Unit,
    onToggleCookies: (Boolean) -> Unit,
    onToggleThirdPartyCookies: (Boolean) -> Unit,
    onToggleAskDownload: (Boolean) -> Unit,
    onToggleBlockDownloads: (Boolean) -> Unit,
    onUpdateSitePermission: (SitePermissionEntity) -> Unit,
    onDeleteSitePermission: (String) -> Unit,
    onClearCookiesAndCache: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Segurança e Privacidade", style = MaterialTheme.typography.titleLarge)
                }
            }

            item {
                SettingSwitchRow(
                    title = "Navegação HTTPS Prioritária",
                    subtitle = "Alerta e atualiza sites HTTP externos (localhost continua liberado)",
                    checked = settings.httpsOnlyEnabled,
                    onCheckedChange = onToggleHttpsOnly
                )
            }
            item {
                SettingSwitchRow(
                    title = "Suporte a JavaScript",
                    subtitle = "Executa scripts nas páginas (padrão global)",
                    checked = settings.javascriptEnabled,
                    onCheckedChange = onToggleJavascript
                )
            }
            item {
                SettingSwitchRow(
                    title = "Permitir Cookies",
                    subtitle = "Armazena sessão e preferências dos sites",
                    checked = settings.cookiesEnabled,
                    onCheckedChange = onToggleCookies
                )
            }
            item {
                SettingSwitchRow(
                    title = "Cookies de terceiros",
                    subtitle = "Restrito por padrão para maior privacidade",
                    checked = settings.thirdPartyCookiesEnabled,
                    onCheckedChange = onToggleThirdPartyCookies
                )
            }
            item {
                SettingSwitchRow(
                    title = "Confirmar antes de baixar",
                    subtitle = "Exibe origem e tamanho antes de iniciar downloads",
                    checked = settings.askBeforeDownload,
                    onCheckedChange = onToggleAskDownload
                )
            }
            item {
                SettingSwitchRow(
                    title = "Bloquear todos os downloads",
                    subtitle = "Impede qualquer arquivo de ser baixado pelo navegador",
                    checked = settings.blockAllDownloads,
                    onCheckedChange = onToggleBlockDownloads
                )
            }

            item {
                OutlinedButton(
                    onClick = onClearCookiesAndCache,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Limpar todos os cookies e cache")
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                Text(
                    text = "Permissões por Site (${sitePermissions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (sitePermissions.isEmpty()) {
                item {
                    Text(
                        text = "Nenhuma regra personalizada por site ainda. Ao conceder ou negar localização/câmera em um site, a regra aparecerá aqui.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(sitePermissions, key = { it.origin }) { perm ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = perm.origin,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = JetBrainsMonoFamily,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(onClick = { onDeleteSitePermission(perm.origin) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remover regra")
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(onClick = {
                                    onUpdateSitePermission(perm.copy(javascriptEnabled = !perm.javascriptEnabled))
                                }) {
                                    Text("JS: ${if (perm.javascriptEnabled) "ON" else "OFF"}")
                                }
                                TextButton(onClick = {
                                    onUpdateSitePermission(perm.copy(cookiesAllowed = !perm.cookiesAllowed))
                                }) {
                                    Text("Cookies: ${if (perm.cookiesAllowed) "ON" else "OFF"}")
                                }
                                TextButton(onClick = {
                                    onUpdateSitePermission(perm.copy(downloadsAllowed = !perm.downloadsAllowed))
                                }) {
                                    Text("DL: ${if (perm.downloadsAllowed) "ON" else "OFF"}")
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
