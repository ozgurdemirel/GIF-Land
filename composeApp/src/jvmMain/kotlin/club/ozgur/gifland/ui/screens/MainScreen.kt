package club.ozgur.gifland.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import club.ozgur.gifland.LocalRecorder
import club.ozgur.gifland.util.Log
import club.ozgur.gifland.core.OutputFormat
import club.ozgur.gifland.core.RecordingState
import club.ozgur.gifland.core.Recorder
import club.ozgur.gifland.core.RecorderSettings
import club.ozgur.gifland.ui.components.AreaSelector
import club.ozgur.gifland.ui.components.CaptureArea
import club.ozgur.gifland.util.openFileLocation
import kotlinx.coroutines.launch
import java.io.File

object MainScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val recorder = LocalRecorder.current

        val recordingState by recorder.state.collectAsState()
        val currentSettings by recorder.settingsFlow.collectAsState()
        var message by remember { mutableStateOf("Ready") }
        var selectedArea by remember { mutableStateOf<CaptureArea?>(null) }

        // Local state for UI updates
        var format by remember { mutableStateOf(currentSettings.format) }
        var maxDuration by remember { mutableStateOf(currentSettings.maxDuration) }

        // Update local state when settings change
        LaunchedEffect(currentSettings) {
            format = currentSettings.format
            maxDuration = currentSettings.maxDuration
        }
        val lastSavedFile by recorder.lastSavedFile.collectAsState()
        val lastError by recorder.lastError.collectAsState()

        // Debug logging for lastSavedFile state
        LaunchedEffect(lastSavedFile) {
            Log.d("MainScreen", "lastSavedFile changed to: ${lastSavedFile?.absolutePath ?: "null"}")
        }


        // Kayıt başladığında RecordingScreen'e geç
        LaunchedEffect(recordingState.isRecording) {
            if (recordingState.isRecording) {
                navigator.push(RecordingScreen)
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF8FAFC)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (lastError != null) {
                    AlertDialog(
                        onDismissRequest = { recorder.clearError() },
                        confirmButton = {
                            TextButton(onClick = { recorder.clearError() }) {
                                Text("OK")
                            }
                        },
                        title = { Text("Hata") },
                        text = { Text(lastError ?: "Bilinmeyen hata") },
                        properties = DialogProperties(dismissOnClickOutside = true)
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Compact Header - even smaller during recording
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (recordingState.isRecording) 60.dp else 80.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (recordingState.isRecording) 1.dp else 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = if (recordingState.isRecording)
                                            listOf(Color(0xFFFF6B6B), Color(0xFFFFE66D))
                                        else
                                            listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                                    )
                                )
                                .padding(horizontal = 12.dp, vertical = if (recordingState.isRecording) 8.dp else 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (recordingState.isRecording) {
                                        // Minimal info during recording
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "\uD83D\uDD34 REC",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "${recordingState.duration}s / ${recorder.settings.maxDuration}s",
                                                color = Color.White.copy(0.9f),
                                                fontSize = 14.sp
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "\uD83C\uDFA5 Quick Recorder",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp
                                        )
                                        Text(
                                            text = "GIF/WebP/MP4 Screen Capture",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(0.9f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                // Smaller recording indicator
                                if (recordingState.isRecording) {
                                    CircularProgressIndicator(
                                        progress = { recordingState.duration / recorder.settings.maxDuration.toFloat() },
                                        modifier = Modifier.size(36.dp),
                                        color = Color.White,
                                        strokeWidth = 3.dp,
                                        trackColor = Color.White.copy(0.3f)
                                    )
                                }
                            }
                        }
                    }

                    // Compact Status Card
                    AnimatedContent(
                        targetState = recordingState.isRecording to recordingState.isSaving,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                        }
                    ) { (isRecording, isSaving) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isSaving -> Color(0xFF4FC3F7)
                                    isRecording -> Color(0xFFFF7043)
                                    else -> Color(0xFF66BB6A)
                                }.copy(alpha = 0.1f)
                            ),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isSaving -> Color(0xFF4FC3F7)
                                    isRecording -> Color(0xFFFF7043)
                                    lastError != null -> Color(0xFFFF5252)
                                    else -> Color(0xFF66BB6A)
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                when {
                                    isRecording -> {
                                        // Ultra compact during recording
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Frames: ${recordingState.frameCount}", fontSize = 12.sp)
                                            Text(String.format("%.1fMB", recordingState.estimatedSize / (1024.0 * 1024.0)), fontSize = 12.sp)
                                        }
                                    }
                                    isSaving -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = Color(0xFF4FC3F7),
                                                strokeWidth = 2.dp
                                            )
                                            Text(
                                                "Saving ${format.name}... ${recordingState.saveProgress}%",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    lastError != null -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                "⚠",
                                                fontSize = 20.sp,
                                                color = Color(0xFFFF5252),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                lastError ?: "Error",
                                                fontSize = 14.sp,
                                                color = Color(0xFFB00020),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    else -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                "✓",
                                                fontSize = 20.sp,
                                                color = Color(0xFF66BB6A),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                message,
                                                fontSize = 14.sp,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            // lastSavedFile varsa her zaman göster
                                            if (lastSavedFile != null) {
                                                Log.d("MainScreen", "Showing Open Folder button for: ${lastSavedFile!!.absolutePath}")
                                                Button(
                                                    onClick = {
                                                        Log.d("MainScreen", "Open Folder clicked for: ${lastSavedFile!!.absolutePath}")
                                                        openFileLocation(lastSavedFile!!)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF4CAF50)
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = Modifier.padding(start = 8.dp)
                                                ) {
                                                    Text("📂 Open Folder", fontSize = 13.sp, color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Area Selection
                    if (!recordingState.isRecording) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (recordingState.isSaving) 0.5f else 1f),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "Capture Area",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CompactSelectionButton(
                                        text = "Full Screen",
                                        icon = "🖥",
                                        selected = selectedArea == null,
                                        onClick = {
                                            if (!recordingState.isSaving) {
                                                selectedArea = null
                                                message = "Full screen"
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )

                                    CompactSelectionButton(
                                        text = "Select Area",
                                        icon = "✂",
                                        selected = selectedArea != null,
                                        onClick = {
                                            if (!recordingState.isSaving) {
                                                val selector = AreaSelector { area ->
                                                    selectedArea = area
                                                    message = area?.let { "${it.width} x ${it.height}" } ?: "Cancelled"
                                                }
                                                selector.isVisible = true
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                if (selectedArea != null) {
                                    Text(
                                        "Selected: ${selectedArea!!.width} x ${selectedArea!!.height}",
                                        color = Color(0xFF667EEA),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }

                        // Settings in single row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Format Card
                            CompactSettingsCard(
                                title = "Format",
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    CompactChip("GIF", format == OutputFormat.GIF) {
                                        format = OutputFormat.GIF
                                        recorder.settings = recorder.settings.copy(format = format)
                                    }
                                    CompactChip("WebP", format == OutputFormat.WEBP) {
                                        format = OutputFormat.WEBP
                                        recorder.settings = recorder.settings.copy(format = format)
                                    }
                                    CompactChip("MP4", format == OutputFormat.MP4) {
                                        format = OutputFormat.MP4
                                        recorder.settings = recorder.settings.copy(format = format)
                                    }
                                }
                            }

                            // Duration Card
                            CompactSettingsCard(
                                title = "Duration",
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    CompactChip("30s", maxDuration == 30) {
                                        maxDuration = 30
                                        recorder.settings = recorder.settings.copy(maxDuration = maxDuration)
                                    }
                                    CompactChip("1m", maxDuration == 60) {
                                        maxDuration = 60
                                        recorder.settings = recorder.settings.copy(maxDuration = maxDuration)
                                    }
                                    CompactChip("2m", maxDuration == 120) {
                                        maxDuration = 120
                                        recorder.settings = recorder.settings.copy(maxDuration = maxDuration)
                                    }
                                    CompactChip("5m", maxDuration == 300) {
                                        maxDuration = 300
                                        recorder.settings = recorder.settings.copy(maxDuration = maxDuration)
                                    }
                                }
                            }
                        }

                        // Quality Settings
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    navigator.push(QualitySettingsScreen(
                                        settings = recorder.settings,
                                        onSettingsChange = { newSettings ->
                                            recorder.settings = newSettings
                                            format = newSettings.format
                                            maxDuration = newSettings.maxDuration
                                        }
                                    ))
                                },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("⚙", fontSize = 18.sp, color = Color(0xFF667EEA))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Quality", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                }
                                Text(
                                    "FPS: ${recorder.settings.fps} • Q: ${recorder.settings.quality}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Action Buttons - more compact during recording
                    Row(
                        modifier = Modifier.padding(vertical = if (recordingState.isRecording) 4.dp else 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!recordingState.isRecording) {
                            Button(
                                onClick = {
                                    // Reset state before starting new recording to clean up old data
                                    // But preserve lastSavedFile which is handled separately
                                    recorder.reset()

                                    recorder.startRecording(
                                        area = selectedArea,
                                        onUpdate = { state ->
                                            // State updates handled by StateFlow
                                        },
                                        onComplete = { result ->
                                            // Bu callback max duration dolunca veya hata olunca çağrılıyor
                                            result.onSuccess { file ->
                                                message = "Saved: ${file.name}"
                                                // Don't reset here - preserve lastSavedFile
                                                Log.d("MainScreen", "Max duration reached, file saved: ${file.name}")
                                            }.onFailure { error ->
                                                Log.e("MainScreen", "Recording failed", error)
                                                message = "Error!"
                                                // Don't reset on error - preserve state for debugging
                                            }
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .height(44.dp)
                                    .weight(1f),
                                enabled = !recordingState.isSaving, // Saving sırasında disabled
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF5252),
                                    disabledContainerColor = Color(0xFFFF5252).copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(22.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (recordingState.isSaving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Text("Saving...", fontSize = 15.sp, color = Color.White.copy(alpha = 0.7f))
                                    } else {
                                        Text("⏺ Start Recording", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            // Smaller buttons during recording
                            Button(
                                onClick = { recorder.pauseRecording() },
                                modifier = Modifier
                                    .height(36.dp)
                                    .weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text(if (recordingState.isPaused) "▶" else "⏸", fontSize = 18.sp)
                            }

                            Spacer(Modifier.width(4.dp))

                            Button(
                                onClick = {
                                    scope.launch {
                                        recorder.stopRecording()
                                            .onSuccess { file ->
                                                message = "Saved: ${file.name}"
                                                // Don't reset immediately - preserve lastSavedFile
                                                // Reset will be called when starting a new recording
                                                Log.d("MainScreen", "Recording saved, preserving state for Open Folder button")
                                            }
                                            .onFailure { error ->
                                                message = "Error: ${error.message}"
                                                Log.e("MainScreen", "Stop button: Save failed", error)
                                                // Don't reset on error - preserve state for debugging
                                            }
                                    }
                                },
                                modifier = Modifier
                                    .height(36.dp)
                                    .weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text("⏹ Stop", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Footer - hide during recording to save space
                    if (!recordingState.isRecording) {
                        Text(
                            text = "Made with ❤️ by @ozgurdemirel",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

    @Composable
    private fun CompactSelectionButton(
        text: String,
        icon: String,
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Card(
            modifier = modifier
                .height(56.dp)
                .clickable { onClick() },
            colors = CardDefaults.cardColors(
                containerColor = if (selected) Color(0xFF667EEA) else Color(0xFFF7F9FC)
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (selected) 2.dp else 0.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    icon,
                    fontSize = 20.sp,
                    color = if (selected) Color.White else Color(0xFF667EEA)
                )
                Text(
                    text,
                    color = if (selected) Color.White else Color.Black,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }

    @Composable
    private fun CompactSettingsCard(
        title: String,
        modifier: Modifier = Modifier,
        content: @Composable ColumnScope.() -> Unit
    ) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                content()
            }
        }
    }

    @Composable
    private fun CompactChip(text: String, selected: Boolean, onClick: () -> Unit) {
        Card(
            modifier = Modifier
                .height(28.dp)
                .clickable { onClick() },
            colors = CardDefaults.cardColors(
                containerColor = if (selected) Color(0xFF667EEA) else Color(0xFFF7F9FC)
            ),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text,
                    fontSize = 11.sp,
                    color = if (selected) Color.White else Color.Black,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }