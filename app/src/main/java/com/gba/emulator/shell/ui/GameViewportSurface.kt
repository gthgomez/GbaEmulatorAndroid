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
    private val bitmap = Bitmap.createBitmap(
        GbaRuntimeBridge.SCREEN_WIDTH,
        GbaRuntimeBridge.SCREEN_HEIGHT,
        Bitmap.Config.RGB_565,
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
        bitmap.copyPixelsFromBuffer(java.nio.ShortBuffer.wrap(rgb565Pixels))
        return bitmap
    }

    /**
     * Draws [frameBitmap] onto the surface. Safe to call from the emulation loop thread
     * (classic render-thread pattern); bails out without locking when the surface is not
     * valid (e.g. BLAST buffer starvation or a destroyed SurfaceView) so the caller never
     * blocks the main thread on [SurfaceHolder.lockCanvas].
     */
    fun presentOnSurface(frameBitmap: Bitmap) {
        val holder = surfaceHolder ?: return
        val surface = holder.surface ?: return
        if (!surface.isValid) {
            return
        }
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
