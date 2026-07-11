package com.example.ytdownloader.domain.model

data class DownloadTask(
    val id: String,
    val url: String,
    val title: String,
    val thumbnail: String?,
    val selectedVideoFormatId: String?,
    val selectedAudioFormatId: String?,
    val subtitleLanguage: String?,
    val status: DownloadStatus,
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val filePath: String?,
    val createdAt: Long,
    val isPlaylist: Boolean = false,
    val playlistIndex: Int = 0,
    val totalPlaylistItems: Int = 0,
    val scheduledTime: Long? = null,
    val isIncognito: Boolean = false
)
