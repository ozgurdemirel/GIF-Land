package club.ozgur.gifland.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import club.ozgur.gifland.domain.model.*
import club.ozgur.gifland.domain.service.RecordingService
import club.ozgur.gifland.presentation.viewmodel.MainViewModel
import club.ozgur.gifland.ui.components.CaptureArea
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Integrated version of MainScreen that uses the new architecture
 */
object MainScreenIntegrated : Screen {

    @Composable
    override fun Content() {
        val mainViewModel = koinInject<MainViewModel>()
        val recordingService = koinInject<RecordingService>()
        val scope = rememberCoroutineScope()

        // Observe app state
        val appState by mainViewModel.appState.collectAsState()
        val settings by mainViewModel.settings.collectAsState()

        // Local UI state
        var selectedArea by remember { mutableStateOf<CaptureArea?>(null) }

        MainScreenContent(
            appState = appState,
            settings = settings,
            selectedArea = selectedArea,
            onAreaSelected = { area ->
                selectedArea = area
            },
            onStartRecording = {
                scope.launch {
                    // Convert CaptureArea to CaptureRegion if needed
                    val captureRegion = selectedArea?.let {
                        CaptureRegion(it.x, it.y, it.width, it.height)
                    }

                    if (captureRegion != null) {
                        mainViewModel.startRecording(captureRegion)
                        // The actual recording will be started by RecordingService
                        // when it detects the state change
                        recordingService.startRecording(selectedArea)
                    } else {
                        mainViewModel.quickCapture()
                        recordingService.startRecording(null)
                    }
                }
            },
            onStopRecording = {
                scope.launch {
                    recordingService.stopRecording()
                }
            },
            onPauseRecording = {
                scope.launch {
                    recordingService.pauseRecording()
                }
            },
            onOpenSettings = {
                mainViewModel.openSettings()
            },
            onClearError = {
                mainViewModel.clearError()
            }
        )
    }
}

@Composable
private fun MainScreenContent(
    appState: AppState,
    settings: AppSettings,
    selectedArea: CaptureArea?,
    onAreaSelected: (CaptureArea?) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearError: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (appState) {
                is AppState.Initializing -> {
                    CircularProgressIndicator()
                    Text("Initializing...", modifier = Modifier.padding(top = 8.dp))
                }

                is AppState.Idle -> {
                    IdleScreenContent(
                        settings = settings,
                        selectedArea = selectedArea,
                        recentRecordings = appState.recentRecordings,
                        onAreaSelected = onAreaSelected,
                        onStartRecording = onStartRecording,
                        onOpenSettings = onOpenSettings
                    )
                }

                is AppState.PreparingRecording -> {
                    Text("Preparing to record...")
                    if (appState.countdown != null) {
                        Text("Starting in ${appState.countdown}...")
                    }
                }

                is AppState.Recording -> {
                    RecordingScreenContent(
                        session = appState.session,
                        isPaused = appState.isPaused,
                        captureMethod = appState.captureMethod,
                        onPause = onPauseRecording,
                        onStop = onStopRecording
                    )
                }

                is AppState.Processing -> {
                    ProcessingScreenContent(
                        progress = appState.progress,
                        stage = appState.stage
                    )
                }

                is AppState.Error -> {
                    ErrorScreenContent(
                        message = appState.message,
                        recoverable = appState.recoverable,
                        onRetry = if (appState.recoverable) onClearError else null,
                        onDismiss = onClearError
                    )
                }

                else -> {
                    // Handle other states
                    Text("State: ${appState::class.simpleName}")
                }
            }
        }
    }
}

@Composable
private fun IdleScreenContent(
    settings: AppSettings,
    selectedArea: CaptureArea?,
    recentRecordings: List<MediaItem>,
    onAreaSelected: (CaptureArea?) -> Unit,
    onStartRecording: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "GIF/WebP/MP4 Recorder",
            style = MaterialTheme.typography.headlineMedium
        )

        // Area selection
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Capture Area", style = MaterialTheme.typography.titleMedium)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onAreaSelected(null) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Full Screen")
                    }

                    Button(
                        onClick = {
                            // TODO: Show area selector
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Select Area")
                    }
                }

                if (selectedArea != null) {
                    Text(
                        "Selected: ${selectedArea.width} x ${selectedArea.height}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Settings summary
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Settings", style = MaterialTheme.typography.titleMedium)
                Text("Format: ${settings.defaultFormat}")
                Text("FPS: ${settings.defaultFps}")
                Text("Quality: ${settings.defaultQuality}")
                Text("Max Duration: ${settings.defaultMaxDuration}s")

                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Settings")
                }
            }
        }

        // Start button
        Button(
            onClick = onStartRecording,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Start Recording")
        }

        // Recent recordings
        if (recentRecordings.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Recent Recordings", style = MaterialTheme.typography.titleMedium)
                    recentRecordings.take(5).forEach { media ->
                        Text(
                            "${media.filePath.substringAfterLast("/")} - ${media.format}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingScreenContent(
    session: RecordingSession,
    isPaused: Boolean,
    captureMethod: CaptureMethod,
    onPause: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            if (isPaused) "Recording Paused" else "Recording...",
            style = MaterialTheme.typography.headlineMedium
        )

        // Stats
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Frames: ${session.frameCount}")
                Text("Duration: ${session.duration}s / ${session.maxDuration}s")
                Text("Size: ${formatFileSize(session.estimatedSize)}")
                Text("Method: $captureMethod")
            }
        }

        // Progress
        LinearProgressIndicator(
            progress = { session.duration.toFloat() / session.maxDuration },
            modifier = Modifier.fillMaxWidth()
        )

        // Controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPause,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isPaused) "Resume" else "Pause")
            }

            Button(
                onClick = onStop,
                modifier = Modifier.weight(1f)
            ) {
                Text("Stop")
            }
        }
    }
}

@Composable
private fun ProcessingScreenContent(
    progress: Float,
    stage: ProcessingStage
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Processing...",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            when (stage) {
                ProcessingStage.CollectingFrames -> "Collecting frames..."
                ProcessingStage.Encoding -> "Encoding..."
                ProcessingStage.Optimizing -> "Optimizing..."
                ProcessingStage.Saving -> "Saving..."
                ProcessingStage.GeneratingThumbnail -> "Generating thumbnail..."
                ProcessingStage.UpdatingIndex -> "Updating index..."
            }
        )

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )

        Text("${(progress * 100).toInt()}%")
    }
}

@Composable
private fun ErrorScreenContent(
    message: String,
    recoverable: Boolean,
    onRetry: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Error",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Text(
                message,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onRetry != null && recoverable) {
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}