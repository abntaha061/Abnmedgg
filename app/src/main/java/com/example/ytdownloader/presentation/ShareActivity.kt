package com.example.ytdownloader.presentation

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ytdownloader.presentation.download.DownloadSheet
import com.example.ytdownloader.util.PermissionHelper

class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val cleanedUrl = extractUrl(sharedText)

        if (cleanedUrl == null) {
            Toast.makeText(this, "لم يتم العثور على رابط صالح / No valid link found", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            MyApplicationTheme {
                ShareActivityScreen(
                    url = cleanedUrl,
                    onDismiss = { finish() },
                    onDownloadStarted = {
                        Toast.makeText(this, "بدأ التحميل في الخلفية / Download started in background", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }

    private fun extractUrl(text: String): String? {
        val pattern = "(https?://[\\w\\-]+(\\.[\\w\\-]+)+[\\w\\-.,@?^=%&:/~+#]*[\\w\\-@?^=%&/~+#])".toRegex()
        return pattern.find(text)?.value
    }
}

@Composable
fun ShareActivityScreen(
    url: String,
    onDismiss: () -> Unit,
    onDownloadStarted: () -> Unit,
    viewModel: DownloadViewModel = viewModel()
) {
    val extractionState by viewModel.extractionState.collectAsState()

    LaunchedEffect(url) {
        viewModel.extractUrl(url)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .clickable(enabled = false) {} // Prevent click propagation to background
        ) {
            when (val state = extractionState) {
                is ExtractionUiState.Idle, is ExtractionUiState.Loading -> {
                    ExtractionLoadingWidget()
                }
                is ExtractionUiState.Success -> {
                    DownloadSheet(
                        videoInfo = state.videoInfo,
                        onDismiss = onDismiss,
                        onStartDownload = { videoId, audioId, subLang, scheduledTime ->
                            viewModel.startDownload(state.videoInfo, videoId, audioId, subLang, scheduledTime)
                            onDownloadStarted()
                        }
                    )
                }
                is ExtractionUiState.Error -> {
                    ExtractionErrorWidget(
                        message = state.message,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
fun ExtractionLoadingWidget() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Downloading,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )

            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )

            Text(
                text = "جاري تحليل الرابط... / Analyzing link...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "يرجى الانتظار، جاري جلب الجودات والخيارات المتاحة\nPlease wait while retrieving formats and details",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ExtractionErrorWidget(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )

            Text(
                text = "فشل تحليل الرابط / Extraction Failed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("إغلاق / Close")
            }
        }
    }
}
