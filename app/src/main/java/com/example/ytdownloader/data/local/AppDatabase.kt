package com.example.ytdownloader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.ytdownloader.data.local.dao.DownloadDao
import com.example.ytdownloader.data.local.entity.DownloadEntity

@Database(entities = [DownloadEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}
