package club.ozgur.gifland.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import club.ozgur.gifland.LocalRecorder
import club.ozgur.gifland.core.ApplicationScope
import kotlinx.coroutines.launch
import club.ozgur.gifland.util.Log
import androidx.compose.ui.window.DialogProperties

import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import club.ozgur.gifland.LocalWindowControl
import club.ozgur.gifland.ui.components.DraggableWindowTitleBar

object RecordingScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val recorder = LocalRecorder.current
        val recordingState by recorder.state.collectAsState()
        val lastError by recorder.lastError.collectAsState()


        // İlk açılışta kayıt yapıyor olmalıyız
        var hasStartedRecording by remember { mutableStateOf(recordingState.isRecording) }

        // Süre dolunca veya kayıt durduğunda otomatik ana ekrana dön ve kaydet
        LaunchedEffect(recordingState.isRecording, recordingState.isSaving) {
            // Kayıt başladıktan sonra durduğunda (süre dolunca veya manuel stop)
            if (hasStartedRecording && !recordingState.isRecording) {
                // Hemen ana ekrana dön (saving MainScreen'de gösterilecek)
                navigator.pop()
            } else if (recordingState.isRecording) {
                hasStartedRecording = true
            }
        }

        // Pulse animasyonu
        val pulseAnimation = rememberInfiniteTransition()
        val pulse by pulseAnimation.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse
            )
        )

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .heightIn(max = 520.dp)
                        .shadow(12.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {

            // Draggable compact window title bar
            val windowControl = LocalWindowControl.current
            DraggableWindowTitleBar(
                title = "Recording",
                onClose = { windowControl.onMinimizeToTray() }
            )

            // Scrollable compact content with fade gradients
            val scrollState = rememberScrollState()
            val canScroll = scrollState.maxValue > 0

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(20.dp)
                    .drawWithContent {
                        drawContent()
                        if (canScroll) {
                            // Top fade when not at top
                            if (scrollState.value > 0) {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White,
                                            Color.White.copy(alpha = 0f)
                                        ),
                                        startY = 0f,
                                        endY = 50f
                                    ),
                                    topLeft = Offset.Zero,
                                    size = Size(size.width, 50f)
                                )
                            }
                            // Bottom fade when not at bottom
                            if (scrollState.value < scrollState.maxValue) {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0f),
                                            Color.White
                                        ),
                                        startY = size.height - 50f,
                                        endY = size.height
                                    ),
                                    topLeft = Offset(0f, size.height - 50f),
                                    size = Size(size.width, 50f)
                                )
                            }
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (lastError != null) {
                    AlertDialog(
                        onDismissRequest = { recorder.clearError() },
                        confirmButton = {
                            TextButton(onClick = { recorder.clearError() }) { Text("OK") }
                        },
                        title = { Text("Hata") },
                        text = { Text(lastError ?: "Bilinmeyen hata") },
                        properties = DialogProperties(dismissOnClickOutside = true)
                    )
                }
                // Kayıt başlığı
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFFF6B6B), Color(0xFFFFE66D))
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Pulse eden kayıt noktası
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .scale(pulse)
                                        .background(Color.Red, CircleShape)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "RECORDING",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "${recordingState.duration}s / ${recorder.settings.maxDuration}s",
                                        color = Color.White.copy(0.9f),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // İlerleme göstergesi (daha akıcı animasyon ve tema renk uyumu)
                            val targetProgress = (recordingState.duration / recorder.settings.maxDuration.toFloat()).coerceIn(0f, 1f)
                            val animatedProgress by animateFloatAsState(
                                targetValue = targetProgress,
                                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                                label = "record-progress"
                            )
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.size(40.dp),
                                color = MaterialTheme.colorScheme.error,
                                strokeWidth = 4.dp,
                                trackColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Kayıt bilgileri kartı
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                    ),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Büyük frame sayacı
                        Text(
                            text = "${recordingState.frameCount}",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "frames captured",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Tahmini boyut
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(32.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Size",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = String.format("%.1f MB",
                                        recordingState.estimatedSize / (1024.0 * 1024.0)),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Format",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = recorder.settings.format.name,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Yakalama Yöntemi etiketi
                        run {
                            val method = recordingState.captureMethod ?: "Bilinmiyor"
                            val details = recordingState.captureMethodDetails
                            val label = if (!details.isNullOrBlank()) "$method ($details)" else method
                            Text(
                                text = "Yakalama Yöntemi: $label",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Uyarı
                        if (recordingState.duration > recorder.settings.maxDuration * 0.8) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                            ) {
                                Text(
                                    text = "⚠️ Recording will stop soon!",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Kontrol butonları
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pause/Resume butonu
                    Button(
                        onClick = { recorder.pauseRecording() },
                        modifier = Modifier
                            .height(56.dp)
                            .weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        ),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                if (recordingState.isPaused) "▶" else "⏸",
                                fontSize = 20.sp
                            )
                            Text(
                                if (recordingState.isPaused) "Resume" else "Pause",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Stop butonu
                    Button(
                        onClick = {
                            // Hemen ana ekrana dön
                            navigator.pop()

                            // Arka planda kaydetme işlemini başlat - ApplicationScope kullan
                            // navigator.pop() sonrası bu ekranın scope'u iptal olur
                            ApplicationScope.launch {
                                val result = recorder.stopRecording()
                                result.onSuccess { file ->
                                    // Don't reset here - let MainScreen handle it
                                    // This ensures lastSavedFile is preserved
                                    Log.d("RecordingScreen", "Recording saved successfully: ${file.absolutePath}")
                                }.onFailure { error ->
                                    Log.e("RecordingScreen", "Save failed", error)
                                    // Don't reset on failure either - preserve state for debugging
                                }
                            }
                        },
                        modifier = Modifier
                            .height(56.dp)
                            .weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("⏹", fontSize = 20.sp)
                            Text(
                                "Stop",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Alt bilgi
                Text(
                    text = "Press Stop to save recording",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            }
            }

        }
    }
}