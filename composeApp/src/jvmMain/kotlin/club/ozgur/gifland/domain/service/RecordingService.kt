package club.ozgur.gifland.domain.service

import club.ozgur.gifland.core.Recorder
import club.ozgur.gifland.core.RecorderSettings
import club.ozgur.gifland.domain.model.*
import club.ozgur.gifland.domain.repository.StateRepository
import club.ozgur.gifland.domain.repository.SettingsRepository
import club.ozgur.gifland.ui.components.CaptureArea
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.File

/**
 * Service that bridges the existing Recorder with the new state-driven architecture
 */
class RecordingService(
    private val stateRepository: StateRepository,
    private val settingsRepository: SettingsRepository
) {
    private val recorder = Recorder()
    private var recordingJob: Job? = null

    init {
        // Sync recorder settings with app settings
        CoroutineScope(Dispatchers.Default).launch {
            settingsRepository.settingsFlow.collect { appSettings ->
                recorder.settings = RecorderSettings(
                    fps = appSettings.defaultFps,
                    quality = appSettings.defaultQuality,
                    format = mapOutputFormat(appSettings.defaultFormat),
                    maxDuration = appSettings.defaultMaxDuration,
                    scale = appSettings.captureScale,
                    fastGifPreview = false // TODO: Add to AppSettings if needed
                )
            }
        }
    }

    suspend fun startRecording(captureArea: CaptureArea? = null) {
        val appState = stateRepository.state.value

        // Only start if in idle state
        if (appState !is AppState.Idle) {
            return
        }

        // Get current settings
        val settings = settingsRepository.getCurrentSettings()

        // Map CaptureArea to CaptureRegion
        val captureRegion = captureArea?.let {
            CaptureRegion(it.x, it.y, it.width, it.height)
        } ?: CaptureRegion(0, 0, 1920, 1080) // Default to full screen

        // Start recording through StateRepository
        stateRepository.startRecording(
            captureArea = captureRegion,
            outputFormat = settings.defaultFormat,
            maxDuration = settings.defaultMaxDuration
        )

        // Start actual recording with callbacks
        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            recorder.startRecording(
                area = captureArea,
                onUpdate = { recorderState ->
                    // Update recording progress in app state
                    CoroutineScope(Dispatchers.IO).launch {
                        stateRepository.updateRecordingProgress(
                            frameCount = recorderState.frameCount,
                            duration = recorderState.duration,
                            estimatedSize = recorderState.estimatedSize,
                            captureMethodDetails = recorderState.captureMethodDetails
                        )

                        // Handle pause state
                        if (recorderState.isPaused) {
                            val currentState = stateRepository.state.value
                            if (currentState is AppState.Recording && !currentState.isPaused) {
                                stateRepository.togglePauseRecording()
                            }
                        }
                    }
                },
                onComplete = { result ->
                    CoroutineScope(Dispatchers.IO).launch {
                        handleRecordingComplete(result)
                    }
                }
            )
        }
    }

    suspend fun pauseRecording() {
        recorder.pauseRecording()
        stateRepository.togglePauseRecording()
    }

    suspend fun stopRecording() {
        val currentState = stateRepository.state.value
        if (currentState is AppState.Recording) {
            // Transition to processing state
            stateRepository.stopRecording()

            // Stop and wait for the result
            val result = recorder.stopRecording()
            handleRecordingComplete(result)
        }
    }

    suspend fun cancelRecording() {
        recordingJob?.cancel()
        recorder.reset()
        stateRepository.cancelCurrentOperation()
    }

    private suspend fun handleRecordingComplete(result: Result<File>) {
        result.fold(
            onSuccess = { file ->
                // Get the current recording session
                val currentState = stateRepository.state.value
                val session = when (currentState) {
                    is AppState.Recording -> currentState.session
                    is AppState.Processing -> currentState.session
                    else -> null
                }

                if (session != null) {
                    // Create media item
                    val mediaItem = MediaItem(
                        id = generateMediaId(),
                        filePath = file.absolutePath,
                        format = mapToAppFormat(recorder.settings.format),
                        createdAt = Clock.System.now(),
                        durationMs = session.duration * 1000L,
                        sizeBytes = file.length(),
                        thumbnailPath = null,
                        dimensions = Dimensions(
                            width = session.captureArea.width,
                            height = session.captureArea.height
                        ),
                        metadata = mapOf(
                            "fps" to recorder.settings.fps.toString(),
                            "quality" to recorder.settings.quality.toString()
                        )
                    )

                    // Complete processing and return to idle with new media
                    stateRepository.completeProcessing(mediaItem)

                    // Open the media in editor if auto-open is enabled
                    val settings = settingsRepository.getCurrentSettings()
                    if (settings.autoSave) {
                        // Auto-saved, possibly open editor
                        stateRepository.startEditing(mediaItem)
                    }
                }
            },
            onFailure = { error ->
                stateRepository.handleError(
                    message = error.message ?: "Recording failed",
                    cause = error,
                    recoverable = true
                )
            }
        )
    }

    private fun mapOutputFormat(format: OutputFormat): club.ozgur.gifland.core.OutputFormat {
        return when (format) {
            OutputFormat.GIF -> club.ozgur.gifland.core.OutputFormat.GIF
            OutputFormat.WEBP -> club.ozgur.gifland.core.OutputFormat.WEBP
            OutputFormat.MP4 -> club.ozgur.gifland.core.OutputFormat.MP4
        }
    }

    private fun mapToAppFormat(format: club.ozgur.gifland.core.OutputFormat): OutputFormat {
        return when (format) {
            club.ozgur.gifland.core.OutputFormat.GIF -> OutputFormat.GIF
            club.ozgur.gifland.core.OutputFormat.WEBP -> OutputFormat.WEBP
            club.ozgur.gifland.core.OutputFormat.MP4 -> OutputFormat.MP4
        }
    }

    private fun generateMediaId(): String {
        return "media_${System.currentTimeMillis()}_${(0..9999).random()}"
    }

    fun clearError() {
        recorder.clearError()
    }

    val lastError: StateFlow<String?> = recorder.lastError
    val lastSavedFile: StateFlow<File?> = recorder.lastSavedFile
}