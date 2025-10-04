package club.ozgur.gifland.core

import club.ozgur.gifland.capture.ScreenCapture
import club.ozgur.gifland.encoder.NativeEncoderSimple
import club.ozgur.gifland.encoder.JAVEEncoder
import club.ozgur.gifland.encoder.NativeRecorderClient
import club.ozgur.gifland.ui.components.CaptureArea
import club.ozgur.gifland.util.debugId
import club.ozgur.gifland.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import java.awt.MouseInfo
import java.awt.Color
import java.awt.Polygon
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
 * - A `Robot` bound to that monitor is used to avoid scaling mismatches.
 */
class Recorder {
    // Native encoder is now used instead of JavaCV
    private val frames = mutableListOf<BufferedImage>()
    // WebP is file-based in this implementation
    private var webpOutputFile: File? = null
	private var mp4Session: Any? = null // Streaming session placeholder
	private var mp4OutputFile: File? = null
	private var tempDir: File? = null
	private val frameFiles = mutableListOf<File>()
	private val frameTimestampsUs = mutableListOf<Long>()
	private var cumulativeBytes: Long = 0
	private var droppedFrames: Long = 0

    private var recordingJob: Job? = null
    private var writerJob: Job? = null
    private var startTime: Long = 0
    private var startNanoTime: Long = 0
    private var frameChannel: Channel<Pair<BufferedImage, Long>>? = null

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

        frames.clear()
        frameFiles.clear()
        frameTimestampsUs.clear()
        cumulativeBytes = 0
		droppedFrames = 0
        startTime = System.currentTimeMillis()
        startNanoTime = System.nanoTime()
        _state.value = RecordingState(isRecording = true)
        Log.d("Recorder", "startRecording area=$area settings=$settings")

        val clampedFps = settings.fps.coerceIn(1, 60)  // Support up to 60 FPS
        val delayMs = (1000L / clampedFps).coerceAtLeast(1)
        val captureRect = area?.let { Rectangle(it.x, it.y, it.width, it.height) } ?: getFullScreenBounds()

        // Choose monitor by rectangle center
        val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        val center = Point(captureRect.x + captureRect.width / 2, captureRect.y + captureRect.height / 2)
        val targetScreen = screens.find { it.defaultConfiguration.bounds.contains(center) } ?: screens[0]
        Log.d("Recorder", "captureRect=$captureRect center=$center targetScreen=${targetScreen.debugId()} bounds=${targetScreen.defaultConfiguration.bounds}")
        val robot = try {
            Robot(targetScreen)
        } catch (e: Exception) {
            Log.e("Recorder", "Failed to create Robot for screen capture", e)
            _state.value = _state.value.copy(isRecording = false)
            _lastError.value = "Ekran kaydı başlatılamadı: Erişim izni veya ekran yakalama hatası"
            onComplete(Result.failure(e))
            return
        }

		// Prepare temp directory for disk-backed frames
		val sessionStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
		tempDir = File(System.getProperty("java.io.tmpdir"), "gifland_${sessionStamp}").also { it.mkdirs() }
		Log.d("Recorder", "Temp dir created: ${tempDir?.absolutePath}")

        // Disk-backed mode for WebP/MP4
        mp4Session = null

        // Start background writer to offload JPEG encoding & disk IO
        // Increase channel capacity to reduce frame drops
        frameChannel = Channel(capacity = 32)
		writerJob = CoroutineScope(Dispatchers.IO).launch {
            val scaledW = (captureRect.width * settings.scale).toInt().coerceAtLeast(1)
            val scaledH = (captureRect.height * settings.scale).toInt().coerceAtLeast(1)
            val jpegQ = mapQualityToJpeg(settings.quality)
            for ((img, tsUs) in frameChannel!!) {
                val toSave = if (settings.scale != 1.0f) scaleFrame(img, scaledW, scaledH) else img
                val fileIdx = frameFiles.size
                val outFile = File(tempDir, String.format("frame_%06d.jpg", fileIdx))
                saveJpeg(toSave, outFile, jpegQ)
                frameFiles.add(outFile)
                frameTimestampsUs.add(tsUs)
                cumulativeBytes += outFile.length()
                // Don't flush - let GC handle it to avoid memory churn
                // if (toSave !== img) toSave.flush()
                // img.flush()
				if (fileIdx % 100 == 0) {
					Log.d("Recorder", "Writer saved ${fileIdx + 1} frames, size=${String.format("%.1f MB", cumulativeBytes/(1024.0*1024.0))}")
				}
            }
			Log.d("Recorder", "Writer finished. totalSaved=${frameFiles.size} dropped=${droppedFrames} totalBytes=${cumulativeBytes}")
        }

		recordingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && _state.value.isRecording) {
                if (!_state.value.isPaused) {
                    try {
                        val frame = robot.createScreenCapture(captureRect)

                        // Draw mouse cursor on the frame
                        val mousePos = MouseInfo.getPointerInfo()?.location
                        if (mousePos != null && captureRect.contains(mousePos)) {
                            val g2d = frame.createGraphics()
                            g2d.color = Color.BLACK
                            val relX = mousePos.x - captureRect.x
                            val relY = mousePos.y - captureRect.y

                            // Draw a simple cursor (arrow shape)
                            val cursorPoly = Polygon()
                            cursorPoly.addPoint(relX, relY)
                            cursorPoly.addPoint(relX, relY + 16)
                            cursorPoly.addPoint(relX + 4, relY + 12)
                            cursorPoly.addPoint(relX + 8, relY + 20)
                            cursorPoly.addPoint(relX + 10, relY + 18)
                            cursorPoly.addPoint(relX + 6, relY + 10)
                            cursorPoly.addPoint(relX + 12, relY + 10)

                            g2d.fillPolygon(cursorPoly)
                            g2d.color = Color.WHITE
                            g2d.drawPolygon(cursorPoly)
                            g2d.dispose()
                        }

                        val tsUs = (System.nanoTime() - startNanoTime) / 1000L
                        val offered = frameChannel?.trySend(frame to tsUs)?.isSuccess == true
                        if (!offered) {
						// Drop frame if writer is busy
						droppedFrames++
						if (droppedFrames % 50L == 0L) {
							Log.d("Recorder", "Dropped frames so far: ${droppedFrames}")
						}
						// Don't flush - let GC handle it
						// frame.flush()
                        }

                        val currentTime = System.currentTimeMillis()
                        val duration = ((currentTime - startTime) / 1000).toInt()
                        val nextCount = _state.value.frameCount + if (offered) 1 else 0
                        val estimatedSize = cumulativeBytes

                        _state.value = _state.value.copy(
							frameCount = nextCount,
                            duration = duration,
                            estimatedSize = estimatedSize
                        )

                        withContext(Dispatchers.Main) {
                            onUpdate(_state.value)
                        }

                        if (duration >= settings.maxDuration) {
                            Log.d("Recorder", "Max duration reached, stopping and saving...")
                            // Avoid stopping from inside the same coroutine that is holding resources
                            val parentScope = this
                            CoroutineScope(Dispatchers.IO).launch {
                                val result = stopRecordingInternal()
                                withContext(Dispatchers.Main) { onComplete(result) }
                            }
                            break
                        }
                    } catch (e: CancellationException) {
                        // Normal path when job is cancelled during stop; ignore
                        break
                    } catch (e: Exception) {
                        // If Robot fails (monitor change or permission), stop and surface error
                        Log.e("Recorder", "Frame capture error", e)
                        _state.value = _state.value.copy(isRecording = false)
                        _lastError.value = e.message ?: "Bilinmeyen ekran yakalama hatası"
                        withContext(Dispatchers.Main) { onComplete(Result.failure(e)) }
                        cancel("Robot capture failed", e)
                    }
                }
                delay(delayMs)
            }
        }
    }

	private fun scaleFrame(image: BufferedImage, newWidth: Int, newHeight: Int): BufferedImage {
		// Use TYPE_INT_RGB for JPEG compatibility (no alpha channel)
		val scaled = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
		val g = scaled.createGraphics()
		g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
		g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY)
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
		// Fill with white background first (for any transparent areas)
		g.setColor(java.awt.Color.WHITE)
		g.fillRect(0, 0, newWidth, newHeight)
		g.drawImage(image, 0, 0, newWidth, newHeight, null)
		g.dispose()
		return scaled
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
        // Close channel and wait writer to drain all pending frames
        frameChannel?.close()
        writerJob?.join()
        writerJob = null

        // Calculate actual recording duration
        val actualDurationMs = System.currentTimeMillis() - startTime

        if (mp4Session == null && frames.isEmpty() && frameFiles.isEmpty()) {
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
                        webpOutputFile = outputFile
                        Log.d("Recorder", "Encoding ${frameFiles.size} JPEG frames to WebP ${outputFile}")
                        Log.d("Recorder", "First 3 frames: ${frameFiles.take(3).map { it.name }} timestampsUs=${frameTimestampsUs.take(3)}")
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
                            Log.d("Recorder", "Using JAVE2 encoder for macOS WebP (no signature issues)")
                            JAVEEncoder.encodeWebPFromFiles(
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
                        webpOutputFile = null
                        result
                }
				OutputFormat.GIF -> {
				val outputFile = File(System.getProperty("user.home") + "/Documents", "recording_${timestamp}.gif")
				val actualFps = if (actualDurationMs > 0) {
					(frameFiles.size * 1000.0 / actualDurationMs).toInt().coerceIn(1, 15) // GIF için max 15 FPS
				} else {
					settings.fps.coerceIn(1, 15)
				}
				Log.d("Recorder", "GIF encoding: frames=${frameFiles.size}, duration=${actualDurationMs}ms, actualFps=$actualFps")

				// GIF için yüksek kalite ayarları
				val gifQuality = when {
					settings.quality >= 40 -> 80  // Çok yüksek kalite
					settings.quality >= 25 -> 60  // Yüksek kalite
					settings.quality >= 15 -> 40  // İyi kalite
					else -> 25  // Minimum kabul edilebilir kalite
				}

				// Use JAVE2 on macOS to avoid signature issues
				val osName = System.getProperty("os.name").lowercase()
				val result = if ((osName.contains("mac") || osName.contains("darwin")) && JAVEEncoder.isAvailable()) {
					Log.d("Recorder", "Using JAVE2 encoder for macOS (no signature issues)")
					JAVEEncoder.encodeGIFFromFiles(
						frameFiles = frameFiles,
						outputFile = outputFile,
						fps = actualFps,
						quality = gifQuality,
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
					val actualFps = settings.fps.coerceIn(1, 60)
					Log.d("Recorder", "Encoding ${frameFiles.size} frames to MP4 $outputFile (fps=$actualFps, actualDuration=${actualDurationMs}ms)")
                    Log.d("Recorder", "FrameFiles=${frameFiles.size} Dropped=${droppedFrames} Bytes=${cumulativeBytes}")

					// Native encoder doesn't support streaming session yet
					if (false) {
						// Streaming not supported with native encoder
						Result.failure(Exception("Streaming not yet supported"))
					} else {
						// Fallback non-streaming path
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
						val preset = when {
							settings.quality >= 45 -> "veryslow"
							settings.quality >= 35 -> "slower"
							settings.quality >= 28 -> "slow"
							settings.quality >= 20 -> "medium"
							settings.quality >= 10 -> "fast"
							else -> "faster"
						}
						val profile = when {
							settings.quality >= 30 -> "high"
							else -> "main"
						}

                        // Calculate actual FPS based on frame count and duration
                        val actualFps = if (actualDurationMs > 0) {
                            (frameFiles.size * 1000.0 / actualDurationMs).toInt().coerceIn(1, 60)
                        } else {
                            settings.fps
                        }
                        Log.d("Recorder", "MP4 encoding: frames=${frameFiles.size}, duration=${actualDurationMs}ms, actualFps=$actualFps (target was ${settings.fps})")

                        // Use file-based encoding to avoid memory issues
                        val result = NativeEncoderSimple.encodeMP4FromFiles(
							frameFiles = frameFiles,
							outputFile = outputFile,
							crf = crf,
							fps = actualFps,
                            onProgress = { p ->
                                Log.d("Recorder", "WebP encoding progress: $p%")
                                _state.value = _state.value.copy(saveProgress = p)
                            }
						)
						cleanupTemp()
                        result
					}
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

    private fun calculateEstimatedSize(): Long {
		if (frames.isEmpty()) return 0
		return cumulativeBytes.takeIf { it > 0 } ?: run {
			val sample = frames.first()
			val avgFrameSize = sample.width * sample.height * 3L / 10
			avgFrameSize * frames.size
		}
    }

	private fun saveJpeg(image: BufferedImage, file: File, quality: Float) {
		javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").asSequence().firstOrNull()?.let { writer ->
			file.outputStream().use { os ->
				writer.output = javax.imageio.ImageIO.createImageOutputStream(os)
				val params = writer.defaultWriteParam
				params.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
				params.compressionQuality = quality
				writer.write(null, javax.imageio.IIOImage(image, null, null), params)
				writer.dispose()
			}
		} ?: run {
			// Fallback
			javax.imageio.ImageIO.write(image, "jpg", file)
		}
	}

	private fun mapQualityToJpeg(quality: Int): Float {
		return when {
			quality >= 45 -> 0.98f
			quality >= 35 -> 0.95f
			quality >= 25 -> 0.9f
			quality >= 15 -> 0.85f
			quality >= 10 -> 0.8f
			else -> 0.7f
		}
	}

	private fun cleanupTemp() {
		runCatching {
			frameFiles.forEach { it.delete() }
            tempDir?.delete()
            tempDir = null
			frameFiles.clear()
			frameTimestampsUs.clear()
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
        frames.clear()
        frameFiles.clear()
        frameTimestampsUs.clear()
        cumulativeBytes = 0
        droppedFrames = 0
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


