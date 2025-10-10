package club.ozgur.gifland.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.*
import java.awt.event.ActionListener
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

/**
 * Manages the system tray functionality for the lightweight agent mode.
 * Provides quick access to recording functions without the main window.
 */
class SystemTrayManager(
    private val onQuickCapture: () -> Unit = {},
    private val onSelectArea: () -> Unit = {},
    private val onShowQuickPanel: () -> Unit = {},
    private val onShowMainWindow: () -> Unit = {},
    private val onOpenSettings: () -> Unit = {},
    private val onExit: () -> Unit = {}
) {
    private var systemTray: SystemTray? = null
    private var trayIcon: TrayIcon? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * Initialize and show the system tray icon
     */
    fun initialize() {
        if (!SystemTray.isSupported()) {
            println("System tray is not supported on this platform")
            return
        }

        SwingUtilities.invokeLater {
            try {
                systemTray = SystemTray.getSystemTray()

                // Create tray icon
                val icon = createTrayIcon()
                trayIcon = TrayIcon(icon, "GIF/WebP Recorder", createPopupMenu())

                // Set properties
                trayIcon?.isImageAutoSize = true
                trayIcon?.addActionListener {
                    // Double-click on tray icon shows quick panel
                    scope.launch { onShowQuickPanel() }
                }

                // Add to system tray
                systemTray?.add(trayIcon)

                // Show initial notification
                showNotification(
                    "GIF/WebP Recorder",
                    "Running in background. Click for quick access.",
                    TrayIcon.MessageType.INFO
                )
            } catch (e: Exception) {
                println("Failed to initialize system tray: ${e.message}")
            }
        }
    }

    /**
     * Create the popup menu for the system tray
     */
    private fun createPopupMenu(): PopupMenu {
        val popup = PopupMenu()

        // Quick Capture
        val quickCaptureItem = MenuItem("Quick Capture (Full Screen)")
        quickCaptureItem.addActionListener {
            scope.launch { onQuickCapture() }
        }
        popup.add(quickCaptureItem)

        // Select Area
        val selectAreaItem = MenuItem("Select Area...")
        selectAreaItem.addActionListener {
            scope.launch { onSelectArea() }
        }
        popup.add(selectAreaItem)

        popup.addSeparator()

        // Recent Recordings submenu
        val recentMenu = Menu("Recent Recordings")
        recentMenu.add(MenuItem("(No recent recordings)"))
        popup.add(recentMenu)

        popup.addSeparator()

        // Show Quick Panel
        val quickPanelItem = MenuItem("Show Quick Panel")
        quickPanelItem.font = Font(quickPanelItem.font.name, Font.BOLD, quickPanelItem.font.size)
        quickPanelItem.addActionListener {
            scope.launch { onShowQuickPanel() }
        }
        popup.add(quickPanelItem)

        // Show Main Window
        val mainWindowItem = MenuItem("Open Main Window")
        mainWindowItem.addActionListener {
            scope.launch { onShowMainWindow() }
        }
        popup.add(mainWindowItem)

        popup.addSeparator()

        // Settings
        val settingsItem = MenuItem("Settings...")
        settingsItem.addActionListener {
            scope.launch { onOpenSettings() }
        }
        popup.add(settingsItem)

        popup.addSeparator()

        // Exit
        val exitItem = MenuItem("Exit")
        exitItem.addActionListener {
            scope.launch { onExit() }
        }
        popup.add(exitItem)

        return popup
    }

    /**
     * Create the tray icon image
     */
    private fun createTrayIcon(): Image {
        return try {
            // Try to load from resources
            val iconStream = javaClass.getResourceAsStream("/icons/tray-icon.png")
            if (iconStream != null) {
                ImageIO.read(iconStream)
            } else {
                // Create a default icon if resource not found
                createDefaultIcon()
            }
        } catch (e: Exception) {
            createDefaultIcon()
        }
    }

    /**
     * Create a default tray icon programmatically
     */
    private fun createDefaultIcon(): Image {
        val size = 16
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()

        // Enable anti-aliasing
        g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        )

        // Draw a simple recording icon that adapts to theme
        val dark = try { club.ozgur.gifland.ui.theme.ThemeManager.isDark } catch (_: Exception) { false }
        g2d.color = Color(255, 67, 67) // Red color
        g2d.fillOval(2, 2, size - 4, size - 4)

        // Add a center dot with contrasting color depending on theme
        g2d.color = if (dark) Color(0xFFEEEEEE.toInt()) else Color.WHITE
        g2d.fillOval(6, 6, size - 12, size - 12)

        g2d.dispose()
        return image
    }

    /**
     * Update the tray icon to show recording state
     */
    fun setRecordingState(isRecording: Boolean) {
        SwingUtilities.invokeLater {
            if (isRecording) {
                trayIcon?.image = createRecordingIcon()
                trayIcon?.toolTip = "Recording in progress..."
            } else {
                trayIcon?.image = createTrayIcon()
                trayIcon?.toolTip = "GIF/WebP Recorder"
            }
        }
    }

    /**
     * Create an animated recording icon
     */
    private fun createRecordingIcon(): Image {
        val size = 16
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()

        // Enable anti-aliasing
        g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        )

        // Draw a pulsing red circle for recording
        g2d.color = Color(255, 0, 0) // Bright red
        g2d.fillOval(1, 1, size - 2, size - 2)

        // Add a white ring
        g2d.color = Color.WHITE
        g2d.setStroke(BasicStroke(2f))
        g2d.drawOval(2, 2, size - 5, size - 5)

        g2d.dispose()
        return image
    }

    /**
     * Show a system tray notification
     */
    fun showNotification(
        title: String,
        message: String,
        type: TrayIcon.MessageType = TrayIcon.MessageType.INFO
    ) {
        SwingUtilities.invokeLater {
            trayIcon?.displayMessage(title, message, type)
        }
    }

    /**
     * Update the recent recordings menu
     */
    fun updateRecentRecordings(recordings: List<RecordingInfo>) {
        SwingUtilities.invokeLater {
            val popup = trayIcon?.popupMenu ?: return@invokeLater

            // Find and update the recent recordings menu
            for (i in 0 until popup.itemCount) {
                val item = popup.getItem(i)
                if (item is Menu && item.label == "Recent Recordings") {
                    item.removeAll()

                    if (recordings.isEmpty()) {
                        item.add(MenuItem("(No recent recordings)"))
                    } else {
                        recordings.take(10).forEach { recording ->
                            val menuItem = MenuItem(recording.name)
                            menuItem.addActionListener {
                                scope.launch { recording.onClick() }
                            }
                            item.add(menuItem)
                        }
                    }
                    break
                }
            }
        }
    }

    /**
     * Remove the system tray icon
     */
    fun dispose() {
        SwingUtilities.invokeLater {
            trayIcon?.let { icon ->
                systemTray?.remove(icon)
            }
        }
    }

    /**
     * Data class for recent recording menu items
     */
    data class RecordingInfo(
        val name: String,
        val path: String,
        val onClick: () -> Unit = {}
    )
}

/**
 * Composable function to integrate system tray with Compose Desktop
 */
@Composable
fun ApplicationScope.SystemTray(
    icon: Painter = painterResource("icons/tray-icon.png"),
    iconRecording: Painter? = null,
    isRecording: Boolean = false,
    tooltip: String = "GIF/WebP/MP4 Recorder",
    onQuickCapture: () -> Unit = {},
    onSelectArea: () -> Unit = {},
    onShowQuickPanel: () -> Unit = {},
    onShowMainWindow: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onExit: () -> Unit = {},
    countdownSeconds: Int? = null,
    defaultDelaySeconds: Int = 3,
    onStartCountdown: (Int) -> Unit = {},
    onCancelCountdown: () -> Unit = {},
    onQuickRecordNoDelay: () -> Unit = onQuickCapture
) {
    // Simple blink effect when recording and a recording icon is provided
    var blink = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(isRecording) {
        blink.value = false
        if (isRecording && iconRecording != null) {
            while (isRecording) {
                blink.value = !blink.value
                kotlinx.coroutines.delay(400)
            }
            blink.value = false
        }
    }

    // Create a painter that overlays a blinking red recording dot on the base icon
    fun overlayRecordingPainter(base: Painter, showDot: Boolean): Painter {
        if (!showDot) return base
        return object : Painter() {
            override val intrinsicSize = base.intrinsicSize
            override fun androidx.compose.ui.graphics.drawscope.DrawScope.onDraw() {
                with(base) { draw(size) }
                val r = (size.minDimension * 0.18f).coerceAtLeast(1f)
                val pad = size.minDimension * 0.10f
                val center = androidx.compose.ui.geometry.Offset(size.width - r - pad, size.height - r - pad)
                // White ring for contrast
                drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = r * 1.4f, center = center)
                // Red dot
                drawCircle(color = androidx.compose.ui.graphics.Color(0xFFFF3B30), radius = r, center = center)
            }
        }
    }

    val currentIcon = overlayRecordingPainter(icon, isRecording && blink.value)

    val trayState = rememberTrayState()

    Tray(
        icon = currentIcon,
        state = trayState,
        tooltip = tooltip,
        onAction = {
            // Double-click action
            onShowQuickPanel()
        },
        menu = {
            // Countdown-aware toggle item
            Item(
                if (countdownSeconds != null) "Cancel countdown (${countdownSeconds}s)" else "Start Recording (${defaultDelaySeconds}s delay)",
                onClick = { if (countdownSeconds != null) onCancelCountdown() else onStartCountdown(defaultDelaySeconds) }
            )
            // Immediate recording option
            Item(
                "Quick Record (No delay)",
                onClick = onQuickRecordNoDelay
            )
            Item(
                "Select Area...",
                onClick = onSelectArea
            )
            Separator()
            Item(
                "Show Quick Panel",
                onClick = onShowQuickPanel
            )
            Item(
                "Open Main Window",
                onClick = onShowMainWindow
            )
            Separator()
            Item(
                "Settings...",
                onClick = onOpenSettings
            )
            Separator()
            Item(
                "Exit",
                onClick = onExit
            )
        }
    )
}