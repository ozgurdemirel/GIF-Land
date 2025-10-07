package club.ozgur.gifland.core

import club.ozgur.gifland.capture.ScreenCapture
import club.ozgur.gifland.capture.FFmpegFrameCapture
import club.ozgur.gifland.capture.FFmpegFrameCapture.FFmpegCaptureSession

import club.ozgur.gifland.encoder.NativeEncoderSimple
import club.ozgur.gifland.encoder.JAVEEncoder
import club.ozgur.gifland.ui.components.CaptureArea
import club.ozgur.gifland.util.debugId
import club.ozgur.gifland.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class RecordingState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val frameCount: Int = 0,
    val duration: Int = 0, // seconds
    val estimatedSize: Long = 0L,
    val isSaving: Boolean = false,
    val saveProgress: Int = 0
)


/**
 * Records a sequence of frames from a fixed screen rectangle and encodes as WebP or MP4.
 *
 * Multi-monitor & DPI notes:
 * - We pin the recording rectangle to a specific monitor determined by its center.
 */
class Recorder {
	private var tempDir: File? = null
	private val frameFiles = mutableListOf<File>()
    private var ffmpegSession: FFmpegCaptureSession? = null
    private var collectorJob: Job? = null

	private var cumulativeBytes: Long = 0

    private var recordingJob: Job? = null
    private var startTime: Long = 0

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state

    private val _settings = MutableStateFlow(RecorderSettings())
    val settingsFlow: StateFlow<RecorderSettings> = _settings
    var settings: RecorderSettings
        get() = _settings.value
        set(value) {
            _settings.value = value
        }

    // Store last saved file for UI access
    private val _lastSavedFile = MutableStateFlow<File?>(null)
    val lastSavedFile: StateFlow<File?> = _lastSavedFile

    // Store last error for surfacing issues in UI
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        Log.d("Recorder", "========================================")
        Log.d("Recorder", "🚀 NEW RECORDER INSTANCE CREATED")
        Log.d("Recorder", "Default settings: fps=${settings.fps}, quality=${settings.quality}")
        Log.d("Recorder", "========================================")
    }

    fun startRecording(
        area: CaptureArea? = null,
        onUpdate: (RecordingState) -> Unit = {},
        onComplete: (Result<File>) -> Unit = {}
    ) {
        if (_state.value.isRecording) return

        // Debug: Print actual settings being used
        Log.d("Recorder", "========================================")
        Log.d("Recorder", "📹 RECORDER STARTING WITH SETTINGS:")
        Log.d("Recorder", "Format: ${settings.format}")
        Log.d("Recorder", "FPS: ${settings.fps}")
        Log.d("Recorder", "Quality: ${settings.quality}")
        Log.d("Recorder", "Max Duration: ${settings.maxDuration}s")
        Log.d("Recorder", "========================================")

        // Clear previous error before starting a new session
        _lastError.value = null

        // Proactively cleanup any stale temp folders from previous abnormal exits
        cleanupStaleTempDirs()

        frameFiles.clear()
        cumulativeBytes = 0
        startTime = System.currentTimeMillis()
        _state.value = RecordingState(isRecording = true)
        Log.d("Recorder", "startRecording area=$area settings=$settings")

        // Apply GIF-specific FPS caps earlier to avoid over-capturing
        val clampedFps = run {
            val base = settings.fps.coerceIn(1, 60)
            if (settings.format == OutputFormat.GIF) {
                val cap = when {
                    settings.fastGifPreview -> 10
                    settings.quality < 20 -> 10
                    settings.quality <= 40 -> 12
                    else -> 15
                }
                minOf(base, cap)
            } else base
        }  // Support up to 60 FPS
        val captureRect = area?.let { Rectangle(it.x, it.y, it.width, it.height) } ?: getFullScreenBounds()

        // Choose monitor by rectangle center (for logs/debug only)
        val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        val center = Point(captureRect.x + captureRect.width / 2, captureRect.y + captureRect.height / 2)
        val targetScreen = screens.find { it.defaultConfiguration.bounds.contains(center) } ?: screens[0]
        Log.d("Recorder", "captureRect=$captureRect center=$center targetScreen=${targetScreen.debugId()} bounds=${targetScreen.defaultConfiguration.bounds}")

		// Prepare temp directory for disk-backed frames
		val sessionStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
		tempDir = File(System.getProperty("java.io.tmpdir"), "gifland_${sessionStamp}").also { it.mkdirs() }
		Log.d("Recorder", "Temp dir created: ${tempDir?.absolutePath}")

        // Start FFmpeg-based image sequence capture
        val jpegQscale = when {
            settings.quality >= 45 -> 3
            settings.quality >= 35 -> 5
            settings.quality >= 25 -> 7
            settings.quality >= 15 -> 10
            else -> 12
        }
        // Ensure FFmpeg path is set (prefer JAVE2 signed binary on macOS)
        runCatching {
            val osName = System.getProperty("os.name").lowercase()
            if ((osName.contains("mac") || osName.contains("darwin")) && JAVEEncoder.isAvailable()) {
                JAVEEncoder.getFFmpegPath()?.let { NativeEncoderSimple.setFFmpegPath(it) }
            }
        }.onFailure { e -> Log.d("Recorder", "Could not set JAVE2 FFmpeg path: ${e.message}") }

        val session = FFmpegFrameCapture.start(area = area, fps = clampedFps, scale = settings.scale, qscale = jpegQscale, outDir = tempDir!!)
        ffmpegSession = session

        // Start collector to watch output directory and update state
        collectorJob = CoroutineScope(Dispatchers.IO).launch {
            var lastCount = 0
            while (isActive && _state.value.isRecording) {
                try {
                    val files = session.outDir.listFiles { f -> f.isFile && f.name.endsWith(".jpg") }?.sortedBy { it.name } ?: emptyList()
                    if (files.size > frameFiles.size) {
                        val newFiles = files.drop(frameFiles.size)
                        frameFiles.addAll(newFiles)
                        newFiles.forEach { cumulativeBytes += it.length() }
                    }

                    val currentTime = System.currentTimeMillis()
                    val duration = ((currentTime - startTime) / 1000).toInt()

                    if (frameFiles.size != lastCount) {
                        lastCount = frameFiles.size
                        _state.value = _state.value.copy(
                            frameCount = frameFiles.size,
                            duration = duration,
                            estimatedSize = cumulativeBytes
                        )
                        withContext(Dispatchers.Main) {
                            onUpdate(_state.value)
                        }
                    }

                    if (duration >= settings.maxDuration) {
                        Log.d("Recorder", "Max duration reached, stopping and saving...")
                        CoroutineScope(Dispatchers.IO).launch {
                            val result = stopRecordingInternal()
                            withContext(Dispatchers.Main) { onComplete(result) }
                        }
                        break
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.e("Recorder", "Collector error", e)
                    _state.value = _state.value.copy(isRecording = false)
                    _lastError.value = e.message ?: "Bilinmeyen ekran yakalama hatas\u0131"
                    withContext(Dispatchers.Main) { onComplete(Result.failure(e)) }
                    cancel("Collector failed", e)
                }
                delay(100)
            }
        }
        recordingJob = collectorJob
    }

    fun pauseRecording() {
        _state.value = _state.value.copy(isPaused = !_state.value.isPaused)
    }

    suspend fun stopRecording(): Result<File> {
        return stopRecordingInternal()
    }

    private suspend fun stopRecordingInternal(): Result<File> {
        _state.value = _state.value.copy(isRecording = false, isSaving = true, saveProgress = 0)
        // Cancel capture loop immediately to stop producing frames
        recordingJob?.cancel()
        recordingJob = null
        collectorJob?.cancel()
        collectorJob = null
        // Stop FFmpeg process gracefully
        ffmpegSession?.let { FFmpegFrameCapture.stop(it) }
        ffmpegSession = null

        // Calculate actual recording duration
        val actualDurationMs = System.currentTimeMillis() - startTime

        if (frameFiles.isEmpty()) {
            Log.e("Recorder", "No frames captured at stop")
            _lastError.value = "Kayıt başarısız: Hiç kare yakalanamadı. Erişim izinlerini ve ekran seçimini kontrol edin."
            return Result.failure(Exception("No frames captured"))
        }

        val saveResult = withContext(Dispatchers.IO) {
            try {
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                when (settings.format) {
                OutputFormat.WEBP -> {
                        val outputFile = File(System.getProperty("user.home") + "/Documents", "recording_${timestamp}.webp")
                        Log.d("Recorder", "Encoding ${frameFiles.size} JPEG frames to WebP ${outputFile}")
                        // Calculate actual FPS based on frame count and duration
                        val actualFps = if (actualDurationMs > 0) {
                            (frameFiles.size * 1000.0 / actualDurationMs).toInt().coerceIn(1, 60)
                        } else {
                            settings.fps
                        }
                        Log.d("Recorder", "WebP encoding: frames=${frameFiles.size}, duration=${actualDurationMs}ms, actualFps=$actualFps (target was ${settings.fps})")
                        // Use JAVE2 on macOS to avoid signature issues
                        val osName = System.getProperty("os.name").lowercase()
                        val webpQuality = when {
                            settings.quality >= 45 -> 75
                            settings.quality >= 35 -> 60
                            settings.quality >= 25 -> 45
                            settings.quality >= 15 -> 30
                            settings.quality >= 10 -> 20
                            else -> 10
                        }

                        val result = if ((osName.contains("mac") || osName.contains("darwin")) && JAVEEncoder.isAvailable()) {
                            // Use JAVE2's signed FFmpeg binary with our encoder
                            val javeFFmpegPath = JAVEEncoder.getFFmpegPath()
                            if (javeFFmpegPath != null) {
                                Log.d("Recorder", "Using JAVE2's signed FFmpeg binary for WebP: $javeFFmpegPath")
                                NativeEncoderSimple.setFFmpegPath(javeFFmpegPath)
                            }
                            NativeEncoderSimple.encodeWebPFromFiles(
                                frameFiles = frameFiles,
                                outputFile = outputFile,
                                quality = webpQuality,
                                fps = actualFps,
                                onProgress = { p ->
                                    Log.d("Recorder", "WebP encoding progress: $p%")
                                    _state.value = _state.value.copy(saveProgress = p)
                                }
                            )
                        } else {
                            NativeEncoderSimple.encodeWebPFromFiles(
                                frameFiles = frameFiles,
                                outputFile = outputFile,
                                quality = webpQuality,
                                fps = actualFps,
                                onProgress = { p ->
                                    Log.d("Recorder", "WebP encoding progress: $p%")
                                    _state.value = _state.value.copy(saveProgress = p)
                                }
                            )
                        }
                        cleanupTemp()
                        result
                }
				OutputFormat.GIF -> {
				val outputFile = File(System.getProperty("user.home") + "/Documents", "recording_${timestamp}.gif")

				// Apply GIF fps cap based on quality and fast preview
				val rawFps = if (actualDurationMs > 0) {
					(frameFiles.size * 1000.0 / actualDurationMs).toInt().coerceIn(1, 60)
				} else {
					settings.fps.coerceIn(1, 60)
				}
				val gifCap = when {
					settings.fastGifPreview -> 10
					settings.quality < 20 -> 10
					settings.quality <= 40 -> 12
					else -> 15
				}
				val actualFps = minOf(rawFps, gifCap)
				Log.d("Recorder", "GIF encoding: frames=${frameFiles.size}, duration=${actualDurationMs}ms, rawFps=$rawFps, gifCap=$gifCap, actualFps=$actualFps")

				// GIF quality settings
				val gifQuality = when {
					settings.quality >= 40 -> 80  // Çok yüksek kalite
					settings.quality >= 25 -> 60  // Yüksek kalite
					settings.quality >= 15 -> 40  // İyi kalite
					else -> 25  // Minimum kabul edilebilir kalite
				}

				// Use JAVE2 on macOS to avoid signature issues
				val osName = System.getProperty("os.name").lowercase()
				val result = if ((osName.contains("mac") || osName.contains("darwin")) && JAVEEncoder.isAvailable()) {
					// Use JAVE2's signed FFmpeg binary with our encoder
					val javeFFmpegPath = JAVEEncoder.getFFmpegPath()
					if (javeFFmpegPath != null) {
						Log.d("Recorder", "Using JAVE2's signed FFmpeg binary: $javeFFmpegPath")
						NativeEncoderSimple.setFFmpegPath(javeFFmpegPath)
					}
					NativeEncoderSimple.encodeGIFFromFiles(
						frameFiles = frameFiles,
						outputFile = outputFile,
						fps = actualFps,
						quality = gifQuality,
						fastMode = settings.fastGifPreview,
						onProgress = { p ->
							Log.d("Recorder", "GIF encoding progress: $p%")
							_state.value = _state.value.copy(saveProgress = p)
						}
					)
				} else {
					NativeEncoderSimple.encodeGIFFromFiles(
						frameFiles = frameFiles,
						outputFile = outputFile,
						fps = actualFps,
						quality = gifQuality,
						fastMode = settings.fastGifPreview,
						onProgress = { p ->
							Log.d("Recorder", "GIF encoding progress: $p%")
							_state.value = _state.value.copy(saveProgress = p)
						}
					)
				}
				Log.d("Recorder", "GIF encoding completed, cleaning up temp files...")
				cleanupTemp()
				Log.d("Recorder", "Temp files cleaned up, returning result")
				result
			}
			OutputFormat.MP4 -> {
					val outputFile = File(System.getProperty("user.home") + "/Documents", "recording_${timestamp}.mp4")

					val crf = when {
						settings.quality >= 50 -> 0
						settings.quality >= 45 -> 5
						settings.quality >= 40 -> 10
						settings.quality >= 35 -> 14
						settings.quality >= 30 -> 18
						settings.quality >= 25 -> 20
						settings.quality >= 20 -> 23
						settings.quality >= 15 -> 26
						settings.quality >= 10 -> 30
						else -> 35
					}

					// Calculate actual FPS based on frame count and duration
					val actualFps = if (actualDurationMs > 0) {
						(frameFiles.size * 1000.0 / actualDurationMs).toInt().coerceIn(1, 60)
					} else {
						settings.fps
					}
					Log.d("Recorder", "MP4 encoding: frames=${frameFiles.size}, duration=${actualDurationMs}ms, actualFps=$actualFps, crf=$crf")

					// Use JAVE2's signed FFmpeg binary on macOS if available
					val osName = System.getProperty("os.name").lowercase()
					if ((osName.contains("mac") || osName.contains("darwin")) && JAVEEncoder.isAvailable()) {
						val javeFFmpegPath = JAVEEncoder.getFFmpegPath()
						if (javeFFmpegPath != null) {
							Log.d("Recorder", "Using JAVE2's signed FFmpeg binary for MP4: $javeFFmpegPath")
							NativeEncoderSimple.setFFmpegPath(javeFFmpegPath)
						}
					}

					// Use file-based encoding
					val result = NativeEncoderSimple.encodeMP4FromFiles(
						frameFiles = frameFiles,
						outputFile = outputFile,
						crf = crf,
						fps = actualFps,
						onProgress = { p ->
							Log.d("Recorder", "MP4 encoding progress: $p%")
							_state.value = _state.value.copy(saveProgress = p)
						}
					)
					cleanupTemp()
					result
				}
                }
            } catch (e: Exception) {
                Log.e("Recorder", "Error during save operation", e)
                _lastError.value = e.message ?: "Kayıt kaydedilemedi"
                cleanupTemp() // Try to cleanup even on error
                Result.failure(e)
            }
        }
        // Mark saving done - clear the progress to 0 when done
        _state.value = _state.value.copy(isSaving = false, saveProgress = 0)
        Log.d("Recorder", "Saving complete, state updated: isSaving=false, saveProgress=0")

        // Store last saved file on success; set error on failure
        saveResult.onSuccess { file ->
            _lastSavedFile.value = file
            Log.d("Recorder", "Last saved file updated: ${file.absolutePath}")
            _lastError.value = null
        }.onFailure { e ->
            _lastError.value = e.message ?: "Kayıt kaydedilemedi"

        }

        return saveResult
    }

	private fun cleanupTemp() {
		runCatching {
			frameFiles.forEach { it.delete() }
            tempDir?.delete()
            tempDir = null
			frameFiles.clear()
			cumulativeBytes = 0
		}
	}

	private fun cleanupStaleTempDirs() {
		val tmpRoot = File(System.getProperty("java.io.tmpdir"))
		val stale = tmpRoot.listFiles { f -> f.isDirectory && f.name.startsWith("gifland_") } ?: return
		stale.forEach { dir ->
			runCatching {
				val ok = dir.deleteRecursively()
				Log.d("Recorder", "Cleaned stale temp dir: ${dir.absolutePath} ok=${ok}")
			}
		}
	}

    private fun getFullScreenBounds(): Rectangle {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val mouse = java.awt.MouseInfo.getPointerInfo().location
        val device = ge.screenDevices.find { it.defaultConfiguration.bounds.contains(mouse) } ?: ge.defaultScreenDevice
        return device.defaultConfiguration.bounds
    }

    fun reset() {
        Log.d("Recorder", "Reset called - clearing recording state (preserving lastSavedFile)")
        _state.value = RecordingState()
        frameFiles.clear()
        cumulativeBytes = 0
        // NOTE: _lastSavedFile is intentionally NOT reset here
        // This preserves the "Open Folder" button functionality after recording
        // But clear any previous error to avoid confusing the user for next session
        _lastError.value = null
    }

    fun clearError() {
        _lastError.value = null
    }
}

enum class OutputFormat { WEBP, MP4, GIF }


