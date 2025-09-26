package club.ozgur.gifland.encoder

import club.ozgur.gifland.core.OutputFormat
import club.ozgur.gifland.core.RecordingState
import club.ozgur.gifland.core.RecorderSettings
import club.ozgur.gifland.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.Rectangle
import java.awt.GraphicsEnvironment

/**
 * Recorder client using pure Kotlin/JVM implementation
 * No external native process needed - all encoding done in JVM
 */
class NativeRecorderClient {
    private var kotlinEncoder: KotlinEncoder? = null

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state

    private var statePollingJob: Job? = null

    /**
     * Initialize the Kotlin encoder (no external process needed)
     */
    private fun initializeEncoder(): Boolean {
        return try {
            // Already initialized
            if (kotlinEncoder != null) {
                return true
            }

            Log.d("NativeRecorderClient", "Using pure Kotlin encoder - no native process needed")
            true
        } catch (e: Exception) {
            Log.e("NativeRecorderClient", "Failed to initialize encoder", e)
            false
        }
    }

    /**
     * Start recording with Kotlin encoder
     */
    suspend fun startRecording(
        area: Rectangle,
        settings: RecorderSettings
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!initializeEncoder()) {
                return@withContext false
            }

            Log.d("NativeRecorderClient", "🎯 Starting Kotlin recording with:")
            Log.d("NativeRecorderClient", "  Area: ${area.x}, ${area.y}, ${area.width}x${area.height}")
            Log.d("NativeRecorderClient", "  FPS: ${settings.fps}")
            Log.d("NativeRecorderClient", "  Quality: ${settings.quality}")
            Log.d("NativeRecorderClient", "  Format: ${settings.format}")
            Log.d("NativeRecorderClient", "  Max Duration: ${settings.maxDuration}s")

            // Create new encoder instance
            kotlinEncoder = KotlinEncoder(
                captureArea = area,
                fps = settings.fps,
                quality = settings.quality,
                scale = settings.scale,
                format = settings.format,
                maxDuration = settings.maxDuration
            )

            val started = kotlinEncoder?.startRecording() ?: false
            if (started) {
                // Start polling for state updates
                startStatePolling()
            }
            started
        } catch (e: Exception) {
            Log.e("NativeRecorderClient", "Failed to start recording", e)
            false
        }
    }

    /**
     * Stop recording and get the output file
     */
    suspend fun stopRecording(): String? = withContext(Dispatchers.IO) {
        try {
            stopStatePolling()

            val outputPath = kotlinEncoder?.stopRecording()
            kotlinEncoder = null
            outputPath
        } catch (e: Exception) {
            Log.e("NativeRecorderClient", "Failed to stop recording", e)
            null
        }
    }

    /**
     * Pause the current recording
     */
    suspend fun pauseRecording(): Boolean = withContext(Dispatchers.IO) {
        try {
            kotlinEncoder?.pauseRecording()
            true
        } catch (e: Exception) {
            Log.e("NativeRecorderClient", "Failed to pause recording", e)
            false
        }
    }

    /**
     * Resume the current recording
     */
    suspend fun resumeRecording(): Boolean = withContext(Dispatchers.IO) {
        try {
            kotlinEncoder?.resumeRecording()
            true
        } catch (e: Exception) {
            Log.e("NativeRecorderClient", "Failed to resume recording", e)
            false
        }
    }

    /**
     * Get available screens
     */
    suspend fun getScreens(): List<ScreenInfo> = withContext(Dispatchers.IO) {
        try {
            val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            screens.mapIndexed { index, screen ->
                val bounds = screen.defaultConfiguration.bounds
                ScreenInfo(
                    id = index,
                    x = bounds.x,
                    y = bounds.y,
                    width = bounds.width,
                    height = bounds.height,
                    isPrimary = index == 0
                )
            }
        } catch (e: Exception) {
            Log.e("NativeRecorderClient", "Failed to get screens", e)
            emptyList()
        }
    }

    /**
     * Start polling for state updates
     */
    private fun startStatePolling() {
        stopStatePolling()
        statePollingJob = GlobalScope.launch {
            while (isActive) {
                try {
                    val encoderState = kotlinEncoder?.getState()
                    if (encoderState != null) {
                        _state.value = RecordingState(
                            isRecording = encoderState.isRecording,
                            isPaused = encoderState.isPaused,
                            frameCount = encoderState.frameCount,
                            duration = encoderState.duration.toInt(),
                            estimatedSize = encoderState.estimatedSize
                        )
                    }
                } catch (e: Exception) {
                    // Ignore polling errors
                }
                delay(100) // Poll every 100ms
            }
        }
    }

    /**
     * Stop polling for state updates
     */
    private fun stopStatePolling() {
        statePollingJob?.cancel()
        statePollingJob = null
    }

    /**
     * Cleanup resources
     */
    fun close() {
        stopStatePolling()
        kotlinEncoder = null
    }

    // Screen info class
    data class ScreenInfo(
        val id: Int,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val isPrimary: Boolean
    )
}