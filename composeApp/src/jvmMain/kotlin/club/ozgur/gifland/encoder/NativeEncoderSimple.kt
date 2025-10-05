package club.ozgur.gifland.encoder

import club.ozgur.gifland.core.RecorderSettings
import club.ozgur.gifland.util.Log
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * Simple native encoder that uses the Rust binary via command line
 */
object NativeEncoderSimple {

    private var bundledFfmpegPath: File? = null
    private var customFFmpegPath: String? = null

    fun setFFmpegPath(path: String) {
        customFFmpegPath = path
        Log.d("NativeEncoderSimple", "Custom FFmpeg path set: $path")
    }

    private fun getBundledFfmpeg(): File? {
        // First check if we have a custom FFmpeg path set (e.g., from JAVE2)
        if (!customFFmpegPath.isNullOrEmpty()) {
            val customFile = File(customFFmpegPath!!)
            if (customFile.exists()) {
                Log.d("NativeEncoderSimple", "Using custom FFmpeg: $customFFmpegPath")
                return customFile
            }
        }

        if (bundledFfmpegPath?.exists() == true) {
            return bundledFfmpegPath
        }

        return try {
            val osName = System.getProperty("os.name").lowercase()
            val isWindows = osName.contains("win")
            val bundledPath = when {
                isWindows -> "/native/windows/ffmpeg.exe"
                osName.contains("mac") || osName.contains("darwin") -> "/native/macos/ffmpeg"
                else -> "/native/macos/ffmpeg"
            }

            val resourceStream = NativeEncoderSimple::class.java.getResourceAsStream(bundledPath)
            if (resourceStream != null) {
                val suffix = if (isWindows) ".exe" else ""
                val tempFile = File(System.getProperty("java.io.tmpdir"), "gifland_ffmpeg_${System.currentTimeMillis()}$suffix")
                tempFile.deleteOnExit()
                Log.d("NativeEncoderSimple", "Extracting FFmpeg to: ${tempFile.absolutePath}")
                FFmpegDebugManager.updateExtractionPath(tempFile.absolutePath)

                resourceStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Update file size in debug info
                FFmpegDebugManager.updateFileSize(tempFile.length())

                // Ensure executable on Unix-like systems
                if (!isWindows) {
                    // Set executable permissions
                    runCatching {
                        val makeExecutable = ProcessBuilder("chmod", "+x", tempFile.absolutePath).start()
                        makeExecutable.waitFor()
                    }
                    if (!tempFile.canExecute()) {
                        tempFile.setExecutable(true, false)
                    }

                    // macOS specific: Try to make FFmpeg work without signing issues
                    if (osName.contains("mac") || osName.contains("darwin")) {
                        Log.d("NativeEncoderSimple", "Preparing FFmpeg for macOS execution...")

                        // Step 1: Remove all extended attributes including quarantine
                        runCatching {
                            Log.d("NativeEncoderSimple", "Removing extended attributes from FFmpeg")
                            val removeAttrs = ProcessBuilder("xattr", "-cr", tempFile.absolutePath).start()
                            val output = removeAttrs.inputStream.readBytes().decodeToString() +
                                        removeAttrs.errorStream.readBytes().decodeToString()
                            removeAttrs.waitFor(5, TimeUnit.SECONDS)
                            val exitCode = removeAttrs.exitValue()

                            FFmpegDebugManager.addCommand(
                                "xattr -cr ${tempFile.name}",
                                exitCode,
                                output.ifBlank { "Success - attributes removed" }
                            )

                            if (exitCode != 0) {
                                // Try alternative: remove specific com.apple.quarantine
                                val removeQuarantine = ProcessBuilder("xattr", "-d", "com.apple.quarantine", tempFile.absolutePath).start()
                                val quarantineOutput = removeQuarantine.inputStream.readBytes().decodeToString() +
                                                      removeQuarantine.errorStream.readBytes().decodeToString()
                                removeQuarantine.waitFor(5, TimeUnit.SECONDS)
                                val quarantineExitCode = removeQuarantine.exitValue()

                                FFmpegDebugManager.addCommand(
                                    "xattr -d com.apple.quarantine ${tempFile.name}",
                                    quarantineExitCode,
                                    quarantineOutput.ifBlank { "Success - quarantine removed" }
                                )
                            }
                        }.onFailure { e ->
                            Log.d("NativeEncoderSimple", "Could not remove attributes: ${e.message}")
                            FFmpegDebugManager.setError("Failed to remove attributes: ${e.message}")
                        }

                        // Step 2: Add local ad-hoc signature (creates new signature valid on THIS machine)
                        runCatching {
                            Log.d("NativeEncoderSimple", "Adding local ad-hoc signature to FFmpeg")

                            // First, remove any existing signature
                            val removeSignProcess = ProcessBuilder(
                                "codesign",
                                "--remove-signature",
                                tempFile.absolutePath
                            ).start()
                            removeSignProcess.waitFor(5, TimeUnit.SECONDS)

                            // Try simple ad-hoc signature (more compatible)
                            val signProcess = ProcessBuilder(
                                "codesign",
                                "--force",           // Replace any existing signature
                                "--sign", "-",       // Ad-hoc signature
                                tempFile.absolutePath
                            ).start()

                            val signOutput = signProcess.inputStream.readBytes().decodeToString() +
                                           signProcess.errorStream.readBytes().decodeToString()
                            val signed = signProcess.waitFor(10, TimeUnit.SECONDS)
                            val signExitCode = signProcess.exitValue()

                            FFmpegDebugManager.addCommand(
                                "codesign --force --sign - ${tempFile.name}",
                                signExitCode,
                                signOutput.ifBlank { "Success - ad-hoc signature added" }
                            )

                            if (signed && signExitCode == 0) {
                                Log.d("NativeEncoderSimple", "Successfully signed FFmpeg locally")
                            } else {
                                // If simple signing failed, try with more options
                                Log.d("NativeEncoderSimple", "Simple signing failed, trying with timestamp and hardened runtime...")

                                // Try WITHOUT hardened runtime (which can cause SIGKILL/error 137)
                                val advancedSignProcess = ProcessBuilder(
                                    "codesign",
                                    "--force",
                                    "--deep",
                                    "--sign", "-",
                                    "--timestamp=none",
                                    tempFile.absolutePath
                                ).start()

                                val advancedSignOutput = advancedSignProcess.inputStream.readBytes().decodeToString() +
                                                       advancedSignProcess.errorStream.readBytes().decodeToString()
                                advancedSignProcess.waitFor(10, TimeUnit.SECONDS)
                                val advancedSignExitCode = advancedSignProcess.exitValue()

                                FFmpegDebugManager.addCommand(
                                    "codesign --force --deep --sign - --timestamp=none ${tempFile.name}",
                                    advancedSignExitCode,
                                    advancedSignOutput.ifBlank { "Success - deep signature without hardened runtime" }
                                )
                            }
                        }.onFailure { e ->
                            Log.d("NativeEncoderSimple", "Could not sign binary (will try to run anyway): ${e.message}")
                            FFmpegDebugManager.setError("Failed to sign binary: ${e.message}")
                        }

                        // Step 3: Verify the binary is ready
                        runCatching {
                            val verifyProcess = ProcessBuilder("codesign", "-v", "-v", tempFile.absolutePath).start()
                            val verifyOutput = verifyProcess.inputStream.readBytes().decodeToString() +
                                             verifyProcess.errorStream.readBytes().decodeToString()
                            verifyProcess.waitFor(2, TimeUnit.SECONDS)
                            val verifyExitCode = verifyProcess.exitValue()

                            FFmpegDebugManager.addCommand(
                                "codesign -v -v ${tempFile.name}",
                                verifyExitCode,
                                verifyOutput.ifBlank { "Success - signature verified" }
                            )

                            if (verifyExitCode == 0) {
                                Log.d("NativeEncoderSimple", "FFmpeg signature verified successfully")
                                FFmpegDebugManager.updateVerification("✅ Signature valid and verified")
                            } else {
                                FFmpegDebugManager.updateVerification("⚠️ Signature verification failed (exit code: $verifyExitCode)")
                            }
                        }

                        // Get FFmpeg version and architecture info
                        runCatching {
                            val versionProcess = ProcessBuilder(tempFile.absolutePath, "-version").start()
                            val versionOutput = versionProcess.inputStream.bufferedReader().readLines()
                            val versionError = versionProcess.errorStream.bufferedReader().readLines()
                            versionProcess.waitFor(2, TimeUnit.SECONDS)
                            val versionExitCode = versionProcess.exitValue()

                            if (versionExitCode == 0 && versionOutput.isNotEmpty()) {
                                FFmpegDebugManager.updateFFmpegVersion(versionOutput.first())
                            } else if (versionExitCode == 134) {
                                // Error 134 detected during version check
                                Log.e("NativeEncoderSimple", "FFmpeg crashed with error 134 during version check")
                                FFmpegDebugManager.setError("FFmpeg error 134 - Signature issue detected. Trying additional fixes...")

                                // Try additional fix: remove code signature requirement
                                runCatching {
                                    Log.d("NativeEncoderSimple", "Attempting to bypass signature requirement...")
                                    val bypassProcess = ProcessBuilder(
                                        "codesign",
                                        "--remove-signature",
                                        tempFile.absolutePath
                                    ).start()
                                    bypassProcess.waitFor(5, TimeUnit.SECONDS)

                                    // Re-sign WITHOUT hardened runtime to avoid error 137
                                    val resignProcess = ProcessBuilder(
                                        "codesign",
                                        "--force",
                                        "--deep",
                                        "--sign", "-",
                                        tempFile.absolutePath
                                    ).start()
                                    resignProcess.waitFor(5, TimeUnit.SECONDS)

                                    FFmpegDebugManager.addCommand(
                                        "codesign --force --deep --sign - (without runtime)",
                                        resignProcess.exitValue(),
                                        "Attempting signature without hardened runtime"
                                    )
                                }
                            } else {
                                Log.e("NativeEncoderSimple", "FFmpeg version check failed with exit code: $versionExitCode")
                                if (versionError.isNotEmpty()) {
                                    Log.e("NativeEncoderSimple", "FFmpeg version error: ${versionError.joinToString("\n")}")
                                }
                            }

                            // Get architecture info
                            val fileProcess = ProcessBuilder("file", tempFile.absolutePath).start()
                            val fileOutput = fileProcess.inputStream.readBytes().decodeToString()
                            fileProcess.waitFor(2, TimeUnit.SECONDS)

                            if (fileOutput.isNotBlank()) {
                                val archInfo = fileOutput.substringAfter(":").trim()
                                FFmpegDebugManager.updateArchitecture(archInfo)

                                // Check if architecture matches system
                                val systemArch = System.getProperty("os.arch")
                                val isM1 = systemArch == "aarch64" || systemArch.contains("arm")

                                if ((isM1 && !archInfo.contains("arm64")) || (!isM1 && archInfo.contains("arm64"))) {
                                    Log.e("NativeEncoderSimple", "Architecture mismatch! System: $systemArch, FFmpeg: $archInfo")
                                    FFmpegDebugManager.setError("Architecture mismatch: System is $systemArch but FFmpeg is $archInfo")
                                }
                            }
                        }
                    }
                }

                bundledFfmpegPath = tempFile
                Log.d("NativeEncoderSimple", "Successfully extracted bundled FFmpeg to: ${tempFile.absolutePath}")
                tempFile
            } else {
                Log.d("NativeEncoderSimple", "FFmpeg resource not found at: $bundledPath")
                null
            }
        } catch (e: Exception) {
            Log.e("NativeEncoderSimple", "Could not extract bundled FFmpeg", e)
            null
        }
    }

    fun findFfmpeg(): String {


        // If no system FFmpeg, try bundled one
        getBundledFfmpeg()?.let {
            if (it.exists()) {
                Log.d("NativeEncoderSimple", "Using bundled FFmpeg at: ${it.absolutePath}")
                return it.absolutePath
            }
        }

        // Last resort: try to find ffmpeg in PATH
       throw RuntimeException("no way to not have bundle ffmpeg")
    }

    fun isAvailable(): Boolean {
        val osName = System.getProperty("os.name").lowercase()
        val isWindows = osName.contains("win")

        val systemPaths = if (isWindows) listOf(
            System.getenv("ProgramFiles") + "\\ffmpeg\\bin\\ffmpeg.exe",
            System.getenv("ProgramFiles(x86)") + "\\ffmpeg\\bin\\ffmpeg.exe",
            "C:\\ffmpeg\\bin\\ffmpeg.exe"
        ) else listOf(
            "/usr/local/bin/ffmpeg",
            "/opt/homebrew/bin/ffmpeg",
            "/usr/bin/ffmpeg"
        )

        for (path in systemPaths) {
            if (path != null && File(path).exists()) return true
        }

        return try {
            val cmd = if (isWindows) listOf("where", "ffmpeg") else listOf("which", "ffmpeg")
            val process = ProcessBuilder(cmd).start()
            process.waitFor(1, TimeUnit.SECONDS)
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun installFFmpeg(): Boolean {
        // Try to install FFmpeg using Homebrew
        return try {
            Log.d("NativeEncoderSimple", "Attempting to install FFmpeg via Homebrew...")

            // Check if Homebrew is installed
            val brewCheck = ProcessBuilder("which", "brew").start()
            brewCheck.waitFor(1, TimeUnit.SECONDS)

            if (brewCheck.exitValue() != 0) {
                Log.e("NativeEncoderSimple", "Homebrew not found. User needs to install manually.")
                return false
            }

            // Install FFmpeg
            val install = ProcessBuilder("brew", "install", "ffmpeg").start()
            val success = install.waitFor(120, TimeUnit.SECONDS) && install.exitValue() == 0

            if (success) {
                Log.d("NativeEncoderSimple", "FFmpeg installed successfully")
            } else {
                Log.e("NativeEncoderSimple", "Failed to install FFmpeg")
            }

            success
        } catch (e: Exception) {
            Log.e("NativeEncoderSimple", "Error installing FFmpeg", e)
            false
        }
    }

    fun encodeWebP(
        images: List<BufferedImage>,
        outputFile: File,
        quality: Int = 80,
        fps: Int = 30,
        onProgress: ((Int) -> Unit)? = null
    ): Result<File> {
        return try {
            // Create temp directory for frames
            val tempDir = File.createTempFile("webp_", "_frames").apply {
                delete()
                mkdirs()
            }

            try {
                // Save frames as JPEG (faster than PNG)
                // Skip frames for faster encoding: only use every 2nd frame when quality is low
                val frameSkip = if (quality < 20) 2 else 1  // Skip every other frame for low quality
                images.forEachIndexed { index, image ->
                    if (index % frameSkip == 0) {
                        val frameFile = File(tempDir, String.format("frame_%06d.jpg", index / frameSkip))
                        ImageIO.write(image, "JPEG", frameFile)
                    }
                    onProgress?.invoke((index + 1) * 50 / images.size)
                }

                // Use bundled or system ffmpeg
                val ffmpegPath = findFfmpeg()
                Log.d("NativeEncoderSimple", "Using FFmpeg at: $ffmpegPath")

                // Scale down for ultra fast encoding when quality is low
                val scaleFilter = if (quality < 20) {
                    listOf("-vf", "scale=iw/2:ih/2") // Half resolution for ultra fast mode
                } else {
                    emptyList()
                }

                val processArgs = mutableListOf(
                    ffmpegPath,
                    "-y", // Overwrite output
                    "-stats", // Enable progress stats output
                    "-threads", "0", // Use all available CPU cores for parallel processing
                    "-framerate", fps.toString(),
                    "-i", "${tempDir.absolutePath}/frame_%06d.jpg"
                )
                processArgs.addAll(scaleFilter)
                processArgs.addAll(listOf(
                    "-c:v", "libwebp",
                    "-lossless", "0",
                    "-quality", quality.toString(),
                    "-compression_level", "0", // Fastest encoding (no compression optimization)
                    "-method", "0", // Fastest method for speed
                    "-preset", "default", // Default preset
                    "-pass", "1", // Single pass encoding for speed
                    "-loop", "0", // Infinite loop
                    outputFile.absolutePath
                ))

                val process = ProcessBuilder(processArgs)
                    .redirectErrorStream(true) // FFmpeg outputs to stderr
                    .start()

                // Monitor FFmpeg progress in a separate thread
                val outputBuilder = StringBuilder()
                val totalFrames = images.size / (if (quality < 20) 2 else 1) // Account for frame skipping

                // Start at 50% since frame saving was 0-50%
                Log.d("NativeEncoderSimple", "Starting FFmpeg encoding, initial progress: 50%")
                onProgress?.invoke(50)

                Thread {
                    try {
                        val reader = process.inputStream.bufferedReader()
                        var line: String?
                        var lastProgress = 50

                        while (reader.readLine().also { line = it } != null) {
                            outputBuilder.appendLine(line)
                            // Debug log
                            if (line?.contains("frame") == true) {
                                Log.d("NativeEncoderSimple", "FFmpeg output: $line")
                            }

                            // FFmpeg outputs: "frame=   10 fps=..." or sometimes just numbers
                            val framePattern = Regex("frame=\\s*(\\d+)")
                            val match = framePattern.find(line ?: "")

                            match?.groupValues?.get(1)?.toIntOrNull()?.let { currentFrame ->
                                // Calculate progress from 50% to 95% (leave 5% for finalization)
                                val encodingProgress = if (totalFrames > 0) {
                                    (currentFrame * 45 / totalFrames).coerceIn(0, 45)
                                } else {
                                    20 // Default progress if we can't calculate
                                }
                                val totalProgress = 50 + encodingProgress
                                if (totalProgress > lastProgress) {
                                    Log.d("NativeEncoderSimple", "FFmpeg progress update: $totalProgress% (frame $currentFrame/$totalFrames)")
                                    onProgress?.invoke(totalProgress)
                                    lastProgress = totalProgress
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("NativeEncoderSimple", "Error reading FFmpeg output", e)
                    }
                }.start()

                // Using only real FFmpeg progress

                // Wait for process to complete
                val success = process.waitFor(60, TimeUnit.SECONDS)
                val output = outputBuilder.toString()

                if (success && process.exitValue() == 0) {
                    Log.d("NativeEncoderSimple", "FFmpeg completed successfully, setting progress to 100%")
                    onProgress?.invoke(100)
                    Result.success(outputFile)
                } else {
                    Log.e("NativeEncoderSimple", "FFmpeg failed with output: $output")
                    Result.failure(Exception("FFmpeg encoding failed: ${output.take(200)}"))
                }
            } finally {
                tempDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e("NativeEncoderSimple", "Encoding error", e)
            Result.failure(e)
        }
    }

    fun encodeWebPFromFiles(
        frameFiles: List<File>,
        outputFile: File,
        quality: Int = 80,
        fps: Int = 30,
        onProgress: ((Int) -> Unit)? = null
    ): Result<File> {
        return try {
            // Use bundled or system ffmpeg
            val ffmpegPath = findFfmpeg()
            val osName = System.getProperty("os.name").lowercase()
            val isMac = osName.contains("mac") || osName.contains("darwin")

            // Create dynamic shell script on macOS to run bundled FFmpeg
            val executableAndArgs: Pair<String, List<String>> = if (isMac && ffmpegPath.contains("gifland_ffmpeg")) {
                try {
                    // Create a temporary shell script that will run FFmpeg
                    val scriptFile = File(System.getProperty("java.io.tmpdir"), "run_ffmpeg_${System.currentTimeMillis()}.sh")
                    scriptFile.deleteOnExit()

                    // Write script content
                    scriptFile.writeText("""
                        #!/bin/sh
                        # Remove quarantine attribute
                        xattr -cr "$ffmpegPath" 2>/dev/null || true
                        # Run FFmpeg with all arguments
                        exec "$ffmpegPath" "${"$"}@"
                    """.trimIndent())

                    // Make script executable
                    ProcessBuilder("chmod", "+x", scriptFile.absolutePath).start().waitFor()

                    Log.d("NativeEncoderSimple", "Using dynamic shell script to bypass signature issues")
                    FFmpegDebugManager.updateVerification("🛡️ Using dynamic shell script to bypass signature checks")

                    Pair(scriptFile.absolutePath, emptyList<String>())
                } catch (e: Exception) {
                    Log.e("NativeEncoderSimple", "Failed to create wrapper script, using FFmpeg directly", e)
                    Pair(ffmpegPath, emptyList<String>())
                }
            } else {
                Pair(ffmpegPath, emptyList<String>())
            }

            val executablePath = executableAndArgs.first
            val ffmpegArgs = executableAndArgs.second

            Log.d("NativeEncoderSimple", "Using FFmpeg at: $ffmpegPath for ${frameFiles.size} frames")

            // Since our minimal FFmpeg doesn't support concat, use image2 pattern matching
            // First, ensure frames are in sequence
            if (frameFiles.isEmpty()) {
                return Result.failure(Exception("No frames to encode"))
            }

            // Get the directory containing the frames
            val frameDir = frameFiles.first().parentFile

            // Log frame files for debugging
            Log.d("NativeEncoderSimple", "Frame directory: ${frameDir.absolutePath}")
            Log.d("NativeEncoderSimple", "First 3 frame files: ${frameFiles.take(3).map { it.name }}")
            Log.d("NativeEncoderSimple", "Frame files exist: ${frameFiles.take(3).map { it.exists() }}")

            // Start progress at 0
            onProgress?.invoke(0)

            // Build FFmpeg command
            val ffmpegCommand = mutableListOf<String>().apply {
                add(executablePath)
                addAll(ffmpegArgs)
                addAll(listOf(
                    "-y", // Overwrite output
                    "-stats", // Enable progress stats output
                    "-threads", "0", // Use all available CPU cores
                    "-framerate", fps.toString(),
                    "-pattern_type", "sequence",
                    "-start_number", "0",
                    "-i", "${frameDir.absolutePath}/frame_%06d.jpg",
                    "-c:v", "libwebp"
                ))
            }

            ffmpegCommand.addAll(listOf(
                "-lossless", "0",
                "-quality", quality.toString(),
                "-compression_level", "0", // Fastest encoding
                "-method", "0", // Fastest method
                "-loop", "0", // Infinite loop
                outputFile.absolutePath
            ))

            Log.d("NativeEncoderSimple", "FFmpeg command: ${ffmpegCommand.joinToString(" ")}")

            val process = ProcessBuilder(ffmpegCommand)
                .redirectErrorStream(false).start() // Don't mix stderr with stdout

            // Monitor FFmpeg progress in a separate thread
            val outputBuilder = StringBuilder()
            val totalFrames = frameFiles.size

            // Track if process crashed with error 134
            var crashedWith134 = false

            Thread {
                try {
                    val reader = process.errorStream.bufferedReader() // FFmpeg outputs to stderr
                    var line: String?
                    var lastProgress = 0

                    while (reader.readLine().also { line = it } != null) {
                        outputBuilder.appendLine(line)

                        // Check for error 134 symptoms
                        if (line?.contains("signal 6") == true ||
                            line?.contains("SIGABRT") == true ||
                            line?.contains("Abort trap") == true) {
                            Log.e("NativeEncoderSimple", "FFmpeg crashed with SIGABRT (error 134): $line")
                            crashedWith134 = true
                            FFmpegDebugManager.setError("FFmpeg crashed with error 134 (SIGABRT) - code signing issue")
                        }

                        // FFmpeg outputs: "frame=   10 fps=..."
                        val framePattern = Regex("frame=\\s*(\\d+)")
                        val match = framePattern.find(line ?: "")

                        match?.groupValues?.get(1)?.toIntOrNull()?.let { currentFrame ->
                            // Calculate progress from 0% to 95% (leave 5% for finalization)
                            val progress = if (totalFrames > 0) {
                                (currentFrame * 95 / totalFrames).coerceIn(0, 95)
                            } else {
                                50 // Default progress if we can't calculate
                            }
                            if (progress > lastProgress) {
                                Log.d("NativeEncoderSimple", "WebP encoding progress: $progress% (frame $currentFrame/$totalFrames)")
                                onProgress?.invoke(progress)
                                lastProgress = progress
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NativeEncoderSimple", "Error reading FFmpeg stderr", e)
                }
            }.start()

            // Using only real FFmpeg progress

            // Read stdout for progress info
            Thread {
                try {
                    val reader = process.inputStream.bufferedReader()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        // Parse progress output if using -progress flag
                        if (line?.startsWith("out_time_ms=") == true) {
                            Log.d("NativeEncoderSimple", "Progress output: $line")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NativeEncoderSimple", "Error reading FFmpeg stdout", e)
                }
            }.start()

            // Wait for process to complete (increased timeout for WebP)
            val success = process.waitFor(540, TimeUnit.SECONDS)
            val output = outputBuilder.toString()

            if (success && process.exitValue() == 0 && outputFile.exists()) {
                Log.d("NativeEncoderSimple", "WebP encoding successful: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
                onProgress?.invoke(100)
                Result.success(outputFile)
            } else {
                val exitCode = if (success) process.exitValue() else -1
                val errorMsg = when {
                    exitCode == 134 || crashedWith134 -> {
                        Log.e("NativeEncoderSimple", "FFmpeg crashed with error 134 (SIGABRT) - This is a code signing issue on macOS")
                        FFmpegDebugManager.setError("Error 134: FFmpeg signature verification failed. This Mac may have stricter security settings.")
                        "FFmpeg exited with error code: 134"
                    }
                    exitCode == 137 -> {
                        Log.e("NativeEncoderSimple", "FFmpeg crashed with error 137 (SIGKILL) - Process was terminated by the system")
                        FFmpegDebugManager.setError("Error 137: FFmpeg was killed by the system. This may be due to memory limits or security restrictions.")
                        "FFmpeg exited with error code: 137"
                    }
                    !success -> "FFmpeg process timed out after 540 seconds"
                    exitCode != 0 -> "FFmpeg exited with error code: $exitCode"
                    !outputFile.exists() -> "Output file was not created"
                    else -> "Unknown error during encoding"
                }
                Log.e("NativeEncoderSimple", "$errorMsg. Output file exists: ${outputFile.exists()}")

                // Extract actual error from FFmpeg output (skip version info)
                val errorLines = output.lines()
                    .dropWhile { it.contains("ffmpeg version") || it.contains("built with") || it.contains("configuration:") || it.contains("lib") }
                    .filter { it.isNotBlank() && !it.startsWith(" ") }
                    .take(5)
                    .joinToString("\n")

                if (errorLines.isNotEmpty()) {
                    Log.e("NativeEncoderSimple", "FFmpeg error output: $errorLines")
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("NativeEncoderSimple", "WebP encoding error", e)
            Result.failure(e)
        }
    }

    fun encodeGIFFromFiles(
        frameFiles: List<File>,
        outputFile: File,
        fps: Int = 10,
        quality: Int = 50,
        onProgress: ((Int) -> Unit)? = null
    ): Result<File> {
        return try {
            val ffmpegPath = findFfmpeg()
            val osName = System.getProperty("os.name").lowercase()
            val isMac = osName.contains("mac") || osName.contains("darwin")

            // Create dynamic shell script on macOS to run bundled FFmpeg
            val executableAndArgs: Pair<String, List<String>> = if (isMac && ffmpegPath.contains("gifland_ffmpeg")) {
                try {
                    // Create a temporary shell script that will run FFmpeg
                    val scriptFile = File(System.getProperty("java.io.tmpdir"), "run_ffmpeg_${System.currentTimeMillis()}.sh")
                    scriptFile.deleteOnExit()

                    // Write script content
                    scriptFile.writeText("""
                        #!/bin/sh
                        # Remove quarantine attribute
                        xattr -cr "$ffmpegPath" 2>/dev/null || true
                        # Run FFmpeg with all arguments
                        exec "$ffmpegPath" "${"$"}@"
                    """.trimIndent())

                    // Make script executable
                    ProcessBuilder("chmod", "+x", scriptFile.absolutePath).start().waitFor()

                    Log.d("NativeEncoderSimple", "Using dynamic shell script to bypass signature issues")
                    FFmpegDebugManager.updateVerification("🛡️ Using dynamic shell script to bypass signature checks")

                    Pair(scriptFile.absolutePath, emptyList<String>())
                } catch (e: Exception) {
                    Log.e("NativeEncoderSimple", "Failed to create wrapper script, using FFmpeg directly", e)
                    Pair(ffmpegPath, emptyList<String>())
                }
            } else {
                Pair(ffmpegPath, emptyList<String>())
            }

            val executablePath = executableAndArgs.first
            val ffmpegArgs = executableAndArgs.second

            Log.d("NativeEncoderSimple", "Using FFmpeg at: $ffmpegPath for ${frameFiles.size} frames (GIF)")

            if (frameFiles.isEmpty()) {
                return Result.failure(Exception("No frames to encode"))
            }

            // Get the directory containing the frames
            val frameDir = frameFiles.first().parentFile

            // Get dimensions from first frame
            val firstImage = ImageIO.read(frameFiles[0])
            var width = firstImage.width
            var height = firstImage.height

            // GIF için orijinal çözünürlüğü koruyalım veya çok az düşürelim
            // Sadece çok büyük ekranlar için sınırlayalım (4K ve üzeri)
            val maxDimension = when {
                quality >= 30 -> 99999  // Yüksek kalite - scaling yok
                quality >= 15 -> 2560   // Orta kalite - 2K/QHD max
                else -> 1920            // Düşük kalite - Full HD max
            }

            if (width > maxDimension || height > maxDimension) {
                val scale = maxDimension.toDouble() / maxOf(width, height)
                width = (width * scale).toInt()
                height = (height * scale).toInt()
                Log.d("NativeEncoderSimple", "GIF scaled from ${firstImage.width}x${firstImage.height} to ${width}x${height}")
            } else {
                Log.d("NativeEncoderSimple", "GIF using original resolution: ${width}x${height}")
            }

            Log.d("NativeEncoderSimple", "GIF encoding: ${frameFiles.size} frames, ${width}x${height}, fps=$fps")

            // Start progress at 0
            onProgress?.invoke(0)

            // Try to generate palette first for better quality
            val paletteFile = File(frameDir, "palette.png")
            var usePalette = false

            try {
                val paletteCmd = mutableListOf<String>().apply {
                    add(executablePath)
                    addAll(ffmpegArgs)
                    addAll(listOf(
                        "-y",
                        "-framerate", fps.toString(),
                        "-i", "${frameDir.absolutePath}/frame_%06d.jpg",
                        "-vf", "fps=$fps,scale=$width:$height:flags=lanczos,palettegen=stats_mode=diff",
                        paletteFile.absolutePath
                    ))
                }

                Log.d("NativeEncoderSimple", "Attempting to generate palette for better quality...")
                val paletteProcess = ProcessBuilder(paletteCmd).start()
                val paletteSuccess = paletteProcess.waitFor(30, TimeUnit.SECONDS)

                if (paletteSuccess && paletteProcess.exitValue() == 0 && paletteFile.exists()) {
                    Log.d("NativeEncoderSimple", "Palette generated successfully")
                    usePalette = true
                    onProgress?.invoke(30) // Palette done
                } else {
                    Log.d("NativeEncoderSimple", "Palette generation failed, will use direct GIF encoding")
                    paletteFile.delete() // Clean up any partial file
                }
            } catch (e: Exception) {
                Log.d("NativeEncoderSimple", "Palette generation error: ${e.message}, falling back to direct encoding")
                usePalette = false
            }

            // Generate GIF with or without palette
            val gifCmd = if (usePalette) {
                // Use palette for better quality
                val dither = when {
                    quality >= 40 -> "floyd_steinberg"  // Best quality dithering
                    quality >= 20 -> "sierra2_4a"  // Balanced
                    else -> "none"  // Fastest, smallest size
                }

                mutableListOf<String>().apply {
                    add(executablePath)
                    addAll(ffmpegArgs)
                    addAll(listOf(
                        "-y",
                        "-framerate", fps.toString(),
                        "-i", "${frameDir.absolutePath}/frame_%06d.jpg",
                        "-i", paletteFile.absolutePath,
                        "-lavfi", "fps=$fps,scale=$width:$height:flags=lanczos [x]; [x][1:v] paletteuse=dither=$dither",
                        outputFile.absolutePath
                    ))
                }
            } else {
                // Direct GIF encoding without palette (works with any FFmpeg build)
                mutableListOf<String>().apply {
                    add(executablePath)
                    addAll(ffmpegArgs)
                    addAll(listOf(
                        "-y",
                        "-framerate", fps.toString(),
                        "-i", "${frameDir.absolutePath}/frame_%06d.jpg",
                        "-vf", "fps=$fps,scale=$width:$height:flags=lanczos",
                        "-pix_fmt", "rgb24",
                        outputFile.absolutePath
                    ))
                }
            }

            Log.d("NativeEncoderSimple", "Encoding GIF ${if (usePalette) "with palette" else "directly"}...")

            val process = ProcessBuilder(gifCmd)
                .redirectErrorStream(false).start()

            // Monitor progress
            val outputBuilder = StringBuilder()
            val totalFrames = frameFiles.size

            Thread {
                try {
                    val reader = process.errorStream.bufferedReader()
                    var line: String?
                    var lastProgress = 30

                    while (reader.readLine().also { line = it } != null) {
                        outputBuilder.appendLine(line)

                        val framePattern = Regex("frame=\\s*(\\d+)")
                        val match = framePattern.find(line ?: "")

                        match?.groupValues?.get(1)?.toIntOrNull()?.let { currentFrame ->
                            val progress = if (totalFrames > 0) {
                                30 + (currentFrame * 65 / totalFrames).coerceIn(0, 65)
                            } else {
                                50
                            }
                            if (progress > lastProgress) {
                                Log.d("NativeEncoderSimple", "GIF encoding progress: $progress% (frame $currentFrame/$totalFrames)")
                                onProgress?.invoke(progress)
                                lastProgress = progress
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NativeEncoderSimple", "Error reading FFmpeg stderr", e)
                }
            }.start()

            // Wait for process to complete
            val success = process.waitFor(120, TimeUnit.SECONDS)
            val output = outputBuilder.toString()

            // Clean up palette file
            paletteFile.delete()

            if (success && process.exitValue() == 0 && outputFile.exists()) {
                Log.d("NativeEncoderSimple", "GIF encoding successful: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
                onProgress?.invoke(100)
                Result.success(outputFile)
            } else {
                val exitCode = if (success) process.exitValue() else -1
                val errorMsg = when {
                    !success -> "FFmpeg process timed out after 120 seconds"
                    exitCode != 0 -> "FFmpeg exited with error code: $exitCode"
                    !outputFile.exists() -> "Output file was not created"
                    else -> "Unknown error during encoding"
                }
                Log.e("NativeEncoderSimple", "$errorMsg. Output file exists: ${outputFile.exists()}")

                val errorLines = output.lines()
                    .dropWhile { it.contains("ffmpeg version") || it.contains("built with") || it.contains("configuration:") || it.contains("lib") }
                    .filter { it.isNotBlank() && !it.startsWith(" ") }
                    .take(5)
                    .joinToString("\n")

                if (errorLines.isNotEmpty()) {
                    Log.e("NativeEncoderSimple", "FFmpeg error output: $errorLines")
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("NativeEncoderSimple", "GIF encoding error", e)
            Result.failure(e)
        }
    }

    fun encodeMP4FromFiles(
        frameFiles: List<File>,
        outputFile: File,
        crf: Int = 23,
        fps: Int = 30,
        onProgress: ((Int) -> Unit)? = null
    ): Result<File> {
        return try {
            // Use bundled or system ffmpeg
            val ffmpegPath = findFfmpeg()
            Log.d("NativeEncoderSimple", "Using FFmpeg at: $ffmpegPath for ${frameFiles.size} frames (MP4)")

            if (frameFiles.isEmpty()) {
                return Result.failure(Exception("No frames to encode"))
            }

            // Start progress at 0
            onProgress?.invoke(0)

            // Get dimensions from first frame
            val firstImage = ImageIO.read(frameFiles[0])
            val width = if (firstImage.width % 2 == 0) firstImage.width else firstImage.width - 1
            val height = if (firstImage.height % 2 == 0) firstImage.height else firstImage.height - 1

            // Get the directory containing the frames
            val frameDir = frameFiles.first().parentFile

            // Baseline profile doesn't support lossless (CRF 0), so ensure minimum CRF of 1
            val adjustedCrf = crf.coerceAtLeast(1)

            // Use different profile for high quality
            val profile = if (crf <= 5) "high" else "baseline"

            val process = ProcessBuilder(
                ffmpegPath,
                "-y", // Overwrite output
                "-stats", // Enable progress stats output
                "-r", fps.toString(), // Input framerate
                "-i", "${frameDir.absolutePath}/frame_%06d.jpg",
                "-vf", "scale=$width:$height", // Ensure dimensions are even
                "-c:v", "libx264", // Use libx264 encoder
                "-profile:v", profile, // Use high profile for near-lossless, baseline for compatibility
                "-level", "3.0",
                "-pix_fmt", "yuv420p",
                "-preset", "fast", // Faster encoding
                "-crf", adjustedCrf.toString(),
                "-movflags", "faststart", // Put moov atom at start for streaming
                outputFile.absolutePath
            ).redirectErrorStream(false).start() // Don't mix stderr with stdout

            // Monitor FFmpeg progress in a separate thread
            val outputBuilder = StringBuilder()
            val totalFrames = frameFiles.size

            Thread {
                try {
                    val reader = process.errorStream.bufferedReader() // FFmpeg outputs to stderr
                    var line: String?
                    var lastProgress = 0

                    while (reader.readLine().also { line = it } != null) {
                        outputBuilder.appendLine(line)

                        // FFmpeg outputs: "frame=   10 fps=..."
                        val framePattern = Regex("frame=\\s*(\\d+)")
                        val match = framePattern.find(line ?: "")

                        match?.groupValues?.get(1)?.toIntOrNull()?.let { currentFrame ->
                            // Calculate progress from 0% to 95% (leave 5% for finalization)
                            val progress = if (totalFrames > 0) {
                                (currentFrame * 95 / totalFrames).coerceIn(0, 95)
                            } else {
                                50 // Default progress if we can't calculate
                            }
                            if (progress > lastProgress) {
                                Log.d("NativeEncoderSimple", "MP4 encoding progress: $progress% (frame $currentFrame/$totalFrames)")
                                onProgress?.invoke(progress)
                                lastProgress = progress
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NativeEncoderSimple", "Error reading FFmpeg stderr", e)
                }
            }.start()

            // Using only real FFmpeg progress

            // Wait for process to complete
            val success = process.waitFor(270, TimeUnit.SECONDS)
            val output = outputBuilder.toString()

            if (success && process.exitValue() == 0 && outputFile.exists()) {
                Log.d("NativeEncoderSimple", "MP4 encoding successful: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
                onProgress?.invoke(100)
                Result.success(outputFile)
            } else {
                val exitCode = if (success) process.exitValue() else -1
                val errorMsg = when {
                    !success -> "FFmpeg process timed out after 270 seconds"
                    exitCode != 0 -> "FFmpeg exited with error code: $exitCode"
                    !outputFile.exists() -> "Output file was not created"
                    else -> "Unknown error during encoding"
                }
                Log.e("NativeEncoderSimple", "$errorMsg. Output file exists: ${outputFile.exists()}")

                // Extract actual error from FFmpeg output (skip version info)
                val errorLines = output.lines()
                    .dropWhile { it.contains("ffmpeg version") || it.contains("built with") || it.contains("configuration:") || it.contains("lib") }
                    .filter { it.isNotBlank() && !it.startsWith(" ") }
                    .take(5)
                    .joinToString("\n")

                if (errorLines.isNotEmpty()) {
                    Log.e("NativeEncoderSimple", "FFmpeg error output: $errorLines")
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("NativeEncoderSimple", "MP4 encoding error", e)
            Result.failure(e)
        }
    }

    fun encodeMP4(
        images: List<BufferedImage>,
        outputFile: File,
        crf: Int = 23,
        fps: Int = 30,
        onProgress: ((Int) -> Unit)? = null
    ): Result<File> {
        return try {
            // Create temp directory for frames
            val tempDir = File.createTempFile("mp4_", "_frames").apply {
                delete()
                mkdirs()
            }

            try {
                // Save frames as PNG
                images.forEachIndexed { index, image ->
                    val frameFile = File(tempDir, String.format("frame_%06d.png", index))
                    ImageIO.write(image, "PNG", frameFile)
                    onProgress?.invoke((index + 1) * 50 / images.size)
                }

                // Use bundled or system ffmpeg
                val ffmpegPath = findFfmpeg()
                Log.d("NativeEncoderSimple", "Using FFmpeg at: $ffmpegPath")

                // Get dimensions (ensure divisible by 2 for H.264)
                val width = if (images.isNotEmpty()) images[0].width else 1920
                val height = if (images.isNotEmpty()) images[0].height else 1080
                val adjustedWidth = if (width % 2 == 0) width else width - 1
                val adjustedHeight = if (height % 2 == 0) height else height - 1

                // Use libx264 encoder for MP4
                val process = ProcessBuilder(
                    ffmpegPath,
                    "-y", // Overwrite output
                    "-framerate", fps.toString(),
                    "-i", "${tempDir.absolutePath}/frame_%06d.png",
                    "-vf", "scale=$adjustedWidth:$adjustedHeight", // Ensure dimensions are even
                    "-c:v", "libx264", // Use libx264 encoder
                    "-pix_fmt", "yuv420p",
                    "-preset", "medium",
                    "-crf", crf.toString(), // Use CRF directly for quality control
                    "-movflags", "+faststart",
                    outputFile.absolutePath
                ).redirectErrorStream(true).start()

                // Read output for debugging
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val success = process.waitFor(30, TimeUnit.SECONDS)

                if (success && process.exitValue() == 0) {
                    onProgress?.invoke(100)
                    Result.success(outputFile)
                } else {
                    Log.e("NativeEncoderSimple", "FFmpeg failed with output: $output")
                    Result.failure(Exception("FFmpeg encoding failed: ${output.take(200)}"))
                }
            } finally {
                tempDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}