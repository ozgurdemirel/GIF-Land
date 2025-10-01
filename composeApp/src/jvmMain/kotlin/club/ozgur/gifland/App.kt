package club.ozgur.gifland

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.WindowState
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import club.ozgur.gifland.core.Recorder
import club.ozgur.gifland.ui.screens.MainScreen
import club.ozgur.gifland.util.WindowResizeEffect

// Create a CompositionLocal for the Recorder to ensure it's shared across screens
val LocalRecorder = compositionLocalOf<Recorder> { error("No Recorder provided") }

// Create a CompositionLocal for the WindowState
val LocalWindowState = compositionLocalOf<WindowState> { error("No WindowState provided") }

@Composable
fun App(windowState: WindowState) {
    // Create a single Recorder instance that persists across navigation
    val recorder = remember { Recorder() }

    // Observe recording state for window resizing
    val recordingState by recorder.state.collectAsState()

    // Handle window resizing based on recording state
    WindowResizeEffect(
        windowState = windowState,
        isRecording = recordingState.isRecording,
        animate = true
    )

    MaterialTheme {
        CompositionLocalProvider(
            LocalRecorder provides recorder,
            LocalWindowState provides windowState
        ) {
            Navigator(MainScreen) { navigator ->
                SlideTransition(navigator)
            }
        }
    }
}