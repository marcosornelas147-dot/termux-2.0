package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.browser.MemoryOverview
import com.example.bridge.BridgeConnectionState
import com.example.bridge.BridgeStatus
import com.example.data.local.BookmarkEntity
import com.example.server.RunningServerInfo
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PhosphorEmerald

private data class QuickDialTarget(
    val title: String,
    val url: String,
    val subtitle: String,
    val icon: ImageVector,
    val isLocal: Boolean = false
)

private val DefaultQuickDials = listOf(
    QuickDialTarget("Google", "https://www.google.com", "google.com", Icons.Default.Public),
    QuickDialTarget("GitHub", "https://github.com", "github.com", Icons.Default.Code),
    QuickDialTarget("YouTube", "https://m.youtube.com", "youtube.com", Icons.Default.PlayCircle),
    QuickDialTarget("Localhost 8080", "http://127.0.0.1:8080", "127.0.0.1:8080", Icons.Default.Dns, isLocal = true),
    QuickDialTarget("Localhost 3000", "http://127.0.0.1:3000", "127.0.0.1:3000", Icons.Default.Terminal, isLocal = true),
    QuickDialTarget("MDN Web Docs", "https://developer.mozilla.org", "developer.mozilla.org", Icons.Default.Language)
)

@Composable
fun StartPageView(
    isPrivate: Boolean,
    runningServers: List<RunningServerInfo>,
    bookmarks: List<BookmarkEntity>,
    memoryOverview: MemoryOverview,
    bridgeStatus: BridgeStatus,
    onNavigate: (String) -> Unit,
    onFocusUrlBar: () -> Unit,
    onStartLocalServer: (Int, String) -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenBridge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (isPrivate) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        if (isPrivate) {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
                        },
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header Hero
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = accent.copy(alpha = 0.16f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPrivate) Icons.Default.Lock else Icons.Default.Terminal,
                                        contentDescription = null,
                                        tint = accent
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isPrivate) "TERMUX 2.0 • ABA PRIVADA" else "TERMUX 2.0 • BROWSER",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = accent
                                )
                                Text(
                                    text = if (isPrivate) {
                                        "Navegação sem histórico e cookies temporários"
                                    } else {
                                        "Navegador nativo integrado ao ambiente Linux"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Quick Search Tap Box
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("start_page_search_box")
                                .clickable(onClick = onFocusUrlBar)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Pesquisar na web ou abrir 127.0.0.1:8080...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // RAM / Tab status pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "RAM: ${memoryOverview.totalBrowserEstimatedMb} MB abas • ${memoryOverview.deviceAvailableMb} MB livre no dispositivo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // TERMUX BRIDGE Quick Status Card on Main Panel (18.3)
            item {
                val bridgeColor = when (bridgeStatus.state) {
                    BridgeConnectionState.CONNECTED -> PhosphorEmerald
                    BridgeConnectionState.CONNECTING -> AmberWarn
                    BridgeConnectionState.DISCONNECTED -> AlertCoral
                }
                val bridgeStateLabel = when (bridgeStatus.state) {
                    BridgeConnectionState.CONNECTED -> "Conectado • ${bridgeStatus.latencyMs ?: 0} ms"
                    BridgeConnectionState.CONNECTING -> "Conectando..."
                    BridgeConnectionState.DISCONNECTED -> "Desconectado"
                }
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, bridgeColor.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("start_page_bridge_card")
                        .clickable(onClick = onOpenBridge)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .clip(CircleShape)
                                    .background(bridgeColor)
                            )
                            Column {
                                Text(
                                    text = "TERMUX BRIDGE",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$bridgeStateLabel • ${bridgeStatus.host}:${bridgeStatus.port} (${bridgeStatus.method.label})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = "Configurar →",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Localhost Dev Servers Section (17.4)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Servidores Localhost (Terminal)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        OutlinedButton(
                            onClick = onOpenTerminal,
                            modifier = Modifier.testTag("start_open_terminal_btn"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Abrir Terminal", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (runningServers.isEmpty()) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Nenhum servidor ativo. Inicie um servidor HTTP local com 1 toque para testar seus arquivos HTML/CSS/JS em 127.0.0.1:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { onStartLocalServer(8080, "python3 -m http.server 8080") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("quick_start_8080_btn"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text(
                                            text = "Iniciar :8080",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { onStartLocalServer(3000, "node dev-server :3000") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("quick_start_3000_btn")
                                    ) {
                                        Text(
                                            text = "Iniciar :3000",
                                            fontFamily = JetBrainsMonoFamily
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(runningServers, key = { it.port }) { srv ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                                    modifier = Modifier
                                        .width(230.dp)
                                        .clickable { onNavigate(srv.baseUrl) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "● ONLINE :${srv.port}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = srv.baseUrl,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontFamily = JetBrainsMonoFamily
                                        )
                                        Text(
                                            text = "${srv.label} • ${srv.requestCount} reqs",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Access Shortcuts
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Acesso Rápido",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    val rows = DefaultQuickDials.chunked(2)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rows.forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                pair.forEach { dial ->
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("quick_dial_${dial.title.lowercase().replace(" ", "_")}")
                                            .clickable {
                                                if (dial.isLocal && runningServers.none { dial.url.contains(":${it.port}") }) {
                                                    val port = dial.url.substringAfterLast(":").toIntOrNull() ?: 8080
                                                    onStartLocalServer(port, "http.server $port")
                                                } else {
                                                    onNavigate(dial.url)
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (dial.isLocal) {
                                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                                                        } else {
                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = dial.icon,
                                                    contentDescription = null,
                                                    tint = if (dial.isLocal) {
                                                        MaterialTheme.colorScheme.secondary
                                                    } else {
                                                        MaterialTheme.colorScheme.primary
                                                    },
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = dial.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = dial.subtitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                                if (pair.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Bookmarks
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Favoritos",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (bookmarks.isEmpty()) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Nenhum favorito salvo ainda. Use o menu (⋮) em qualquer página para favoritar.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            bookmarks.take(8).forEach { bm ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigate(bm.url) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bookmark,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
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
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
