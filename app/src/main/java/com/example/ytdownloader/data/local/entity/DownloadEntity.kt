package com.example.ytdownloader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.ytdownloader.domain.model.DownloadStatus
import com.example.ytdownloader.domain.model.DownloadTask

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val thumbnail: String?,
    val selectedVideoFormatId: String?,
    val selectedAudioFormatId: String?,
    val subtitleLanguage: String?,
    val status: String,
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val filePath: String?,
    val createdAt: Long,
    val isPlaylist: Boolean,
    val playlistIndex: Int,
    val totalPlaylistItems: Int,
    val scheduledTime: Long?,
    val isIncognito: Boolean
) {
    fun toDomain(): DownloadTask {
        return DownloadTask(
            id = id,
            url = url,
            title = title,
            thumbnail = thumbnail,
            selectedVideoFormatId = selectedVideoFormatId,
            selectedAudioFormatId = selectedAudioFormatId,
            subtitleLanguage = subtitleLanguage,
            status = try { DownloadStatus.valueOf(status) } catch (e: Exception) { DownloadStatus.QUEUED },
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            filePath = filePath,
            createdAt = createdAt,
            isPlaylist = isPlaylist,
            playlistIndex = playlistIndex,
            totalPlaylistItems = totalPlaylistItems,
            scheduledTime = scheduledTime,
            isIncognito = isIncognito
        )
    }

    companion object {
        fun fromDomain(task: DownloadTask): DownloadEntity {
            return DownloadEntity(
                id = task.id,
                url = task.url,
                title = task.title,
                thumbnail = task.thumbnail,
                selectedVideoFormatId = task.selectedVideoFormatId,
                selectedAudioFormatId = task.selectedAudioFormatId,
                subtitleLanguage = task.subtitleLanguage,
                status = task.status.name,
                progress = task.progress,
                downloadedBytes = task.downloadedBytes,
                totalBytes = task.totalBytes,
                filePath = task.filePath,
                createdAt = task.createdAt,
                isPlaylist = task.isPlaylist,
                playlistIndex = task.playlistIndex,
                totalPlaylistItems = task.totalPlaylistItems,
                scheduledTime = task.scheduledTime,
                isIncognito = task.isIncognito
            )
        }
    }
}
