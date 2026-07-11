package com.example.ytdownloader.domain.model

data class VideoInfo(
    val id: String,
    val title: String,
    val description: String?,
    val uploader: String?,
    val duration: Int?,
    val thumbnail: String?,
    val webpageUrl: String,
    val formats: List<FormatInfo>,
    val subtitles: List<SubtitleInfo>
)
