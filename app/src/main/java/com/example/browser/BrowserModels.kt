package com.example.browser

import android.graphics.Bitmap
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import java.net.URLEncoder
import java.util.UUID

const val HOME_TAB_URL = "termux://home"

enum class WorkspaceDestination {
    BROWSER,
    BRIDGE,
    EXTERNAL_TERMINAL,
    WORKSPACE
}

enum class PageSecurityState {
    INTERNAL,
    HTTPS_SECURE,
    LOCALHOST,
    HTTP_INSECURE,
    SSL_ERROR
}

data class BrowserTabState(
    val id: String = UUID.randomUUID().toString(),
    val url: String = HOME_TAB_URL,
    val title: String = "Nova aba",
    val favicon: Bitmap? = null,
    val thumbnail: Bitmap? = null,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isDesktopMode: Boolean = false,
    val zoomPercent: Int = 100,
    val isPrivate: Boolean = false,
    val isSuspended: Boolean = false,
    val estimatedMemoryMb: Int = 0,
    val lastActiveAt: Long = System.currentTimeMillis(),
    val securityState: PageSecurityState = PageSecurityState.INTERNAL
)

data class MemoryOverview(
    val activeTabsCount: Int = 0,
    val suspendedTabsCount: Int = 0,
    val totalBrowserEstimatedMb: Int = 0,
    val appHeapUsedMb: Int = 0,
    val deviceAvailableMb: Long = 0,
    val deviceTotalMb: Long = 0,
    val isLowMemory: Boolean = false
)

data class PendingSslError(
    val tabId: String,
    val url: String,
    val host: String,
    val errorSummary: String,
    val handler: SslErrorHandler
)

data class PendingSitePermission(
    val tabId: String,
    val origin: String,
    val kind: SitePermissionKind,
    val webPermissionRequest: PermissionRequest? = null,
    val geoCallback: GeolocationPermissions.Callback? = null
)

enum class SitePermissionKind {
    GEOLOCATION,
    CAMERA_MIC
}

data class PendingDownloadPrompt(
    val url: String,
    val origin: String,
    val fileName: String,
    val mimeType: String,
    val contentLength: Long,
    val userAgent: String
)

data class PendingHttpWarning(
    val tabId: String,
    val httpUrl: String,
    val httpsCandidateUrl: String
)

object BrowserUrlUtils {
    fun normalizeInputToUrl(rawInput: String, httpsOnly: Boolean): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || trimmed.equals("termux://home", ignoreCase = true)) {
            return HOME_TAB_URL
        }
        if (trimmed.startsWith("file://", ignoreCase = true) ||
            trimmed.startsWith("content://", ignoreCase = true)
        ) {
            // Security rule 17.6: do not allow unrestricted filesystem navigation
            return HOME_TAB_URL
        }
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }

        val lower = trimmed.lowercase()
        val isLocalhost = lower.startsWith("localhost") ||
            lower.startsWith("127.0.0.1") ||
            lower.startsWith("10.0.2.2") ||
            Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$").matches(lower)

        if (isLocalhost) {
            return "http://$trimmed"
        }

        val looksLikeDomain = !trimmed.contains(" ") && trimmed.contains(".")
        return if (looksLikeDomain) {
            if (httpsOnly) "https://$trimmed" else "https://$trimmed"
        } else {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            "https://www.google.com/search?q=$encoded"
        }
    }

    fun isLocalhostUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://localhost") ||
            lower.startsWith("https://localhost") ||
            lower.startsWith("http://127.0.0.1") ||
            lower.startsWith("https://127.0.0.1") ||
            lower.startsWith("http://10.0.2.2")
    }

    fun extractOrigin(url: String): String {
        return runCatching {
            val uri = android.net.Uri.parse(url)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return url
            val port = if (uri.port != -1) ":${uri.port}" else ""
            "$scheme://$host$port"
        }.getOrDefault(url)
    }

    fun resolveSecurityState(url: String): PageSecurityState = when {
        url == HOME_TAB_URL || url.startsWith("termux://") -> PageSecurityState.INTERNAL
        isLocalhostUrl(url) -> PageSecurityState.LOCALHOST
        url.startsWith("https://", ignoreCase = true) -> PageSecurityState.HTTPS_SECURE
        url.startsWith("http://", ignoreCase = true) -> PageSecurityState.HTTP_INSECURE
        else -> PageSecurityState.INTERNAL
    }
}
