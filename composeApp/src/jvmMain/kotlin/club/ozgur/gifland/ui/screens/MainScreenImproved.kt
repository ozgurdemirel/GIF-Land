package club.ozgur.gifland.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import club.ozgur.gifland.encoder.FFmpegDebugManager
import kotlinx.coroutines.launch

/**
 * Improved Main Screen with better spacing and less cramped UI
 */
object MainScreenImproved : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val recorder = LocalRecorder.current

        val recordingState by recorder.state.collectAsState()
        val currentSettings by recorder.settingsFlow.collectAsState()
        var message by remember { mutableStateOf("Ready to record") }
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

        // Navigate to recording screen when recording starts
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
                // Error dialog
                if (lastError != null) {
                    AlertDialog(
                        onDismissRequest = { recorder.clearError() },
                        confirmButton = {
                            TextButton(onClick = { recorder.clearError() }) {
                                Text("OK")
                            }
                        },
                        title = { Text("Error") },
                        text = { Text(lastError ?: "Unknown error") },
                        properties = DialogProperties(dismissOnClickOutside = true)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 800.dp) // Wider max width for better spacing
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 24.dp, vertical = 16.dp) // More padding
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp) // More spacing between elements
                ) {
                    // Header Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp), // Taller header
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = if (recordingState.isSaving)
                                            listOf(Color(0xFF4FC3F7), Color(0xFF29B6F6))
                                        else
                                            listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                                    )
                                )
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column {
                                Text(
                                    text = "🎬 GIF/WebP/MP4 Recorder",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (recordingState.isSaving) "Processing..." else "High-quality screen capture",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(0.9f)
                                )
                            }
                        }
                    }

                    // Status Card with more breathing room
                    AnimatedContent(
                        targetState = recordingState.isSaving to lastSavedFile,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                        }
                    ) { (isSaving, savedFile) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp), // Minimum height for consistency
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isSaving -> Color(0xFF4FC3F7).copy(alpha = 0.1f)
                                    savedFile != null -> Color(0xFF66BB6A).copy(alpha = 0.1f)
                                    else -> Color(0xFF66BB6A).copy(alpha = 0.1f)
                                }
                            ),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isSaving -> Color(0xFF4FC3F7)
                                    savedFile != null -> Color(0xFF66BB6A)
                                    else -> Color(0xFF66BB6A)
                                }
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    isSaving -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = Color(0xFF4FC3F7),
                                                strokeWidth = 3.dp
                                            )
                                            Text(
                                                "Saving ${format.name}... ${recordingState.saveProgress}%",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    savedFile != null -> {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Text("✅", fontSize = 24.sp)
                                                Text(
                                                    "Recording saved successfully",
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            Button(
                                                onClick = {
                                                    Log.d("MainScreen", "Open Folder clicked for: ${savedFile.absolutePath}")
                                                    openFileLocation(savedFile)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF4CAF50)
                                                )
                                            ) {
                                                Text("📂 Open Folder", fontSize = 14.sp)
                                            }
                                        }
                                    }
                                    else -> {
                                        Text(
                                            message,
                                            fontSize = 16.sp,
                                            textAlign = TextAlign.Center,
                                            color = Color(0xFF666666)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Capture Area Selection - More spacious
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (recordingState.isSaving) 0.5f else 1f),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "Capture Area",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = Color(0xFF333333)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Full Screen button
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(80.dp)
                                        .clickable(enabled = !recordingState.isSaving) {
                                            selectedArea = null
                                            message = "Full screen selected"
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedArea == null)
                                            Color(0xFF667EEA) else Color(0xFFF7F9FC)
                                    ),
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = if (selectedArea == null) 3.dp else 1.dp
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            "🖥️",
                                            fontSize = 28.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Full Screen",
                                            color = if (selectedArea == null) Color.White else Color.Black,
                                            fontSize = 14.sp,
                                            fontWeight = if (selectedArea == null) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }

                                // Select Area button
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(80.dp)
                                        .clickable(enabled = !recordingState.isSaving) {
                                            val selector = AreaSelector { area ->
                                                selectedArea = area
                                                message = area?.let { "Area: ${it.width} × ${it.height}" }
                                                    ?: "Selection cancelled"
                                            }
                                            selector.isVisible = true
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedArea != null)
                                            Color(0xFF667EEA) else Color(0xFFF7F9FC)
                                    ),
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = if (selectedArea != null) 3.dp else 1.dp
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            "✂️",
                                            fontSize = 28.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Select Area",
                                            color = if (selectedArea != null) Color.White else Color.Black,
                                            fontSize = 14.sp,
                                            fontWeight = if (selectedArea != null) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }

                            // Show selected area dimensions
                            AnimatedVisibility(visible = selectedArea != null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF667EEA).copy(alpha = 0.1f)
                                    )
                                ) {
                                    Text(
                                        "Selected: ${selectedArea?.width} × ${selectedArea?.height} pixels",
                                        modifier = Modifier.padding(12.dp),
                                        color = Color(0xFF667EEA),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Recording Settings - Better organized
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Text(
                                "Recording Settings",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = Color(0xFF333333)
                            )

                            // Format Selection
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Output Format",
                                    fontSize = 14.sp,
                                    color = Color(0xFF666666),
                                    fontWeight = FontWeight.Medium
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FormatChip("GIF", format == OutputFormat.GIF, Modifier.weight(1f)) {
                                        format = OutputFormat.GIF
                                        recorder.settings = recorder.settings.copy(format = format)
                                    }
                                    FormatChip("WebP", format == OutputFormat.WEBP, Modifier.weight(1f)) {
                                        format = OutputFormat.WEBP
                                        recorder.settings = recorder.settings.copy(format = format)
                                    }
                                    FormatChip("MP4", format == OutputFormat.MP4, Modifier.weight(1f)) {
                                        format = OutputFormat.MP4
                                        recorder.settings = recorder.settings.copy(format = format)
                                    }
                                }
                            }

                            // Duration Selection
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Max Duration",
                                    fontSize = 14.sp,
                                    color = Color(0xFF666666),
                                    fontWeight = FontWeight.Medium
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DurationChip("30s", maxDuration == 30, Modifier.weight(1f)) {
                                        maxDuration = 30
                                        recorder.settings = recorder.settings.copy(maxDuration = maxDuration)
                                    }
                                    DurationChip("1m", maxDuration == 60, Modifier.weight(1f)) {
                                        maxDuration = 60
                                        recorder.settings = recorder.settings.copy(maxDuration = maxDuration)
                                    }
                                    DurationChip("2m", maxDuration == 120, Modifier.weight(1f)) {
                                        maxDuration = 120
                                        recorder.settings = recorder.settings.copy(maxDuration = maxDuration)
                                    }
                                    DurationChip("5m", maxDuration == 300, Modifier.weight(1f)) {
                                        maxDuration = 300
                                        recorder.settings = recorder.settings.copy(maxDuration = maxDuration)
                                    }
                                }
                            }

                            // Quality Settings Button
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navigator.push(IntegratedSettingsScreen)
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFF5F7FA)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("⚙️", fontSize = 20.sp)
                                        Column {
                                            Text(
                                                "Quality & Advanced Settings",
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                "FPS: ${recorder.settings.fps} • Quality: ${recorder.settings.quality}",
                                                fontSize = 13.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                    Text("→", fontSize = 18.sp, color = Color(0xFF667EEA))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Start Recording Button - Prominent and accessible
                    Button(
                        onClick = {
                            recorder.reset()
                            recorder.startRecording(
                                area = selectedArea,
                                onUpdate = { /* Handled by StateFlow */ },
                                onComplete = { result ->
                                    result.onSuccess { file ->
                                        message = "Recording saved!"
                                        Log.d("MainScreen", "Recording saved: ${file.name}")
                                    }.onFailure { error ->
                                        Log.e("MainScreen", "Recording failed", error)
                                        message = "Error: ${error.message}"
                                    }
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        enabled = !recordingState.isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5252),
                            disabledContainerColor = Color(0xFFFF5252).copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(30.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (recordingState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Text("Processing...", fontSize = 18.sp, color = Color.White.copy(alpha = 0.7f))
                            } else {
                                Text("⏺", fontSize = 22.sp)
                                Text("Start Recording", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Footer
                    Text(
                        text = "Press Ctrl+Shift+R to start recording with hotkey",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun FormatChip(
        text: String,
        selected: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        Card(
            modifier = modifier
                .height(42.dp)
                .clickable { onClick() },
            colors = CardDefaults.cardColors(
                containerColor = if (selected) Color(0xFF667EEA) else Color(0xFFF7F9FC)
            ),
            shape = RoundedCornerShape(21.dp),
            elevation = CardDefaults.cardElevation(if (selected) 2.dp else 0.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text,
                    fontSize = 14.sp,
                    color = if (selected) Color.White else Color.Black,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }

    @Composable
    private fun DurationChip(
        text: String,
        selected: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        Card(
            modifier = modifier
                .height(42.dp)
                .clickable { onClick() },
            colors = CardDefaults.cardColors(
                containerColor = if (selected) Color(0xFF667EEA) else Color(0xFFF7F9FC)
            ),
            shape = RoundedCornerShape(21.dp),
            elevation = CardDefaults.cardElevation(if (selected) 2.dp else 0.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text,
                    fontSize = 14.sp,
                    color = if (selected) Color.White else Color.Black,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}