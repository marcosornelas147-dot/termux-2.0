package com.example.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.bridge.BridgeStatus
import com.example.browser.BrowserTabState
import com.example.browser.HOME_TAB_URL
import com.example.browser.MemoryOverview
import com.example.browser.PageSecurityState
import com.example.browser.WebViewPoolManager
import com.example.data.local.BookmarkEntity
import com.example.data.local.BrowserSettings
import com.example.server.RunningServerInfo
import com.example.ui.components.BrowserSheetType
import com.example.ui.components.StartPageView
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun BrowserScreen(
    activeTab: BrowserTabState,
    tabsCount: Int,
    bookmarks: List<BookmarkEntity>,
    runningServers: List<RunningServerInfo>,
    memoryOverview: MemoryOverview,
    bridgeStatus: BridgeStatus,
    settings: BrowserSettings,
    isFullscreen: Boolean,
    htmlFullscreenView: android.view.View?,
    webViewPool: WebViewPoolManager,
    onNavigate: (String) -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onOpenTabSwitcher: () -> Unit,
    onNewTab: (Boolean) -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onAdjustZoom: (Int) -> Unit,
    onSetFullscreen: (Boolean) -> Unit,
    onOpenSheet: (BrowserSheetType) -> Unit,
    onToggleTheme: (Boolean) -> Unit,
    onStartLocalServer: (Int, String) -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenBridge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val urlFocusRequester = remember { FocusRequester() }
    var menuExpanded by remember { mutableStateOf(false) }

    val displayUrl = if (activeTab.url == HOME_TAB_URL) "" else activeTab.url
    var urlFieldValue by remember(activeTab.id, displayUrl) {
        mutableStateOf(TextFieldValue(displayUrl))
    }
    var isUrlFocused by remember { mutableStateOf(false) }

    LaunchedEffect(displayUrl, activeTab.id) {
        if (!isUrlFocused) {
            urlFieldValue = TextFieldValue(displayUrl)
        }
    }

    val isBookmarked = remember(bookmarks, activeTab.url) {
        bookmarks.any { it.url == activeTab.url }
    }

    // Handle Android Back button inside the browser tab
    BackHandler(enabled = isFullscreen || activeTab.canGoBack) {
        if (isFullscreen) {
            onSetFullscreen(false)
        } else if (activeTab.canGoBack) {
            onGoBack()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // HTML5 Video Fullscreen custom view if active
        if (htmlFullscreenView != null) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        (htmlFullscreenView.parent as? ViewGroup)?.removeView(htmlFullscreenView)
                        addView(htmlFullscreenView)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            SmallFloatingActionButton(
                onClick = { onSetFullscreen(false) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.FullscreenExit, contentDescription = "Sair da tela cheia")
            }
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Permanent Browser Navigation & Address Bar (hidden in Fullscreen mode)
            if (!isFullscreen) {
                Surface(
                    color = if (activeTab.isPrivate) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // [ ← ] Back button
                            IconButton(
                                onClick = onGoBack,
                                enabled = activeTab.canGoBack,
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("browser_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Voltar",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // [ → ] Forward button
                            IconButton(
                                onClick = onGoForward,
                                enabled = activeTab.canGoForward,
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("browser_forward_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Avançar",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // [ URL DO SITE ] Address Bar Pill
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = when {
                                        isUrlFocused -> MaterialTheme.colorScheme.primary
                                        activeTab.isPrivate -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Security indicator icon
                                    val secIcon = when {
                                        activeTab.isPrivate -> Icons.Default.Shield
                                        activeTab.securityState == PageSecurityState.HTTPS_SECURE -> Icons.Default.Lock
                                        activeTab.securityState == PageSecurityState.LOCALHOST -> Icons.Default.Dns
                                        activeTab.securityState == PageSecurityState.SSL_ERROR -> Icons.Default.GppBad
                                        activeTab.securityState == PageSecurityState.HTTP_INSECURE -> Icons.Default.LockOpen
                                        else -> Icons.Default.Terminal
                                    }
                                    val secTint = when {
                                        activeTab.isPrivate -> MaterialTheme.colorScheme.tertiary
                                        activeTab.securityState == PageSecurityState.HTTPS_SECURE -> MaterialTheme.colorScheme.primary
                                        activeTab.securityState == PageSecurityState.LOCALHOST -> MaterialTheme.colorScheme.secondary
                                        activeTab.securityState == PageSecurityState.SSL_ERROR ||
                                            activeTab.securityState == PageSecurityState.HTTP_INSECURE -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Icon(
                                        imageVector = secIcon,
                                        contentDescription = "Status de segurança",
                                        tint = secTint,
                                        modifier = Modifier.size(16.dp)
                                    )

                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (urlFieldValue.text.isEmpty() && !isUrlFocused) {
                                            Text(
                                                text = "URL ou 127.0.0.1:8080",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                        BasicTextField(
                                            value = urlFieldValue,
                                            onValueChange = { urlFieldValue = it },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                fontFamily = JetBrainsMonoFamily,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Uri,
                                                imeAction = ImeAction.Go
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onGo = {
                                                    focusManager.clearFocus()
                                                    onNavigate(urlFieldValue.text)
                                                }
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(urlFocusRequester)
                                                .testTag("address_bar_input")
                                                .onFocusChanged { state ->
                                                    isUrlFocused = state.isFocused
                                                    if (state.isFocused) {
                                                        urlFieldValue = urlFieldValue.copy(
                                                            selection = TextRange(0, urlFieldValue.text.length)
                                                        )
                                                    }
                                                }
                                        )
                                    }

                                    // Clear when focused, otherwise Reload/Stop
                                    if (isUrlFocused && urlFieldValue.text.isNotEmpty()) {
                                        IconButton(
                                            onClick = { urlFieldValue = TextFieldValue("") },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Limpar endereço",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else if (activeTab.url != HOME_TAB_URL) {
                                        IconButton(
                                            onClick = onReloadOrStop,
                                            modifier = Modifier
                                                .size(30.dp)
                                                .testTag("browser_reload_stop_button")
                                        ) {
                                            Icon(
                                                imageVector = if (activeTab.isLoading) Icons.Default.Close else Icons.Default.Refresh,
                                                contentDescription = if (activeTab.isLoading) "Interromper carregamento" else "Atualizar página",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // [ N abas ] Permanent Tab Selector Badge Button (17.3)
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("tab_switcher_button")
                                    .clickable(onClick = onOpenTabSwitcher),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(7.dp),
                                    color = if (activeTab.isPrivate) {
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    },
                                    border = BorderStroke(
                                        1.8.dp,
                                        if (activeTab.isPrivate) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    ),
                                    modifier = Modifier.size(25.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = if (tabsCount > 99) "99+" else tabsCount.toString(),
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = if (tabsCount >= 10) 10.sp else 12.sp,
                                            textAlign = TextAlign.Center,
                                            color = if (activeTab.isPrivate) {
                                                MaterialTheme.colorScheme.tertiary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            }

                            // [ ⋮ ] Overflow Menu
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("browser_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Mais opções"
                                    )
                                }

                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Nova aba") },
                                        leadingIcon = { Icon(Icons.Default.Add, null) },
                                        onClick = {
                                            menuExpanded = false
                                            onNewTab(false)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Nova aba privada") },
                                        leadingIcon = { Icon(Icons.Default.Shield, null) },
                                        onClick = {
                                            menuExpanded = false
                                            onNewTab(true)
                                        }
                                    )
                                    if (activeTab.url != HOME_TAB_URL) {
                                        DropdownMenuItem(
                                            text = { Text("Página inicial") },
                                            leadingIcon = { Icon(Icons.Default.Home, null) },
                                            onClick = {
                                                menuExpanded = false
                                                onNavigate(HOME_TAB_URL)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (isBookmarked) "Remover favorito" else "Favoritar página") },
                                            leadingIcon = {
                                                Icon(
                                                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                    null
                                                )
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                onToggleBookmark()
                                            }
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Modo Desktop") },
                                        leadingIcon = { Icon(Icons.Default.DesktopWindows, null) },
                                        trailingIcon = {
                                            Switch(
                                                checked = activeTab.isDesktopMode,
                                                onCheckedChange = null
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onToggleDesktopMode()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text("Zoom (${activeTab.zoomPercent}%)")
                                                IconButton(
                                                    onClick = { onAdjustZoom(-15) },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(Icons.Default.ZoomOut, "Diminuir zoom")
                                                }
                                                IconButton(
                                                    onClick = { onAdjustZoom(15) },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(Icons.Default.ZoomIn, "Aumentar zoom")
                                                }
                                            }
                                        },
                                        onClick = {}
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Tela cheia") },
                                        leadingIcon = { Icon(Icons.Default.Fullscreen, null) },
                                        onClick = {
                                            menuExpanded = false
                                            onSetFullscreen(true)
                                        }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Histórico") },
                                        leadingIcon = { Icon(Icons.Default.History, null) },
                                        onClick = {
                                            menuExpanded = false
                                            onOpenSheet(BrowserSheetType.HISTORY)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Favoritos") },
                                        leadingIcon = { Icon(Icons.Default.Bookmark, null) },
                                        onClick = {
                                            menuExpanded = false
                                            onOpenSheet(BrowserSheetType.BOOKMARKS)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Downloads") },
                                        leadingIcon = { Icon(Icons.Default.Download, null) },
                                        onClick = {
                                            menuExpanded = false
                                            onOpenSheet(BrowserSheetType.DOWNLOADS)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Memória & Abas (RAM)") },
                                        leadingIcon = { Icon(Icons.Default.Memory, null) },
                                        onClick = {
                                            menuExpanded = false
                                            onOpenSheet(BrowserSheetType.MEMORY)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Segurança e Permissões") },
                                        leadingIcon = { Icon(Icons.Default.Security, null) },
                                        onClick = {
                                            menuExpanded = false
                                            onOpenSheet(BrowserSheetType.SECURITY)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Tema Escuro") },
                                        leadingIcon = { Icon(Icons.Default.DarkMode, null) },
                                        trailingIcon = {
                                            Switch(checked = settings.darkTheme, onCheckedChange = null)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onToggleTheme(!settings.darkTheme)
                                        }
                                    )
                                }
                            }
                        }

                        // Loading Progress Bar
                        if (activeTab.isLoading) {
                            LinearProgressIndicator(
                                progress = { (activeTab.progress / 100f).coerceIn(0.05f, 1f) },
                                color = if (activeTab.isPrivate) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp)
                            )
                        } else {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            // Main Web Content Area: StartPageView OR Live Pooled WebView
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (activeTab.url == HOME_TAB_URL) {
                    StartPageView(
                        isPrivate = activeTab.isPrivate,
                        runningServers = runningServers,
                        bookmarks = bookmarks,
                        memoryOverview = memoryOverview,
                        bridgeStatus = bridgeStatus,
                        onNavigate = onNavigate,
                        onFocusUrlBar = {
                            urlFocusRequester.requestFocus()
                        },
                        onStartLocalServer = onStartLocalServer,
                        onOpenTerminal = onOpenTerminal,
                        onOpenBridge = onOpenBridge
                    )
                } else {
                    // Live pooled AndroidView WebView container (instant switching without reloads!)
                    AndroidView(
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { container ->
                            val targetWebView = webViewPool.getOrCreateWebView(activeTab)
                            val currentChild = if (container.childCount > 0) container.getChildAt(0) else null
                            if (currentChild !== targetWebView) {
                                container.removeAllViews()
                                (targetWebView.parent as? ViewGroup)?.removeView(targetWebView)
                                container.addView(
                                    targetWebView,
                                    ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("browser_webview_container")
                    )
                }
            }
        }

        // Floating Exit Fullscreen button when in Fullscreen mode
        if (isFullscreen) {
            SmallFloatingActionButton(
                onClick = { onSetFullscreen(false) },
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("exit_fullscreen_fab")
            ) {
                Icon(Icons.Default.FullscreenExit, contentDescription = "Sair da tela cheia")
            }
        }
    }
}
