package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.browserDataStore: DataStore<Preferences> by preferencesDataStore(name = "termux_browser_prefs")

data class BrowserSettings(
    val maxActiveTabs: Int = 5,
    val autoSuspendEnabled: Boolean = true,
    val javascriptEnabled: Boolean = true,
    val cookiesEnabled: Boolean = true,
    val thirdPartyCookiesEnabled: Boolean = false,
    val httpsOnlyEnabled: Boolean = true,
    val askBeforeDownload: Boolean = true,
    val blockAllDownloads: Boolean = false,
    val defaultZoomPercent: Int = 100,
    val darkTheme: Boolean = true,
    val bridgeHost: String = "127.0.0.1",
    val bridgeHttpPort: Int = 8765,
    val bridgeSshPort: Int = 8022,
    val bridgeSshUser: String = "u0_a",
    val bridgeMethod: String = "HTTP_API",
    val bridgeAuthorized: Boolean = false,
    val bridgeAllowedRoots: String = "/data/data/com.termux/files/home"
)

class BrowserPreferencesRepository(private val context: Context) {

    private object Keys {
        val MAX_ACTIVE_TABS = intPreferencesKey("max_active_tabs")
        val AUTO_SUSPEND = booleanPreferencesKey("auto_suspend")
        val JAVASCRIPT_ENABLED = booleanPreferencesKey("javascript_enabled")
        val COOKIES_ENABLED = booleanPreferencesKey("cookies_enabled")
        val THIRD_PARTY_COOKIES = booleanPreferencesKey("third_party_cookies")
        val HTTPS_ONLY = booleanPreferencesKey("https_only")
        val ASK_BEFORE_DOWNLOAD = booleanPreferencesKey("ask_before_download")
        val BLOCK_ALL_DOWNLOADS = booleanPreferencesKey("block_all_downloads")
        val DEFAULT_ZOOM = intPreferencesKey("default_zoom")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val BRIDGE_HOST = stringPreferencesKey("bridge_host")
        val BRIDGE_HTTP_PORT = intPreferencesKey("bridge_http_port")
        val BRIDGE_SSH_PORT = intPreferencesKey("bridge_ssh_port")
        val BRIDGE_SSH_USER = stringPreferencesKey("bridge_ssh_user")
        val BRIDGE_METHOD = stringPreferencesKey("bridge_method")
        val BRIDGE_AUTHORIZED = booleanPreferencesKey("bridge_authorized")
        val BRIDGE_ALLOWED_ROOTS = stringPreferencesKey("bridge_allowed_roots")
    }

    val settingsFlow: Flow<BrowserSettings> = context.browserDataStore.data.map { prefs ->
        BrowserSettings(
            maxActiveTabs = (prefs[Keys.MAX_ACTIVE_TABS] ?: 5).coerceIn(2, 12),
            autoSuspendEnabled = prefs[Keys.AUTO_SUSPEND] ?: true,
            javascriptEnabled = prefs[Keys.JAVASCRIPT_ENABLED] ?: true,
            cookiesEnabled = prefs[Keys.COOKIES_ENABLED] ?: true,
            thirdPartyCookiesEnabled = prefs[Keys.THIRD_PARTY_COOKIES] ?: false,
            httpsOnlyEnabled = prefs[Keys.HTTPS_ONLY] ?: true,
            askBeforeDownload = prefs[Keys.ASK_BEFORE_DOWNLOAD] ?: true,
            blockAllDownloads = prefs[Keys.BLOCK_ALL_DOWNLOADS] ?: false,
            defaultZoomPercent = (prefs[Keys.DEFAULT_ZOOM] ?: 100).coerceIn(50, 200),
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            bridgeHost = prefs[Keys.BRIDGE_HOST] ?: "127.0.0.1",
            bridgeHttpPort = (prefs[Keys.BRIDGE_HTTP_PORT] ?: 8765).coerceIn(1024, 65535),
            bridgeSshPort = (prefs[Keys.BRIDGE_SSH_PORT] ?: 8022).coerceIn(1024, 65535),
            bridgeSshUser = prefs[Keys.BRIDGE_SSH_USER] ?: "u0_a",
            bridgeMethod = prefs[Keys.BRIDGE_METHOD] ?: "HTTP_API",
            bridgeAuthorized = prefs[Keys.BRIDGE_AUTHORIZED] ?: false,
            bridgeAllowedRoots = prefs[Keys.BRIDGE_ALLOWED_ROOTS] ?: "/data/data/com.termux/files/home"
        )
    }

    suspend fun updateMaxActiveTabs(limit: Int) {
        context.browserDataStore.edit { it[Keys.MAX_ACTIVE_TABS] = limit.coerceIn(2, 12) }
    }

    suspend fun updateAutoSuspend(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.AUTO_SUSPEND] = enabled }
    }

    suspend fun updateJavascriptEnabled(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.JAVASCRIPT_ENABLED] = enabled }
    }

    suspend fun updateCookiesEnabled(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.COOKIES_ENABLED] = enabled }
    }

    suspend fun updateThirdPartyCookies(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.THIRD_PARTY_COOKIES] = enabled }
    }

    suspend fun updateHttpsOnly(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.HTTPS_ONLY] = enabled }
    }

    suspend fun updateAskBeforeDownload(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.ASK_BEFORE_DOWNLOAD] = enabled }
    }

    suspend fun updateBlockAllDownloads(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.BLOCK_ALL_DOWNLOADS] = enabled }
    }

    suspend fun updateDefaultZoom(zoom: Int) {
        context.browserDataStore.edit { it[Keys.DEFAULT_ZOOM] = zoom.coerceIn(50, 200) }
    }

    suspend fun updateDarkTheme(dark: Boolean) {
        context.browserDataStore.edit { it[Keys.DARK_THEME] = dark }
    }

    suspend fun updateBridgeConfig(
        host: String,
        httpPort: Int,
        sshPort: Int,
        sshUser: String,
        method: String
    ) {
        context.browserDataStore.edit {
            it[Keys.BRIDGE_HOST] = host
            it[Keys.BRIDGE_HTTP_PORT] = httpPort.coerceIn(1024, 65535)
            it[Keys.BRIDGE_SSH_PORT] = sshPort.coerceIn(1024, 65535)
            it[Keys.BRIDGE_SSH_USER] = sshUser.ifBlank { "u0_a" }
            it[Keys.BRIDGE_METHOD] = method
        }
    }

    suspend fun updateBridgeAuthorized(authorized: Boolean) {
        context.browserDataStore.edit { it[Keys.BRIDGE_AUTHORIZED] = authorized }
    }

    suspend fun updateBridgeAllowedRoots(roots: String) {
        context.browserDataStore.edit { it[Keys.BRIDGE_ALLOWED_ROOTS] = roots }
    }
}
