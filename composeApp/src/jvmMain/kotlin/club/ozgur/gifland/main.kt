package club.ozgur.gifland

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.Alignment

fun main() = application {
    val windowState = rememberWindowState(
        width = 520.dp,
        height = 700.dp,
        position = androidx.compose.ui.window.WindowPosition(Alignment.Center)
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "WebP/MP4 Recorder",
        state = windowState,
        resizable = false  // Prevent manual resizing to control it programmatically
    ) {
        App(windowState)
    }
}