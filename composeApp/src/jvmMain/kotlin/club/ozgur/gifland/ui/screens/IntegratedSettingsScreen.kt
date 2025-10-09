package club.ozgur.gifland.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import club.ozgur.gifland.core.RecorderSettings
import club.ozgur.gifland.core.OutputFormat as CoreOutputFormat
import club.ozgur.gifland.domain.model.OutputFormat
import club.ozgur.gifland.domain.model.SettingsTab
import club.ozgur.gifland.domain.model.AppSettings
import club.ozgur.gifland.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import org.koin.compose.getKoin

/**
 * Integrated settings screen that connects QualitySettingsScreen with the new architecture.
 * Bridges RecorderSettings with AppSettings through the SettingsViewModel.
 */
data object IntegratedSettingsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val koin = getKoin()
        val settingsViewModel = remember { koin.get<SettingsViewModel>() }
        val scope = rememberCoroutineScope()

        val settings by settingsViewModel.settings.collectAsState()

        // Convert AppSettings to RecorderSettings for the existing UI
        val recorderSettings = remember(settings) {
            RecorderSettings(
                fps = settings.defaultFps,
                quality = settings.defaultQuality,
                format = convertOutputFormat(settings.defaultFormat),
                maxDuration = settings.defaultMaxDuration,
                scale = settings.captureScale,
                fastGifPreview = false // This setting doesn't exist in AppSettings yet
            )
        }

        // Track local state for the settings
        var currentFps by remember(settings) { mutableStateOf(settings.defaultFps) }
        var currentQuality by remember(settings) { mutableStateOf(settings.defaultQuality) }
        var currentFormat by remember(settings) { mutableStateOf(settings.defaultFormat) }
        var fastGif by remember { mutableStateOf(false) }
        var showCountdown by remember(settings) { mutableStateOf(settings.showCountdown) }
        var countdownDuration by remember(settings) { mutableStateOf(settings.countdownDuration) }
        var captureMouseCursor by remember(settings) { mutableStateOf(settings.captureMouseCursor) }

        // Tab selection
        var selectedTab by remember { mutableStateOf(SettingsTab.General) }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = {
                            // Apply settings when navigating back
                            scope.launch {
                                settingsViewModel.updateCaptureSettings(
                                    fps = currentFps,
                                    quality = currentQuality,
                                    format = currentFormat
                                )
                                settingsViewModel.toggleCountdown(showCountdown)
                                settingsViewModel.updateCountdownDuration(countdownDuration)
                                settingsViewModel.toggleMouseCursor(captureMouseCursor)
                                settingsViewModel.applySettings()
                            }
                            navigator.pop()
                        }) {
                            Text("←", fontSize = 24.sp)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color(0xFF3A7BD5),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = Color(0xFFF5F5F5)
                ) {
                    SettingsTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.name) }
                        )
                    }
                }

                // Content based on selected tab
                when (selectedTab) {
                    SettingsTab.General -> GeneralSettingsContent(
                        settings = settings,
                        viewModel = settingsViewModel,
                        modifier = Modifier.fillMaxSize()
                    )

                    SettingsTab.Capture -> CaptureSettingsContent(
                        currentFps = currentFps,
                        currentQuality = currentQuality,
                        currentFormat = currentFormat,
                        fastGif = fastGif,
                        showCountdown = showCountdown,
                        countdownDuration = countdownDuration,
                        captureMouseCursor = captureMouseCursor,
                        onFpsChange = { currentFps = it },
                        onQualityChange = { currentQuality = it },
                        onFormatChange = { currentFormat = it },
                        onFastGifChange = { fastGif = it },
                        onShowCountdownChange = { showCountdown = it },
                        onCountdownDurationChange = { countdownDuration = it },
                        onCaptureMouseCursorChange = { captureMouseCursor = it },
                        modifier = Modifier.fillMaxSize()
                    )

                    SettingsTab.Export -> ExportSettingsContent(
                        settings = settings,
                        viewModel = settingsViewModel,
                        modifier = Modifier.fillMaxSize()
                    )

                    SettingsTab.Shortcuts -> ShortcutsSettingsContent(
                        settings = settings,
                        viewModel = settingsViewModel,
                        modifier = Modifier.fillMaxSize()
                    )

                    SettingsTab.Integrations -> IntegrationsSettingsContent(
                        settings = settings,
                        viewModel = settingsViewModel,
                        modifier = Modifier.fillMaxSize()
                    )

                    SettingsTab.Advanced -> AdvancedSettingsContent(
                        settings = settings,
                        viewModel = settingsViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            // Reset to defaults
                            scope.launch {
                                settingsViewModel.resetToDefaults()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset to Defaults")
                    }

                    Button(
                        onClick = {
                            // Apply all pending changes
                            scope.launch {
                                settingsViewModel.updateCaptureSettings(
                                    fps = currentFps,
                                    quality = currentQuality,
                                    format = currentFormat
                                )
                                settingsViewModel.toggleCountdown(showCountdown)
                                settingsViewModel.updateCountdownDuration(countdownDuration)
                                settingsViewModel.toggleMouseCursor(captureMouseCursor)
                                settingsViewModel.applySettings()
                                navigator.pop()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C7ED6))
                    ) {
                        Text("Save Settings")
                    }
                }
            }
        }
    }

    /**
     * Convert between domain OutputFormat and core OutputFormat
     */
    private fun convertOutputFormat(format: OutputFormat): CoreOutputFormat {
        return when (format) {
            OutputFormat.GIF -> CoreOutputFormat.GIF
            OutputFormat.WEBP -> CoreOutputFormat.WEBP
            OutputFormat.MP4 -> CoreOutputFormat.MP4
        }
    }

    private fun convertCoreOutputFormat(format: CoreOutputFormat): OutputFormat {
        return when (format) {
            CoreOutputFormat.GIF -> OutputFormat.GIF
            CoreOutputFormat.WEBP -> OutputFormat.WEBP
            CoreOutputFormat.MP4 -> OutputFormat.MP4
        }
    }
}

@Composable
private fun GeneralSettingsContent(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // System Integration
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("System Integration", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))

                    SwitchPreference(
                        title = "Launch on Startup",
                        description = "Start the application when system boots",
                        checked = settings.launchOnStartup,
                        onCheckedChange = {
                            scope.launch { viewModel.toggleLaunchOnStartup(it) }
                        }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    SwitchPreference(
                        title = "Show in System Tray",
                        description = "Display icon in system tray for quick access",
                        checked = settings.showInSystemTray,
                        onCheckedChange = {
                            scope.launch { viewModel.toggleSystemTray(it) }
                        }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    SwitchPreference(
                        title = "Minimize to Tray",
                        description = "Minimize to system tray instead of taskbar",
                        checked = settings.minimizeToTray,
                        onCheckedChange = {
                            scope.launch { viewModel.toggleMinimizeToTray(it) }
                        }
                    )
                }
            }

            // Theme
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))

                    Text("Theme", fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.theme == club.ozgur.gifland.domain.model.AppTheme.Light,
                            onClick = {
                                scope.launch {
                                    viewModel.changeTheme(club.ozgur.gifland.domain.model.AppTheme.Light)
                                }
                            },
                            label = { Text("Light") }
                        )
                        FilterChip(
                            selected = settings.theme == club.ozgur.gifland.domain.model.AppTheme.Dark,
                            onClick = {
                                scope.launch {
                                    viewModel.changeTheme(club.ozgur.gifland.domain.model.AppTheme.Dark)
                                }
                            },
                            label = { Text("Dark") }
                        )
                        FilterChip(
                            selected = settings.theme == club.ozgur.gifland.domain.model.AppTheme.System,
                            onClick = {
                                scope.launch {
                                    viewModel.changeTheme(club.ozgur.gifland.domain.model.AppTheme.System)
                                }
                            },
                            label = { Text("System") }
                        )
                    }
                }
            }
        }

        // Scrollbar
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun CaptureSettingsContent(
    currentFps: Int,
    currentQuality: Int,
    currentFormat: OutputFormat,
    fastGif: Boolean,
    showCountdown: Boolean,
    countdownDuration: Int,
    captureMouseCursor: Boolean,
    onFpsChange: (Int) -> Unit,
    onQualityChange: (Int) -> Unit,
    onFormatChange: (OutputFormat) -> Unit,
    onFastGifChange: (Boolean) -> Unit,
    onShowCountdownChange: (Boolean) -> Unit,
    onCountdownDurationChange: (Int) -> Unit,
    onCaptureMouseCursorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Format Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F6FF))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Output Format", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = currentFormat == OutputFormat.GIF,
                            onClick = { onFormatChange(OutputFormat.GIF) },
                            label = { Text("GIF") }
                        )
                        FilterChip(
                            selected = currentFormat == OutputFormat.WEBP,
                            onClick = { onFormatChange(OutputFormat.WEBP) },
                            label = { Text("WebP") }
                        )
                        FilterChip(
                            selected = currentFormat == OutputFormat.MP4,
                            onClick = { onFormatChange(OutputFormat.MP4) },
                            label = { Text("MP4") }
                        )
                    }
                }
            }

            // FPS Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F6FF))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("FPS", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("$currentFps", fontWeight = FontWeight.Medium, color = Color(0xFF2196F3))
                    }
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = currentFps.toFloat(),
                        onValueChange = { onFpsChange(it.toInt()) },
                        valueRange = 5f..60f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF2196F3),
                            activeTrackColor = Color(0xFF64B5F6)
                        )
                    )
                    Text(
                        "Higher FPS creates smoother recordings but larger files",
                        fontSize = 12.sp,
                        color = Color(0xFF7B8794)
                    )
                }
            }

            // Quality Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F6FF))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Quality", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("$currentQuality", fontWeight = FontWeight.Medium, color = Color(0xFF2196F3))
                    }
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = currentQuality.toFloat(),
                        onValueChange = { onQualityChange(it.toInt()) },
                        valueRange = 1f..50f,
                        steps = 48,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF2196F3),
                            activeTrackColor = Color(0xFF64B5F6)
                        )
                    )
                    Text(
                        "Higher quality preserves more detail but creates larger files",
                        fontSize = 12.sp,
                        color = Color(0xFF7B8794)
                    )
                }
            }

            // Recording Options
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Recording Options", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))

                    SwitchPreference(
                        title = "Fast GIF Preview",
                        description = "Lower quality for quick previews",
                        checked = fastGif,
                        onCheckedChange = onFastGifChange
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    SwitchPreference(
                        title = "Show Countdown",
                        description = "Display countdown before recording starts",
                        checked = showCountdown,
                        onCheckedChange = onShowCountdownChange
                    )

                    if (showCountdown) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Countdown Duration", fontSize = 14.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                (1..5).forEach { seconds ->
                                    FilterChip(
                                        selected = countdownDuration == seconds,
                                        onClick = { onCountdownDurationChange(seconds) },
                                        label = { Text("${seconds}s") }
                                    )
                                }
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    SwitchPreference(
                        title = "Capture Mouse Cursor",
                        description = "Include mouse cursor in recordings",
                        checked = captureMouseCursor,
                        onCheckedChange = onCaptureMouseCursorChange
                    )
                }
            }

            // Quick Presets
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Quick Presets", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onFpsChange(15)
                                onQualityChange(10)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Small", fontSize = 12.sp)
                                Text("Fast upload", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                onFpsChange(23)
                                onQualityChange(15)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Balanced", fontSize = 12.sp)
                                Text("Good quality", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                onFpsChange(30)
                                onQualityChange(30)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("High", fontSize = 12.sp)
                                Text("Best detail", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }

        // Scrollbar
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun ExportSettingsContent(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Export Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))

                    SwitchPreference(
                        title = "Auto Save",
                        description = "Automatically save recordings after capture",
                        checked = settings.autoSave,
                        onCheckedChange = {
                            scope.launch {
                                viewModel.updateExportSettings(autoSave = it)
                            }
                        }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    SwitchPreference(
                        title = "Auto Optimize",
                        description = "Optimize file size after recording",
                        checked = settings.autoOptimize,
                        onCheckedChange = {
                            scope.launch { viewModel.toggleAutoOptimize(it) }
                        }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    SwitchPreference(
                        title = "Generate Thumbnails",
                        description = "Create preview thumbnails for recordings",
                        checked = settings.generateThumbnails,
                        onCheckedChange = {
                            scope.launch { viewModel.toggleThumbnails(it) }
                        }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("File Management", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))

                    Text("Save Location", fontSize = 14.sp)
                    Text(settings.saveLocation, fontSize = 12.sp, color = Color(0xFF7B8794))
                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            // TODO: Open file picker
                        }
                    ) {
                        Text("Choose Location")
                    }

                    Spacer(Modifier.height(12.dp))

                    Text("File Naming Pattern", fontSize = 14.sp)
                    Text(settings.fileNamingPattern, fontSize = 12.sp, color = Color(0xFF7B8794))
                }
            }
        }

        // Scrollbar
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun ShortcutsSettingsContent(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Global Hotkeys", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))

                    settings.globalHotkeys.forEach { (action, key) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(action.name, fontSize = 14.sp)
                            Text(key, fontSize = 14.sp, color = Color(0xFF2196F3))
                        }
                        Divider()
                    }
                }
            }
        }

        // Scrollbar
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun IntegrationsSettingsContent(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Cloud Sync", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))

                    if (settings.cloudSync == null) {
                        Text(
                            "No cloud sync configured",
                            fontSize = 14.sp,
                            color = Color(0xFF7B8794)
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { /* TODO: Setup cloud sync */ }) {
                            Text("Setup Cloud Sync")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Provider", fontSize = 14.sp)
                            Text(settings.cloudSync.provider.name, fontSize = 14.sp, color = Color(0xFF2196F3))
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Share Targets", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))

                    if (settings.shareTargets.isEmpty()) {
                        Text(
                            "No share targets configured",
                            fontSize = 14.sp,
                            color = Color(0xFF7B8794)
                        )
                    } else {
                        settings.shareTargets.forEach { target ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(target.name, fontSize = 14.sp)
                                Switch(
                                    checked = target.enabled,
                                    onCheckedChange = { /* TODO: Toggle share target */ }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Scrollbar
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun AdvancedSettingsContent(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Performance", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))

                    SwitchPreference(
                        title = "Hardware Acceleration",
                        description = "Use GPU for encoding when available",
                        checked = settings.hardwareAcceleration,
                        onCheckedChange = {
                            scope.launch { viewModel.toggleHardwareAcceleration(it) }
                        }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Max Memory Usage", fontSize = 14.sp)
                        Text("${settings.maxMemoryUsageMB} MB", fontSize = 14.sp, color = Color(0xFF2196F3))
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Developer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))

                    SwitchPreference(
                        title = "Debug Mode",
                        description = "Show additional logging and debug information",
                        checked = settings.debugMode,
                        onCheckedChange = {
                            scope.launch { viewModel.toggleDebugMode(it) }
                        }
                    )
                }
            }
        }

        // Scrollbar
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun SwitchPreference(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(description, fontSize = 12.sp, color = Color(0xFF7B8794))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}