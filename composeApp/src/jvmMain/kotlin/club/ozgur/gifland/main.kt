package club.ozgur.gifland

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import club.ozgur.gifland.di.appModule
import club.ozgur.gifland.di.platformModule
import club.ozgur.gifland.domain.repository.StateRepository
import club.ozgur.gifland.domain.service.RecordingService
import club.ozgur.gifland.platform.GlobalHotkeyManager
import club.ozgur.gifland.platform.SystemTray
import club.ozgur.gifland.ui.components.AreaSelector
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.get

fun main() = application {
    // Initialize Koin dependency injection
    val koinApp = remember {
        startKoin {
            modules(appModule, platformModule)
        }
    }

    // Get dependencies
    val stateRepository = remember { koinApp.koin.get<StateRepository>() }
    val recordingService = remember { koinApp.koin.get<RecordingService>() }
    val scope = rememberCoroutineScope()

    // Window visibility state for minimize to tray
    var isWindowVisible by remember { mutableStateOf(true) }
    var shouldExit by remember { mutableStateOf(false) }

    // Initialize global hotkey manager
    val hotkeyManager = remember { GlobalHotkeyManager() }

    DisposableEffect(hotkeyManager) {
        hotkeyManager.initialize()

        // Register hotkeys with their actions
        val hotkeys = mapOf(
            club.ozgur.gifland.domain.model.HotkeyAction.StartRecording to "Ctrl+Shift+R",
            club.ozgur.gifland.domain.model.HotkeyAction.StopRecording to "Ctrl+Shift+S",
            club.ozgur.gifland.domain.model.HotkeyAction.PauseRecording to "Ctrl+Shift+P",
            club.ozgur.gifland.domain.model.HotkeyAction.SelectArea to "Ctrl+Shift+A",
            club.ozgur.gifland.domain.model.HotkeyAction.QuickCapture to "Ctrl+Shift+Q",
            club.ozgur.gifland.domain.model.HotkeyAction.ShowQuickPanel to "Ctrl+Shift+Space",
            club.ozgur.gifland.domain.model.HotkeyAction.ShowMainWindow to "Ctrl+Shift+M"
        )

        val callbacks = mapOf<club.ozgur.gifland.domain.model.HotkeyAction, () -> Unit>(
            club.ozgur.gifland.domain.model.HotkeyAction.StartRecording to {
                scope.launch {
                    isWindowVisible = true
                    recordingService.startRecording(null)
                }
                Unit
            },
            club.ozgur.gifland.domain.model.HotkeyAction.StopRecording to {
                scope.launch {
                    recordingService.stopRecording()
                }
                Unit
            },
            club.ozgur.gifland.domain.model.HotkeyAction.PauseRecording to {
                scope.launch {
                    recordingService.pauseRecording()
                }
                Unit
            },
            club.ozgur.gifland.domain.model.HotkeyAction.SelectArea to {
                val selector = AreaSelector { area ->
                    if (area != null) {
                        scope.launch {
                            // Start recording first, then show window
                            recordingService.startRecording(area)
                            isWindowVisible = true
                        }
                    }
                }
                selector.isVisible = true
                Unit
            },
            club.ozgur.gifland.domain.model.HotkeyAction.QuickCapture to {
                scope.launch {
                    isWindowVisible = true
                    recordingService.startRecording(null)
                }
                Unit
            },
            club.ozgur.gifland.domain.model.HotkeyAction.ShowQuickPanel to {
                scope.launch {
                    stateRepository.toggleQuickPanel()
                }
                Unit
            },
            club.ozgur.gifland.domain.model.HotkeyAction.ShowMainWindow to {
                isWindowVisible = true
                Unit
            }
        )

        hotkeyManager.registerHotkeys(hotkeys, callbacks)

        onDispose {
            hotkeyManager.dispose()
        }
    }

    val windowState = rememberWindowState(
        width = 360.dp,  // Compact width
        height = 480.dp, // Compact height
        position = WindowPosition(Alignment.Center)
    )

    // System tray support
    if (!shouldExit) {
        SystemTray(
            tooltip = "GIF/WebP/MP4 Recorder",
            onQuickCapture = {
                scope.launch {
                    // Start full screen recording immediately
                    isWindowVisible = true // Show window to see recording UI
                    recordingService.startRecording(null) // null means full screen
                }
            },
            onSelectArea = {
                // Show area selector WITHOUT showing main window first
                val selector = AreaSelector { area ->
                    if (area != null) {
                        scope.launch {
                            // Only show window AFTER area is selected
                            recordingService.startRecording(area)
                            isWindowVisible = true // Show window after recording starts
                        }
                    }
                }
                selector.isVisible = true
            },
            onShowQuickPanel = {
                scope.launch {
                    stateRepository.toggleQuickPanel()
                }
            },
            onShowMainWindow = {
                isWindowVisible = true
            },
            onOpenSettings = {
                scope.launch {
                    isWindowVisible = true
                    stateRepository.openSettings()
                }
            },
            onExit = {
                shouldExit = true
                exitApplication()
            }
        )
    }

    // Main window (can be hidden for tray-only mode)
    if (isWindowVisible && !shouldExit) {
        Window(
            onCloseRequest = {
                // Minimize to tray instead of exiting
                isWindowVisible = false
            },
            title = "GIF/WebP/MP4 Recorder",
            state = windowState,
            resizable = false,
            visible = isWindowVisible
        ) {
            App(windowState)
        }
    }
}