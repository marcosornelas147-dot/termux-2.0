package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.browser.BrowserTabState
import com.example.browser.HOME_TAB_URL
import com.example.browser.MemoryOverview
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun TabSwitcherOverlay(
    tabs: List<BrowserTabState>,
    activeTabId: String,
    memoryOverview: MemoryOverview,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onDuplicateTab: (String) -> Unit,
    onSuspendTab: (String) -> Unit,
    onSuspendInactive: () -> Unit,
    onNewTab: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "${tabs.size} ${if (tabs.size == 1) "aba aberta" else "abas abertas"}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${memoryOverview.activeTabsCount} ativa(s) • ${memoryOverview.suspendedTabsCount} suspensa(s) • ~${memoryOverview.totalBrowserEstimatedMb} MB RAM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onSuspendInactive,
                        modifier = Modifier.testTag("suspend_inactive_tabs_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Otimizar RAM")
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_tab_switcher_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar painel de abas")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            // Grid of tab cards with thumbnails
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val columns = when {
                    maxWidth >= 840.dp -> 4
                    maxWidth >= 560.dp -> 3
                    else -> 2
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        TabPreviewCard(
                            tab = tab,
                            isActive = tab.id == activeTabId,
                            onSelect = {
                                onSelectTab(tab.id)
                                onDismiss()
                            },
                            onClose = { onCloseTab(tab.id) },
                            onDuplicate = { onDuplicateTab(tab.id) },
                            onSuspend = { onSuspendTab(tab.id) }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            // Bottom New Tab Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onNewTab(false)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("new_tab_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Nova Aba")
                }
                OutlinedButton(
                    onClick = {
                        onNewTab(true)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("new_private_tab_button"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.tertiary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Aba Privada")
                }
            }
        }
    }
}

@Composable
private fun TabPreviewCard(
    tab: BrowserTabState,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onDuplicate: () -> Unit,
    onSuspend: () -> Unit
) {
    val borderColor = when {
        isActive && tab.isPrivate -> MaterialTheme.colorScheme.tertiary
        isActive -> MaterialTheme.colorScheme.primary
        tab.isPrivate -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outline
    }

    val faviconImage = remember(tab.favicon) { tab.favicon?.asImageBitmap() }
    val thumbImage = remember(tab.thumbnail) { tab.thumbnail?.asImageBitmap() }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(if (isActive) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tab_card_${tab.id}")
            .clickable(onClick = onSelect)
    ) {
        Column {
            // Card Header: Favicon + Title + Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (faviconImage != null) {
                    Image(
                        bitmap = faviconImage,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                } else {
                    Icon(
                        imageVector = if (tab.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                        contentDescription = null,
                        tint = if (tab.isPrivate) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = tab.title.ifBlank { tab.url },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar aba",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Thumbnail Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (thumbImage != null) {
                    Image(
                        bitmap = thumbImage,
                        contentDescription = "Miniatura da página ${tab.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                tab.isSuspended -> Icons.Default.Bedtime
                                tab.isPrivate -> Icons.Default.Shield
                                else -> Icons.Default.Public
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = when {
                                tab.url == HOME_TAB_URL -> "Página Inicial"
                                tab.isSuspended -> "Aba suspensa"
                                else -> "Carregando..."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status Badges overlay
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (tab.isPrivate) {
                        StatusChip(text = "Privada", color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (tab.isDesktopMode) {
                        StatusChip(text = "Desktop", color = MaterialTheme.colorScheme.secondary)
                    }
                    if (tab.isSuspended) {
                        StatusChip(text = "Suspensa", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // URL & Action Row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (tab.url == HOME_TAB_URL) "termux://home" else tab.url,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "~${tab.estimatedMemoryMb} MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (!tab.isSuspended && tab.url != HOME_TAB_URL) {
                            IconButton(
                                onClick = onSuspend,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = "Suspender aba para liberar RAM",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onDuplicate,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Duplicar aba",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
