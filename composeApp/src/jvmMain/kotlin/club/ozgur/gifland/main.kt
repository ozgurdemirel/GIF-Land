package club.ozgur.gifland

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import club.ozgur.gifland.di.appModule
import club.ozgur.gifland.di.platformModule
import club.ozgur.gifland.domain.repository.StateRepository
import club.ozgur.gifland.domain.repository.WindowStateRepository
import club.ozgur.gifland.domain.repository.SettingsRepository
import club.ozgur.gifland.domain.model.AppState
import club.ozgur.gifland.domain.model.AppSettings
import club.ozgur.gifland.domain.service.RecordingService
import club.ozgur.gifland.core.ApplicationScope
import club.ozgur.gifland.util.DebounceManager
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
    val settingsRepository = remember { koinApp.koin.get<SettingsRepository>() }
    val appSettingsState = settingsRepository.settingsFlow.collectAsState(initial = settingsRepository.getCurrentSettings())
    val appSettings = appSettingsState.value

    val stateRepository = remember { koinApp.koin.get<StateRepository>() }
    val recordingService = remember { koinApp.koin.get<RecordingService>() }
    val windowStateRepository = remember { WindowStateRepository() }
    val debounceManager = remember { DebounceManager() }

    // Observe window visibility state
    val isWindowVisible by windowStateRepository.windowVisible.collectAsState()


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
                ApplicationScope.launch {
                    debounceManager.debounce(
                        DebounceManager.Companion.Keys.START_RECORDING,
                        DebounceManager.Companion.Delays.RECORDING
                    ) {
                        windowStateRepository.showWindow("hotkey_start_recording")
                        recordingService.startRecording(null)
                    }
                }
                Unit
            },
            club.ozgur.gifland.domain.model.HotkeyAction.StopRecording to {
                ApplicationScope.launch {
                    debounceManager.debounce(
                        DebounceManager.Companion.Keys.STOP_RECORDING,
                        DebounceManager.Companion.Delays.RECORDING
                    ) {
                        recordingService.stopRecording()
                    }
                }
                Unit
            },
            club.ozgur.gifland.domain.model.HotkeyAction.PauseRecording to {
                ApplicationScope.launch {
                    recordingService.pauseRecording()
                }
                Unit
            },
            club.ozgur.gifland.domain.model.HotkeyAction.SelectArea to {
                ApplicationScope.launch {
                    debounceManager.debounce(
                        DebounceManager.Companion.Keys.AREA_SELECT,
                        DebounceManager.Companion.Delays.UI
                    ) {
                        val selector = AreaSelector { area ->
                            if (area != null) {
                                ApplicationScope.launch {
                                    // Show window and start recording after area is selected
                                    windowStateRepository.showWindow("tray_area_selected")
                                    recordingService.startRecording(area)
                                }
                            }
                        }
                        selector.isVisible = true
                    }
                }
                Unit
            },
            club.ozgur.gifland.domain.model.HotkeyAction.QuickCapture to {
                ApplicationScope.launch {
                    debounceManager.debounce(
                        DebounceManager.Companion.Keys.START_RECORDING,
                        DebounceManager.Companion.Delays.RECORDING
                    ) {
                        windowStateRepository.showWindow("hotkey_quick_capture")
                        recordingService.startRecording(null)
                    }
                }
                Unit
            },
            club.ozgur.gifland.domain.model.HotkeyAction.ShowQuickPanel to {
                ApplicationScope.launch {
                    debounceManager.debounce(
                        DebounceManager.Companion.Keys.QUICK_PANEL_TOGGLE,
                        DebounceManager.Companion.Delays.UI
                    ) {
                        stateRepository.toggleQuickPanel()
                    }
                }
                Unit
            },
            club.ozgur.gifland.domain.model.HotkeyAction.ShowMainWindow to {
                ApplicationScope.launch {
                    debounceManager.debounce(
                        DebounceManager.Companion.Keys.SHOW_WINDOW,
                        DebounceManager.Companion.Delays.WINDOW
                    ) {
                        windowStateRepository.showWindow("hotkey_show_window")
                    }
                }
                Unit
            }
        )

        hotkeyManager.registerHotkeys(hotkeys, callbacks)
        onDispose {
            hotkeyManager.dispose()
        }
    }

    // Tray tooltip feedback based on countdown and recording state
    val appState by stateRepository.state.collectAsState()
    fun formatTime(s: Int): String = "%02d:%02d".format(s / 60, s % 60)
    val trayTooltip = when (val st = appState) {
        is AppState.Recording ->
            "Recording... ${formatTime(st.session.duration)} / ${formatTime(st.session.maxDuration)}"
        is AppState.PreparingRecording ->
            st.countdown?.let { "Recording starts in ${it}s..." } ?: "Preparing to record..."
        else -> "GIF/WebP/MP4 Recorder"
    }

    val windowState = rememberWindowState(
        width = 360.dp,  // Compact width
        height = 480.dp, // Compact height
        position = WindowPosition(Alignment.Center)
    )

    // System tray support
    if (!shouldExit) {
        val countdownSeconds = (appState as? AppState.PreparingRecording)?.countdown

        SystemTray(
            tooltip = trayTooltip,
            isRecording = appState is AppState.Recording,
            onQuickCapture = {
                ApplicationScope.launch {
                    try {
                        debounceManager.debounce(
                            DebounceManager.Companion.Keys.START_RECORDING,
                            DebounceManager.Companion.Delays.RECORDING
                        ) {
                            // Immediate recording (no delay) - keep window hidden
                            windowStateRepository.showWindow("tray_quick_capture")
                            recordingService.startRecording(null)
                        }
                    } catch (e: Exception) {
                        println("Error starting quick capture: ${e.message}")
                    }
                }
            },
            onSelectArea = {
                ApplicationScope.launch {
                    try {
                        debounceManager.debounce(
                            DebounceManager.Companion.Keys.AREA_SELECT,
                            DebounceManager.Companion.Delays.UI
                        ) {
                            // Show area selector WITHOUT showing main window first
                            val selector = AreaSelector { area ->
                                if (area != null) {
                                    ApplicationScope.launch {
                                        // Start recording silently after area is selected
                                        recordingService.startRecording(area)
                                    }
                                }
                            }
                            selector.isVisible = true
                        }
                    } catch (e: Exception) {
                        println("Error selecting area: ${e.message}")
                    }
                }
            },
            onShowQuickPanel = {
                ApplicationScope.launch {
                    try {
                        debounceManager.debounce(
                            DebounceManager.Companion.Keys.QUICK_PANEL_TOGGLE,
                            DebounceManager.Companion.Delays.UI
                        ) {
                            stateRepository.toggleQuickPanel()
                        }
                    } catch (e: Exception) {
                        println("Error toggling quick panel: ${e.message}")
                    }
                }
            },
            onShowMainWindow = {
                ApplicationScope.launch {
                    try {
                        debounceManager.debounce(
                            DebounceManager.Companion.Keys.SHOW_WINDOW,
                            DebounceManager.Companion.Delays.WINDOW
                        ) {
                            windowStateRepository.showWindow("tray_show_main")
                        }
                    } catch (e: Exception) {
                        println("Error showing main window: ${e.message}")
                    }
                }
            },
            onOpenSettings = {
                ApplicationScope.launch {
                    try {
                        windowStateRepository.showWindow("tray_open_settings")
                        stateRepository.openSettings()
                    } catch (e: Exception) {
                        println("Error opening settings: ${e.message}")
                    }
                }
            },
            onExit = {
                ApplicationScope.launch {
                    try {
                        ApplicationScope.shutdown()
                        shouldExit = true
                        exitApplication()
                    } catch (e: Exception) {
                        println("Error during exit: ${e.message}")
                    }
                }
            },
            countdownSeconds = countdownSeconds,
            defaultDelaySeconds = if (appSettings.showCountdown) appSettings.countdownDuration else 0,
            onStartCountdown = { seconds ->
                ApplicationScope.launch {
                    try {
                        debounceManager.debounce(
                            DebounceManager.Companion.Keys.COUNTDOWN_START,
                            DebounceManager.Companion.Delays.COUNTDOWN
                        ) {
                            stateRepository.startCountdownRecording(seconds) {
                                windowStateRepository.showWindow("tray_countdown_complete")
                                recordingService.startRecording(null)
                            }
                        }
                    } catch (e: Exception) {
                        println("Error starting countdown: ${e.message}")
                    }
                }
            },
            onCancelCountdown = {
                ApplicationScope.launch {
                    try {
                        debounceManager.debounce(
                            DebounceManager.Companion.Keys.COUNTDOWN_CANCEL,
                            DebounceManager.Companion.Delays.COUNTDOWN
                        ) {
                            stateRepository.cancelCountdown()
                        }
                    } catch (e: Exception) {
                        println("Error cancelling countdown: ${e.message}")
                    }
                }
            },
            onQuickRecordNoDelay = {
                ApplicationScope.launch {
                    try {
                        debounceManager.debounce(
                            DebounceManager.Companion.Keys.START_RECORDING,
                            DebounceManager.Companion.Delays.RECORDING
                        ) {
                            // Keep window hidden during quick record from tray
                            windowStateRepository.showWindow("tray_quick_record")
                            recordingService.startRecording(null)
                        }
                    } catch (e: Exception) {
                        println("Error starting quick record: ${e.message}")
                    }
                }
            }
        )
    }

    // Main window (can be hidden for tray-only mode)
    if (isWindowVisible && !shouldExit) {
        Window(
            onCloseRequest = {
                // Minimize to tray instead of exiting
                ApplicationScope.launch {
                    windowStateRepository.minimizeToTray()
                }
            },
            title = "GIF/WebP/MP4 Recorder",
            state = windowState,
            resizable = false,
            visible = isWindowVisible,
            transparent = true,
            undecorated = true
        ) {
            App(
                windowState,
                onMinimizeToTray = {
                    ApplicationScope.launch {
                        windowStateRepository.minimizeToTray()
                    }
                },
                onHideMainWindow = {
                    ApplicationScope.launch {
                        windowStateRepository.hideWindow("app_request_hide")
                    }
                },
                onShowMainWindow = {
                    ApplicationScope.launch {
                        windowStateRepository.showWindow("app_request_show")
                    }
                }
            )
        }
    }
}