package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import android.webkit.WebChromeClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bridge.BridgeConnectionMethod
import com.example.bridge.BridgeConnectionState
import com.example.bridge.BridgeScriptGenerator
import com.example.bridge.BridgeSecurityPolicy
import com.example.bridge.BridgeStatus
import com.example.bridge.ExternalTerminalSession
import com.example.bridge.InstallerStepStatus
import com.example.bridge.PendingDestructiveCommand
import com.example.bridge.TermuxBridgeManager
import com.example.browser.BrowserTabState
import com.example.browser.BrowserUrlUtils
import com.example.browser.HOME_TAB_URL
import com.example.browser.MemoryOverview
import com.example.browser.PageSecurityState
import com.example.browser.PendingDownloadPrompt
import com.example.browser.PendingHttpWarning
import com.example.browser.PendingSitePermission
import com.example.browser.PendingSslError
import com.example.browser.SitePermissionKind
import com.example.browser.WebViewCallbacks
import com.example.browser.WebViewPoolManager
import com.example.browser.WorkspaceDestination
import com.example.data.local.BookmarkEntity
import com.example.data.local.BridgeAuditEntity
import com.example.data.local.BrowserDatabase
import com.example.data.local.BrowserPreferencesRepository
import com.example.data.local.BrowserSettings
import com.example.data.local.DownloadEntryEntity
import com.example.data.local.HistoryEntryEntity
import com.example.data.local.SavedTabEntity
import com.example.data.local.SitePermissionEntity
import com.example.data.repository.BrowserRepository
import com.example.server.LocalDevServerManager
import com.example.server.RunningServerInfo
import com.example.server.TerminalCommandProcessor
import com.example.server.TerminalLine
import com.example.server.WorkspaceFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class TermuxBrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val database = BrowserDatabase.getInstance(application)
    private val prefsRepo = BrowserPreferencesRepository(application)
    private val repository = BrowserRepository(database.browserDao(), prefsRepo)

    val serverManager = LocalDevServerManager(application)
    val terminalProcessor = TerminalCommandProcessor(
        context = application,
        serverManager = serverManager,
        onOpenUrlInBrowser = { url ->
            viewModelScope.launch(Dispatchers.Main) {
                openUrlInNewOrCurrentTab(url, forceNewTab = false)
                setDestination(WorkspaceDestination.BROWSER)
            }
        }
    )

    private val sitePermissionsCache = ConcurrentHashMap<String, SitePermissionEntity>()
    private val latestSettings = MutableStateFlow(BrowserSettings())

    val settings: StateFlow<BrowserSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, BrowserSettings())

    val history: StateFlow<List<HistoryEntryEntity>> = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.bookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<DownloadEntryEntity>> = repository.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sitePermissions: StateFlow<List<SitePermissionEntity>> = repository.sitePermissions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val runningServers: StateFlow<List<RunningServerInfo>> = serverManager.runningServers
    val terminalLines: StateFlow<List<TerminalLine>> = terminalProcessor.lines
    val currentPromptPath: StateFlow<String> = terminalProcessor.currentPromptPath
    val workspaceFiles: StateFlow<List<WorkspaceFileItem>> = terminalProcessor.workspaceFiles

    private val _destination = MutableStateFlow(WorkspaceDestination.BROWSER)
    val destination: StateFlow<WorkspaceDestination> = _destination.asStateFlow()

    private val _tabs = MutableStateFlow<List<BrowserTabState>>(listOf(BrowserTabState()))
    val tabs: StateFlow<List<BrowserTabState>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow(_tabs.value.first().id)
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private val _htmlFullscreenView = MutableStateFlow<View?>(null)
    val htmlFullscreenView: StateFlow<View?> = _htmlFullscreenView.asStateFlow()
    private var htmlCustomViewCallback: WebChromeClient.CustomViewCallback? = null

    private val _memoryOverview = MutableStateFlow(MemoryOverview())
    val memoryOverview: StateFlow<MemoryOverview> = _memoryOverview.asStateFlow()

    private val _pendingSslError = MutableStateFlow<PendingSslError?>(null)
    val pendingSslError: StateFlow<PendingSslError?> = _pendingSslError.asStateFlow()

    private val _pendingHttpWarning = MutableStateFlow<PendingHttpWarning?>(null)
    val pendingHttpWarning: StateFlow<PendingHttpWarning?> = _pendingHttpWarning.asStateFlow()

    private val _pendingSitePermission = MutableStateFlow<PendingSitePermission?>(null)
    val pendingSitePermission: StateFlow<PendingSitePermission?> = _pendingSitePermission.asStateFlow()

    private val _pendingDownloadPrompt = MutableStateFlow<PendingDownloadPrompt?>(null)
    val pendingDownloadPrompt: StateFlow<PendingDownloadPrompt?> = _pendingDownloadPrompt.asStateFlow()

    val bridgeManager = TermuxBridgeManager(application)
    val bridgeStatus: StateFlow<BridgeStatus> = bridgeManager.status
    val bridgeSessions: StateFlow<List<ExternalTerminalSession>> = bridgeManager.sessions
    val activeBridgeSessionId: StateFlow<String> = bridgeManager.activeSessionId

    val bridgeAuditLogs: StateFlow<List<BridgeAuditEntity>> = repository.bridgeAuditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _pendingFirstBridgeAuth = MutableStateFlow(false)
    val pendingFirstBridgeAuth: StateFlow<Boolean> = _pendingFirstBridgeAuth.asStateFlow()

    private var pendingConnectParams: BridgeConnectParams? = null

    private val _pendingDestructiveCommand = MutableStateFlow<PendingDestructiveCommand?>(null)
    val pendingDestructiveCommand: StateFlow<PendingDestructiveCommand?> = _pendingDestructiveCommand.asStateFlow()

    private val _installerSteps = MutableStateFlow<List<InstallerStepStatus>>(emptyList())
    val installerSteps: StateFlow<List<InstallerStepStatus>> = _installerSteps.asStateFlow()

    private val _installerScript = MutableStateFlow("")
    val installerScript: StateFlow<String> = _installerScript.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val webViewPool: WebViewPoolManager = WebViewPoolManager(
        context = application,
        callbacks = object : WebViewCallbacks {
            override fun onPageStarted(tabId: String, url: String) {
                viewModelScope.launch(Dispatchers.Main) {
                    updateTab(tabId) {
                        it.copy(
                            url = url,
                            isLoading = true,
                            progress = 10,
                            isSuspended = false,
                            securityState = BrowserUrlUtils.resolveSecurityState(url)
                        )
                    }
                    refreshMemoryStats()
                }
            }

            override fun onPageFinished(
                tabId: String,
                url: String,
                title: String,
                canGoBack: Boolean,
                canGoForward: Boolean
            ) {
                viewModelScope.launch(Dispatchers.Main) {
                    val tab = _tabs.value.firstOrNull { it.id == tabId }
                    val thumb = webViewPool.captureThumbnail(tabId)
                    updateTab(tabId) {
                        it.copy(
                            url = url,
                            title = title,
                            isLoading = false,
                            progress = 100,
                            canGoBack = canGoBack,
                            canGoForward = canGoForward,
                            thumbnail = thumb ?: it.thumbnail,
                            securityState = BrowserUrlUtils.resolveSecurityState(url)
                        )
                    }
                    if (tab != null && !tab.isPrivate) {
                        repository.recordHistory(url, title)
                    }
                    persistNormalTabs()
                    refreshMemoryStats()
                }
            }

            override fun onProgressChanged(tabId: String, progress: Int) {
                viewModelScope.launch(Dispatchers.Main) {
                    updateTab(tabId) {
                        it.copy(
                            progress = progress,
                            isLoading = progress in 1..99
                        )
                    }
                }
            }

            override fun onTitleReceived(tabId: String, title: String) {
                viewModelScope.launch(Dispatchers.Main) {
                    updateTab(tabId) { it.copy(title = title) }
                }
            }

            override fun onFaviconReceived(tabId: String, icon: Bitmap?) {
                if (icon == null) return
                viewModelScope.launch(Dispatchers.Main) {
                    updateTab(tabId) { it.copy(favicon = icon) }
                }
            }

            override fun onOpenNewTabRequested(url: String, isPrivate: Boolean) {
                viewModelScope.launch(Dispatchers.Main) {
                    openNewTab(url = url, isPrivate = isPrivate)
                }
            }

            override fun onSslErrorDetected(error: PendingSslError) {
                viewModelScope.launch(Dispatchers.Main) {
                    updateTab(error.tabId) { it.copy(securityState = PageSecurityState.SSL_ERROR) }
                    _pendingSslError.value = error
                }
            }

            override fun onHttpBlockedWarning(warning: PendingHttpWarning) {
                viewModelScope.launch(Dispatchers.Main) {
                    _pendingHttpWarning.value = warning
                }
            }

            override fun onPermissionPrompt(prompt: PendingSitePermission) {
                viewModelScope.launch(Dispatchers.Main) {
                    _pendingSitePermission.value = prompt
                }
            }

            override fun onDownloadPrompt(prompt: PendingDownloadPrompt) {
                viewModelScope.launch(Dispatchers.Main) {
                    if (latestSettings.value.askBeforeDownload) {
                        _pendingDownloadPrompt.value = prompt
                    } else {
                        confirmDownload(prompt)
                    }
                }
            }

            override fun onDownloadBlocked(fileName: String) {
                viewModelScope.launch(Dispatchers.Main) {
                    _statusMessage.value = "Download bloqueado pela política de segurança: $fileName"
                }
            }

            override fun onEnterHtmlFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
                viewModelScope.launch(Dispatchers.Main) {
                    htmlCustomViewCallback = callback
                    _htmlFullscreenView.value = view
                    _isFullscreen.value = true
                }
            }

            override fun onExitHtmlFullscreen() {
                viewModelScope.launch(Dispatchers.Main) {
                    _htmlFullscreenView.value = null
                    htmlCustomViewCallback = null
                    _isFullscreen.value = false
                }
            }

            override fun getSitePermissionSync(origin: String): SitePermissionEntity? =
                sitePermissionsCache[origin]

            override fun getCurrentSettings(): BrowserSettings = latestSettings.value
        }
    )

    init {
        viewModelScope.launch {
            repository.settings.collect { s ->
                latestSettings.value = s
                enforceActiveTabMemoryLimit()
            }
        }
        viewModelScope.launch {
            repository.sitePermissions.collect { list ->
                sitePermissionsCache.clear()
                list.forEach { sitePermissionsCache[it.origin] = it }
            }
        }
        viewModelScope.launch {
            val saved = repository.loadSavedTabs()
            if (saved.isNotEmpty()) {
                val restored = saved.map { entity ->
                    BrowserTabState(
                        id = entity.tabId,
                        url = entity.url,
                        title = entity.title,
                        isDesktopMode = entity.isDesktopMode,
                        zoomPercent = entity.zoomPercent,
                        isSuspended = entity.url != HOME_TAB_URL,
                        securityState = BrowserUrlUtils.resolveSecurityState(entity.url)
                    )
                }
                _tabs.value = restored
                // Wake up first tab immediately
                val first = restored.first()
                _activeTabId.value = first.id
                updateTab(first.id) { it.copy(isSuspended = false) }
            }
            refreshMemoryStats()
        }
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }

    fun setDestination(dest: WorkspaceDestination) {
        if (_destination.value == WorkspaceDestination.BROWSER && dest != WorkspaceDestination.BROWSER) {
            captureActiveTabThumbnail()
        }
        _destination.value = dest
    }

    fun setFullscreen(enabled: Boolean) {
        if (!enabled && _htmlFullscreenView.value != null) {
            htmlCustomViewCallback?.onCustomViewHidden()
            _htmlFullscreenView.value = null
            htmlCustomViewCallback = null
        }
        _isFullscreen.value = enabled
    }

    fun activeTab(): BrowserTabState {
        return _tabs.value.firstOrNull { it.id == _activeTabId.value } ?: _tabs.value.first()
    }

    fun navigateTo(rawInput: String) {
        val current = activeTab()
        val s = latestSettings.value
        val normalized = BrowserUrlUtils.normalizeInputToUrl(rawInput, s.httpsOnlyEnabled)

        if (normalized == HOME_TAB_URL) {
            webViewPool.closeTab(current.id, current.isPrivate)
            updateTab(current.id) {
                it.copy(
                    url = HOME_TAB_URL,
                    title = if (it.isPrivate) "Aba privada" else "Nova aba",
                    isLoading = false,
                    progress = 0,
                    canGoBack = false,
                    canGoForward = false,
                    favicon = null,
                    thumbnail = null,
                    isSuspended = false,
                    securityState = PageSecurityState.INTERNAL
                )
            }
            persistNormalTabs()
            refreshMemoryStats()
            return
        }

        if (s.httpsOnlyEnabled &&
            normalized.startsWith("http://", ignoreCase = true) &&
            !BrowserUrlUtils.isLocalhostUrl(normalized)
        ) {
            _pendingHttpWarning.value = PendingHttpWarning(
                tabId = current.id,
                httpUrl = normalized,
                httpsCandidateUrl = normalized.replaceFirst("http://", "https://", ignoreCase = true)
            )
            return
        }

        performNavigateInTab(current.id, normalized)
    }

    private fun performNavigateInTab(tabId: String, url: String) {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        val wasHome = tab.url == HOME_TAB_URL
        val updated = tab.copy(
            url = url,
            title = url,
            isLoading = true,
            progress = 15,
            isSuspended = false,
            lastActiveAt = System.currentTimeMillis(),
            securityState = BrowserUrlUtils.resolveSecurityState(url)
        )
        updateTab(tabId) { updated }
        if (!wasHome || webViewPool.hasActiveWebView(tabId)) {
            webViewPool.loadUrl(updated, url)
        }
        enforceActiveTabMemoryLimit()
        refreshMemoryStats()
    }

    fun openUrlInNewOrCurrentTab(url: String, forceNewTab: Boolean = true) {
        if (forceNewTab) {
            openNewTab(url = url, isPrivate = false)
        } else {
            val current = activeTab()
            performNavigateInTab(current.id, url)
        }
    }

    fun openNewTab(url: String = HOME_TAB_URL, isPrivate: Boolean = false) {
        captureActiveTabThumbnail()
        val newTab = BrowserTabState(
            url = url,
            title = if (url == HOME_TAB_URL) {
                if (isPrivate) "Aba privada" else "Nova aba"
            } else url,
            zoomPercent = latestSettings.value.defaultZoomPercent,
            isPrivate = isPrivate,
            isLoading = url != HOME_TAB_URL,
            securityState = BrowserUrlUtils.resolveSecurityState(url)
        )
        _tabs.update { it + newTab }
        _activeTabId.value = newTab.id
        _destination.value = WorkspaceDestination.BROWSER
        enforceActiveTabMemoryLimit()
        persistNormalTabs()
        refreshMemoryStats()
    }

    fun selectTab(tabId: String) {
        if (tabId == _activeTabId.value) return
        captureActiveTabThumbnail()
        val target = _tabs.value.firstOrNull { it.id == tabId } ?: return
        val resumed = target.copy(
            isSuspended = false,
            lastActiveAt = System.currentTimeMillis()
        )
        updateTab(tabId) { resumed }
        _activeTabId.value = tabId
        enforceActiveTabMemoryLimit()
        refreshMemoryStats()
    }

    fun duplicateTab(tabId: String) {
        val source = _tabs.value.firstOrNull { it.id == tabId } ?: return
        captureActiveTabThumbnail()
        val copy = BrowserTabState(
            url = source.url,
            title = source.title,
            favicon = source.favicon,
            isDesktopMode = source.isDesktopMode,
            zoomPercent = source.zoomPercent,
            isPrivate = source.isPrivate,
            isLoading = source.url != HOME_TAB_URL,
            securityState = source.securityState
        )
        _tabs.update { list ->
            val idx = list.indexOfFirst { it.id == tabId }
            if (idx >= 0) {
                list.toMutableList().apply { add(idx + 1, copy) }
            } else {
                list + copy
            }
        }
        _activeTabId.value = copy.id
        enforceActiveTabMemoryLimit()
        persistNormalTabs()
        refreshMemoryStats()
    }

    fun closeTab(tabId: String) {
        val list = _tabs.value
        val target = list.firstOrNull { it.id == tabId } ?: return
        webViewPool.closeTab(tabId, target.isPrivate)

        val remaining = list.filterNot { it.id == tabId }
        if (remaining.isEmpty()) {
            val fresh = BrowserTabState(zoomPercent = latestSettings.value.defaultZoomPercent)
            _tabs.value = listOf(fresh)
            _activeTabId.value = fresh.id
        } else {
            _tabs.value = remaining
            if (_activeTabId.value == tabId) {
                val next = remaining.maxByOrNull { it.lastActiveAt } ?: remaining.first()
                updateTab(next.id) { it.copy(isSuspended = false, lastActiveAt = System.currentTimeMillis()) }
                _activeTabId.value = next.id
            }
        }
        persistNormalTabs()
        refreshMemoryStats()
    }

    fun suspendTab(tabId: String) {
        val target = _tabs.value.firstOrNull { it.id == tabId } ?: return
        if (target.url == HOME_TAB_URL || target.isSuspended) return
        val thumb = webViewPool.suspendTab(tabId)
        updateTab(tabId) {
            it.copy(
                isSuspended = true,
                isLoading = false,
                thumbnail = thumb ?: it.thumbnail
            )
        }
        if (tabId == _activeTabId.value) {
            val another = _tabs.value.firstOrNull { it.id != tabId && !it.isSuspended }
            if (another != null) {
                _activeTabId.value = another.id
            }
        }
        refreshMemoryStats()
    }

    fun suspendAllInactiveTabs() {
        val currentId = _activeTabId.value
        var count = 0
        _tabs.value.forEach { tab ->
            if (tab.id != currentId && !tab.isSuspended && tab.url != HOME_TAB_URL) {
                val thumb = webViewPool.suspendTab(tab.id)
                updateTab(tab.id) {
                    it.copy(isSuspended = true, isLoading = false, thumbnail = thumb ?: it.thumbnail)
                }
                count++
            }
        }
        refreshMemoryStats()
        _statusMessage.value = if (count > 0) {
            "$count aba(s) inativa(s) suspensa(s) para economizar RAM."
        } else {
            "Nenhuma aba inativa elegível para suspensão."
        }
    }

    private fun enforceActiveTabMemoryLimit() {
        val s = latestSettings.value
        if (!s.autoSuspendEnabled) return
        val currentId = _activeTabId.value
        val activeNonHome = _tabs.value
            .filter { !it.isSuspended && it.url != HOME_TAB_URL && webViewPool.hasActiveWebView(it.id) }
            .sortedBy { it.lastActiveAt }

        val excess = activeNonHome.size - s.maxActiveTabs
        if (excess > 0) {
            activeNonHome
                .filter { it.id != currentId }
                .take(excess)
                .forEach { candidate ->
                    val thumb = webViewPool.suspendTab(candidate.id)
                    updateTab(candidate.id) {
                        it.copy(isSuspended = true, isLoading = false, thumbnail = thumb ?: it.thumbnail)
                    }
                }
        }
    }

    private fun captureActiveTabThumbnail() {
        val current = activeTab()
        if (current.url != HOME_TAB_URL && !current.isSuspended) {
            val thumb = webViewPool.captureThumbnail(current.id)
            if (thumb != null) {
                updateTab(current.id) { it.copy(thumbnail = thumb) }
            }
        }
    }

    fun goBack() {
        val tab = activeTab()
        if (tab.canGoBack) {
            webViewPool.goBack(tab.id)
        }
    }

    fun goForward() {
        val tab = activeTab()
        if (tab.canGoForward) {
            webViewPool.goForward(tab.id)
        }
    }

    fun reloadOrStop() {
        val tab = activeTab()
        if (tab.url == HOME_TAB_URL) return
        if (tab.isLoading) {
            webViewPool.stopLoading(tab.id)
            updateTab(tab.id) { it.copy(isLoading = false, progress = 0) }
        } else {
            webViewPool.reload(tab.id)
        }
    }

    fun toggleDesktopMode() {
        val tab = activeTab()
        val updated = tab.copy(isDesktopMode = !tab.isDesktopMode)
        updateTab(tab.id) { updated }
        if (tab.url != HOME_TAB_URL) {
            webViewPool.toggleDesktopAndReload(updated)
        }
        persistNormalTabs()
        refreshMemoryStats()
    }

    fun adjustZoom(delta: Int) {
        val tab = activeTab()
        val nextZoom = (tab.zoomPercent + delta).coerceIn(50, 200)
        val updated = tab.copy(zoomPercent = nextZoom)
        updateTab(tab.id) { updated }
        webViewPool.applyZoom(updated)
        persistNormalTabs()
    }

    fun toggleCurrentBookmark() {
        val tab = activeTab()
        if (tab.url == HOME_TAB_URL) return
        viewModelScope.launch {
            val added = repository.toggleBookmark(tab.url, tab.title)
            _statusMessage.value = if (added) "Adicionado aos favoritos" else "Removido dos favoritos"
        }
    }

    fun addBookmark(url: String, title: String) {
        viewModelScope.launch {
            repository.addBookmark(url, title)
            _statusMessage.value = "Favorito salvo"
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { repository.deleteBookmark(id) }
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch { repository.deleteHistory(id) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _statusMessage.value = "Histórico de navegação limpo"
        }
    }

    // Security & Prompts resolution
    fun resolveSslError(proceedUnsafe: Boolean, rememberHostForSession: Boolean) {
        val err = _pendingSslError.value ?: return
        _pendingSslError.value = null
        if (proceedUnsafe) {
            if (rememberHostForSession) {
                webViewPool.allowSslHostForSession(err.host)
            }
            err.handler.proceed()
        } else {
            err.handler.cancel()
        }
    }

    fun resolveHttpWarning(useHttps: Boolean) {
        val warn = _pendingHttpWarning.value ?: return
        _pendingHttpWarning.value = null
        if (useHttps) {
            performNavigateInTab(warn.tabId, warn.httpsCandidateUrl)
        } else {
            webViewPool.allowHttpOnce(warn.httpUrl)
            performNavigateInTab(warn.tabId, warn.httpUrl)
        }
    }

    fun resolveSitePermission(allow: Boolean, rememberForSite: Boolean) {
        val prompt = _pendingSitePermission.value ?: return
        _pendingSitePermission.value = null

        when (prompt.kind) {
            SitePermissionKind.GEOLOCATION -> {
                prompt.geoCallback?.invoke(prompt.origin, allow, rememberForSite)
            }
            SitePermissionKind.CAMERA_MIC -> {
                if (allow) {
                    prompt.webPermissionRequest?.grant(prompt.webPermissionRequest.resources)
                } else {
                    prompt.webPermissionRequest?.deny()
                }
            }
        }

        if (rememberForSite) {
            viewModelScope.launch {
                val existing = repository.getSitePermission(prompt.origin)
                    ?: SitePermissionEntity(origin = prompt.origin)
                val updated = when (prompt.kind) {
                    SitePermissionKind.GEOLOCATION -> existing.copy(geolocationAllowed = allow)
                    SitePermissionKind.CAMERA_MIC -> existing.copy(cameraMicAllowed = allow)
                }
                repository.saveSitePermission(updated)
            }
        }
    }

    fun confirmDownload(prompt: PendingDownloadPrompt) {
        _pendingDownloadPrompt.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val id = repository.createDownloadEntry(
                url = prompt.url,
                fileName = prompt.fileName,
                mimeType = prompt.mimeType,
                filePath = ""
            )
            webViewPool.performHttpDownload(prompt) { downloaded, total, status, path ->
                repository.updateDownload(id, downloaded, total, status, path)
            }
            _statusMessage.value = "Download concluído: ${prompt.fileName}"
        }
    }

    fun cancelDownloadPrompt() {
        _pendingDownloadPrompt.value = null
    }

    fun deleteDownload(id: Long) {
        viewModelScope.launch { repository.deleteDownload(id) }
    }

    fun clearDownloads() {
        viewModelScope.launch { repository.clearDownloads() }
    }

    fun saveSitePermission(entity: SitePermissionEntity) {
        viewModelScope.launch {
            repository.saveSitePermission(entity)
            // Immediately update active WebView if matching origin
            val current = activeTab()
            if (BrowserUrlUtils.extractOrigin(current.url) == entity.origin) {
                webViewPool.reload(current.id)
            }
        }
    }

    fun deleteSitePermission(origin: String) {
        viewModelScope.launch { repository.deleteSitePermission(origin) }
    }

    // Settings & Memory
    fun updateMaxActiveTabs(limit: Int) = viewModelScope.launch { repository.updateMaxActiveTabs(limit) }
    fun updateAutoSuspend(enabled: Boolean) = viewModelScope.launch { repository.updateAutoSuspend(enabled) }
    fun updateJavascript(enabled: Boolean) = viewModelScope.launch { repository.updateJavascriptEnabled(enabled) }
    fun updateCookies(enabled: Boolean) = viewModelScope.launch { repository.updateCookiesEnabled(enabled) }
    fun updateThirdPartyCookies(enabled: Boolean) = viewModelScope.launch { repository.updateThirdPartyCookies(enabled) }
    fun updateHttpsOnly(enabled: Boolean) = viewModelScope.launch { repository.updateHttpsOnly(enabled) }
    fun updateAskBeforeDownload(enabled: Boolean) = viewModelScope.launch { repository.updateAskBeforeDownload(enabled) }
    fun updateBlockAllDownloads(enabled: Boolean) = viewModelScope.launch { repository.updateBlockAllDownloads(enabled) }
    fun updateDarkTheme(dark: Boolean) = viewModelScope.launch { repository.updateDarkTheme(dark) }

    fun clearCache(includeCookies: Boolean = false) {
        webViewPool.clearAllCacheAndStorage(includeCookies = includeCookies)
        refreshMemoryStats()
        _statusMessage.value = if (includeCookies) {
            "Cache, armazenamento e cookies limpos."
        } else {
            "Cache e dados temporários das páginas limpos."
        }
    }

    fun refreshMemoryStats() {
        val currentTabs = _tabs.value.map { tab ->
            tab.copy(estimatedMemoryMb = webViewPool.estimateTabMemoryMb(tab))
        }
        _tabs.value = currentTabs
        _memoryOverview.value = webViewPool.readMemoryOverview(currentTabs)
    }

    // Terminal & Local Server helpers
    fun runTerminalCommand(cmd: String) {
        terminalProcessor.executeCommand(cmd)
    }

    fun startLocalServerAndOpen(port: Int, label: String) {
        val res = serverManager.startServer(port, label)
        res.onSuccess { info ->
            openUrlInNewOrCurrentTab(info.baseUrl, forceNewTab = true)
            setDestination(WorkspaceDestination.BROWSER)
        }.onFailure { err ->
            _statusMessage.value = "Erro ao iniciar servidor: ${err.message}"
        }
    }

    fun stopLocalServer(port: Int) {
        serverManager.stopServer(port)
    }

    // --- TERMUX BRIDGE (18.1 - 18.10) ---

    data class BridgeConnectParams(
        val host: String,
        val httpPort: Int,
        val sshPort: Int,
        val sshUser: String,
        val method: BridgeConnectionMethod
    )

    fun requestBridgeConnect(
        host: String,
        httpPort: Int,
        sshPort: Int,
        sshUser: String,
        method: BridgeConnectionMethod
    ) {
        val params = BridgeConnectParams(host.trim(), httpPort, sshPort, sshUser.trim(), method)
        viewModelScope.launch {
            repository.updateBridgeConfig(params.host, params.httpPort, params.sshPort, params.sshUser, params.method.name)
            if (!latestSettings.value.bridgeAuthorized) {
                pendingConnectParams = params
                _pendingFirstBridgeAuth.value = true
            } else {
                val ok = bridgeManager.connect(params.host, params.httpPort, params.sshPort, params.sshUser, params.method)
                if (ok) _statusMessage.value = "Termux Bridge conectado via ${params.method.label}"
            }
        }
    }

    fun resolveFirstBridgeAuthorization(authorized: Boolean) {
        _pendingFirstBridgeAuth.value = false
        val params = pendingConnectParams
        pendingConnectParams = null
        if (!authorized || params == null) {
            _statusMessage.value = "Autorização do Termux Bridge recusada."
            return
        }
        viewModelScope.launch {
            repository.updateBridgeAuthorized(true)
            val ok = bridgeManager.connect(params.host, params.httpPort, params.sshPort, params.sshUser, params.method)
            if (ok) _statusMessage.value = "Conexão autorizada e estabelecida!"
        }
    }

    fun testBridgeConnection(
        host: String,
        httpPort: Int,
        sshPort: Int,
        sshUser: String,
        method: BridgeConnectionMethod
    ) {
        requestBridgeConnect(host, httpPort, sshPort, sshUser, method)
    }

    fun disconnectBridge() {
        bridgeManager.disconnect()
        _statusMessage.value = "Termux Bridge desconectado."
    }

    fun selectBridgeSession(sessionId: String) = bridgeManager.selectSession(sessionId)

    fun createBridgeSession() {
        val currentCwd = bridgeSessions.value.firstOrNull { it.id == activeBridgeSessionId.value }?.workingDirectory
            ?: "/data/data/com.termux/files/home"
        bridgeManager.createNewSession(currentCwd)
    }

    fun closeBridgeSession(sessionId: String) = bridgeManager.closeSession(sessionId)

    fun sendCommandToExternalTerminal(command: String) {
        val clean = command.trim()
        if (clean.isEmpty()) return
        val sessionId = activeBridgeSessionId.value
        val destructiveReason = BridgeSecurityPolicy.detectDestructiveReason(clean)
        if (destructiveReason != null) {
            _pendingDestructiveCommand.value = PendingDestructiveCommand(
                sessionId = sessionId,
                command = clean,
                reason = destructiveReason
            )
            return
        }
        executeValidatedExternalCommand(sessionId, clean)
    }

    fun resolveDestructiveCommand(proceed: Boolean) {
        val pending = _pendingDestructiveCommand.value ?: return
        _pendingDestructiveCommand.value = null
        if (!proceed) {
            viewModelScope.launch {
                repository.insertBridgeAudit(
                    BridgeAuditEntity(
                        sessionId = pending.sessionId,
                        method = bridgeStatus.value.method.name,
                        command = pending.command,
                        workingDirectory = bridgeSessions.value.firstOrNull { it.id == pending.sessionId }?.workingDirectory ?: "~",
                        status = "BLOCKED"
                    )
                )
            }
            _statusMessage.value = "Comando destrutivo cancelado pelo usuário."
            return
        }
        executeValidatedExternalCommand(pending.sessionId, pending.command)
    }

    private fun executeValidatedExternalCommand(sessionId: String, command: String) {
        bridgeManager.executeCommandInSession(
            sessionId = sessionId,
            rawCommand = command,
            allowedRootsCsv = latestSettings.value.bridgeAllowedRoots,
            onAuditStart = { sId, method, cmd, cwd ->
                repository.insertBridgeAudit(
                    BridgeAuditEntity(
                        sessionId = sId,
                        method = method,
                        command = cmd,
                        workingDirectory = cwd,
                        status = "EXECUTING"
                    )
                )
            },
            onAuditFinish = { auditId, exitCode, status ->
                repository.updateBridgeAuditStatus(auditId, exitCode, status)
            }
        )
    }

    fun interruptExternalProcess(sessionId: String) {
        bridgeManager.interruptSessionProcess(sessionId)
    }

    fun openTermuxServerInBrowser(port: Int) {
        openUrlInNewOrCurrentTab("http://127.0.0.1:$port", forceNewTab = true)
        setDestination(WorkspaceDestination.BROWSER)
    }

    fun revokeBridgeConnection() {
        viewModelScope.launch {
            bridgeManager.revokeBridgeCredentialsAndDisconnect()
            repository.updateBridgeAuthorized(false)
            _statusMessage.value = "Conexão e credenciais do Termux Bridge revogadas."
        }
    }

    fun updateBridgeAllowedRoots(rootsCsv: String) {
        viewModelScope.launch {
            repository.updateBridgeAllowedRoots(rootsCsv)
            _statusMessage.value = "Diretórios permitidos atualizados."
        }
    }

    fun clearBridgeAuditLogs() {
        viewModelScope.launch { repository.clearBridgeAuditLogs() }
    }

    fun prepareBridgeInstaller(regenerateCredentials: Boolean = false) {
        bridgeManager.refreshEnvironmentDetection()
        val s = latestSettings.value
        val secrets = if (regenerateCredentials || !bridgeManager.credentialStore.hasCredentials()) {
            bridgeManager.credentialStore.regenerateAllSecrets()
        } else {
            bridgeManager.credentialStore.getOrCreateSecrets()
        }
        val script = BridgeScriptGenerator.buildSetupScript(
            secrets = secrets,
            httpPort = s.bridgeHttpPort,
            sshPort = s.bridgeSshPort,
            allowedRootsCsv = s.bridgeAllowedRoots
        )
        _installerScript.value = script

        val st = bridgeStatus.value
        _installerSteps.value = listOf(
            InstallerStepStatus(
                stepNumber = 1,
                title = "Detectar Termux original (com.termux)",
                detail = if (st.termuxInstalled) {
                    "Termux detectado (versão ${st.termuxVersionName ?: "instalada"}) • ABI ${st.deviceAbi}"
                } else {
                    "Pacote com.termux não localizado. Instale o Termux (F-Droid/GitHub) ou teste na porta local."
                },
                completed = st.termuxInstalled,
                error = if (!st.termuxInstalled) "Termux não encontrado neste dispositivo" else null
            ),
            InstallerStepStatus(
                stepNumber = 2,
                title = "Credenciais criptografadas (Keystore)",
                detail = "Token API 256-bit e par de chaves SSH RSA-2048 protegidos no Android Keystore.",
                completed = bridgeManager.credentialStore.hasCredentials()
            ),
            InstallerStepStatus(
                stepNumber = 3,
                title = "Script do serviço & inicialização",
                detail = "Script gera ~/.termux-bridge/bridge_server.py (127.0.0.1:${s.bridgeHttpPort}), sshd (:${s.bridgeSshPort}) e Termux:Boot.",
                completed = script.isNotEmpty()
            ),
            InstallerStepStatus(
                stepNumber = 4,
                title = "Dependências (python + openssh)",
                detail = "O script executa 'pkg install -y python openssh' e grava ~/.ssh/authorized_keys.",
                completed = st.state == BridgeConnectionState.CONNECTED
            ),
            InstallerStepStatus(
                stepNumber = 5,
                title = "Verificação de comunicação",
                detail = if (st.state == BridgeConnectionState.CONNECTED) {
                    "Conectado com sucesso (${st.latencyMs ?: 0} ms) • ${st.serviceStatusText}"
                } else {
                    "Execute o script copiado no Termux e toque em 'Testar Conexão'."
                },
                completed = st.state == BridgeConnectionState.CONNECTED,
                error = st.lastError
            )
        )
    }

    private fun updateTab(tabId: String, transform: (BrowserTabState) -> BrowserTabState) {
        _tabs.update { list ->
            list.map { if (it.id == tabId) transform(it) else it }
        }
    }

    private fun persistNormalTabs() {
        viewModelScope.launch(Dispatchers.IO) {
            val normalTabs = _tabs.value
                .filter { !it.isPrivate }
                .mapIndexed { index, tab ->
                    SavedTabEntity(
                        tabId = tab.id,
                        url = tab.url,
                        title = tab.title,
                        isDesktopMode = tab.isDesktopMode,
                        zoomPercent = tab.zoomPercent,
                        position = index,
                        lastAccessedAt = tab.lastActiveAt
                    )
                }
            repository.persistTabs(normalTabs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        bridgeManager.destroy()
        serverManager.stopAll()
        webViewPool.destroyAll()
    }
}
