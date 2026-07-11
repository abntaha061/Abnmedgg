package com.example.ytdownloader.presentation.home

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytdownloader.presentation.DownloadViewModel
import com.example.ytdownloader.presentation.ExtractionUiState
import com.example.ytdownloader.presentation.download.DownloadSheet
import com.example.ytdownloader.util.PermissionHelper

data class DownloadParams(
    val videoInfo: com.example.ytdownloader.domain.model.VideoInfo,
    val videoId: String?,
    val audioId: String?,
    val subLang: String?,
    val scheduledTime: Long?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DownloadViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf("") }
    val extractionState by viewModel.extractionState.collectAsState()

    var showPermissionExplanation by remember { mutableStateOf(false) }
    var pendingDownloadParams by remember { mutableStateOf<DownloadParams?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val allGranted = permissionsMap.values.all { it }
        if (allGranted) {
            pendingDownloadParams?.let { params ->
                viewModel.startDownload(params.videoInfo, params.videoId, params.audioId, params.subLang, params.scheduledTime)
                pendingDownloadParams = null
            }
        } else {
            android.widget.Toast.makeText(
                context,
                "الرجاء الموافقة على صلاحيات الوصول لحفظ الملفات\nPlease grant storage permission to save files",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    // Clipboard function
    fun getClipboardText(): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text
            if (text != null) return text.toString()
        }
        return ""
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Elegant Cosmic Branding Header
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "App Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "YTDLnis Downloader",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Download, schedule, and merge media flawlessly from modern sites with audio/video format options",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // URL input field with trailing/leading icons
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("url_input_field"),
                label = { Text("Paste Video or Audio Link Here") },
                placeholder = { Text("https://youtube.com/watch?v=...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Link icon",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val pasted = getClipboardText()
                            if (pasted.isNotEmpty()) {
                                urlInput = pasted
                            }
                        },
                        modifier = Modifier.testTag("paste_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste from clipboard",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Extract Button
            Button(
                onClick = {
                    viewModel.extractUrl(urlInput)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("analyze_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Analyze Link",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Status State Display
            AnimatedContent(
                targetState = extractionState,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "Extraction State"
            ) { state ->
                when (state) {
                    is ExtractionUiState.Loading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Extracting media streams and info...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    is ExtractionUiState.Error -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Error Icon",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    else -> {
                        // Empty spacer when idle
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }

    // Launch BottomSheet if Extraction is Success
    if (extractionState is ExtractionUiState.Success) {
        val videoInfo = (extractionState as ExtractionUiState.Success).videoInfo
        DownloadSheet(
            videoInfo = videoInfo,
            onDismiss = { viewModel.clearExtractionState() },
            onStartDownload = { videoId, audioId, subLang, scheduledTime ->
                val params = DownloadParams(videoInfo, videoId, audioId, subLang, scheduledTime)
                if (PermissionHelper.hasPermissions(context)) {
                    viewModel.startDownload(videoInfo, videoId, audioId, subLang, scheduledTime)
                } else {
                    pendingDownloadParams = params
                    showPermissionExplanation = true
                }
            }
        )
    }

    if (showPermissionExplanation) {
        AlertDialog(
            onDismissRequest = { 
                showPermissionExplanation = false 
                pendingDownloadParams = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "إذن الوصول للملفات مطلوب\nStorage Permission Required",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    lineHeight = 24.sp
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "يحتاج التطبيق إلى إذن الوصول للملفات لحفظ مقاطع الفيديو والملفات الصوتية التي تقوم بتنزيلها إلى جهازك بشكل سليم.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The app requires storage permissions to successfully save downloaded video and audio files to your device.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionExplanation = false
                        permissionLauncher.launch(
                            PermissionHelper.getRequiredPermissions().toTypedArray()
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("سماح / Grant")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionExplanation = false
                        pendingDownloadParams = null
                    }
                ) {
                    Text("إلغاء / Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        )
    }
}
