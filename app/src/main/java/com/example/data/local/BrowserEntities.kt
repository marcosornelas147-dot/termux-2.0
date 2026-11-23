package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_entries")
data class HistoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val folder: String = "Geral",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val filePath: String,
    val bytesTotal: Long,
    val bytesDownloaded: Long,
    val status: String, // DOWNLOADING, COMPLETED, FAILED, CANCELLED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "site_permissions")
data class SitePermissionEntity(
    @PrimaryKey val origin: String,
    val javascriptEnabled: Boolean = true,
    val cookiesAllowed: Boolean = true,
    val geolocationAllowed: Boolean = false,
    val cameraMicAllowed: Boolean = false,
    val downloadsAllowed: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_tabs")
data class SavedTabEntity(
    @PrimaryKey val tabId: String,
    val url: String,
    val title: String,
    val isDesktopMode: Boolean = false,
    val zoomPercent: Int = 100,
    val position: Int = 0,
    val lastAccessedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bridge_audit_logs")
data class BridgeAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val method: String,
    val command: String,
    val workingDirectory: String,
    val exitCode: Int? = null,
    val status: String, // EXECUTING, COMPLETED, FAILED, INTERRUPTED, BLOCKED
    val executedAt: Long = System.currentTimeMillis()
)
