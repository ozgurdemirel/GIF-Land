package club.ozgur.gifland.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import club.ozgur.gifland.core.RecorderSettings

data class QualitySettingsScreen(
    val settings: RecorderSettings,
    val onSettingsChange: (RecorderSettings) -> Unit
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        var fps by remember { mutableStateOf(settings.fps) }
        var quality by remember { mutableStateOf(settings.quality) }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Quality Settings") },
                    navigationIcon = {
                        IconButton(onClick = {
                            // Debug: Print current variable values
                            println("========================================")
                            println("⬅️ GOING BACK WITH SETTINGS:")
                            println("========================================")
                            println("📹 Format: ${settings.format}")
                            println("🎯 FPS: $fps (was: ${settings.fps})")
                            println("✨ Quality: $quality (was: ${settings.quality})")
                            println("⏱️ Max Duration: ${settings.maxDuration}s")
                            println("========================================")

                            // Save settings before navigating back
                            onSettingsChange(settings.copy(fps = fps, quality = quality))
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
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // FPS Section
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
                            Text("$fps", fontWeight = FontWeight.Medium, color = Color(0xFF2196F3))
                        }
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = fps.toFloat(),
                            onValueChange = {
                                fps = it.toInt()
                                println("🎯 FPS changed to: $fps")
                            },
                            valueRange = 5f..60f,
                            steps = 11,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF2196F3),
                                activeTrackColor = Color(0xFF64B5F6),
                                inactiveTrackColor = Color(0xFFE0E0E0)
                            )
                        )
                        Text(
                            "Higher FPS creates smoother recordings but larger files",
                            fontSize = 12.sp,
                            color = Color(0xFF7B8794)
                        )
                    }
                }

                // Quality Section
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
                            Text("$quality${when {
                                quality > 45 -> " (LOSSLESS)"
                                quality > 35 -> " (ULTRA+)"
                                quality > 25 -> " (ULTRA)"
                                quality > 20 -> " (MAX)"
                                quality > 15 -> " (HIGH)"
                                else -> " (STANDARD)"
                            }}", fontWeight = FontWeight.Medium, color = Color(0xFF2196F3))
                        }
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = quality.toFloat(),
                            onValueChange = {
                                quality = it.toInt()
                                println("✨ Quality changed to: $quality")
                            },
                            valueRange = 1f..50f,
                            steps = 48,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF2196F3),
                                activeTrackColor = Color(0xFF64B5F6),
                                inactiveTrackColor = Color(0xFFE0E0E0)
                            )
                        )
                        Text(
                            "Higher quality preserves more detail but creates larger files",
                            fontSize = 12.sp,
                            color = Color(0xFF7B8794)
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
                                    fps = 15
                                    quality = 10
                                    println("🎯 PRESET: Small - FPS: 15, Quality: 10")
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
                                    fps = 23
                                    quality = 15
                                    println("🎯 PRESET: Balanced - FPS: 23, Quality: 15")
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
                                    fps = 30
                                    quality = 30
                                    println("🎯 PRESET: High - FPS: 30, Quality: 30")
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

                Spacer(Modifier.weight(1f))

                // Save Button
                Button(
                    onClick = {
                        // Debug: Print current variable values
                        println("========================================")
                        println("💾 SETTINGS SAVED:")
                        println("========================================")
                        println("📹 Format: ${settings.format}")
                        println("🎯 FPS: $fps (was: ${settings.fps})")
                        println("✨ Quality: $quality (was: ${settings.quality})")
                        println("⏱️ Max Duration: ${settings.maxDuration}s")
                        println("========================================")

                        onSettingsChange(settings.copy(fps = fps, quality = quality))
                        navigator.pop()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C7ED6))
                ) {
                    Text("Save Settings", fontSize = 16.sp)
                }
            }
        }
    }
}