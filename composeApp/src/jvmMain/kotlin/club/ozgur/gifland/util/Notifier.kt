package club.ozgur.gifland.util

import java.awt.*
import java.awt.image.BufferedImage
import java.io.File

/**
 * Simple system tray notifier. Falls back to println when SystemTray is not supported.
 */
object Notifier {
    private var trayIcon: TrayIcon? = null

    private fun ensureTray() {
        if (trayIcon != null) return
        if (!SystemTray.isSupported()) return
        val tray = SystemTray.getSystemTray()
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val icon = TrayIcon(image, "WebP Recorder")
        icon.isImageAutoSize = true
        tray.add(icon)
        trayIcon = icon
    }

    fun notifySaved(file: File) {
        val msg = "Kaydedildi: ${file.absolutePath}"
        if (SystemTray.isSupported()) {
            try {
                ensureTray()
                trayIcon?.displayMessage("WebP/MP4 Kaydedildi", msg, TrayIcon.MessageType.INFO)
            } catch (_: Exception) {
                println(msg)
            }
        } else {
            println(msg)
        }
    }
}



