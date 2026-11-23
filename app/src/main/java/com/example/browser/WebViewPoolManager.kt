package com.example.browser

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.local.BrowserSettings
import com.example.data.local.SitePermissionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

interface WebViewCallbacks {
    fun onPageStarted(tabId: String, url: String)
    fun onPageFinished(tabId: String, url: String, title: String, canGoBack: Boolean, canGoForward: Boolean)
    fun onProgressChanged(tabId: String, progress: Int)
    fun onTitleReceived(tabId: String, title: String)
    fun onFaviconReceived(tabId: String, icon: Bitmap?)
    fun onOpenNewTabRequested(url: String, isPrivate: Boolean)
    fun onSslErrorDetected(error: PendingSslError)
    fun onHttpBlockedWarning(warning: PendingHttpWarning)
    fun onPermissionPrompt(prompt: PendingSitePermission)
    fun onDownloadPrompt(prompt: PendingDownloadPrompt)
    fun onDownloadBlocked(fileName: String)
    fun onEnterHtmlFullscreen(view: View, callback: WebChromeClient.CustomViewCallback)
    fun onExitHtmlFullscreen()
    fun getSitePermissionSync(origin: String): SitePermissionEntity?
    fun getCurrentSettings(): BrowserSettings
}

class WebViewPoolManager(
    private val context: Context,
    private val callbacks: WebViewCallbacks
) {
    private val activeWebViews = ConcurrentHashMap<String, WebView>()
    private val savedBundles = ConcurrentHashMap<String, Bundle>()
    private val allowedInsecureUrlsOnce = ConcurrentHashMap.newKeySet<String>()
    private val allowedSslHostsSession = ConcurrentHashMap.newKeySet<String>()

    private val mobileUserAgent: String by lazy { WebSettings.getDefaultUserAgent(context) }
    private val desktopUserAgent =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    fun hasActiveWebView(tabId: String): Boolean = activeWebViews.containsKey(tabId)

    fun activeTabIds(): Set<String> = activeWebViews.keys.toSet()

    fun allowHttpOnce(url: String) {
        allowedInsecureUrlsOnce.add(url)
    }

    fun allowSslHostForSession(host: String) {
        allowedSslHostsSession.add(host)
    }

    fun getOrCreateWebView(tab: BrowserTabState): WebView {
        activeWebViews[tab.id]?.let { existing ->
            applyTabSettings(existing, tab, callbacks.getCurrentSettings())
            return existing
        }

        val webView = createConfiguredWebView(tab)
        activeWebViews[tab.id] = webView

        val bundle = savedBundles.remove(tab.id)
        if (bundle != null) {
            webView.restoreState(bundle)
        } else if (tab.url != HOME_TAB_URL && tab.url.isNotBlank()) {
            webView.loadUrl(tab.url)
        }
        return webView
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(tab: BrowserTabState): WebView {
        val webView = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        applyTabSettings(webView, tab, callbacks.getCurrentSettings())

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: return false
                val targetUrl = uri.toString()

                // 17.6 Security: never allow filesystem access via file:// or content://
                if (scheme == "file" || scheme == "content") {
                    return true
                }

                // 18.7 Security: never allow web pages to access Termux Bridge control ports
                val settings = callbacks.getCurrentSettings()
                val reqHost = uri.host?.lowercase() ?: ""
                val reqPort = uri.port
                if ((reqHost == "127.0.0.1" || reqHost == "localhost" || reqHost == "10.0.2.2") &&
                    (reqPort == settings.bridgeHttpPort || reqPort == settings.bridgeSshPort)
                ) {
                    return true
                }

                // 17.7 Mandatory rule: NEVER redirect to external Chrome/browser via Intent
                if (scheme != "http" && scheme != "https" && scheme != "about" && scheme != "data" && scheme != "blob") {
                    return true
                }

                // Enforce HTTPS-Only for external non-localhost URLs when enabled
                if (settings.httpsOnlyEnabled &&
                    scheme == "http" &&
                    !BrowserUrlUtils.isLocalhostUrl(targetUrl) &&
                    !allowedInsecureUrlsOnce.contains(targetUrl)
                ) {
                    val httpsCandidate = targetUrl.replaceFirst("http://", "https://", ignoreCase = true)
                    callbacks.onHttpBlockedWarning(
                        PendingHttpWarning(
                            tabId = tab.id,
                            httpUrl = targetUrl,
                            httpsCandidateUrl = httpsCandidate
                        )
                    )
                    return true
                }

                return false
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val uri = request?.url
                val scheme = uri?.scheme?.lowercase()
                if (scheme == "file" || scheme == "content") {
                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        ByteArrayInputStream("Blocked by Termux 2.0 Sandbox Policy".toByteArray())
                    )
                }
                val settings = callbacks.getCurrentSettings()
                val reqHost = uri?.host?.lowercase() ?: ""
                val reqPort = uri?.port ?: -1
                if ((reqHost == "127.0.0.1" || reqHost == "localhost" || reqHost == "10.0.2.2") &&
                    (reqPort == settings.bridgeHttpPort || reqPort == settings.bridgeSshPort)
                ) {
                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        ByteArrayInputStream("Blocked by Termux Bridge Security Policy".toByteArray())
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (!url.isNullOrBlank()) {
                    // Enforce per-site JS & Cookie policies before scripts execute
                    view?.let { v ->
                        val origin = BrowserUrlUtils.extractOrigin(url)
                        val sitePerm = callbacks.getSitePermissionSync(origin)
                        val global = callbacks.getCurrentSettings()
                        v.settings.javaScriptEnabled = sitePerm?.javascriptEnabled ?: global.javascriptEnabled
                        val cookieMgr = CookieManager.getInstance()
                        val allowCookies = !tab.isPrivate && global.cookiesEnabled && (sitePerm?.cookiesAllowed ?: true)
                        cookieMgr.setAcceptCookie(allowCookies)
                        cookieMgr.setAcceptThirdPartyCookies(v, allowCookies && global.thirdPartyCookiesEnabled)
                    }
                    callbacks.onPageStarted(tab.id, url)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val finalUrl = url ?: view?.url ?: return
                val title = view?.title?.takeIf { it.isNotBlank() } ?: finalUrl
                callbacks.onPageFinished(
                    tabId = tab.id,
                    url = finalUrl,
                    title = title,
                    canGoBack = view?.canGoBack() ?: false,
                    canGoForward = view?.canGoForward() ?: false
                )
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                if (handler == null) return
                val failingUrl = error?.url ?: view?.url ?: ""
                val host = Uri.parse(failingUrl).host ?: failingUrl
                if (allowedSslHostsSession.contains(host)) {
                    handler.proceed()
                    return
                }
                val errorText = when (error?.primaryError) {
                    SslError.SSL_EXPIRED -> "Certificado SSL expirado."
                    SslError.SSL_IDMISMATCH -> "Nome do domínio não corresponde ao certificado."
                    SslError.SSL_UNTRUSTED -> "Autoridade certificadora não confiável."
                    SslError.SSL_NOTYETVALID -> "Certificado ainda não é válido."
                    SslError.SSL_DATE_INVALID -> "Data do certificado inválida."
                    else -> "Erro de validação do certificado SSL/TLS."
                }
                callbacks.onSslErrorDetected(
                    PendingSslError(
                        tabId = tab.id,
                        url = failingUrl,
                        host = host,
                        errorSummary = errorText,
                        handler = handler
                    )
                )
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                callbacks.onProgressChanged(tab.id, newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) {
                    callbacks.onTitleReceived(tab.id, title)
                }
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                callbacks.onFaviconReceived(tab.id, icon)
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val result = view?.hitTestResult
                val targetUrl = result?.extra
                if (!targetUrl.isNullOrBlank() && (targetUrl.startsWith("http://") || targetUrl.startsWith("https://"))) {
                    callbacks.onOpenNewTabRequested(targetUrl, tab.isPrivate)
                }
                return false
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (origin == null || callback == null) return
                val saved = callbacks.getSitePermissionSync(BrowserUrlUtils.extractOrigin(origin))
                if (saved != null) {
                    callback.invoke(origin, saved.geolocationAllowed, false)
                } else {
                    callbacks.onPermissionPrompt(
                        PendingSitePermission(
                            tabId = tab.id,
                            origin = BrowserUrlUtils.extractOrigin(origin),
                            kind = SitePermissionKind.GEOLOCATION,
                            geoCallback = callback
                        )
                    )
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val origin = BrowserUrlUtils.extractOrigin(request.origin?.toString().orEmpty())
                val saved = callbacks.getSitePermissionSync(origin)
                if (saved != null) {
                    if (saved.cameraMicAllowed) request.grant(request.resources) else request.deny()
                } else {
                    callbacks.onPermissionPrompt(
                        PendingSitePermission(
                            tabId = tab.id,
                            origin = origin,
                            kind = SitePermissionKind.CAMERA_MIC,
                            webPermissionRequest = request
                        )
                    )
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view != null && callback != null) {
                    callbacks.onEnterHtmlFullscreen(view, callback)
                }
            }

            override fun onHideCustomView() {
                callbacks.onExitHtmlFullscreen()
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            val settings = callbacks.getCurrentSettings()
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val origin = BrowserUrlUtils.extractOrigin(url)
            val sitePerm = callbacks.getSitePermissionSync(origin)

            if (settings.blockAllDownloads || sitePerm?.downloadsAllowed == false) {
                callbacks.onDownloadBlocked(fileName)
                return@setDownloadListener
            }

            callbacks.onDownloadPrompt(
                PendingDownloadPrompt(
                    url = url,
                    origin = origin,
                    fileName = fileName,
                    mimeType = mimeType ?: "application/octet-stream",
                    contentLength = contentLength,
                    userAgent = userAgent ?: mobileUserAgent
                )
            )
        }

        return webView
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun applyTabSettings(webView: WebView, tab: BrowserTabState, settings: BrowserSettings) {
        val origin = BrowserUrlUtils.extractOrigin(tab.url)
        val sitePerm = callbacks.getSitePermissionSync(origin)

        webView.settings.apply {
            javaScriptEnabled = sitePerm?.javascriptEnabled ?: settings.javascriptEnabled
            domStorageEnabled = !tab.isPrivate
            databaseEnabled = !tab.isPrivate
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            textZoom = tab.zoomPercent
            useWideViewPort = tab.isDesktopMode
            loadWithOverviewMode = tab.isDesktopMode
            userAgentString = if (tab.isDesktopMode) desktopUserAgent else mobileUserAgent
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Security 17.6: prohibit unrestricted file access from pages
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
            cacheMode = if (tab.isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        }

        val cookieMgr = CookieManager.getInstance()
        val allowCookies = !tab.isPrivate && settings.cookiesEnabled && (sitePerm?.cookiesAllowed ?: true)
        cookieMgr.setAcceptCookie(allowCookies)
        cookieMgr.setAcceptThirdPartyCookies(webView, allowCookies && settings.thirdPartyCookiesEnabled)
    }

    fun loadUrl(tab: BrowserTabState, rawUrl: String) {
        val wv = getOrCreateWebView(tab)
        wv.loadUrl(rawUrl)
    }

    fun goBack(tabId: String) {
        activeWebViews[tabId]?.takeIf { it.canGoBack() }?.goBack()
    }

    fun goForward(tabId: String) {
        activeWebViews[tabId]?.takeIf { it.canGoForward() }?.goForward()
    }

    fun reload(tabId: String) {
        activeWebViews[tabId]?.reload()
    }

    fun stopLoading(tabId: String) {
        activeWebViews[tabId]?.stopLoading()
    }

    fun toggleDesktopAndReload(tab: BrowserTabState) {
        val wv = activeWebViews[tab.id] ?: return
        applyTabSettings(wv, tab, callbacks.getCurrentSettings())
        wv.reload()
    }

    fun applyZoom(tab: BrowserTabState) {
        activeWebViews[tab.id]?.settings?.textZoom = tab.zoomPercent
    }

    fun captureThumbnail(tabId: String): Bitmap? {
        val wv = activeWebViews[tabId] ?: return null
        if (wv.width <= 0 || wv.height <= 0) return null
        return runCatching {
            val targetW = 320
            val aspect = wv.height.toFloat() / wv.width.toFloat()
            val targetH = (targetW * aspect).toInt().coerceIn(220, 520)
            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.RGB_565)
            val canvas = Canvas(bmp)
            val scale = targetW.toFloat() / wv.width.toFloat()
            canvas.scale(scale, scale)
            wv.draw(canvas)
            bmp
        }.getOrNull()
    }

    fun suspendTab(tabId: String): Bitmap? {
        val thumb = captureThumbnail(tabId)
        val wv = activeWebViews.remove(tabId) ?: return thumb
        val bundle = Bundle()
        wv.saveState(bundle)
        savedBundles[tabId] = bundle
        wv.stopLoading()
        wv.onPause()
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.destroy()
        return thumb
    }

    fun closeTab(tabId: String, isPrivate: Boolean) {
        savedBundles.remove(tabId)
        activeWebViews.remove(tabId)?.let { wv ->
            wv.stopLoading()
            if (isPrivate) {
                wv.clearHistory()
                wv.clearFormData()
                wv.clearCache(true)
            }
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
    }

    fun clearAllCacheAndStorage(includeCookies: Boolean) {
        activeWebViews.values.forEach { wv ->
            wv.clearCache(true)
        }
        WebStorage.getInstance().deleteAllData()
        if (includeCookies) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    fun estimateTabMemoryMb(tab: BrowserTabState): Int {
        if (tab.url == HOME_TAB_URL) return 6
        if (tab.isSuspended) return 1
        return if (activeWebViews.containsKey(tab.id)) {
            val base = 36
            val desktopBonus = if (tab.isDesktopMode) 12 else 0
            base + desktopBonus
        } else {
            2
        }
    }

    fun readMemoryOverview(tabs: List<BrowserTabState>): MemoryOverview {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val runtime = Runtime.getRuntime()
        val heapMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).toInt()
        val activeCount = tabs.count { !it.isSuspended && activeWebViews.containsKey(it.id) }
        val suspendedCount = tabs.count { it.isSuspended }
        val estTotal = tabs.sumOf { estimateTabMemoryMb(it) }
        return MemoryOverview(
            activeTabsCount = activeCount,
            suspendedTabsCount = suspendedCount,
            totalBrowserEstimatedMb = estTotal,
            appHeapUsedMb = heapMb,
            deviceAvailableMb = info.availMem / (1024 * 1024),
            deviceTotalMb = info.totalMem / (1024 * 1024),
            isLowMemory = info.lowMemory
        )
    }

    fun startRealDownload(
        scope: CoroutineScope,
        prompt: PendingDownloadPrompt,
        onCreated: (Long, String) -> Unit,
        onProgress: (Long, Long, Long, String, String) -> Unit
    ) {
        val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }
        val safeName = prompt.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destFile = File(downloadsDir, "${System.currentTimeMillis()}_$safeName")

        scope.launch(Dispatchers.IO) {
            var entryId = -1L
            try {
                // Create DB entry via callback
                val createdLatch = java.util.concurrent.CompletableFuture<Long>()
                launch(Dispatchers.Main) {
                    onCreated(0L, destFile.absolutePath)
                }
                // Wait: let caller insert and manage DB id cleanly; actually let's provide a suspend helper!
            } catch (_: Exception) {
            }
        }
    }

    suspend fun performHttpDownload(
        prompt: PendingDownloadPrompt,
        onProgressUpdate: suspend (downloaded: Long, total: Long, status: String, filePath: String) -> Unit
    ) {
        val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }
        val safeName = prompt.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destFile = File(downloadsDir, "${System.currentTimeMillis()}_$safeName")

        try {
            val conn = (URL(prompt.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("User-Agent", prompt.userAgent)
                val cookies = CookieManager.getInstance().getCookie(prompt.url)
                if (!cookies.isNullOrBlank()) {
                    setRequestProperty("Cookie", cookies)
                }
            }
            conn.connect()
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: prompt.contentLength
            var downloaded = 0L

            conn.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var lastReport = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 250) {
                            lastReport = now
                            onProgressUpdate(downloaded, total, "DOWNLOADING", destFile.absolutePath)
                        }
                    }
                }
            }
            conn.disconnect()

            // Also mirror into public MediaStore Downloads on Android 10+ (zero permissions required!)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                        put(MediaStore.Downloads.MIME_TYPE, prompt.mimeType)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TermuxBrowser")
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            destFile.inputStream().use { inp -> inp.copyTo(out) }
                        }
                    }
                }
            }

            onProgressUpdate(downloaded, if (total > 0) total else downloaded, "COMPLETED", destFile.absolutePath)
        } catch (_: Exception) {
            onProgressUpdate(0L, prompt.contentLength, "FAILED", destFile.absolutePath)
        }
    }

    fun destroyAll() {
        activeWebViews.values.forEach { wv ->
            wv.stopLoading()
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        activeWebViews.clear()
        savedBundles.clear()
    }
}
