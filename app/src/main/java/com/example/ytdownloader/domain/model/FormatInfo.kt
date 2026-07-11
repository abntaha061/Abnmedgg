package com.example.ytdownloader.domain.model

data class FormatInfo(
    val formatId: String,
    val ext: String,
    val resolution: String?,
    val filesize: Long?,
    val fps: Int?,
    val vcodec: String?,
    val acodec: String?,
    val url: String,
    val note: String?,
    val isVideo: Boolean,
    val isAudio: Boolean
)
