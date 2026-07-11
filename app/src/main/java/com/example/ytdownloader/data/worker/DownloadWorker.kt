package com.example.ytdownloader.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.ytdownloader.YTDLApplication
import com.example.ytdownloader.domain.model.DownloadStatus
import com.example.ytdownloader.domain.model.DownloadTask
import com.example.ytdownloader.util.FFmpegHelper
import com.example.ytdownloader.util.FileHelper
import com.example.ytdownloader.util.FormatSelector
import com.example.ytdownloader.util.MergeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

class DownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val container = (context.applicationContext as YTDLApplication).container
    private val repository = container.downloadRepository
    private val preferences = container.userPreferences

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val KEY_TASK_ID = "task_id"
        private const val CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.failure()
        
        var task = repository.getDownloadById(taskId) ?: return@withContext Result.failure()
        
        createNotificationChannel()
        setForeground(createForegroundInfo(task.title, 0f))

        try {
            task = task.copy(status = DownloadStatus.RUNNING, progress = 0f)
            repository.updateDownload(task)

            executeDownloadPipeline(task)
            Result.success()
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Download execution failed", e)
            
            task = task.copy(
                status = DownloadStatus.FAILED,
                progress = 0f
            )
            repository.updateDownload(task)
            
            showFailureNotification(task.title, e.localizedMessage ?: "Unknown media download error")
            Result.failure()
        } finally {
            cleanupTempFiles(task.id)
        }
    }

    private suspend fun executeDownloadPipeline(task: DownloadTask) {
        val tempDir = FFmpegHelper.getTempDir(context, task.id)
        val folderUri = preferences.downloadUri.firstOrNull()

        // ─────────────────────────────────────────
        // STEP 1: جلب معلومات الفيديو (Fetch video info)
        // ─────────────────────────────────────────
        updateNotification(task.title, 0.05f, "Analyzing media content...")
        val videoInfo = repository.fetchVideoInfo(task.url)

        val targetHeight = when (task.selectedVideoFormatId) {
            "1080p" -> 1080
            "720p" -> 720
            "480p" -> 480
            else -> 0
        }

        val selectedFormats = FormatSelector.selectBestFormats(
            formats = videoInfo.formats,
            targetHeight = targetHeight,
            preferAvc = true
        )

        var currentFile: String

        if (selectedFormats.needsMerge) {
            // ─────────────────────────────────────────
            // STEP 2A: تحميل الفيديو بدون صوت (Download video only)
            // ─────────────────────────────────────────
            val tempVideoPath = "$tempDir/video_${task.id}.${selectedFormats.videoFormat!!.ext}"
            updateNotification(task.title, 0.1f, "Downloading high quality video stream...")

            val videoFormatId = selectedFormats.videoFormat.formatId
            val videoTask = task.copy(selectedVideoFormatId = videoFormatId, selectedAudioFormatId = null)

            repository.downloadMedia(videoTask) { progress, downloaded, total ->
                val overallProgress = progress * 0.5f
                runBlocking {
                    updateProgress(task, overallProgress, downloaded, total)
                }
            }

            // ─────────────────────────────────────────
            // STEP 2B: تحميل الصوت بدون فيديو (Download audio only)
            // ─────────────────────────────────────────
            val tempAudioPath = "$tempDir/audio_${task.id}.${selectedFormats.audioFormat!!.ext}"
            updateNotification(task.title, 0.5f, "Downloading high quality audio stream...")

            val audioFormatId = selectedFormats.audioFormat.formatId
            val audioTask = task.copy(selectedVideoFormatId = null, selectedAudioFormatId = audioFormatId)

            repository.downloadMedia(audioTask) { progress, downloaded, total ->
                val overallProgress = 0.5f + (progress * 0.3f)
                runBlocking {
                    updateProgress(task, overallProgress, downloaded, total)
                }
            }

            // ─────────────────────────────────────────
            // STEP 3: دمج الفيديو + الصوت بـ FFmpeg (FFmpeg merge)
            // ─────────────────────────────────────────
            val outputMergedPath = File(context.cacheDir, "${task.id}_merged.mp4").absolutePath
            updateNotification(task.title, 0.8f, "Merging audio and video tracks natively...")

            val mergeResult = FFmpegHelper.withFFmpegSession {
                FFmpegHelper.mergeVideoAudio(
                    context = context,
                    videoPath = tempVideoPath,
                    audioPath = tempAudioPath,
                    outputPath = outputMergedPath,
                    onProgress = { pct, stage ->
                        val overallProgress = 0.8f + (pct * 0.001f)
                        updateNotification(task.title, overallProgress, stage)
                    }
                )
            }

            when (mergeResult) {
                is MergeResult.Failed -> throw Exception("FFmpeg merging failed: ${mergeResult.logs}")
                is MergeResult.Cancelled -> throw Exception("Merge was cancelled")
                is MergeResult.Success -> {
                    currentFile = mergeResult.outputPath
                }
            }
        } else {
            // ─────────────────────────────────────────
            // تحميل مباشر بدون دمج (Direct download)
            // ─────────────────────────────────────────
            val isAudioOnly = task.selectedVideoFormatId == "audio_only" || task.selectedVideoFormatId == null
            val targetFormatId = if (isAudioOnly) {
                selectedFormats.audioFormat?.formatId ?: "audio_only"
            } else {
                selectedFormats.muxedFallback?.formatId ?: task.selectedVideoFormatId ?: "720p"
            }
            val targetTask = if (isAudioOnly) {
                task.copy(selectedVideoFormatId = null, selectedAudioFormatId = targetFormatId)
            } else {
                task.copy(selectedVideoFormatId = targetFormatId, selectedAudioFormatId = null)
            }

            updateNotification(task.title, 0.1f, "Downloading media stream...")
            val path = repository.downloadMedia(targetTask) { progress, downloaded, total ->
                runBlocking {
                    updateProgress(task, progress * 0.8f, downloaded, total)
                }
            }
            currentFile = path
        }

        // ─────────────────────────────────────────
        // STEP 4: Embedding (ترجمة + Thumbnail)
        // ─────────────────────────────────────────
        val isAudioOnly = task.selectedVideoFormatId == "audio_only" || task.selectedVideoFormatId == null

        if (!task.subtitleLanguage.isNullOrEmpty() && !isAudioOnly) {
            updateNotification(task.title, 0.9f, "Embedding subtitles...")
            val subUrl = videoInfo.subtitles.firstOrNull { it.language == task.subtitleLanguage }?.url
            if (!subUrl.isNullOrEmpty()) {
                val tempSubPath = "$tempDir/sub_${task.id}.srt"
                val downloaded = downloadFileFromUrl(subUrl, tempSubPath)
                if (downloaded && File(tempSubPath).exists()) {
                    val withSubPath = currentFile.replace(".mp4", "_sub.mp4")
                    val embedSuccess = FFmpegHelper.embedSubtitle(currentFile, tempSubPath, withSubPath) { }
                    if (embedSuccess && File(withSubPath).exists()) {
                        File(currentFile).delete()
                        currentFile = withSubPath
                    }
                }
            }
        }

        if (!task.thumbnail.isNullOrEmpty()) {
            updateNotification(task.title, 0.95f, "Embedding thumbnail artwork...")
            val tempThumbPath = "$tempDir/thumb_${task.id}.jpg"
            val downloaded = downloadFileFromUrl(task.thumbnail, tempThumbPath)
            if (downloaded && File(tempThumbPath).exists()) {
                val extension = if (isAudioOnly) "mp3" else "mp4"
                val withThumbPath = currentFile.replace(".$extension", "_final.$extension")
                val embedSuccess = FFmpegHelper.embedThumbnail(currentFile, tempThumbPath, withThumbPath)
                if (embedSuccess && File(withThumbPath).exists()) {
                    File(currentFile).delete()
                    currentFile = withThumbPath
                }
                File(tempThumbPath).delete()
            }
        }

        // ─────────────────────────────────────────
        // STEP 5: إتمام التحميل (Saving to Storage)
        // ─────────────────────────────────────────
        updateNotification(task.title, 0.99f, "Saving downloaded media file...")
        val extension = if (isAudioOnly) "mp3" else "mp4"
        
        val savedUri = FileHelper.saveFileToSaf(
            context = context,
            sourcePath = currentFile,
            targetFolderUriString = folderUri,
            title = task.title,
            ext = extension
        )

        if (savedUri == null) {
            throw Exception("Could not write media file to final storage folder.")
        }

        val completedTask = task.copy(
            status = DownloadStatus.DONE,
            progress = 1.0f,
            filePath = savedUri
        )
        repository.updateDownload(completedTask)
        
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private suspend fun downloadFileFromUrl(url: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                val file = File(outputPath)
                file.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                true
            }
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Failed to download url $url", e)
            false
        }
    }

    private suspend fun updateProgress(task: DownloadTask, progress: Float, downloadedBytes: Long, totalBytes: Long) {
        val updatedTask = task.copy(
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes
        )
        repository.updateDownload(updatedTask)
        updateNotification(task.title, progress, "Downloading: ${(progress * 100).toInt()}%")
    }

    private fun createForegroundInfo(title: String, progress: Float): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Downloading: ${(progress * 100).toInt()}%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, (progress * 100).toInt(), false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(title: String, progress: Float, statusMessage: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(statusMessage)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, (progress * 100).toInt(), false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showFailureNotification(title: String, errorMsg: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Fail: $title")
            .setContentText(errorMsg)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun cleanupTempFiles(taskId: String) {
        val tempDir = File(context.cacheDir, "downloads_temp/$taskId")
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
    }
}
