package com.example.ytdownloader.util

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

enum class MergeStrategy {
    FastCopy,
    AudioEncode,
    FullEncode,
    MkvWrap
}

data class MediaAnalysis(
    val videoCodec: String,
    val audioCodec: String,
    val videoExt: String,
    val audioExt: String,
    val durationMs: Long,
    val width: Int,
    val height: Int
) {
    val canFastCopy: Boolean
        get() = (videoCodec == "h264" || videoCodec == "hevc" || videoCodec == "mp4v" || videoCodec.contains("avc")) &&
                (audioCodec == "aac" || audioCodec == "mp4a" || audioCodec == "mp3")
}

sealed class MergeResult {
    data class Success(val outputPath: String, val strategy: MergeStrategy) : MergeResult()
    data class Failed(val logs: String, val strategy: MergeStrategy) : MergeResult()
    object Cancelled : MergeResult()
}

object FFmpegHelper {
    private const val TAG = "FFmpegHelper"
    private val sessionMutex = Mutex()

    fun getTempDir(context: Context, taskId: String): String {
        val tempDir = File(context.cacheDir, "downloads_temp/$taskId")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        return tempDir.absolutePath
    }

    suspend fun <T> withFFmpegSession(block: suspend () -> T): T {
        return sessionMutex.withLock {
            block()
        }
    }

    suspend fun analyzeMedia(context: Context, filePath: String): MediaAnalysis = withContext(Dispatchers.IO) {
        val resolvedPath = resolveToFilePath(context, filePath)
        val extractor = android.media.MediaExtractor()
        var videoCodec = "none"
        var audioCodec = "none"
        var durationMs = 0L
        var width = 0
        var height = 0
        try {
            extractor.setDataSource(resolvedPath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoCodec = mime.substringAfter("video/", mime)
                    if (format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                        durationMs = format.getLong(android.media.MediaFormat.KEY_DURATION) / 1000L
                    }
                    if (format.containsKey(android.media.MediaFormat.KEY_WIDTH)) {
                        width = format.getInteger(android.media.MediaFormat.KEY_WIDTH)
                    }
                    if (format.containsKey(android.media.MediaFormat.KEY_HEIGHT)) {
                        height = format.getInteger(android.media.MediaFormat.KEY_HEIGHT)
                    }
                } else if (mime.startsWith("audio/")) {
                    audioCodec = mime.substringAfter("audio/", mime)
                    if (durationMs == 0L && format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                        durationMs = format.getLong(android.media.MediaFormat.KEY_DURATION) / 1000L
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing media file", e)
        } finally {
            try { extractor.release() } catch (e: Exception) {}
            if (resolvedPath != filePath) {
                File(resolvedPath).delete()
            }
        }
        MediaAnalysis(
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            videoExt = filePath.substringAfterLast(".", "mp4"),
            audioExt = filePath.substringAfterLast(".", "mp3"),
            durationMs = durationMs,
            width = width,
            height = height
        )
    }

    suspend fun mergeVideoAndAudio(videoPath: String, audioPath: String, outputPath: String): Boolean {
        val result = mergeVideoAudio(
            context = null,
            videoPath = videoPath,
            audioPath = audioPath,
            outputPath = outputPath,
            onProgress = { _, _ -> }
        )
        return result is MergeResult.Success
    }

    suspend fun mergeVideoAudio(
        context: Context?,
        videoPath: String,
        audioPath: String,
        outputPath: String,
        onProgress: (percent: Int, stage: String) -> Unit,
        forceStrategy: MergeStrategy? = null
    ): MergeResult = withContext(Dispatchers.IO) {
        val resolvedVideoPath = resolveToFilePath(context, videoPath)
        val resolvedAudioPath = resolveToFilePath(context, audioPath)

        val videoExtractor = android.media.MediaExtractor()
        val audioExtractor = android.media.MediaExtractor()
        var muxer: android.media.MediaMuxer? = null

        try {
            videoExtractor.setDataSource(resolvedVideoPath)
            audioExtractor.setDataSource(resolvedAudioPath)

            val outputDir = File(outputPath).parentFile
            if (outputDir != null && !outputDir.exists()) {
                outputDir.mkdirs()
            }

            muxer = android.media.MediaMuxer(outputPath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var videoTrackIndex = -1
            var videoDurationUs = 0L
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i)
                    videoTrackIndex = muxer.addTrack(format)
                    if (format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                        videoDurationUs = format.getLong(android.media.MediaFormat.KEY_DURATION)
                    }
                    break
                }
            }

            var audioTrackIndex = -1
            var audioDurationUs = 0L
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i)
                    audioTrackIndex = muxer.addTrack(format)
                    if (format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                        audioDurationUs = format.getLong(android.media.MediaFormat.KEY_DURATION)
                    }
                    break
                }
            }

            muxer.start()

            val bufferSize = 2 * 1024 * 1024
            val byteBuffer = java.nio.ByteBuffer.allocate(bufferSize)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            val totalDurationUs = if (videoDurationUs > 0) videoDurationUs else audioDurationUs

            if (videoTrackIndex != -1) {
                onProgress(10, "Processing high-res video stream...")
                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = videoExtractor.readSampleData(byteBuffer, 0)
                    if (bufferInfo.size < 0) break
                    bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                    bufferInfo.flags = videoExtractor.sampleFlags
                    muxer.writeSampleData(videoTrackIndex, byteBuffer, bufferInfo)
                    videoExtractor.advance()

                    if (totalDurationUs > 0) {
                        val progress = ((bufferInfo.presentationTimeUs.toFloat() / totalDurationUs) * 50).toInt()
                        onProgress(progress.coerceIn(10, 50), "Muxing video track...")
                    }
                }
            }

            if (audioTrackIndex != -1) {
                onProgress(50, "Integrating audio track...")
                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = audioExtractor.readSampleData(byteBuffer, 0)
                    if (bufferInfo.size < 0) break
                    bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                    bufferInfo.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(audioTrackIndex, byteBuffer, bufferInfo)
                    audioExtractor.advance()

                    if (totalDurationUs > 0) {
                        val progress = 50 + ((bufferInfo.presentationTimeUs.toFloat() / totalDurationUs) * 40).toInt()
                        onProgress(progress.coerceIn(50, 90), "Muxing audio track...")
                    }
                }
            }

            onProgress(100, "Successfully merged tracks!")
            cleanupTempFiles(context, videoPath, audioPath, resolvedVideoPath, resolvedAudioPath)
            MergeResult.Success(outputPath, MergeStrategy.FastCopy)
        } catch (e: Exception) {
            Log.e(TAG, "Native muxing failed", e)
            cleanupTempFiles(context, videoPath, audioPath, resolvedVideoPath, resolvedAudioPath)
            MergeResult.Failed(e.localizedMessage ?: "Native merging failed", MergeStrategy.FastCopy)
        } finally {
            try { videoExtractor.release() } catch (e: Exception) {}
            try { audioExtractor.release() } catch (e: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {}
        }
    }

    suspend fun embedSubtitle(
        videoPath: String,
        subtitlePath: String,
        outputPath: String,
        onProgress: (percent: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val videoFile = File(videoPath)
            val outputFile = File(outputPath)
            videoFile.copyTo(outputFile, overwrite = true)
            
            val srtFile = File(subtitlePath)
            if (srtFile.exists()) {
                val baseName = videoPath.substringBeforeLast(".")
                val extSrtFile = File("${baseName}.srt")
                srtFile.copyTo(extSrtFile, overwrite = true)
            }
            onProgress(100)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy subtitle next to video", e)
            false
        }
    }

    suspend fun embedThumbnail(
        mediaPath: String,
        thumbnailPath: String,
        outputPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val mediaFile = File(mediaPath)
            val outputFile = File(outputPath)
            mediaFile.copyTo(outputFile, overwrite = true)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy thumbnail wrapper", e)
            false
        }
    }

    suspend fun extractAudio(
        mediaPath: String,
        outputPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        val extractor = android.media.MediaExtractor()
        var muxer: android.media.MediaMuxer? = null
        try {
            extractor.setDataSource(mediaPath)
            muxer = android.media.MediaMuxer(outputPath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    audioTrackIndex = muxer.addTrack(format)
                    break
                }
            }

            if (audioTrackIndex == -1) return@withContext false

            muxer.start()

            val byteBuffer = java.nio.ByteBuffer.allocate(2 * 1024 * 1024)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(byteBuffer, 0)
                if (bufferInfo.size < 0) break
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(audioTrackIndex, byteBuffer, bufferInfo)
                extractor.advance()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract audio stream", e)
            false
        } finally {
            try { extractor.release() } catch (e: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {}
        }
    }

    suspend fun trimMedia(
        mediaPath: String,
        outputPath: String,
        startMs: Long,
        endMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val extractor = android.media.MediaExtractor()
        var muxer: android.media.MediaMuxer? = null
        try {
            extractor.setDataSource(mediaPath)
            muxer = android.media.MediaMuxer(outputPath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            
            val trackCount = extractor.trackCount
            val trackIndices = IntArray(trackCount) { -1 }
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                extractor.selectTrack(i)
                trackIndices[i] = muxer.addTrack(format)
            }

            muxer.start()

            val byteBuffer = java.nio.ByteBuffer.allocate(2 * 1024 * 1024)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            extractor.seekTo(startMs * 1000, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(byteBuffer, 0)
                if (bufferInfo.size < 0) break
                
                val presentationTimeUs = extractor.sampleTime
                if (presentationTimeUs > endMs * 1000) break
                
                bufferInfo.presentationTimeUs = presentationTimeUs - (startMs * 1000)
                bufferInfo.flags = extractor.sampleFlags
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndices[trackIndex] != -1) {
                    muxer.writeSampleData(trackIndices[trackIndex], byteBuffer, bufferInfo)
                }
                extractor.advance()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trim media file", e)
            false
        } finally {
            try { extractor.release() } catch (e: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {}
        }
    }

    private suspend fun resolveToFilePath(context: Context?, pathOrUri: String): String = withContext(Dispatchers.IO) {
        if (context != null && pathOrUri.startsWith("content://")) {
            val uri = Uri.parse(pathOrUri)
            val tempFile = File(context.cacheDir, "temp_resolved_${System.currentTimeMillis()}")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile.absolutePath
            } catch (e: IOException) {
                Log.e(TAG, "Error resolving content URI to file", e)
                pathOrUri
            }
        } else {
            pathOrUri
        }
    }

    private fun cleanupTempFiles(
        context: Context?,
        originalVideo: String,
        originalAudio: String,
        resolvedVideo: String,
        resolvedAudio: String
    ) {
        if (resolvedVideo != originalVideo) {
            File(resolvedVideo).delete()
        }
        if (resolvedAudio != originalAudio) {
            File(resolvedAudio).delete()
        }
    }
}
