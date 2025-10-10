package club.ozgur.gifland.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import kotlinx.coroutines.isActive

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
    // Improved blink effect with proper cleanup and memory leak prevention
    val blink = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(key1 = isRecording) {
        blink.value = false
        if (isRecording && iconRecording != null) {
            try {
                while (isRecording && this.isActive) {
                    blink.value = !blink.value
                    kotlinx.coroutines.delay(400)
                }
            } finally {
                // Ensure blink is reset on cancellation
                blink.value = false
            }
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