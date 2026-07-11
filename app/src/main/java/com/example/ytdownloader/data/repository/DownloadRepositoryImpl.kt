package com.example.ytdownloader.data.repository

import com.example.ytdownloader.data.local.dao.DownloadDao
import com.example.ytdownloader.data.local.entity.DownloadEntity
import com.example.ytdownloader.domain.model.DownloadTask
import com.example.ytdownloader.domain.model.VideoInfo
import com.example.ytdownloader.domain.repository.DownloadRepository
import com.example.ytdownloader.util.PythonBridge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DownloadRepositoryImpl(
    private val downloadDao: DownloadDao,
    private val pythonBridge: PythonBridge
) : DownloadRepository {

    override fun getAllDownloads(): Flow<List<DownloadTask>> {
        return downloadDao.getAllDownloads().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getDownloadById(id: String): DownloadTask? {
        return downloadDao.getDownloadById(id)?.toDomain()
    }

    override suspend fun insertDownload(task: DownloadTask) {
        downloadDao.insertDownload(DownloadEntity.fromDomain(task))
    }

    override suspend fun updateDownload(task: DownloadTask) {
        downloadDao.updateDownload(DownloadEntity.fromDomain(task))
    }

    override suspend fun deleteDownloadById(id: String) {
        downloadDao.deleteDownloadById(id)
    }

    override suspend fun clearHistory() {
        downloadDao.clearHistory()
    }

    override suspend fun fetchVideoInfo(url: String): VideoInfo {
        return pythonBridge.fetchVideoInfo(url)
    }

    override suspend fun downloadMedia(
        task: DownloadTask,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): String {
        return pythonBridge.downloadMediaFile(task, onProgress)
    }
}
