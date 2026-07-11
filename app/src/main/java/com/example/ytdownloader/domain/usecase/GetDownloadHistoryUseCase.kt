package com.example.ytdownloader.domain.usecase

import com.example.ytdownloader.domain.model.DownloadTask
import com.example.ytdownloader.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow

class GetDownloadHistoryUseCase(private val repository: DownloadRepository) {
    operator fun invoke(): Flow<List<DownloadTask>> {
        return repository.getAllDownloads()
    }
}
