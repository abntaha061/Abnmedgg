package com.example.ytdownloader.domain.repository

import com.example.ytdownloader.domain.model.DownloadTask
import com.example.ytdownloader.domain.model.VideoInfo
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun getAllDownloads(): Flow<List<DownloadTask>>
    suspend fun getDownloadById(id: String): DownloadTask?
    suspend fun insertDownload(task: DownloadTask)
    suspend fun updateDownload(task: DownloadTask)
    suspend fun deleteDownloadById(id: String)
    suspend fun clearHistory()
    
    suspend fun fetchVideoInfo(url: String): VideoInfo
    suspend fun downloadMedia(
        task: DownloadTask,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): String
}
