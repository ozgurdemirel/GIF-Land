package club.ozgur.gifland.encoder

import club.ozgur.gifland.util.Log
import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.AudioAttributes
import ws.schild.jave.encode.EncodingAttributes
import ws.schild.jave.encode.VideoAttributes
import ws.schild.jave.encode.enums.X264_PROFILE
import ws.schild.jave.info.VideoSize
import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator
import ws.schild.jave.progress.EncoderProgressListener
import java.io.File
import javax.imageio.ImageIO

/**
 * Encoder using JAVE2 library which provides properly signed FFmpeg binaries
 */
object JAVEEncoder {

    init {
        try {
            // Get the FFmpeg path from JAVE
            val locator = DefaultFFMPEGLocator()
            val ffmpegPath = locator.getExecutablePath()
            Log.d("JAVEEncoder", "JAVE2 FFmpeg path: $ffmpegPath")
            FFmpegDebugManager.updateFFmpegVersion("Using JAVE2 FFmpeg: $ffmpegPath")
            FFmpegDebugManager.updateVerification("✅ Using JAVE2 signed FFmpeg - no signature issues!")
        } catch (e: Exception) {
            Log.e("JAVEEncoder", "Failed to initialize JAVE2", e)
        }
    }

    fun encodeWebPFromFiles(
        frameFiles: List<File>,
        outputFile: File,
        quality: Int = 80,
        fps: Int = 30,
        onProgress: ((Int) -> Unit)? = null
    ): Result<File> {
        return try {
            Log.d("JAVEEncoder", "Encoding WebP with JAVE2: ${frameFiles.size} frames")

            if (frameFiles.isEmpty()) {
                return Result.failure(Exception("No frames to encode"))
            }

            // Get dimensions from first frame
            val firstImage = ImageIO.read(frameFiles[0])
            val width = firstImage.width
            val height = firstImage.height

            // Create a temporary video from frames first (JAVE works with video files)
            val tempVideo = File.createTempFile("jave_temp", ".mp4")
            tempVideo.deleteOnExit()

            // First encode frames to temporary MP4
            // JAVE2 needs actual files to exist - let's verify they do
            val frameDir = frameFiles.first().parentFile
            Log.d("JAVEEncoder", "Frame directory: ${frameDir.absolutePath}")
            Log.d("JAVEEncoder", "Frame files exist: ${frameFiles.all { it.exists() }}")
            Log.d("JAVEEncoder", "First frame: ${frameFiles.first().name}")

            // For JAVE2, we need to use the actual frame pattern
            val firstFrameName = frameFiles.first().name
            val inputPattern = if (firstFrameName.matches(Regex("frame_\\d{6}\\.jpg"))) {
                File(frameDir, "frame_%06d.jpg")
            } else {
                // Fallback to using first file directly
                frameFiles.first()
            }

            Log.d("JAVEEncoder", "Input pattern: ${inputPattern.absolutePath}")

            val encoder = Encoder()
            val multimediaObject = MultimediaObject(inputPattern)

            val videoAttrs = VideoAttributes()
            videoAttrs.setCodec("libx264")
            videoAttrs.setBitRate(5000000) // 5 Mbps
            videoAttrs.setFrameRate(fps)
            videoAttrs.setSize(VideoSize(width, height))
            videoAttrs.setX264Profile(X264_PROFILE.BASELINE)

            val encodingAttrs = EncodingAttributes().apply {
                setInputFormat("image2")
                setOutputFormat("mp4")
                setVideoAttributes(videoAttrs)
                // Explicitly set frame rate for image sequences
                setDecodingThreads(1)
            }

            // Progress listener
            val progressListener = object : EncoderProgressListener {
                override fun sourceInfo(info: ws.schild.jave.info.MultimediaInfo?) {}
                override fun progress(permil: Int) {
                    onProgress?.invoke(permil / 10) // Convert from per-mille to percentage
                }
                override fun message(message: String?) {
                    Log.d("JAVEEncoder", "JAVE message: $message")
                }
            }

            encoder.encode(multimediaObject, tempVideo, encodingAttrs, progressListener)

            // Now convert MP4 to WebP
            val webpEncoder = Encoder()
            val mp4Object = MultimediaObject(tempVideo)

            val webpVideoAttrs = VideoAttributes()
            webpVideoAttrs.setCodec("libwebp")
            webpVideoAttrs.setBitRate((quality * 50000).coerceIn(100000, 5000000)) // Quality to bitrate
            webpVideoAttrs.setFrameRate(fps)
            webpVideoAttrs.setSize(VideoSize(width, height))

            val webpEncodingAttrs = EncodingAttributes().apply {
                setOutputFormat("webp")
                setVideoAttributes(webpVideoAttrs)
            }

            webpEncoder.encode(mp4Object, outputFile, webpEncodingAttrs)

            // Clean up temp file
            tempVideo.delete()

            if (outputFile.exists()) {
                Log.d("JAVEEncoder", "WebP encoding successful: ${outputFile.absolutePath}")
                onProgress?.invoke(100)
                Result.success(outputFile)
            } else {
                Result.failure(Exception("WebP file was not created"))
            }

        } catch (e: Exception) {
            Log.e("JAVEEncoder", "WebP encoding failed", e)
            Result.failure(e)
        }
    }

    fun encodeGIFFromFiles(
        frameFiles: List<File>,
        outputFile: File,
        fps: Int = 10,
        quality: Int = 50,
        onProgress: ((Int) -> Unit)? = null
    ): Result<File> {
        return try {
            Log.d("JAVEEncoder", "Encoding GIF with JAVE2: ${frameFiles.size} frames")

            if (frameFiles.isEmpty()) {
                return Result.failure(Exception("No frames to encode"))
            }

            // Get dimensions from first frame
            val firstImage = ImageIO.read(frameFiles[0])
            var width = firstImage.width
            var height = firstImage.height

            // Scale down for GIF if needed
            val maxDimension = when {
                quality >= 70 -> 800
                quality >= 40 -> 640
                else -> 480
            }

            if (width > maxDimension || height > maxDimension) {
                val scale = maxDimension.toDouble() / maxOf(width, height)
                width = (width * scale).toInt()
                height = (height * scale).toInt()
            }

            // JAVE2 needs actual files to exist - let's verify they do
            val frameDir = frameFiles.first().parentFile
            Log.d("JAVEEncoder", "Frame directory: ${frameDir.absolutePath}")
            Log.d("JAVEEncoder", "Frame files exist: ${frameFiles.all { it.exists() }}")
            Log.d("JAVEEncoder", "First frame: ${frameFiles.first().name}")

            // For JAVE2, we need to use the actual frame pattern
            val firstFrameName = frameFiles.first().name
            val inputPattern = if (firstFrameName.matches(Regex("frame_\\d{6}\\.jpg"))) {
                File(frameDir, "frame_%06d.jpg")
            } else {
                // Fallback to using first file directly
                frameFiles.first()
            }

            Log.d("JAVEEncoder", "Input pattern: ${inputPattern.absolutePath}")

            val encoder = Encoder()
            val multimediaObject = MultimediaObject(inputPattern)

            val videoAttrs = VideoAttributes()
            videoAttrs.setCodec("gif")
            videoAttrs.setFrameRate(fps)
            videoAttrs.setSize(VideoSize(width, height))

            val encodingAttrs = EncodingAttributes().apply {
                // Check if using pattern or single file
                if (inputPattern.name.contains("%")) {
                    setInputFormat("image2")
                } else {
                    // For single file, we need to handle differently
                    Log.d("JAVEEncoder", "Warning: Using single file input, may not work correctly")
                }
                setOutputFormat("gif")
                setVideoAttributes(videoAttrs)
            }

            // Progress listener
            val progressListener = object : EncoderProgressListener {
                override fun sourceInfo(info: ws.schild.jave.info.MultimediaInfo?) {}
                override fun progress(permil: Int) {
                    onProgress?.invoke(permil / 10)
                }
                override fun message(message: String?) {
                    Log.d("JAVEEncoder", "JAVE message: $message")
                }
            }

            encoder.encode(multimediaObject, outputFile, encodingAttrs, progressListener)

            if (outputFile.exists()) {
                Log.d("JAVEEncoder", "GIF encoding successful: ${outputFile.absolutePath}")
                onProgress?.invoke(100)
                Result.success(outputFile)
            } else {
                Result.failure(Exception("GIF file was not created"))
            }

        } catch (e: Exception) {
            Log.e("JAVEEncoder", "GIF encoding failed", e)
            Result.failure(e)
        }
    }

    /**
     * Check if JAVE2 is available
     */
    fun isAvailable(): Boolean {
        return try {
            val locator = DefaultFFMPEGLocator()
            val path = locator.getExecutablePath()
            path.isNotEmpty()
        } catch (e: Exception) {
            Log.e("JAVEEncoder", "JAVE2 not available", e)
            false
        }
    }

    /**
     * Get the FFmpeg executable path from JAVE2
     */
    fun getFFmpegPath(): String? {
        return try {
            val locator = DefaultFFMPEGLocator()
            locator.getExecutablePath()
        } catch (e: Exception) {
            Log.e("JAVEEncoder", "Failed to get FFmpeg path from JAVE2", e)
            null
        }
    }
}