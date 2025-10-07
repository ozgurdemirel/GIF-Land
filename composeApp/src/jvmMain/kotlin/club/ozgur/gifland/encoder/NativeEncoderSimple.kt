package club.ozgur.gifland.encoder

import club.ozgur.gifland.util.Log
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
        // Try bundled/custom first (custom path is honored inside getBundledFfmpeg)
        getBundledFfmpeg()?.let {
            if (it.exists()) {
                Log.d("NativeEncoderSimple", "Using bundled FFmpeg at: ${it.absolutePath}")
                return it.absolutePath
            }
        }

        // Try common system locations
        val osName = System.getProperty("os.name").lowercase()
        val isWindows = osName.contains("win")
        val systemPaths = if (isWindows) listOf(
            System.getenv("ProgramFiles") + "\\ffmpeg\\bin\\ffmpeg.exe",
            System.getenv("ProgramFiles(x86)") + "\\ffmpeg\\bin\\ffmpeg.exe",
            "C:\\ffmpeg\\bin\\ffmpeg.exe"
        ) else listOf(
            "/opt/homebrew/bin/ffmpeg",
            "/usr/local/bin/ffmpeg",
            "/usr/bin/ffmpeg"
        )

        for (p in systemPaths) {
            if (File(p).exists()) {
                Log.d("NativeEncoderSimple", "Using system FFmpeg at: $p")
                return p
            }
        }

        // Try PATH (which/where)
        runCatching {
            val cmd = if (isWindows) listOf("where", "ffmpeg") else listOf("which", "ffmpeg")
            val proc = ProcessBuilder(cmd).start()
            if (proc.waitFor(1, TimeUnit.SECONDS)) {
                val out = proc.inputStream.bufferedReader().readText().lineSequence().firstOrNull()?.trim()
                if (!out.isNullOrEmpty()) {
                    Log.d("NativeEncoderSimple", "Using FFmpeg from PATH: $out")
                    return out
                }
            }
        }

        throw RuntimeException("FFmpeg not found. No bundled resource and no system ffmpeg detected")
    }

    // Build robust input args for an image sequence from actual file names
    private fun buildImageSequenceInputArgs(frameFiles: List<File>, fps: Int): List<String> {
        val first = frameFiles.first()
        val name = first.name
        val dir = first.parentFile.absolutePath
        val m = Regex("^(.*?)(\\d+)(\\.[A-Za-z0-9]+)$").find(name)
        return if (m != null) {
            val prefix = m.groupValues[1]
            val digits = m.groupValues[2]
            val ext = m.groupValues[3]
            val pad = digits.length
            val startNum = runCatching { digits.toInt() }.getOrElse { 1 }
            val pattern = "$dir/${prefix}%0${pad}d${ext}"
            val args = mutableListOf("-framerate", fps.toString(), "-pattern_type", "sequence")
            if (startNum != 1) {
                args += listOf("-start_number", startNum.toString())
            }
            args + listOf("-i", pattern)
        } else {
            // Fallback to glob pattern (e.g., ffcap_*.jpg)
            val base = name.substringBeforeLast('.')
            val prefixOnly = base.substringBeforeLast('_', base)
            val ext = name.substringAfterLast('.', "jpg")
            listOf("-framerate", fps.toString(), "-pattern_type", "glob", "-i", "$dir/${prefixOnly}_*.${ext}")
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
                ))
                // Input image sequence (support both numeric and glob patterns)
                addAll(buildImageSequenceInputArgs(frameFiles, fps))
                addAll(listOf(
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
        fastMode: Boolean = false,
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
            val srcW = firstImage.width
            val srcH = firstImage.height

            // Derive target fps cap and scale factor from quality/fast mode
            val low = quality <= 30
            val mid = quality in 31..60
            val targetFpsCap = when {
                fastMode -> 10
                low -> 10
                mid -> 12
                else -> 15
            }
            val scaleFactor = when {
                fastMode -> 0.5
                low -> 0.6
                mid -> 0.75
                else -> 1.0
            }
            val maxColors = when {
                fastMode -> 64
                low -> 64
                mid -> 128
                else -> 256
            }
            val dither = when {
                fastMode -> "bayer:bayer_scale=5"
                low -> "bayer:bayer_scale=5"
                mid -> "sierra2_4a"
                else -> "floyd_steinberg"
            }

            val targetFps = minOf(fps, targetFpsCap)
            var width = (srcW * scaleFactor).toInt().coerceAtLeast(1)
            var height = (srcH * scaleFactor).toInt().coerceAtLeast(1)

            Log.d("NativeEncoderSimple", "GIF params: fastMode=$fastMode, targetFps=$targetFps, scaleFactor=$scaleFactor, size=${width}x${height}, maxColors=$maxColors, dither=$dither")
            Log.d("NativeEncoderSimple", "GIF encoding: ${frameFiles.size} frames, ${width}x${height}, fps=$targetFps")

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
                        // Input image sequence
                        *buildImageSequenceInputArgs(frameFiles, targetFps).toTypedArray(),
                        "-vf", "fps=$targetFps,scale=$width:$height:flags=lanczos,palettegen=max_colors=$maxColors:stats_mode=single",
                        paletteFile.absolutePath
                    ))
                }

                Log.d("NativeEncoderSimple", "Attempting to generate palette for better quality...")
                val paletteProcess = ProcessBuilder(paletteCmd).start()
                val paletteSuccess = paletteProcess.waitFor(240, TimeUnit.SECONDS)

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
                // Dither already derived above
                mutableListOf<String>().apply {
                    add(executablePath)
                    addAll(ffmpegArgs)
                    addAll(listOf(
                        "-y",
                        // Input image sequence
                        *buildImageSequenceInputArgs(frameFiles, targetFps).toTypedArray(),
                        "-i", paletteFile.absolutePath,
                        "-lavfi", "fps=$targetFps,scale=$width:$height:flags=lanczos [x]; [x][1:v] paletteuse=dither=$dither",
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
                        // Input image sequence
                        *buildImageSequenceInputArgs(frameFiles, targetFps).toTypedArray(),
                        "-vf", "fps=$targetFps,scale=$width:$height:flags=lanczos",
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
            val success = process.waitFor(600, TimeUnit.SECONDS)
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
                // Input image sequence
                *buildImageSequenceInputArgs(frameFiles, fps).toTypedArray(),
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
}