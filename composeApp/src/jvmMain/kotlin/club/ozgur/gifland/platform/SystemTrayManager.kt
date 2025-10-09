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

        // Draw a simple recording icon
        g2d.color = Color(255, 67, 67) // Red color
        g2d.fillOval(2, 2, size - 4, size - 4)

        // Add a white center dot
        g2d.color = Color.WHITE
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
    tooltip: String = "GIF/WebP Recorder",
    onQuickCapture: () -> Unit = {},
    onSelectArea: () -> Unit = {},
    onShowQuickPanel: () -> Unit = {},
    onShowMainWindow: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onExit: () -> Unit = {}
) {
    val trayState = rememberTrayState()

    Tray(
        icon = icon,
        state = trayState,
        tooltip = tooltip,
        onAction = {
            // Double-click action
            onShowQuickPanel()
        },
        menu = {
            Item(
                "Quick Capture",
                onClick = onQuickCapture
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