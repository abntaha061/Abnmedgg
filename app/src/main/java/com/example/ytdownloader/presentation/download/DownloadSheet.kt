package com.example.ytdownloader.presentation.download

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ytdownloader.domain.model.FormatInfo
import com.example.ytdownloader.domain.model.VideoInfo
import com.example.ytdownloader.util.FormatHelper
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(
    videoInfo: VideoInfo,
    onDismiss: () -> Unit,
    onStartDownload: (
        selectedVideoId: String?,
        selectedAudioId: String?,
        subtitleLang: String?,
        scheduledTimestamp: Long?
    ) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Download modes: 0 = Video + Audio (merged), 1 = Audio Only
    var selectedTab by remember { mutableStateOf(0) }

    // Video Formats
    val videoFormats = remember(videoInfo) {
        videoInfo.formats.filter { it.isVideo }
    }
    var selectedVideoFormat by remember {
        mutableStateOf(videoFormats.firstOrNull())
    }

    // Audio Formats
    val audioFormats = remember(videoInfo) {
        videoInfo.formats.filter { it.isAudio }
    }
    var selectedAudioFormat by remember {
        mutableStateOf(audioFormats.firstOrNull { it.formatId == "audio_only" || it.formatId.contains("audio") } ?: audioFormats.firstOrNull())
    }

    // Subtitles
    val subtitleOptions = remember(videoInfo) {
        listOf(null) + videoInfo.subtitles.map { it.language }
    }
    var selectedSubtitle by remember { mutableStateOf<String?>(null) }

    // Scheduling
    var isSchedulingEnabled by remember { mutableStateOf(false) }
    val calendar = remember { Calendar.getInstance() }
    var scheduledDateString by remember { mutableStateOf("") }
    var scheduledTimeString by remember { mutableStateOf("") }

    val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, day ->
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month)
        calendar.set(Calendar.DAY_OF_MONTH, day)
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        scheduledDateString = format.format(calendar.time)
    }

    val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        scheduledTimeString = format.format(calendar.time)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
        ) {
            // Title & Thumbnail Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AsyncImage(
                    model = videoInfo.thumbnail,
                    contentDescription = "Video Thumbnail",
                    modifier = Modifier
                        .width(120.dp)
                        .height(68.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = videoInfo.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Uploader: ${videoInfo.uploader ?: "Unknown"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Duration: ${FormatHelper.formatDuration(videoInfo.duration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tabs for Video vs Audio Only
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Video & Audio", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Audio Only", fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Formats Selection
            Text(
                text = "Select Output Format Quality",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedTab == 0) {
                // Video Qualities Card
                videoFormats.forEach { format ->
                    val isSelected = selectedVideoFormat?.formatId == format.formatId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selectedVideoFormat = format },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        ),
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = format.note ?: format.resolution ?: "Standard Video",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Codec: ${format.vcodec ?: "h264"} | Extension: ${format.ext}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = format.filesize?.let { FormatHelper.formatBytes(it) } ?: "Dynamic size",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // Audio Qualities Card
                audioFormats.forEach { format ->
                    val isSelected = selectedAudioFormat?.formatId == format.formatId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selectedAudioFormat = format },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        ),
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = format.note ?: "High Quality Audio",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Codec: ${format.acodec ?: "mp3"} | Extension: ${format.ext}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = format.filesize?.let { FormatHelper.formatBytes(it) } ?: "Dynamic size",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle Selection
            if (videoInfo.subtitles.isNotEmpty()) {
                Text(
                    text = "Subtitles",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subtitleOptions.forEach { lang ->
                        val isSelected = selectedSubtitle == lang
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSubtitle = lang },
                            label = { Text(lang ?: "None") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Scheduling Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Schedule icon",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Schedule Download",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = isSchedulingEnabled,
                    onCheckedChange = { isSchedulingEnabled = it }
                )
            }

            if (isSchedulingEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                dateSetListener,
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (scheduledDateString.isEmpty()) "Choose Date" else scheduledDateString)
                    }

                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                timeSetListener,
                                calendar.get(Calendar.HOUR_OF_DAY),
                                calendar.get(Calendar.MINUTE),
                                true
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (scheduledTimeString.isEmpty()) "Choose Time" else scheduledTimeString)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Button(
                onClick = {
                    val scheduledTimestamp = if (isSchedulingEnabled && scheduledDateString.isNotEmpty() && scheduledTimeString.isNotEmpty()) {
                        calendar.timeInMillis
                    } else {
                        null
                    }
                    
                    onStartDownload(
                        if (selectedTab == 0) selectedVideoFormat?.formatId else null,
                        if (selectedTab == 1) selectedAudioFormat?.formatId else "audio_only",
                        selectedSubtitle,
                        scheduledTimestamp
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = "Download")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSchedulingEnabled) "Schedule Task" else "Download Now",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
