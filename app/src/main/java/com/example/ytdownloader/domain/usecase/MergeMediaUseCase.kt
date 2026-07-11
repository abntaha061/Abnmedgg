package com.example.ytdownloader.domain.usecase

import com.example.ytdownloader.util.FFmpegHelper

class MergeMediaUseCase {
    suspend operator fun invoke(videoPath: String, audioPath: String, outputPath: String): Boolean {
        return FFmpegHelper.mergeVideoAndAudio(videoPath, audioPath, outputPath)
    }
}
