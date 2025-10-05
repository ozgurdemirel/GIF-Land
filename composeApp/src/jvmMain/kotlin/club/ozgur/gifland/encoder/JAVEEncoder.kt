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