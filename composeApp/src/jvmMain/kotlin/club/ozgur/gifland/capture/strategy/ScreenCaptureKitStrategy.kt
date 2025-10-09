package club.ozgur.gifland.capture.strategy

import club.ozgur.gifland.ui.components.CaptureArea
import club.ozgur.gifland.capture.sck.NativeLoader
import club.ozgur.gifland.capture.sck.SCKBridge
import club.ozgur.gifland.util.Log
import com.sun.jna.Pointer
import java.awt.GraphicsEnvironment
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ScreenCaptureKitStrategy : ScreenCaptureStrategy {
    override val name: String = "ScreenCaptureKitStrategy"

    private var bridge: SCKBridge? = null
    private var running = false
    private val frameIndex = AtomicInteger(0)
    @Volatile var lastFrameAt: Long = 0

    override fun start(area: CaptureArea?, fps: Int, scale: Float, jpegQuality: Int, outDir: File) {
        if (!NativeLoader.isMac()) throw IllegalStateException("ScreenCaptureKit is macOS-only")
        bridge = SCKBridge.loadOrNull() ?: throw IllegalStateException("ScreenCaptureKit bridge unavailable")

        val displayId = pickDisplayId(area)
        val targetFps = fps.coerceIn(1, 120)
        frameIndex.set(0)
        running = true

        // Simple callback - Swift handles all JPEG encoding and disk I/O
        val cb = object : SCKBridge.FrameCb {
            override fun invoke(frameIdx: Int, user: Pointer?) {
                try {
                    lastFrameAt = System.currentTimeMillis()
                    frameIndex.set(frameIdx + 1)
                    if (frameIdx % 10 == 0) {
                        Log.d(name, "Frame $frameIdx saved by Swift")
                    }
                } catch (t: Throwable) {
                    Log.e(name, "Callback error", t)
                }
            }
        }

        // Region to request from SCK (falls back to full display if null)
        val rx = area?.x ?: -1
        val ry = area?.y ?: -1
        val rw = area?.width ?: 0
        val rh = area?.height ?: 0

        var rc = bridge!!.sck_start_display_capture(
            displayId, targetFps, rx, ry, rw, rh,
            outDir.absolutePath, jpegQuality, scale,
            cb, null
        )
        if (rc == -2 || rc == -4) {
            // Selected display not found or no displays from SCK; retry with the first available display
            val fallbackId = pickDisplayId(null)
            Log.d(name, "SCK start rc=$rc; retrying with fallback displayId=$fallbackId (original=$displayId)")
            if (fallbackId != 0) {
                rc = bridge!!.sck_start_display_capture(
                    fallbackId, targetFps, rx, ry, rw, rh,
                    outDir.absolutePath, jpegQuality, scale,
                    cb, null
                )
            }
        }
        if (rc != 0) throw IllegalStateException("ScreenCaptureKit start failed rc=$rc")
        Log.d(name, "Started SCK capture on displayId=$displayId @ ${targetFps}fps (Swift-side JPEG encoding)")
    }

    override fun stop() {
        runCatching { bridge?.sck_stop_capture() }
        running = false
        Log.d(name, "Stopped SCK capture")
    }

    override fun isRunning(): Boolean = running

    // Helpers
    private fun pickDisplayId(area: CaptureArea?): Int {
        val b = bridge ?: return 0
        val ptr = b.sck_list_displays_json() ?: return 0
        val json = ptr.getString(0)
        Log.d(name, "SCK displays JSON: ${json.take(256)}")
        // Parse displays from JSON (order-agnostic)
        val objRegex = Regex("\\{[^}]*}")
        val idR = Regex("\"id\"\\s*:\\s*(\\d+)")
        val wR = Regex("\"width\"\\s*:\\s*(\\d+)")
        val hR = Regex("\"height\"\\s*:\\s*(\\d+)")
        val objs = objRegex.findAll(json).toList()
        if (objs.isEmpty()) { Log.d(name, "SCK displays JSON contained no objects"); return 0 }
        data class Disp(val id: Int, val w: Int, val h: Int)
        val displays = buildList {
            for (o in objs) {
                val s = o.value
                val id = idR.find(s)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val w = wR.find(s)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val h = hR.find(s)?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (id != null && w != null && h != null) add(Disp(id, w, h))
            }
        }
        if (displays.isEmpty()) { Log.d(name, "SCK displays JSON parse could not extract id/width/height"); return 0 }
        // Target bounds from area or default screen
        val targetBounds = area?.let { java.awt.Rectangle(it.x, it.y, it.width, it.height) } ?: run {
            GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
        }
        val targetW = targetBounds.width
        val targetH = targetBounds.height
        var best = displays.first()
        var bestScore = Int.MAX_VALUE
        for (d in displays) {
            val score = kotlin.math.abs(d.w - targetW) + kotlin.math.abs(d.h - targetH)
            if (score < bestScore) { bestScore = score; best = d }
        }
        return best.id
    }
}

