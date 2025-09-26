package club.ozgur.gifland

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import club.ozgur.gifland.core.Recorder
import club.ozgur.gifland.ui.screens.MainScreen

// Create a CompositionLocal for the Recorder to ensure it's shared across screens
val LocalRecorder = compositionLocalOf<Recorder> { error("No Recorder provided") }

@Composable
fun App() {
    // Create a single Recorder instance that persists across navigation
    val recorder = remember { Recorder() }

    MaterialTheme {
        CompositionLocalProvider(LocalRecorder provides recorder) {
            Navigator(MainScreen) { navigator ->
                SlideTransition(navigator)
            }
        }
    }
}