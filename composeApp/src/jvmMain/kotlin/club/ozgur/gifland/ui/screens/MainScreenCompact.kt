package club.ozgur.gifland.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import club.ozgur.gifland.LocalRecorder
import club.ozgur.gifland.util.Log
import club.ozgur.gifland.core.OutputFormat
import club.ozgur.gifland.ui.components.AreaSelector
import club.ozgur.gifland.ui.components.CaptureArea
import club.ozgur.gifland.util.openFileLocation
import kotlinx.coroutines.launch

/**
 * Compact Main Screen - Minimal floating window style
 * Small, focused interface with essential controls only
 */
object MainScreenCompact : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val recorder = LocalRecorder.current

        val recordingState by recorder.state.collectAsState()
        val currentSettings by recorder.settingsFlow.collectAsState()
        var selectedArea by remember { mutableStateOf<CaptureArea?>(null) }

        val lastSavedFile by recorder.lastSavedFile.collectAsState()
        val lastError by recorder.lastError.collectAsState()

        // Pulse animation for record button
        val pulseAnimation = rememberInfiniteTransition()
        val pulse by pulseAnimation.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        // Navigate to recording screen when recording starts
        LaunchedEffect(recordingState.isRecording) {
            if (recordingState.isRecording) {
                navigator.push(RecordingScreen)
            }
        }

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

        // Compact floating window style UI
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .widthIn(max = 320.dp) // Compact width
                        .shadow(12.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Compact Header
                        Text(
                            text = "🎬 Screen Recorder",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333)
                        )

                        // Status indicator
                        AnimatedContent(
                            targetState = when {
                                recordingState.isSaving -> "Processing..."
                                lastSavedFile != null -> "✅ Ready"
                                else -> "Ready to record"
                            },
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            }
                        ) { status ->
                            Text(
                                text = status,
                                fontSize = 13.sp,
                                color = when {
                                    recordingState.isSaving -> Color(0xFF4FC3F7)
                                    lastSavedFile != null -> Color(0xFF66BB6A)
                                    else -> Color(0xFF666666)
                                },
                                textAlign = TextAlign.Center
                            )
                        }

                        Divider(color = Color(0xFFEEEEEE))

                        // Main Record Button
                        Box(
                            modifier = Modifier.size(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Outer ring
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scale(if (!recordingState.isSaving) pulse else 1f)
                                    .border(
                                        width = 3.dp,
                                        color = Color(0xFFFF5252).copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                            )

                            // Record button
                            Button(
                                onClick = {
                                    recorder.reset()
                                    recorder.startRecording(
                                        area = selectedArea,
                                        onUpdate = { /* Handled by StateFlow */ },
                                        onComplete = { result ->
                                            result.onFailure { error ->
                                                Log.e("MainScreen", "Recording failed", error)
                                            }
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .size(80.dp)
                                    .alpha(if (recordingState.isSaving) 0.5f else 1f),
                                enabled = !recordingState.isSaving,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF5252),
                                    disabledContainerColor = Color(0xFFFF5252).copy(alpha = 0.4f)
                                ),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 6.dp,
                                    pressedElevation = 2.dp
                                )
                            ) {
                                if (recordingState.isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = "⏺",
                                        fontSize = 32.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Compact area selection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Full Screen
                            TextButton(
                                onClick = {
                                    selectedArea = null
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (selectedArea == null)
                                        Color(0xFF667EEA) else Color(0xFF999999)
                                )
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("🖥️", fontSize = 20.sp)
                                    Text("Full", fontSize = 11.sp)
                                }
                            }

                            // Select Area
                            TextButton(
                                onClick = {
                                    val selector = AreaSelector { area ->
                                        selectedArea = area
                                    }
                                    selector.isVisible = true
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (selectedArea != null)
                                        Color(0xFF667EEA) else Color(0xFF999999)
                                )
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("✂️", fontSize = 20.sp)
                                    Text("Area", fontSize = 11.sp)
                                }
                            }
                        }

                        // Format selector - minimal
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FormatChip(
                                format = OutputFormat.GIF,
                                currentFormat = currentSettings.format,
                                onClick = {
                                    recorder.settings = recorder.settings.copy(format = OutputFormat.GIF)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FormatChip(
                                format = OutputFormat.WEBP,
                                currentFormat = currentSettings.format,
                                onClick = {
                                    recorder.settings = recorder.settings.copy(format = OutputFormat.WEBP)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FormatChip(
                                format = OutputFormat.MP4,
                                currentFormat = currentSettings.format,
                                onClick = {
                                    recorder.settings = recorder.settings.copy(format = OutputFormat.MP4)
                                }
                            )
                        }

                        // Bottom actions - minimal
                        AnimatedVisibility(
                            visible = lastSavedFile != null && !recordingState.isSaving,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Divider(color = Color(0xFFEEEEEE))
                                TextButton(
                                    onClick = {
                                        lastSavedFile?.let { openFileLocation(it) }
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = Color(0xFF4CAF50)
                                    )
                                ) {
                                    Text("📂 Open Last Recording", fontSize = 12.sp)
                                }
                            }
                        }

                        // Settings button - minimal
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Settings
                            IconButton(
                                onClick = { navigator.push(IntegratedSettingsScreen) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("⚙️", fontSize = 16.sp)
                            }

                            // Duration indicator
                            Text(
                                text = "${currentSettings.maxDuration}s",
                                fontSize = 12.sp,
                                color = Color(0xFF999999)
                            )

                            // Quality indicator
                            Text(
                                text = "Q${currentSettings.quality}",
                                fontSize = 12.sp,
                                color = Color(0xFF999999)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FormatChip(
        format: OutputFormat,
        currentFormat: OutputFormat,
        onClick: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .clickable { onClick() },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (format == currentFormat)
                    Color(0xFF667EEA) else Color(0xFFF5F5F5)
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Text(
                text = format.name,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 11.sp,
                fontWeight = if (format == currentFormat) FontWeight.Bold else FontWeight.Normal,
                color = if (format == currentFormat) Color.White else Color(0xFF666666)
            )
        }
    }
}