package com.example.ytdownloader.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.example.ytdownloader.YTDLApplication
import com.example.ytdownloader.data.worker.DownloadWorker
import com.example.ytdownloader.domain.model.DownloadStatus
import com.example.ytdownloader.domain.model.DownloadTask
import com.example.ytdownloader.domain.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

sealed interface ExtractionUiState {
    object Idle : ExtractionUiState
    object Loading : ExtractionUiState
    data class Success(val videoInfo: VideoInfo) : ExtractionUiState
    data class Error(val message: String) : ExtractionUiState
}

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as YTDLApplication).container
    private val fetchVideoInfoUseCase = container.fetchVideoInfoUseCase
    private val startDownloadUseCase = container.startDownloadUseCase
    private val getDownloadHistoryUseCase = container.getDownloadHistoryUseCase
    private val userPreferences = container.userPreferences
    private val repository = container.downloadRepository

    private val workManager = WorkManager.getInstance(application)

    private val _extractionState = MutableStateFlow<ExtractionUiState>(ExtractionUiState.Idle)
    val extractionState: StateFlow<ExtractionUiState> = _extractionState.asStateFlow()

    val downloads: StateFlow<List<DownloadTask>> = getDownloadHistoryUseCase()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadFolderUri: StateFlow<String?> = userPreferences.downloadUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isIncognito: StateFlow<Boolean> = userPreferences.isIncognitoMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val defaultQuality: StateFlow<String> = userPreferences.defaultQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "1080p")

    fun extractUrl(url: String) {
        if (url.isBlank()) {
            _extractionState.value = ExtractionUiState.Error("Please enter a valid link")
            return
        }

        viewModelScope.launch {
            _extractionState.value = ExtractionUiState.Loading
            try {
                val info = fetchVideoInfoUseCase(url)
                _extractionState.value = ExtractionUiState.Success(info)
            } catch (e: Exception) {
                _extractionState.value = ExtractionUiState.Error(e.localizedMessage ?: "Failed to parse link")
            }
        }
    }

    fun clearExtractionState() {
        _extractionState.value = ExtractionUiState.Idle
    }

    fun toggleIncognito(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setIncognitoMode(enabled)
        }
    }

    fun setDownloadFolder(uriString: String) {
        viewModelScope.launch {
            userPreferences.setDownloadUri(uriString)
        }
    }

    fun setDefaultQuality(quality: String) {
        viewModelScope.launch {
            userPreferences.setDefaultQuality(quality)
        }
    }

    fun startDownload(
        videoInfo: VideoInfo,
        selectedVideoId: String?,
        selectedAudioId: String?,
        subtitleLang: String?,
        scheduledTimestamp: Long? = null
    ) {
        viewModelScope.launch {
            val isIncognitoActive = isIncognito.value
            val task = DownloadTask(
                id = videoInfo.id,
                url = videoInfo.webpageUrl,
                title = videoInfo.title,
                thumbnail = videoInfo.thumbnail,
                selectedVideoFormatId = selectedVideoId,
                selectedAudioFormatId = selectedAudioId,
                subtitleLanguage = subtitleLang,
                status = DownloadStatus.QUEUED,
                progress = 0f,
                downloadedBytes = 0L,
                totalBytes = 0L,
                filePath = null,
                createdAt = System.currentTimeMillis(),
                scheduledTime = scheduledTimestamp,
                isIncognito = isIncognitoActive
            )

            startDownloadUseCase(task)

            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(DownloadWorker.KEY_TASK_ID to task.id))
                .addTag(task.id)

            if (scheduledTimestamp != null) {
                val delayMs = scheduledTimestamp - System.currentTimeMillis()
                if (delayMs > 0) {
                    workRequest.setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                }
            }

            workManager.enqueueUniqueWork(
                task.id,
                ExistingWorkPolicy.REPLACE,
                workRequest.build()
            )
        }
    }

    fun cancelDownload(taskId: String) {
        viewModelScope.launch {
            workManager.cancelUniqueWork(taskId)
            val task = repository.getDownloadById(taskId)
            if (task != null) {
                repository.updateDownload(task.copy(status = DownloadStatus.FAILED))
            }
        }
    }

    fun deleteHistoryItem(taskId: String) {
        viewModelScope.launch {
            repository.deleteDownloadById(taskId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
