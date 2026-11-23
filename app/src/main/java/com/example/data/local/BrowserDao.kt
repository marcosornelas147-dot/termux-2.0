package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserDao {

    // History
    @Query("SELECT * FROM history_entries ORDER BY visitedAt DESC LIMIT 300")
    fun observeHistory(): Flow<List<HistoryEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: HistoryEntryEntity)

    @Query("DELETE FROM history_entries WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM history_entries")
    suspend fun clearHistory()

    // Bookmarks
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun getBookmarkByUrl(url: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteBookmarkByUrl(url: String)

    // Downloads
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeDownloads(): Flow<List<DownloadEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(entry: DownloadEntryEntity): Long

    @Query("UPDATE downloads SET bytesDownloaded = :downloaded, bytesTotal = :total, status = :status, filePath = :filePath WHERE id = :id")
    suspend fun updateDownloadProgress(id: Long, downloaded: Long, total: Long, status: String, filePath: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: Long)

    @Query("DELETE FROM downloads")
    suspend fun clearDownloads()

    // Site Permissions
    @Query("SELECT * FROM site_permissions ORDER BY updatedAt DESC")
    fun observeSitePermissions(): Flow<List<SitePermissionEntity>>

    @Query("SELECT * FROM site_permissions WHERE origin = :origin LIMIT 1")
    suspend fun getSitePermission(origin: String): SitePermissionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSitePermission(permission: SitePermissionEntity)

    @Query("DELETE FROM site_permissions WHERE origin = :origin")
    suspend fun deleteSitePermission(origin: String)

    // Saved Tabs (non-private session restore)
    @Query("SELECT * FROM saved_tabs ORDER BY position ASC")
    suspend fun getSavedTabsOnce(): List<SavedTabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedTabs(tabs: List<SavedTabEntity>)

    @Query("DELETE FROM saved_tabs")
    suspend fun clearSavedTabs()

    // Bridge Audit Logs
    @Query("SELECT * FROM bridge_audit_logs ORDER BY executedAt DESC LIMIT 200")
    fun observeBridgeAuditLogs(): Flow<List<BridgeAuditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBridgeAudit(entry: BridgeAuditEntity): Long

    @Query("UPDATE bridge_audit_logs SET exitCode = :exitCode, status = :status WHERE id = :id")
    suspend fun updateBridgeAuditStatus(id: Long, exitCode: Int?, status: String)

    @Query("DELETE FROM bridge_audit_logs")
    suspend fun clearBridgeAuditLogs()
}
