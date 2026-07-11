package com.example.ytdownloader.domain.usecase

import com.example.ytdownloader.domain.model.VideoInfo
import com.example.ytdownloader.domain.repository.DownloadRepository

class FetchVideoInfoUseCase(private val repository: DownloadRepository) {
    suspend operator fun invoke(url: String): VideoInfo {
        if (url.isBlank()) throw IllegalArgumentException("URL cannot be empty")
        return repository.fetchVideoInfo(url)
    }
}
