package club.ozgur.gifland.encoder

import club.ozgur.gifland.core.OutputFormat
import club.ozgur.gifland.util.Log
import kotlinx.coroutines.*
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempDirectory

class KotlinEncoder(
    private val captureArea: Rectangle,
    private val fps: Int = 30,
    private val quality: Int = 80,
    private val scale: Float = 1.0f,
    private val format: OutputFormat = OutputFormat.WEBP,
    private val maxDuration: Int = 300 // seconds
) {
    private val robot = Robot()
    private val frames = mutableListOf<BufferedImage>()
    private var isRecording = false
    private var isPaused = false
    private var recordingJob: Job? = null
    private var startTime = 0L
    private var frameCount = 0

    data class RecorderState(
        val isRecording: Boolean = false,
        val isPaused: Boolean = false,
        val frameCount: Int = 0,
        val duration: Double = 0.0,
        val estimatedSize: Long = 0L
    )

    private var currentState = RecorderState()

    suspend fun startRecording(): Boolean = withContext(Dispatchers.IO) {
        if (isRecording) {
            Log.e("KotlinEncoder", "Recording already in progress")
            return@withContext false
        }

        frames.clear()
        frameCount = 0
        isRecording = true
        isPaused = false
        startTime = System.currentTimeMillis()

        Log.d("KotlinEncoder", "Starting recording: ${captureArea.width}x${captureArea.height} @ $fps fps")

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            val frameDelay = 1000L / fps

            while (isActive && isRecording) {
                val frameStartTime = System.currentTimeMillis()

                // Check max duration
                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                if (elapsed >= maxDuration) {
                    Log.d("KotlinEncoder", "Max duration reached: $maxDuration seconds")
                    break
                }

                if (!isPaused) {
                    try {
                        // Capture frame
                        val frame = robot.createScreenCapture(captureArea)

                        // Apply scaling if needed
                        val scaledFrame = if (scale != 1.0f) {
                            val newWidth = (frame.width * scale).toInt()
                            val newHeight = (frame.height * scale).toInt()
                            val scaledImage = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
                            val g2d = scaledImage.createGraphics()
                            g2d.drawImage(frame, 0, 0, newWidth, newHeight, null)
                            g2d.dispose()
                            scaledImage
                        } else {
                            frame
                        }

                        frames.add(scaledFrame)
                        frameCount++

                        // Update state
                        currentState = RecorderState(
                            isRecording = true,
                            isPaused = isPaused,
                            frameCount = frameCount,
                            duration = elapsed,
                            estimatedSize = frameCount * 100_000L // Rough estimate
                        )

                    } catch (e: Exception) {
                        Log.e("KotlinEncoder", "Failed to capture frame", e)
                    }
                }

                // Maintain consistent frame rate
                val frameDuration = System.currentTimeMillis() - frameStartTime
                val sleepTime = frameDelay - frameDuration
                if (sleepTime > 0) {
                    delay(sleepTime)
                }
            }
        }

        true
    }

    suspend fun stopRecording(): String? = withContext(Dispatchers.IO) {
        if (!isRecording) {
            Log.e("KotlinEncoder", "No recording in progress")
            return@withContext null
        }

        Log.d("KotlinEncoder", "Stopping recording, processing ${frames.size} frames")

        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        if (frames.isEmpty()) {
            Log.e("KotlinEncoder", "No frames captured")
            return@withContext null
        }

        // Generate output filename
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val extension = when (format) {
            OutputFormat.WEBP -> "webp"
            OutputFormat.MP4 -> "mp4"
            OutputFormat.GIF -> "gif"
        }
        val outputFile = File(
            System.getProperty("user.home") + "/Documents",
            "recording_$timestamp.$extension"
        )

        // Encode frames
        val success = encodeFrames(outputFile)

        // Clear frames to free memory
        frames.clear()
        frameCount = 0

        // Reset state
        currentState = RecorderState()

        if (success) {
            Log.d("KotlinEncoder", "Recording saved to: ${outputFile.absolutePath}")
            outputFile.absolutePath
        } else {
            null
        }
    }

    fun pauseRecording() {
        isPaused = true
        currentState = currentState.copy(isPaused = true)
        Log.d("KotlinEncoder", "Recording paused")
    }

    fun resumeRecording() {
        isPaused = false
        currentState = currentState.copy(isPaused = false)
        Log.d("KotlinEncoder", "Recording resumed")
    }

    fun getState(): RecorderState = currentState

    private fun encodeFrames(outputFile: File): Boolean {
        return try {
            // Create temp directory for frames
            val tempDir = createTempDirectory("webp_recorder_").toFile()

            Log.d("KotlinEncoder", "Saving ${frames.size} frames to temp directory")

            // Save frames as PNG files
            frames.forEachIndexed { index, frame ->
                val frameFile = File(tempDir, String.format("frame_%06d.png", index))
                ImageIO.write(frame, "png", frameFile)
            }

            // Calculate actual FPS based on recording duration
            val duration = (System.currentTimeMillis() - startTime) / 1000.0
            val actualFps = if (duration > 0) {
                (frames.size / duration).toInt().coerceIn(1, fps)
            } else {
                fps
            }

            Log.d("KotlinEncoder", "Encoding with FFmpeg: format=$format, fps=$actualFps, quality=$quality")

            // Find FFmpeg
            val ffmpegPath = findFfmpeg()
            if (ffmpegPath == null) {
                Log.e("KotlinEncoder", "FFmpeg not found!")
                return false
            }

            // Build FFmpeg command
            val command = mutableListOf(
                ffmpegPath,
                "-y", // Overwrite output
                "-framerate", actualFps.toString(),
                "-i", File(tempDir, "frame_%06d.png").absolutePath
            )

            // Add format-specific options
            when (format) {
                OutputFormat.WEBP -> {
                    command.addAll(listOf(
                        "-c:v", "libwebp",
                        "-lossless", "0",
                        "-quality", quality.toString(),
                        "-compression_level", "4",
                        "-loop", "0",
                        outputFile.absolutePath
                    ))
                }
                OutputFormat.MP4 -> {
                    val crf = (51 - (quality * 51 / 100)).toString()
                    command.addAll(listOf(
                        "-c:v", "libx264",
                        "-pix_fmt", "yuv420p",
                        "-crf", crf,
                        "-preset", "medium",
                        "-movflags", "+faststart",
                        outputFile.absolutePath
                    ))
                }
                OutputFormat.GIF -> {
                    // Generate palette for better quality
                    val paletteFile = File(tempDir, "palette.png")
                    val paletteCommand = listOf(
                        ffmpegPath,
                        "-y",
                        "-framerate", actualFps.toString(),
                        "-i", File(tempDir, "frame_%06d.png").absolutePath,
                        "-vf", "palettegen=max_colors=256",
                        paletteFile.absolutePath
                    )

                    val paletteProcess = ProcessBuilder(paletteCommand).start()
                    paletteProcess.waitFor()

                    if (paletteProcess.exitValue() == 0 && paletteFile.exists()) {
                        // Use palette for GIF generation
                        command.clear()
                        command.addAll(listOf(
                            ffmpegPath,
                            "-y",
                            "-framerate", actualFps.toString(),
                            "-i", File(tempDir, "frame_%06d.png").absolutePath,
                            "-i", paletteFile.absolutePath,
                            "-filter_complex", "paletteuse=dither=bayer:bayer_scale=5",
                            "-loop", "0",
                            outputFile.absolutePath
                        ))
                    } else {
                        // Fallback without palette
                        command.addAll(listOf(
                            "-loop", "0",
                            outputFile.absolutePath
                        ))
                    }
                }
            }

            // Execute FFmpeg
            Log.d("KotlinEncoder", "Executing: ${command.joinToString(" ")}")
            val process = ProcessBuilder(command).start()
            val exitCode = process.waitFor()

            // Clean up temp directory
            tempDir.deleteRecursively()

            if (exitCode == 0) {
                Log.d("KotlinEncoder", "Encoding successful: ${outputFile.length()} bytes")
                true
            } else {
                val error = process.errorStream.bufferedReader().readText()
                Log.e("KotlinEncoder", "FFmpeg failed with exit code $exitCode: $error")
                false
            }

        } catch (e: Exception) {
            Log.e("KotlinEncoder", "Failed to encode frames", e)
            false
        }
    }

    private fun findFfmpeg(): String? {
        // Determine OS-specific bundled path
        val osName = System.getProperty("os.name").lowercase()
        val resourcePath = when {
            osName.contains("win") -> "/native/windows/ffmpeg.exe"
            osName.contains("mac") || osName.contains("darwin") -> "/native/macos/ffmpeg"
            else -> "/native/macos/ffmpeg" // default to mac for now
        }

        val resourceUrl = this::class.java.getResource(resourcePath)
            ?: throw IllegalStateException("Bundled FFmpeg not found in resources at: $resourcePath")

        val file = File(resourceUrl.path)
        if (!file.exists()) {
            throw IllegalStateException("Bundled FFmpeg exists in resources but file not found at: ${file.absolutePath}")
        }

        if (!file.canExecute()) {
            file.setExecutable(true, false)
        }

        return file.absolutePath
    }
}