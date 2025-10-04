package club.ozgur.gifland.encoder

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class FFmpegDebugInfo(
    val extractionPath: String? = null,
    val ffmpegVersion: String? = null,
    val signingCommands: List<CommandResult> = emptyList(),
    val verificationResult: String? = null,
    val extractionTime: String? = null,
    val architecture: String? = null,
    val fileSize: Long = 0,
    val lastError: String? = null
)

data class CommandResult(
    val command: String,
    val exitCode: Int,
    val output: String,
    val success: Boolean
)

object FFmpegDebugManager {
    var debugInfo by mutableStateOf(FFmpegDebugInfo())
        private set

    private val commandHistory = mutableListOf<CommandResult>()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    fun updateExtractionPath(path: String) {
        debugInfo = debugInfo.copy(
            extractionPath = path,
            extractionTime = LocalDateTime.now().format(timeFormatter)
        )
    }

    fun updateFFmpegVersion(version: String) {
        debugInfo = debugInfo.copy(ffmpegVersion = version)
    }

    fun addCommand(command: String, exitCode: Int, output: String) {
        val result = CommandResult(
            command = command,
            exitCode = exitCode,
            output = output.take(500), // Limit output for UI
            success = exitCode == 0
        )
        commandHistory.add(result)
        debugInfo = debugInfo.copy(signingCommands = commandHistory.toList())
    }

    fun updateVerification(result: String) {
        debugInfo = debugInfo.copy(verificationResult = result)
    }

    fun updateArchitecture(arch: String) {
        debugInfo = debugInfo.copy(architecture = arch)
    }

    fun updateFileSize(size: Long) {
        debugInfo = debugInfo.copy(fileSize = size)
    }

    fun setError(error: String) {
        debugInfo = debugInfo.copy(lastError = error)
    }

    fun clear() {
        commandHistory.clear()
        debugInfo = FFmpegDebugInfo()
    }
}