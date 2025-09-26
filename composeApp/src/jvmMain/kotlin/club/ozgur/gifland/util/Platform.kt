package club.ozgur.gifland.util

import java.awt.Desktop
import java.io.File
import java.net.URI

class JVMPlatform {
    val name: String = "Java ${System.getProperty("java.version")}"
}

fun getPlatform() = JVMPlatform()

fun openFileLocation(file: File) {
    runCatching {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
            desktop.browseFileDirectory(file)
        } else if (desktop.isSupported(Desktop.Action.OPEN)) {
            desktop.open(file.parentFile)
        }
    }.onFailure {
        println("Could not open file location: ${it.message}")
    }
}

fun openUrl(url: String) {
    runCatching {
        Desktop.getDesktop().browse(URI(url))
    }.onFailure {
        println("Could not open url: ${it.message}")
    }
}