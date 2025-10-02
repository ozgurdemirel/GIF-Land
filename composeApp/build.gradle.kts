import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()
    
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.animation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            
            // Coroutines
            implementation(libs.kotlinx.coroutinesCore)
            
            // Serialization
            implementation(libs.kotlinx.serializationJson)
            
            // DateTime
            implementation(libs.kotlinx.datetime)

            // Navigation
            val voyagerVersion = "1.0.0"
            implementation("cafe.adriel.voyager:voyager-navigator:$voyagerVersion")
            implementation("cafe.adriel.voyager:voyager-transitions:$voyagerVersion")
            implementation("cafe.adriel.voyager:voyager-tab-navigator:$voyagerVersion")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)

            // Global mouse/keyboard tracking
            implementation(libs.jnativehook)

            // Video/WebP & MP4 processing - now using native ffmpeg
            // implementation(libs.javacv.platform) // REMOVED - using native encoder

            // Note: Robot API is part of Java AWT, no additional dependency needed
            // We'll use java.awt.Robot for screen capture
        }
    }
}

compose.desktop {
    application {
        mainClass = "club.ozgur.gifland.MainKt"

        // JVM arguments for better performance
        jvmArgs += listOf(
            "-Xmx2G",          // Reduced from 4GB since we removed JavaCV
            "-Xms256M",        // Reduced initial heap
            "-XX:+UseG1GC",    // Use G1 garbage collector for better performance
            "-XX:MaxGCPauseMillis=100", // Target max GC pause
            "-XX:+HeapDumpOnOutOfMemoryError", // Create heap dump on OOM
            "-Djava.awt.headless=false" // Ensure GUI support
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "WebP Recorder"
            packageVersion = "1.0.0"

            description = "Screen to WebP/MP4 Recorder"
            copyright = "© 2024 Ozgur Club"
            vendor = "Ozgur Club"


            windows {
                iconFile.set(project.file("src/jvmMain/resources/icons/app-icon.ico"))
                menuGroup = "WebP Recorder"
                // Bundle the ffmpeg.exe into resources path used at runtime
                // Compose will include files under resources automatically
            }

            macOS {
                iconFile.set(project.file("src/jvmMain/resources/icons/app-icon.icns"))
                bundleID = "club.ozgur.gifland"
            }

            linux {
                iconFile.set(project.file("src/jvmMain/resources/icons/app-icon.png"))
            }
        }
    }
}
