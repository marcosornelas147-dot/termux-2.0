package com.example.data.repository

import com.example.data.local.BookmarkEntity
import com.example.data.local.BridgeAuditEntity
import com.example.data.local.BrowserDao
import com.example.data.local.BrowserPreferencesRepository
import com.example.data.local.BrowserSettings
import com.example.data.local.DownloadEntryEntity
import com.example.data.local.HistoryEntryEntity
import com.example.data.local.SavedTabEntity
import com.example.data.local.SitePermissionEntity
import kotlinx.coroutines.flow.Flow

class BrowserRepository(
    private val dao: BrowserDao,
    private val prefs: BrowserPreferencesRepository
) {
    val history: Flow<List<HistoryEntryEntity>> = dao.observeHistory()
    val bookmarks: Flow<List<BookmarkEntity>> = dao.observeBookmarks()
    val downloads: Flow<List<DownloadEntryEntity>> = dao.observeDownloads()
    val sitePermissions: Flow<List<SitePermissionEntity>> = dao.observeSitePermissions()
    val bridgeAuditLogs: Flow<List<BridgeAuditEntity>> = dao.observeBridgeAuditLogs()
    val settings: Flow<BrowserSettings> = prefs.settingsFlow

    suspend fun recordHistory(url: String, title: String) {
        if (url.isBlank() || url.startsWith("termux://") || url.startsWith("about:")) return
        dao.insertHistory(
            HistoryEntryEntity(
                url = url,
                title = title.ifBlank { url },
                visitedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteHistory(id: Long) = dao.deleteHistoryById(id)
    suspend fun clearHistory() = dao.clearHistory()

    suspend fun toggleBookmark(url: String, title: String): Boolean {
        if (url.isBlank() || url.startsWith("termux://")) return false
        val existing = dao.getBookmarkByUrl(url)
        return if (existing != null) {
            dao.deleteBookmarkByUrl(url)
            false
        } else {
            dao.insertBookmark(
                BookmarkEntity(
                    url = url,
                    title = title.ifBlank { url },
                    createdAt = System.currentTimeMillis()
                )
            )
            true
        }
    }

    suspend fun addBookmark(url: String, title: String, folder: String = "Geral") {
        dao.insertBookmark(BookmarkEntity(url = url, title = title.ifBlank { url }, folder = folder))
    }

    suspend fun deleteBookmark(id: Long) = dao.deleteBookmarkById(id)

    suspend fun createDownloadEntry(
        url: String,
        fileName: String,
        mimeType: String,
        filePath: String
    ): Long {
        return dao.insertDownload(
            DownloadEntryEntity(
                url = url,
                fileName = fileName,
                mimeType = mimeType,
                filePath = filePath,
                bytesTotal = -1L,
                bytesDownloaded = 0L,
                status = "DOWNLOADING"
            )
        )
    }

    suspend fun updateDownload(
        id: Long,
        downloaded: Long,
        total: Long,
        status: String,
        filePath: String
    ) = dao.updateDownloadProgress(id, downloaded, total, status, filePath)

    suspend fun deleteDownload(id: Long) = dao.deleteDownloadById(id)
    suspend fun clearDownloads() = dao.clearDownloads()

    suspend fun getSitePermission(origin: String): SitePermissionEntity? = dao.getSitePermission(origin)

    suspend fun saveSitePermission(permission: SitePermissionEntity) =
        dao.upsertSitePermission(permission.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteSitePermission(origin: String) = dao.deleteSitePermission(origin)

    suspend fun loadSavedTabs(): List<SavedTabEntity> = dao.getSavedTabsOnce()

    suspend fun persistTabs(tabs: List<SavedTabEntity>) {
        dao.clearSavedTabs()
        if (tabs.isNotEmpty()) {
            dao.upsertSavedTabs(tabs)
        }
    }

    suspend fun insertBridgeAudit(entry: BridgeAuditEntity): Long = dao.insertBridgeAudit(entry)

    suspend fun updateBridgeAuditStatus(id: Long, exitCode: Int?, status: String) =
        dao.updateBridgeAuditStatus(id, exitCode, status)

    suspend fun clearBridgeAuditLogs() = dao.clearBridgeAuditLogs()

    suspend fun updateMaxActiveTabs(limit: Int) = prefs.updateMaxActiveTabs(limit)
    suspend fun updateAutoSuspend(enabled: Boolean) = prefs.updateAutoSuspend(enabled)
    suspend fun updateJavascriptEnabled(enabled: Boolean) = prefs.updateJavascriptEnabled(enabled)
    suspend fun updateCookiesEnabled(enabled: Boolean) = prefs.updateCookiesEnabled(enabled)
    suspend fun updateThirdPartyCookies(enabled: Boolean) = prefs.updateThirdPartyCookies(enabled)
    suspend fun updateHttpsOnly(enabled: Boolean) = prefs.updateHttpsOnly(enabled)
    suspend fun updateAskBeforeDownload(enabled: Boolean) = prefs.updateAskBeforeDownload(enabled)
    suspend fun updateBlockAllDownloads(enabled: Boolean) = prefs.updateBlockAllDownloads(enabled)
    suspend fun updateDefaultZoom(zoom: Int) = prefs.updateDefaultZoom(zoom)
    suspend fun updateDarkTheme(dark: Boolean) = prefs.updateDarkTheme(dark)

    suspend fun updateBridgeConfig(host: String, httpPort: Int, sshPort: Int, sshUser: String, method: String) =
        prefs.updateBridgeConfig(host, httpPort, sshPort, sshUser, method)

    suspend fun updateBridgeAuthorized(authorized: Boolean) = prefs.updateBridgeAuthorized(authorized)
    suspend fun updateBridgeAllowedRoots(roots: String) = prefs.updateBridgeAllowedRoots(roots)
}
