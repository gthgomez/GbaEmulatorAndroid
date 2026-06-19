package com.gba.emulator.shell.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.gba.emulator.shell.GbaRuntimeBridge

object Rgb565Framebuffer {
    fun toImageBitmap(rgb565Pixels: ShortArray): ImageBitmap =
        FramebufferPresenter().update(rgb565Pixels).image
}

/**
 * Bitmap + monotonic generation for Compose invalidation when the [ImageBitmap] reference is reused.
 */
data class PresentedFramebuffer(
    val image: ImageBitmap,
    val generation: Int,
)

/**
 * Converts RGB565 core pixels to a fresh [ImageBitmap] each frame.
 *
 * Reusing one [Bitmap]/[ImageBitmap] and mutating via [Bitmap.setPixels] does not reliably
 * invalidate Compose's GPU texture cache on Android — the viewport sizes correctly but stays black.
 */
class FramebufferPresenter {
    private val argbScratch = IntArray(GbaRuntimeBridge.FRAMEBUFFER_PIXELS)
    private var generation = 0

    fun update(rgb565Pixels: ShortArray): PresentedFramebuffer {
        require(rgb565Pixels.size == GbaRuntimeBridge.FRAMEBUFFER_PIXELS) {
            "expected ${GbaRuntimeBridge.FRAMEBUFFER_PIXELS} pixels, got ${rgb565Pixels.size}"
        }
        for (i in rgb565Pixels.indices) {
            argbScratch[i] = rgb565ToArgb(rgb565Pixels[i].toInt() and 0xFFFF)
        }
        val bitmap = Bitmap.createBitmap(
            GbaRuntimeBridge.SCREEN_WIDTH,
            GbaRuntimeBridge.SCREEN_HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
        bitmap.setPixels(
            argbScratch,
            0,
            GbaRuntimeBridge.SCREEN_WIDTH,
            0,
            0,
            GbaRuntimeBridge.SCREEN_WIDTH,
            GbaRuntimeBridge.SCREEN_HEIGHT,
        )
        bitmap.prepareToDraw()
        generation += 1
        return PresentedFramebuffer(image = bitmap.asImageBitmap(), generation = generation)
    }

    private fun rgb565ToArgb(rgb565: Int): Int {
        val r = ((rgb565 shr 11) and 0x1F) shl 3
        val g = ((rgb565 shr 5) and 0x3F) shl 2
        val b = (rgb565 and 0x1F) shl 3
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
