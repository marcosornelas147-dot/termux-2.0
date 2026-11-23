package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bridge.BridgeConnectionMethod
import com.example.browser.WorkspaceDestination
import com.example.ui.components.BookmarksModalSheet
import com.example.ui.components.BridgeInstallerSheet
import com.example.ui.components.BridgeSecurityAuditSheet
import com.example.ui.components.BrowserSheetType
import com.example.ui.components.DestructiveCommandDialog
import com.example.ui.components.DownloadConfirmDialog
import com.example.ui.components.DownloadsModalSheet
import com.example.ui.components.FirstBridgeAuthDialog
import com.example.ui.components.HistoryModalSheet
import com.example.ui.components.HttpWarningDialog
import com.example.ui.components.MemoryManagerSheet
import com.example.ui.components.SecuritySettingsSheet
import com.example.ui.components.SitePermissionDialog
import com.example.ui.components.SslErrorSecurityDialog
import com.example.ui.components.TabSwitcherOverlay
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.ExternalTerminalScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.screens.TermuxBridgeScreen
import com.example.ui.screens.WorkspaceFilesScreen
import com.example.ui.theme.TermuxBrowserTheme
import com.example.viewmodel.TermuxBrowserViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: TermuxBrowserViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val isFullscreen by viewModel.isFullscreen.collectAsStateWithLifecycle()

            var wasFullscreen by remember { mutableStateOf(false) }
            LaunchedEffect(isFullscreen) {
                if (isFullscreen != wasFullscreen) {
                    wasFullscreen = isFullscreen
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (isFullscreen) {
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }

            TermuxBrowserTheme(darkTheme = settings.darkTheme) {
                TermuxBrowserRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun TermuxBrowserRoot(viewModel: TermuxBrowserViewModel) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val sitePermissions by viewModel.sitePermissions.collectAsStateWithLifecycle()
    val memoryOverview by viewModel.memoryOverview.collectAsStateWithLifecycle()
    val isFullscreen by viewModel.isFullscreen.collectAsStateWithLifecycle()
    val htmlFullscreenView by viewModel.htmlFullscreenView.collectAsStateWithLifecycle()
    val runningServers by viewModel.runningServers.collectAsStateWithLifecycle()
    val terminalLines by viewModel.terminalLines.collectAsStateWithLifecycle()
    val currentPromptPath by viewModel.currentPromptPath.collectAsStateWithLifecycle()
    val workspaceFiles by viewModel.workspaceFiles.collectAsStateWithLifecycle()

    val bridgeStatus by viewModel.bridgeStatus.collectAsStateWithLifecycle()
    val bridgeSessions by viewModel.bridgeSessions.collectAsStateWithLifecycle()
    val activeBridgeSessionId by viewModel.activeBridgeSessionId.collectAsStateWithLifecycle()
    val bridgeAuditLogs by viewModel.bridgeAuditLogs.collectAsStateWithLifecycle()
    val pendingFirstBridgeAuth by viewModel.pendingFirstBridgeAuth.collectAsStateWithLifecycle()
    val pendingDestructiveCommand by viewModel.pendingDestructiveCommand.collectAsStateWithLifecycle()
    val installerSteps by viewModel.installerSteps.collectAsStateWithLifecycle()
    val installerScript by viewModel.installerScript.collectAsStateWithLifecycle()

    val pendingSslError by viewModel.pendingSslError.collectAsStateWithLifecycle()
    val pendingHttpWarning by viewModel.pendingHttpWarning.collectAsStateWithLifecycle()
    val pendingSitePermission by viewModel.pendingSitePermission.collectAsStateWithLifecycle()
    val pendingDownloadPrompt by viewModel.pendingDownloadPrompt.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var showTabSwitcher by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf(BrowserSheetType.NONE) }
    var showBridgeInstaller by remember { mutableStateOf(false) }
    var showBridgeSecurityAudit by remember { mutableStateOf(false) }
    var workspaceSubTab by remember { mutableIntStateOf(0) } // 0 = Terminal Local, 1 = Projetos HTML
    val snackbarHostState = remember { SnackbarHostState() }

    val activeTab = remember(tabs, activeTabId) {
        tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()
    }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeStatusMessage()
        }
    }

    BackHandler(enabled = showTabSwitcher || destination != WorkspaceDestination.BROWSER) {
        if (showTabSwitcher) {
            showTabSwitcher = false
        } else if (destination != WorkspaceDestination.BROWSER) {
            viewModel.setDestination(WorkspaceDestination.BROWSER)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 720.dp

            Scaffold(
                contentWindowInsets = if (isFullscreen) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!isFullscreen && !useRail && !showTabSwitcher) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            NavigationBarItem(
                                selected = destination == WorkspaceDestination.BROWSER,
                                onClick = { viewModel.setDestination(WorkspaceDestination.BROWSER) },
                                icon = {
                                    Icon(
                                        if (destination == WorkspaceDestination.BROWSER) Icons.Filled.Public else Icons.Outlined.Public,
                                        contentDescription = "Navegador"
                                    )
                                },
                                label = { Text("Navegador", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.testTag("nav_browser")
                            )
                            NavigationBarItem(
                                selected = destination == WorkspaceDestination.BRIDGE,
                                onClick = { viewModel.setDestination(WorkspaceDestination.BRIDGE) },
                                icon = {
                                    Icon(
                                        if (destination == WorkspaceDestination.BRIDGE) Icons.Filled.Link else Icons.Outlined.Link,
                                        contentDescription = "Termux Bridge"
                                    )
                                },
                                label = { Text("Bridge", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.testTag("nav_bridge")
                            )
                            NavigationBarItem(
                                selected = destination == WorkspaceDestination.EXTERNAL_TERMINAL,
                                onClick = { viewModel.setDestination(WorkspaceDestination.EXTERNAL_TERMINAL) },
                                icon = {
                                    Icon(
                                        if (destination == WorkspaceDestination.EXTERNAL_TERMINAL) Icons.Filled.Terminal else Icons.Outlined.Terminal,
                                        contentDescription = "Terminal Externo"
                                    )
                                },
                                label = { Text("Term. Ext", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.testTag("nav_external_terminal")
                            )
                            NavigationBarItem(
                                selected = destination == WorkspaceDestination.WORKSPACE,
                                onClick = { viewModel.setDestination(WorkspaceDestination.WORKSPACE) },
                                icon = {
                                    Icon(
                                        if (destination == WorkspaceDestination.WORKSPACE) Icons.Filled.Code else Icons.Outlined.Code,
                                        contentDescription = "Workspace Local"
                                    )
                                },
                                label = { Text("Local", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.testTag("nav_workspace")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (!isFullscreen && useRail && !showTabSwitcher) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            NavigationRailItem(
                                selected = destination == WorkspaceDestination.BROWSER,
                                onClick = { viewModel.setDestination(WorkspaceDestination.BROWSER) },
                                icon = { Icon(Icons.Default.Public, "Navegador") },
                                label = { Text("Navegador") }
                            )
                            NavigationRailItem(
                                selected = destination == WorkspaceDestination.BRIDGE,
                                onClick = { viewModel.setDestination(WorkspaceDestination.BRIDGE) },
                                icon = { Icon(Icons.Default.Link, "Bridge") },
                                label = { Text("Bridge") }
                            )
                            NavigationRailItem(
                                selected = destination == WorkspaceDestination.EXTERNAL_TERMINAL,
                                onClick = { viewModel.setDestination(WorkspaceDestination.EXTERNAL_TERMINAL) },
                                icon = { Icon(Icons.Default.Terminal, "Term. Ext") },
                                label = { Text("Term. Ext") }
                            )
                            NavigationRailItem(
                                selected = destination == WorkspaceDestination.WORKSPACE,
                                onClick = { viewModel.setDestination(WorkspaceDestination.WORKSPACE) },
                                icon = { Icon(Icons.Default.Code, "Local") },
                                label = { Text("Local") }
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        when (destination) {
                            WorkspaceDestination.BROWSER -> {
                                BrowserScreen(
                                    activeTab = activeTab,
                                    tabsCount = tabs.size,
                                    bookmarks = bookmarks,
                                    runningServers = runningServers,
                                    memoryOverview = memoryOverview,
                                    bridgeStatus = bridgeStatus,
                                    settings = settings,
                                    isFullscreen = isFullscreen,
                                    htmlFullscreenView = htmlFullscreenView,
                                    webViewPool = viewModel.webViewPool,
                                    onNavigate = { viewModel.navigateTo(it) },
                                    onGoBack = { viewModel.goBack() },
                                    onGoForward = { viewModel.goForward() },
                                    onReloadOrStop = { viewModel.reloadOrStop() },
                                    onOpenTabSwitcher = {
                                        viewModel.refreshMemoryStats()
                                        showTabSwitcher = true
                                    },
                                    onNewTab = { isPriv -> viewModel.openNewTab(isPrivate = isPriv) },
                                    onToggleBookmark = { viewModel.toggleCurrentBookmark() },
                                    onToggleDesktopMode = { viewModel.toggleDesktopMode() },
                                    onAdjustZoom = { delta -> viewModel.adjustZoom(delta) },
                                    onSetFullscreen = { viewModel.setFullscreen(it) },
                                    onOpenSheet = { sheet ->
                                        if (sheet == BrowserSheetType.MEMORY) viewModel.refreshMemoryStats()
                                        activeSheet = sheet
                                    },
                                    onToggleTheme = { viewModel.updateDarkTheme(it) },
                                    onStartLocalServer = { port, label ->
                                        viewModel.startLocalServerAndOpen(port, label)
                                    },
                                    onOpenTerminal = {
                                        workspaceSubTab = 0
                                        viewModel.setDestination(WorkspaceDestination.WORKSPACE)
                                    },
                                    onOpenBridge = { viewModel.setDestination(WorkspaceDestination.BRIDGE) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            WorkspaceDestination.BRIDGE -> {
                                TermuxBridgeScreen(
                                    status = bridgeStatus,
                                    settings = settings,
                                    onConnect = { host, httpPort, sshPort, sshUser, method ->
                                        viewModel.requestBridgeConnect(host, httpPort, sshPort, sshUser, method)
                                    },
                                    onDisconnect = { viewModel.disconnectBridge() },
                                    onTestConnection = { host, httpPort, sshPort, sshUser, method ->
                                        viewModel.testBridgeConnection(host, httpPort, sshPort, sshUser, method)
                                    },
                                    onOpenExternalTerminal = {
                                        viewModel.setDestination(WorkspaceDestination.EXTERNAL_TERMINAL)
                                    },
                                    onOpenInstaller = {
                                        viewModel.prepareBridgeInstaller()
                                        showBridgeInstaller = true
                                    },
                                    onOpenSecurityAudit = { showBridgeSecurityAudit = true },
                                    onOpenLocalPortInBrowser = { port ->
                                        viewModel.openTermuxServerInBrowser(port)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            WorkspaceDestination.EXTERNAL_TERMINAL -> {
                                ExternalTerminalScreen(
                                    bridgeStatus = bridgeStatus,
                                    sessions = bridgeSessions,
                                    activeSessionId = activeBridgeSessionId,
                                    onSelectSession = { viewModel.selectBridgeSession(it) },
                                    onNewSession = { viewModel.createBridgeSession() },
                                    onCloseSession = { viewModel.closeBridgeSession(it) },
                                    onSendCommand = { viewModel.sendCommandToExternalTerminal(it) },
                                    onInterruptProcess = { viewModel.interruptExternalProcess(it) },
                                    onOpenServerPortInBrowser = { port ->
                                        viewModel.openTermuxServerInBrowser(port)
                                    },
                                    onGoToBridgeConfig = {
                                        viewModel.setDestination(WorkspaceDestination.BRIDGE)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            WorkspaceDestination.WORKSPACE -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                            SegmentedButton(
                                                selected = workspaceSubTab == 0,
                                                onClick = { workspaceSubTab = 0 },
                                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                                modifier = Modifier.testTag("workspace_tab_terminal")
                                            ) {
                                                Text("Terminal Sandbox")
                                            }
                                            SegmentedButton(
                                                selected = workspaceSubTab == 1,
                                                onClick = { workspaceSubTab = 1 },
                                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                                modifier = Modifier.testTag("workspace_tab_projects")
                                            ) {
                                                Text("Projetos HTML")
                                            }
                                        }
                                    }
                                    if (workspaceSubTab == 0) {
                                        TerminalScreen(
                                            lines = terminalLines,
                                            promptPath = currentPromptPath,
                                            runningServers = runningServers,
                                            onExecuteCommand = { viewModel.runTerminalCommand(it) },
                                            onAutocomplete = { viewModel.terminalProcessor.autocomplete(it) },
                                            getHistory = { viewModel.terminalProcessor.getHistoryCommands() },
                                            onOpenUrlInBrowser = { url ->
                                                viewModel.openUrlInNewOrCurrentTab(url, forceNewTab = true)
                                                viewModel.setDestination(WorkspaceDestination.BROWSER)
                                            },
                                            onStopServer = { port -> viewModel.stopLocalServer(port) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        WorkspaceFilesScreen(
                                            files = workspaceFiles,
                                            terminalProcessor = viewModel.terminalProcessor,
                                            onRunInBrowser = {
                                                viewModel.startLocalServerAndOpen(8080, "python3 -m http.server 8080")
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Visual Tab Switcher Panel (17.3)
        if (showTabSwitcher) {
            TabSwitcherOverlay(
                tabs = tabs,
                activeTabId = activeTab.id,
                memoryOverview = memoryOverview,
                onSelectTab = { viewModel.selectTab(it) },
                onCloseTab = { viewModel.closeTab(it) },
                onDuplicateTab = { viewModel.duplicateTab(it) },
                onSuspendTab = { viewModel.suspendTab(it) },
                onSuspendInactive = { viewModel.suspendAllInactiveTabs() },
                onNewTab = { isPriv -> viewModel.openNewTab(isPrivate = isPriv) },
                onDismiss = { showTabSwitcher = false }
            )
        }

        // Management Bottom Sheets
        when (activeSheet) {
            BrowserSheetType.HISTORY -> {
                HistoryModalSheet(
                    history = history,
                    onOpenUrl = { viewModel.navigateTo(it) },
                    onDeleteItem = { viewModel.deleteHistoryEntry(it) },
                    onClearAll = { viewModel.clearHistory() },
                    onDismiss = { activeSheet = BrowserSheetType.NONE }
                )
            }
            BrowserSheetType.BOOKMARKS -> {
                BookmarksModalSheet(
                    bookmarks = bookmarks,
                    currentUrl = activeTab.url,
                    currentTitle = activeTab.title,
                    onAddCurrent = { viewModel.addBookmark(activeTab.url, activeTab.title) },
                    onOpenUrl = { viewModel.navigateTo(it) },
                    onDeleteBookmark = { viewModel.deleteBookmark(it) },
                    onDismiss = { activeSheet = BrowserSheetType.NONE }
                )
            }
            BrowserSheetType.DOWNLOADS -> {
                DownloadsModalSheet(
                    downloads = downloads,
                    onDeleteDownload = { viewModel.deleteDownload(it) },
                    onClearAll = { viewModel.clearDownloads() },
                    onDismiss = { activeSheet = BrowserSheetType.NONE }
                )
            }
            BrowserSheetType.MEMORY -> {
                MemoryManagerSheet(
                    tabs = tabs,
                    memoryOverview = memoryOverview,
                    settings = settings,
                    onMaxActiveTabsChange = { viewModel.updateMaxActiveTabs(it) },
                    onAutoSuspendChange = { viewModel.updateAutoSuspend(it) },
                    onSuspendInactive = { viewModel.suspendAllInactiveTabs() },
                    onClearCache = { includeCookies -> viewModel.clearCache(includeCookies) },
                    onDismiss = { activeSheet = BrowserSheetType.NONE }
                )
            }
            BrowserSheetType.SECURITY -> {
                SecuritySettingsSheet(
                    settings = settings,
                    sitePermissions = sitePermissions,
                    onToggleHttpsOnly = { viewModel.updateHttpsOnly(it) },
                    onToggleJavascript = { viewModel.updateJavascript(it) },
                    onToggleCookies = { viewModel.updateCookies(it) },
                    onToggleThirdPartyCookies = { viewModel.updateThirdPartyCookies(it) },
                    onToggleAskDownload = { viewModel.updateAskBeforeDownload(it) },
                    onToggleBlockDownloads = { viewModel.updateBlockAllDownloads(it) },
                    onUpdateSitePermission = { viewModel.saveSitePermission(it) },
                    onDeleteSitePermission = { viewModel.deleteSitePermission(it) },
                    onClearCookiesAndCache = { viewModel.clearCache(includeCookies = true) },
                    onDismiss = { activeSheet = BrowserSheetType.NONE }
                )
            }
            BrowserSheetType.NONE -> Unit
        }

        // Bridge Installer & Security Sheets (18.7 & 18.8)
        if (showBridgeInstaller) {
            BridgeInstallerSheet(
                steps = installerSteps,
                scriptText = installerScript,
                termuxInstalled = bridgeStatus.termuxInstalled,
                onRegenerateSecrets = { viewModel.prepareBridgeInstaller(regenerateCredentials = true) },
                onTestConnection = {
                    val method = runCatching { BridgeConnectionMethod.valueOf(settings.bridgeMethod) }
                        .getOrDefault(BridgeConnectionMethod.HTTP_API)
                    viewModel.testBridgeConnection(
                        settings.bridgeHost,
                        settings.bridgeHttpPort,
                        settings.bridgeSshPort,
                        settings.bridgeSshUser,
                        method
                    )
                    viewModel.prepareBridgeInstaller()
                },
                onDismiss = { showBridgeInstaller = false }
            )
        }

        if (showBridgeSecurityAudit) {
            BridgeSecurityAuditSheet(
                settings = settings,
                auditLogs = bridgeAuditLogs,
                onSaveAllowedRoots = { viewModel.updateBridgeAllowedRoots(it) },
                onClearLogs = { viewModel.clearBridgeAuditLogs() },
                onRevokeAll = { viewModel.revokeBridgeConnection() },
                onDismiss = { showBridgeSecurityAudit = false }
            )
        }

        // Security & Bridge Authorization Dialogs
        if (pendingFirstBridgeAuth) {
            FirstBridgeAuthDialog(
                host = settings.bridgeHost,
                port = settings.bridgeHttpPort,
                onAuthorize = { viewModel.resolveFirstBridgeAuthorization(true) },
                onDeny = { viewModel.resolveFirstBridgeAuthorization(false) }
            )
        }

        pendingDestructiveCommand?.let { pending ->
            DestructiveCommandDialog(
                pending = pending,
                onConfirm = { viewModel.resolveDestructiveCommand(true) },
                onCancel = { viewModel.resolveDestructiveCommand(false) }
            )
        }

        pendingSslError?.let { ssl ->
            SslErrorSecurityDialog(
                error = ssl,
                onResolve = { proceed, rememberHost ->
                    viewModel.resolveSslError(proceed, rememberHost)
                }
            )
        }

        pendingHttpWarning?.let { warn ->
            HttpWarningDialog(
                warning = warn,
                onResolve = { useHttps -> viewModel.resolveHttpWarning(useHttps) }
            )
        }

        pendingSitePermission?.let { perm ->
            SitePermissionDialog(
                prompt = perm,
                onResolve = { allow, rememberSite ->
                    viewModel.resolveSitePermission(allow, rememberSite)
                }
            )
        }

        pendingDownloadPrompt?.let { dl ->
            DownloadConfirmDialog(
                prompt = dl,
                onConfirm = { viewModel.confirmDownload(dl) },
                onCancel = { viewModel.cancelDownloadPrompt() }
            )
        }
    }
}
