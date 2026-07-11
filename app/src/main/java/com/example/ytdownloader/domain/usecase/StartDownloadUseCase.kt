package com.example.ytdownloader.domain.usecase

import com.example.ytdownloader.domain.model.DownloadTask
import com.example.ytdownloader.domain.repository.DownloadRepository

class StartDownloadUseCase(private val repository: DownloadRepository) {
    suspend operator fun invoke(task: DownloadTask) {
        repository.insertDownload(task)
    }
}
