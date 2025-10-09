import org.jetbrains.compose.desktop.application.dsl.TargetFormat

import org.gradle.internal.os.OperatingSystem

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

            // JAVE2 - FFmpeg Java wrapper (includes signed binaries for macOS)
            implementation("ws.schild:jave-core:3.5.0")

            // Platform-specific FFmpeg binaries
            val osName = System.getProperty("os.name").lowercase()
            val osArch = System.getProperty("os.arch").lowercase()
            when {
                osName.contains("mac") && (osArch == "aarch64" || osArch.contains("arm")) -> {
                    // Apple Silicon (M1/M2/M3)
                    implementation("ws.schild:jave-nativebin-osxm1:3.5.0")
                }
                osName.contains("mac") -> {
                    // Intel Mac
                    implementation("ws.schild:jave-nativebin-osx64:3.5.0")
                }
                osName.contains("win") -> {
                    implementation("ws.schild:jave-nativebin-win64:3.5.0")
                }
                else -> {
                    implementation("ws.schild:jave-nativebin-linux64:3.5.0")
                }
            }

            // JNA for ScreenCaptureKit bridge
            implementation("net.java.dev.jna:jna:5.14.0")
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
                val winIcon = project.file("src/jvmMain/resources/icons/app-icon.ico")
                if (winIcon.exists()) {
                    iconFile.set(winIcon)
                }
                menuGroup = "WebP Recorder"
                // Compose will include files under resources automatically
            }

            macOS {
                val macIcon = project.file("src/jvmMain/resources/icons/app-icon.icns")
                if (macIcon.exists()) {
                    iconFile.set(macIcon)
                }
                bundleID = "club.ozgur.gifland"

                // Required permissions for screen recording
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSScreenCaptureUsageDescription</key>
                        <string>WebP Recorder needs screen recording permission to capture your screen.</string>
                        <key>NSCameraUsageDescription</key>
                        <string>WebP Recorder does not use the camera.</string>
                        <key>NSMicrophoneUsageDescription</key>
                        <string>WebP Recorder does not use the microphone.</string>
                    """.trimIndent()
                }

                // Entitlements for hardened runtime
                entitlementsFile.set(project.file("entitlements.plist"))
            }

            linux {
                iconFile.set(project.file("src/jvmMain/resources/icons/app-icon.png"))
            }
        }
    }
}


// Build ScreenCaptureKit Swift bridge and copy into resources on macOS
val swiftSrc = layout.projectDirectory.dir("native/Swift")
val resourcesDir = layout.projectDirectory.dir("src/jvmMain/resources")

val buildSckBridgeMac by tasks.registering(Exec::class) {
    // Exec with dynamic script; mark as not CC-compatible
    notCompatibleWithConfigurationCache("Swift build script is dynamic")
    onlyIf { OperatingSystem.current().isMacOsX }
    workingDir = swiftSrc.asFile

    doFirst {
        file("${resourcesDir.asFile}/natives/darwin/arm64").mkdirs()
        file("${resourcesDir.asFile}/natives/darwin/x64").mkdirs()
        file(layout.buildDirectory.dir("native").get().asFile.absolutePath).mkdirs()
    }

    val nativeOut = layout.buildDirectory.dir("native").get().asFile.absolutePath
    commandLine("bash", "-c", """
        set -e
        swiftc -emit-library -module-name sck_bridge_swift -target arm64-apple-macos12 SCKBridge.swift \
          -o "$nativeOut/libsck_bridge_swift.arm64.dylib" \
          -framework ScreenCaptureKit -framework Foundation -framework CoreMedia -framework CoreVideo
        swiftc -emit-library -module-name sck_bridge_swift -target x86_64-apple-macos12 SCKBridge.swift \
          -o "$nativeOut/libsck_bridge_swift.x64.dylib" \
          -framework ScreenCaptureKit -framework Foundation -framework CoreMedia -framework CoreVideo
        cp "$nativeOut/libsck_bridge_swift.arm64.dylib" "${resourcesDir.asFile}/natives/darwin/arm64/libsck_bridge_swift.dylib"
        cp "$nativeOut/libsck_bridge_swift.x64.dylib" "${resourcesDir.asFile}/natives/darwin/x64/libsck_bridge_swift.dylib"
    """.trimIndent())
}

tasks.named("jvmProcessResources") {
    if (OperatingSystem.current().isMacOsX) {
        dependsOn(buildSckBridgeMac)
    }
}
