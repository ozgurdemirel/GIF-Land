package club.ozgur.gifland.capture.sck

import com.sun.jna.*

interface SCKBridge : Library {
    companion object {
        fun loadOrNull(): SCKBridge? {
            if (!NativeLoader.loadIfMac()) return null
            val abs = NativeLoader.lastLoadedPath
            return runCatching {
                if (abs != null) {
                    Native.load(abs, SCKBridge::class.java) as SCKBridge
                } else {
                    Native.load("sck_bridge_swift", SCKBridge::class.java) as SCKBridge
                }
            }.getOrNull()
        }
    }

    interface FrameCb : Callback {
        fun invoke(
            frameIndex: Int,
            user: Pointer?
        )
    }

    fun sck_start_display_capture(
        displayId: Int,
        fps: Int,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        outputDir: String,
        jpegQuality: Int,
        scale: Float,
        cb: FrameCb,
        user: Pointer?
    ): Int
    fun sck_stop_capture()
    fun sck_list_displays_json(): Pointer?
}

