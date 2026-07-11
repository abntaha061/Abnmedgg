package com.example.ytdownloader.util

import com.example.ytdownloader.domain.model.FormatInfo
import kotlin.math.abs

object FormatSelector {

    /**
     * يحلل قائمة الـ formats من yt-dlp ويختار أفضل تركيبة
     * خاصة لـ YouTube وأي موقع يفصل الفيديو عن الصوت
     */
    fun selectBestFormats(
        formats: List<FormatInfo>,
        targetHeight: Int,          // 1080, 720, 480, إلخ — 0 = أعلى جودة
        preferAvc: Boolean = true,  // true = H.264 للتوافق، false = VP9/AV1 للجودة
        audioQuality: AudioQuality = AudioQuality.BEST
    ): SelectedFormats {

        // 1. فصل الـ formats حسب نوعها
        val videoOnlyFormats = formats.filter {
            it.isVideo && !it.isAudio && it.resolution != null
        }
        val audioOnlyFormats = formats.filter {
            it.isAudio && !it.isVideo
        }
        val muxedFormats = formats.filter {
            it.isVideo && it.isAudio && it.resolution != null
        }

        // Helper to extract height from format
        val getHeight = { format: FormatInfo ->
            val res = format.resolution ?: ""
            val h = res.substringAfter('x').toIntOrNull()
                ?: format.note?.filter { it.isDigit() }?.toIntOrNull()
                ?: 0
            h
        }

        // 2. اختيار الـ Video Format
        val videoFormat = if (videoOnlyFormats.isNotEmpty()) {
            val candidates = if (targetHeight > 0) {
                videoOnlyFormats.filter { getHeight(it) <= targetHeight }
            } else {
                videoOnlyFormats
            }
            if (candidates.isEmpty()) {
                videoOnlyFormats.sortedByDescending { getHeight(it) }.firstOrNull()
            } else if (preferAvc) {
                // يفضل H.264 (h264/avc1) على VP9/AV1
                candidates
                    .sortedByDescending { getHeight(it) * 10000 + (it.filesize ?: 0L).toInt() }
                    .let { sorted ->
                        sorted.firstOrNull { (it.vcodec ?: "").contains("avc") || (it.vcodec ?: "").contains("h264") }
                            ?: sorted.firstOrNull { (it.vcodec ?: "").contains("vp9") }
                            ?: sorted.firstOrNull()
                    }
            } else {
                // يفضل الجودة العالية (AV1 > VP9 > H.264)
                candidates
                    .sortedByDescending { getHeight(it) * 10000 + (it.filesize ?: 0L).toInt() }
                    .let { sorted ->
                        sorted.firstOrNull { (it.vcodec ?: "").contains("av01") || (it.vcodec ?: "").contains("av1") }
                            ?: sorted.firstOrNull { (it.vcodec ?: "").contains("vp9") }
                            ?: sorted.firstOrNull()
                    }
            }
        } else {
            // لا يوجد video-only → استخدم muxed كـ fallback
            muxedFormats
                .filter { if (targetHeight > 0) getHeight(it) <= targetHeight else true }
                .sortedByDescending { getHeight(it) }
                .firstOrNull()
        }

        // 3. اختيار الـ Audio Format
        val audioFormat = selectBestAudio(audioOnlyFormats, audioQuality)

        // 4. تحديد استراتيجية الدمج
        // isVideoOnly can be inferred if it's in videoOnlyFormats
        val isVideoOnly = videoFormat != null && videoOnlyFormats.contains(videoFormat)
        val needsMerge = videoFormat != null &&
                         audioFormat != null &&
                         isVideoOnly

        return SelectedFormats(
            videoFormat = videoFormat,
            audioFormat = if (needsMerge) audioFormat else null,
            muxedFallback = muxedFormats.sortedByDescending { getHeight(it) }.firstOrNull(),
            needsMerge = needsMerge,
            estimatedVideoSize = videoFormat?.filesize,
            estimatedAudioSize = audioFormat?.filesize
        )
    }

    private fun selectBestAudio(
        audioFormats: List<FormatInfo>,
        quality: AudioQuality
    ): FormatInfo? {
        if (audioFormats.isEmpty()) return null

        // Safe bit rate estimation from notes or size
        val getBitrate = { format: FormatInfo ->
            format.note?.filter { it.isDigit() }?.toDoubleOrNull() ?: 128.0
        }

        return when (quality) {
            AudioQuality.BEST -> {
                // يفضل m4a/aac للتوافق مع FFmpeg على Android
                audioFormats
                    .sortedByDescending { getBitrate(it) }
                    .let { sorted ->
                        sorted.firstOrNull { it.ext == "m4a" || (it.acodec ?: "").contains("mp4a") }
                            ?: sorted.firstOrNull { it.ext == "webm" || (it.acodec ?: "").contains("opus") }
                            ?: sorted.first()
                    }
            }
            AudioQuality.HIGH -> {
                audioFormats
                    .filter { getBitrate(it) >= 128.0 }
                    .sortedByDescending { getBitrate(it) }
                    .firstOrNull() ?: audioFormats.sortedByDescending { getBitrate(it) }.first()
            }
            AudioQuality.MEDIUM -> {
                audioFormats
                    .minByOrNull { abs(getBitrate(it) - 128.0) }
                    ?: audioFormats.first()
            }
            AudioQuality.LOW -> {
                audioFormats.minByOrNull { getBitrate(it) } ?: audioFormats.first()
            }
        }
    }

    /**
     * يبني yt-dlp format string
     * مثال: "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[height<=1080]"
     */
    fun buildYtDlpFormatString(
        selectedFormats: SelectedFormats,
        targetHeight: Int,
        preferAvc: Boolean
    ): String {
        val videoCodecFilter = if (preferAvc) "[vcodec^=avc]" else ""
        val heightFilter = if (targetHeight > 0) "[height<=$targetHeight]" else ""

        return if (selectedFormats.needsMerge) {
            "bestvideo$heightFilter$videoCodecFilter+bestaudio[ext=m4a]" +
            "/bestvideo$heightFilter+bestaudio" +
            "/best$heightFilter"
        } else {
            "best$heightFilter/bestvideo+bestaudio"
        }
    }
}

data class SelectedFormats(
    val videoFormat: FormatInfo?,
    val audioFormat: FormatInfo?,
    val muxedFallback: FormatInfo?,
    val needsMerge: Boolean,
    val estimatedVideoSize: Long?,
    val estimatedAudioSize: Long?
) {
    val totalEstimatedSize: Long?
        get() = when {
            estimatedVideoSize != null && estimatedAudioSize != null ->
                estimatedVideoSize + estimatedAudioSize
            estimatedVideoSize != null -> estimatedVideoSize
            else -> null
        }
}

enum class AudioQuality { BEST, HIGH, MEDIUM, LOW }
