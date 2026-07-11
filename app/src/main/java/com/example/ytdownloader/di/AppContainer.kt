package com.example.ytdownloader.di

import android.content.Context
import androidx.room.Room
import com.example.ytdownloader.data.local.AppDatabase
import com.example.ytdownloader.data.local.dao.DownloadDao
import com.example.ytdownloader.data.preferences.UserPreferences
import com.example.ytdownloader.data.repository.DownloadRepositoryImpl
import com.example.ytdownloader.domain.repository.DownloadRepository
import com.example.ytdownloader.domain.usecase.FetchVideoInfoUseCase
import com.example.ytdownloader.domain.usecase.GetDownloadHistoryUseCase
import com.example.ytdownloader.domain.usecase.MergeMediaUseCase
import com.example.ytdownloader.domain.usecase.StartDownloadUseCase
import com.example.ytdownloader.util.PythonBridge

class AppContainer(private val context: Context) {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "ytdl_database"
        ).build()
    }

    val downloadDao: DownloadDao by lazy {
        database.downloadDao()
    }

    val pythonBridge: PythonBridge by lazy {
        PythonBridge(context.applicationContext)
    }

    val downloadRepository: DownloadRepository by lazy {
        DownloadRepositoryImpl(downloadDao, pythonBridge)
    }

    val userPreferences: UserPreferences by lazy {
        UserPreferences(context.applicationContext)
    }

    val fetchVideoInfoUseCase: FetchVideoInfoUseCase by lazy {
        FetchVideoInfoUseCase(downloadRepository)
    }

    val startDownloadUseCase: StartDownloadUseCase by lazy {
        StartDownloadUseCase(downloadRepository)
    }

    val mergeMediaUseCase: MergeMediaUseCase by lazy {
        MergeMediaUseCase()
    }

    val getDownloadHistoryUseCase: GetDownloadHistoryUseCase by lazy {
        GetDownloadHistoryUseCase(downloadRepository)
    }
}
