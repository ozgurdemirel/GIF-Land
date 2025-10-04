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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import club.ozgur.gifland.util.Log
import androidx.compose.ui.window.DialogProperties

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
            color = Color(0xFFF8FAFC)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
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

                            // İlerleme göstergesi
                            CircularProgressIndicator(
                                progress = { recordingState.duration / recorder.settings.maxDuration.toFloat() },
                                modifier = Modifier.size(40.dp),
                                color = Color.White,
                                strokeWidth = 3.dp,
                                trackColor = Color.White.copy(0.3f)
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
                        containerColor = Color(0xFFFF7043).copy(alpha = 0.1f)
                    ),
                    border = BorderStroke(2.dp, Color(0xFFFF7043))
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
                            color = Color(0xFFFF7043)
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

                        Spacer(modifier = Modifier.height(32.dp))

                        // Uyarı
                        if (recordingState.duration > recorder.settings.maxDuration * 0.8) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFFC107).copy(alpha = 0.1f)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFFFC107))
                            ) {
                                Text(
                                    text = "⚠️ Recording will stop soon!",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    color = Color(0xFFF57C00),
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
                            containerColor = Color(0xFFFFC107)
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

                            // Arka planda kaydetme işlemini başlat - GlobalScope kullan çünkü
                            // navigator.pop() sonrası RecordingScreen scope iptal oluyor
                            GlobalScope.launch {
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
                            containerColor = Color(0xFFFF5252)
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