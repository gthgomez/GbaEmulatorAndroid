package com.gba.emulator.shell.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.gba.emulator.shell.GbaRuntimeBridge

/**
 * Primary gameplay viewport: [SurfaceView] + [Canvas.drawBitmap] avoids Compose/Skia texture
 * caching issues with per-frame RGB565 uploads.
 */
class GameViewportSurfaceController {
    private val argbScratch = IntArray(GbaRuntimeBridge.FRAMEBUFFER_PIXELS)
    private val bitmap = Bitmap.createBitmap(
        GbaRuntimeBridge.SCREEN_WIDTH,
        GbaRuntimeBridge.SCREEN_HEIGHT,
        Bitmap.Config.ARGB_8888,
    )
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val srcRect = Rect(0, 0, GbaRuntimeBridge.SCREEN_WIDTH, GbaRuntimeBridge.SCREEN_HEIGHT)
    private val dstRect = Rect()

    @Volatile
    private var surfaceHolder: SurfaceHolder? = null

    fun bind(surfaceView: SurfaceView) {
        surfaceView.holder.addCallback(
            object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    surfaceHolder = holder
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    surfaceHolder = holder
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    if (surfaceHolder === holder) {
                        surfaceHolder = null
                    }
                }
            },
        )
    }

    fun prepareBitmap(rgb565Pixels: ShortArray): Bitmap {
        require(rgb565Pixels.size == GbaRuntimeBridge.FRAMEBUFFER_PIXELS) {
            "expected ${GbaRuntimeBridge.FRAMEBUFFER_PIXELS} pixels, got ${rgb565Pixels.size}"
        }
        for (i in rgb565Pixels.indices) {
            argbScratch[i] = rgb565ToArgb(rgb565Pixels[i].toInt() and 0xFFFF)
        }
        bitmap.setPixels(
            argbScratch,
            0,
            GbaRuntimeBridge.SCREEN_WIDTH,
            0,
            0,
            GbaRuntimeBridge.SCREEN_WIDTH,
            GbaRuntimeBridge.SCREEN_HEIGHT,
        )
        return bitmap
    }

    fun presentOnSurface(frameBitmap: Bitmap) {
        val holder = surfaceHolder ?: return
        val canvas = holder.lockCanvas() ?: return
        try {
            val viewWidth = holder.surfaceFrame.width()
            val viewHeight = holder.surfaceFrame.height()
            if (viewWidth <= 0 || viewHeight <= 0) {
                return
            }
            val scale = minOf(
                viewWidth.toFloat() / GbaRuntimeBridge.SCREEN_WIDTH,
                viewHeight.toFloat() / GbaRuntimeBridge.SCREEN_HEIGHT,
            )
            val drawnWidth = (GbaRuntimeBridge.SCREEN_WIDTH * scale).toInt()
            val drawnHeight = (GbaRuntimeBridge.SCREEN_HEIGHT * scale).toInt()
            val left = (viewWidth - drawnWidth) / 2
            val top = (viewHeight - drawnHeight) / 2
            dstRect.set(left, top, left + drawnWidth, top + drawnHeight)
            canvas.drawBitmap(frameBitmap, srcRect, dstRect, paint)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun rgb565ToArgb(rgb565: Int): Int {
        val r = ((rgb565 shr 11) and 0x1F) shl 3
        val g = ((rgb565 shr 5) and 0x3F) shl 2
        val b = (rgb565 and 0x1F) shl 3
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

@Composable
fun GameViewportSurface(
    controller: GameViewportSurfaceController,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).also { surfaceView ->
                controller.bind(surfaceView)
            }
        },
    )
}
