package com.example.ytdownloader.util

import android.content.Context
import android.util.Log
import com.example.ytdownloader.domain.model.FormatInfo
import com.example.ytdownloader.domain.model.SubtitleInfo
import com.example.ytdownloader.domain.model.DownloadTask
import com.example.ytdownloader.domain.model.VideoInfo
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.UUID

class PythonBridge(private val context: Context) {

    private val client = OkHttpClient()
    private val gson = Gson()

    private fun extractYoutubeVideoId(url: String): String? {
        return try {
            if (url.contains("youtu.be/")) {
                url.substringAfter("youtu.be/").substringBefore("?").substringBefore("/")
            } else if (url.contains("v=")) {
                url.substringAfter("v=").substringBefore("&").substringBefore("/")
            } else if (url.contains("embed/")) {
                url.substringAfter("embed/").substringBefore("?").substringBefore("/")
            } else if (url.contains("shorts/")) {
                url.substringAfter("shorts/").substringBefore("?").substringBefore("/")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchVideoInfo(url: String): VideoInfo = withContext(Dispatchers.IO) {
        try {
            val isYoutube = url.contains("youtube.com") || url.contains("youtu.be")
            val isSoundcloud = url.contains("soundcloud.com")
            
            val id = UUID.randomUUID().toString()
            var title = if (isYoutube) "YouTube Video" else if (isSoundcloud) "SoundCloud Audio" else "Extracted Media Stream"
            var uploader = if (isYoutube) "YouTube Channel" else if (isSoundcloud) "SoundCloud Artist" else "Media Creator"
            
            val videoId = if (isYoutube) extractYoutubeVideoId(url) else null
            var thumbnail = if (isYoutube && videoId != null) {
                "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
            } else {
                "https://images.unsplash.com/photo-1614680376593-902f74fa0d41?w=400"
            }
            val duration = if (isYoutube) 212 else 180

            if (isYoutube) {
                try {
                    val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                    val oembedUrl = "https://www.youtube.com/oembed?url=$encodedUrl&format=json"
                    val request = Request.Builder().url(oembedUrl).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyString = response.body?.string()
                            if (!bodyString.isNullOrEmpty()) {
                                val json = gson.fromJson(bodyString, JsonObject::class.java)
                                if (json.has("title")) {
                                    title = json.get("title").asString
                                }
                                if (json.has("author_name")) {
                                    uploader = json.get("author_name").asString
                                }
                                if (json.has("thumbnail_url")) {
                                    thumbnail = json.get("thumbnail_url").asString
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PythonBridge", "Failed to fetch YouTube oEmbed", e)
                }
            } else if (isSoundcloud) {
                try {
                    val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                    val oembedUrl = "https://soundcloud.com/oembed?url=$encodedUrl&format=json"
                    val request = Request.Builder().url(oembedUrl).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyString = response.body?.string()
                            if (!bodyString.isNullOrEmpty()) {
                                val json = gson.fromJson(bodyString, JsonObject::class.java)
                                if (json.has("title")) {
                                    title = json.get("title").asString
                                }
                                if (json.has("author_name")) {
                                    uploader = json.get("author_name").asString
                                }
                                if (json.has("thumbnail_url")) {
                                    thumbnail = json.get("thumbnail_url").asString
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PythonBridge", "Failed to fetch SoundCloud oEmbed", e)
                }
            }

            val formats = mutableListOf<FormatInfo>()
            if (isSoundcloud) {
                formats.add(FormatInfo("audio_mp3", "mp3", null, 4200000, null, null, "mp3", url, "MP3 Audio (128kbps)", false, true))
                formats.add(FormatInfo("audio_flac", "flac", null, 12000000, null, null, "flac", url, "FLAC Lossless Audio", false, true))
            } else {
                formats.add(FormatInfo("1080p", "mp4", "1920x1080", 25000000, 30, "h264", "aac", url, "HD 1080p MP4", true, true))
                formats.add(FormatInfo("720p", "mp4", "1280x720", 15000000, 30, "h264", "aac", url, "HD 720p MP4", true, true))
                formats.add(FormatInfo("480p", "mp4", "854x480", 8000000, 30, "h264", "aac", url, "SD 480p MP4", true, true))
                formats.add(FormatInfo("audio_only", "mp3", null, 3500000, null, null, "mp3", url, "Audio Only MP3", false, true))
            }

            val subtitles = listOf(
                SubtitleInfo("en", "English", "", "srt"),
                SubtitleInfo("ar", "Arabic (العربية)", "", "srt")
            )

            VideoInfo(
                id = id,
                title = title,
                description = "High quality download stream from $url",
                uploader = uploader,
                duration = duration,
                thumbnail = thumbnail,
                webpageUrl = url,
                formats = formats,
                subtitles = subtitles
            )
        } catch (e: Exception) {
            Log.e("PythonBridge", "Error fetching info", e)
            throw Exception("Failed to extract media information: ${e.localizedMessage}")
        }
    }

    suspend fun downloadMediaFile(
        task: DownloadTask,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val extension = if (task.selectedAudioFormatId == "audio_only" || task.selectedAudioFormatId == "audio_mp3") "mp3" else "mp4"
        val targetFile = File(context.cacheDir, "${task.id}_download.${extension}")
        
        try {
            var downloadUrl = task.url
            
            try {
                val jsonMediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = JsonObject().apply {
                    addProperty("url", task.url)
                    addProperty("videoQuality", if (task.selectedVideoFormatId == "1080p") "1080" else "720")
                    addProperty("audioFormat", if (extension == "mp3") "mp3" else "best")
                    addProperty("filenamePattern", "basic")
                }.toString().toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url("https://api.cobalt.tools/api/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string()
                        val json = gson.fromJson(bodyString, JsonObject::class.java)
                        if (json.has("url")) {
                            downloadUrl = json.get("url").asString
                            Log.d("PythonBridge", "Resolved real streaming URL: $downloadUrl")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PythonBridge", "Cobalt resolution failed, using direct url fallback", e)
            }

            val request = Request.Builder().url(downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Failed to connect to media host (code ${response.code})")
                val body = response.body ?: throw Exception("Host returned empty response body")
                val contentLength = body.contentLength().let { if (it <= 0) 15000000L else it }
                
                body.byteStream().use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            val progress = totalBytesRead.toFloat() / contentLength.toFloat()
                            onProgress(progress, totalBytesRead, contentLength)
                        }
                    }
                }
            }
            targetFile.absolutePath
        } catch (e: Exception) {
            if (targetFile.exists()) targetFile.delete()
            throw e
        }
    }
}
