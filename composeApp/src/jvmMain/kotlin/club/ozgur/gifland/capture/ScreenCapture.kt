package club.ozgur.gifland.capture

import club.ozgur.gifland.encoder.NativeEncoderSimple
import club.ozgur.gifland.ui.components.CaptureArea
import club.ozgur.gifland.util.Log
import club.ozgur.gifland.util.debugId
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Captures screenshots from a specific monitor using FFmpeg.
 */
class ScreenCapture {

    /**
     * Capture either a full-screen image of the monitor under the mouse
     * or a specific rectangle.
     *
     * @param area When non-null, the rectangle in absolute screen coordinates
     *             to capture. When null, captures the full monitor under the mouse.
     * @return Saved image file or null on failure.
     */
    fun takeScreenshot(area: CaptureArea? = null): File? {
        return takeScreenshotFFmpeg(area)
    }

    private fun takeScreenshotFFmpeg(area: CaptureArea? = null): File? {
        return runCatching {
            Log.d("ScreenCapture", "takeScreenshotFFmpeg(area=${area != null}) request")

            val ffmpegPath = NativeEncoderSimple.findFfmpeg()
            val outputFile = File(
                System.getProperty("user.home") + "/Documents",
                "screen_${System.currentTimeMillis()}.png"
            )

            val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            val mouseLocation = MouseInfo.getPointerInfo().location
            val targetScreen = screens.find { screen ->
                val bounds = screen.defaultConfiguration.bounds
                bounds.contains(mouseLocation)
            } ?: screens.firstOrNull() ?: return@runCatching null

            val screenIndex = screens.indexOf(targetScreen)

            val command = mutableListOf(
                ffmpegPath,
                "-f", "avfoundation",
                "-capture_cursor", "1",
                "-i", "$screenIndex",
                "-r", "1",
                "-vframes", "1"
            )

            if (area != null) {
                command.addAll(listOf(
                    "-vf", "crop=${area.width}:${area.height}:${area.x}:${area.y}"
                ))
            }

            command.add(outputFile.absolutePath)

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val success = process.waitFor(5, TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().use { it.readText() }

            if (success && process.exitValue() == 0) {
                Log.d("ScreenCapture", "Screenshot saved to ${outputFile.absolutePath} size=${outputFile.length()} bytes")
                outputFile
            } else {
                Log.e("ScreenCapture", "FFmpeg failed with output: $output")
                null
            }
        }.onFailure { e ->
            Log.e("ScreenCapture", "Error while capturing with FFmpeg", e)
        }.getOrNull()
    }
}
