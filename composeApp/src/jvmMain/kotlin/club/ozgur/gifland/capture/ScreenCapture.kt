package club.ozgur.gifland.capture

import club.ozgur.gifland.ui.components.CaptureArea
import club.ozgur.gifland.util.Log
import club.ozgur.gifland.util.debugId
import java.awt.*
import java.io.File
import javax.imageio.ImageIO

/**
 * Captures screenshots from a specific monitor using AWT `Robot`.
 *
 * Why per-monitor `Robot`?
 * - On mixed-DPI setups, `Robot(GraphicsDevice)` ensures the OS maps screen
 *   coordinates correctly for the chosen device, avoiding double scaling.
 * - We therefore keep `CaptureArea` in unscaled absolute screen coordinates.
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
        return runCatching {
            Log.d("ScreenCapture", "takeScreenshot(area=${area != null}) request")
            val image = if (area != null) {
                // AREA CAPTURE
                // Determine which monitor the requested area belongs to.
                val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
                val areaCenter = Point(area.x + area.width / 2, area.y + area.height / 2)

                val targetScreen = screens.find { screen ->
                    val b = screen.defaultConfiguration.bounds
                    b.contains(areaCenter)
                } ?: screens.find { screen ->
                    val b = screen.defaultConfiguration.bounds
                    b.contains(Point(area.x, area.y))
                } ?: screens[0]

                Log.d("ScreenCapture", "Area center=$areaCenter targetScreen=${targetScreen.debugId()} bounds=${targetScreen.defaultConfiguration.bounds}")
                // Use a Robot bound to that monitor. We pass raw screen coords.
                val robot = Robot(targetScreen)
                robot.createScreenCapture(
                    Rectangle(area.x, area.y, area.width, area.height)
                )
            } else {
                // FULL-SCREEN CAPTURE
                // Choose the monitor under the current mouse position.
                val mouseLocation = MouseInfo.getPointerInfo().location
                val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices

                val targetScreen = screens.find { screen ->
                    val bounds = screen.defaultConfiguration.bounds
                    bounds.contains(mouseLocation)
                } ?: screens[0]

                Log.d("ScreenCapture", "Mouse=$mouseLocation targetScreen=${targetScreen.debugId()} bounds=${targetScreen.defaultConfiguration.bounds}")
                val bounds = targetScreen.defaultConfiguration.bounds
                val robot = Robot(targetScreen)
                robot.createScreenCapture(
                    Rectangle(bounds.x, bounds.y, bounds.width, bounds.height)
                )
            }

            val outputFile = File(
                System.getProperty("user.home") + "/Documents",
                "screen_${System.currentTimeMillis()}.png"
            )

            ImageIO.write(image, "png", outputFile)
            Log.d("ScreenCapture", "Screenshot saved to ${outputFile.absolutePath} size=${outputFile.length()} bytes")

            outputFile
        }.onFailure { e ->
            Log.e("ScreenCapture", "Error while capturing", e)
        }.getOrNull()
    }
}