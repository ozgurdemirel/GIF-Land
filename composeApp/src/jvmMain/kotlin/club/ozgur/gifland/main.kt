package club.ozgur.gifland

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import club.ozgur.gifland.di.appModule
import club.ozgur.gifland.di.platformModule
import club.ozgur.gifland.domain.repository.StateRepository
import club.ozgur.gifland.domain.repository.SettingsRepository
import club.ozgur.gifland.domain.model.AppSettings
import club.ozgur.gifland.domain.service.RecordingService
import club.ozgur.gifland.platform.GlobalHotkeyManager
import club.ozgur.gifland.platform.SystemTray
import club.ozgur.gifland.ui.components.AreaSelector
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val scope = rememberCoroutineScope()

    // Window visibility state for minimize to tray
    var isWindowVisible by remember { mutableStateOf(true) }
    // Countdown state for tray-based delayed recording
    var countdownSeconds by remember { mutableStateOf<Int?>(null) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }

    fun startCountdownRecording(seconds: Int = appSettings.countdownDuration) {
        countdownJob?.cancel()
        // If countdown disabled or 0, start immediately
        if (seconds <= 0) {
            scope.launch { recordingService.startRecording(null) }
            return
        }
        countdownJob = scope.launch {
            for (i in seconds downTo 1) {
                countdownSeconds = i
                delay(1000)
            }
            countdownSeconds = null
            // Start recording silently without showing the main window
            recordingService.startRecording(null)
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        countdownSeconds = null
    }

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

    // Tray tooltip feedback based on countdown and recording state
    val appState by stateRepository.state.collectAsState()
    fun formatTime(s: Int): String = "%02d:%02d".format(s / 60, s % 60)
    val trayTooltip = when (val st = appState) {
        is club.ozgur.gifland.domain.model.AppState.Recording ->
            "Recording... ${formatTime(st.session.duration)} / ${formatTime(st.session.maxDuration)}"
        else -> countdownSeconds?.let { "Recording starts in ${it}s..." } ?: "GIF/WebP/MP4 Recorder"
    }

    val windowState = rememberWindowState(
        width = 360.dp,  // Compact width
        height = 480.dp, // Compact height
        position = WindowPosition(Alignment.Center)
    )

    // System tray support
    if (!shouldExit) {
        SystemTray(
            tooltip = trayTooltip,
            isRecording = appState is club.ozgur.gifland.domain.model.AppState.Recording,
            onQuickCapture = {
                scope.launch {
                    // Immediate recording (no delay) - keep window hidden
                    recordingService.startRecording(null)
                }
            },
            onSelectArea = {
                // Show area selector WITHOUT showing main window first
                val selector = AreaSelector { area ->
                    if (area != null) {
                        scope.launch {
                            // Start recording silently after area is selected
                            recordingService.startRecording(area)
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
            },
            countdownSeconds = countdownSeconds,
            defaultDelaySeconds = if (appSettings.showCountdown) appSettings.countdownDuration else 0,
            onStartCountdown = { startCountdownRecording(it) },
            onCancelCountdown = { cancelCountdown() },
            onQuickRecordNoDelay = {
                scope.launch {
                    // Keep window hidden during quick record from tray
                    recordingService.startRecording(null)
                }
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
            visible = isWindowVisible,
            transparent = true,
            undecorated = true
        ) {
            App(
                windowState,
                onMinimizeToTray = { isWindowVisible = false },
                onHideMainWindow = { isWindowVisible = false },
                onShowMainWindow = { isWindowVisible = true }
            )
        }
    }
}