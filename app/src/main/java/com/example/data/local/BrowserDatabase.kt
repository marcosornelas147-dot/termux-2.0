package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HistoryEntryEntity::class,
        BookmarkEntity::class,
        DownloadEntryEntity::class,
        SitePermissionEntity::class,
        SavedTabEntity::class,
        BridgeAuditEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class BrowserDatabase : RoomDatabase() {
    abstract fun browserDao(): BrowserDao

    companion object {
        @Volatile
        private var INSTANCE: BrowserDatabase? = null

        fun getInstance(context: Context): BrowserDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BrowserDatabase::class.java,
                    "termux_browser.db"
                ).fallbackToDestructiveMigration(true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
