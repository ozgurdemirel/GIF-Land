package club.ozgur.gifland.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.delay

object WindowManager {
    // Normal window size when not recording
    val NORMAL_SIZE = DpSize(520.dp, 700.dp)

    // Compact size during recording - just shows essential info
    val COMPACT_SIZE = DpSize(380.dp, 280.dp)

    // Animation duration in milliseconds
    const val ANIMATION_DURATION = 300L

    /**
     * Smoothly resizes the window from current size to target size
     */
    suspend fun animateResize(
        windowState: WindowState,
        targetSize: DpSize,
        duration: Long = ANIMATION_DURATION
    ) {
        val startWidth = windowState.size.width
        val startHeight = windowState.size.height
        val endWidth = targetSize.width
        val endHeight = targetSize.height

        val steps = 20
        val stepDelay = duration / steps

        for (i in 1..steps) {
            val progress = i.toFloat() / steps
            val easeProgress = easeInOutCubic(progress)

            val currentWidth = lerp(startWidth.value, endWidth.value, easeProgress)
            val currentHeight = lerp(startHeight.value, endHeight.value, easeProgress)

            windowState.size = DpSize(currentWidth.dp, currentHeight.dp)
            delay(stepDelay)
        }
    }

    /**
     * Immediately sets the window size without animation
     */
    fun setSize(windowState: WindowState, size: DpSize) {
        windowState.size = size
    }

    /**
     * Easing function for smooth animation
     */
    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) {
            4 * t * t * t
        } else {
            1 - (Math.pow((-2 * t + 2).toDouble(), 3.0) / 2).toFloat()
        }
    }

    /**
     * Linear interpolation between two values
     */
    private fun lerp(start: Float, end: Float, progress: Float): Float {
        return start + (end - start) * progress
    }
}

/**
 * Composable effect to handle window resizing based on recording state
 */
@Composable
fun WindowResizeEffect(
    windowState: WindowState,
    isRecording: Boolean,
    animate: Boolean = true
) {
    LaunchedEffect(isRecording) {
        val targetSize = if (isRecording) {
            WindowManager.COMPACT_SIZE
        } else {
            WindowManager.NORMAL_SIZE
        }

        if (animate) {
            WindowManager.animateResize(windowState, targetSize)
        } else {
            WindowManager.setSize(windowState, targetSize)
        }
    }
}