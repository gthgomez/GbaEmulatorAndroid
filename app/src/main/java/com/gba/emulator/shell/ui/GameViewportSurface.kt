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
 * Outcome of a [GameViewportSurfaceController.presentOnSurface] attempt. Lets the frame loop
 * distinguish "emulated but never posted" from "posted" so soak telemetry can measure
 * zero-presentation stalls.
 */
enum class PresentResult {
    /** Frame was drawn and posted via [SurfaceHolder.unlockCanvasAndPost]. */
    Posted,

    /** No [SurfaceHolder] is bound (surface not created yet or already destroyed). */
    NoHolder,

    /** The holder's surface exists but is not valid. */
    InvalidSurface,

    /** [SurfaceHolder.lockCanvas] returned null (e.g. BLAST buffer pool starved). */
    LockFailed,

    /** The surface frame has zero/negative dimensions; nothing was drawn. */
    InvalidDimensions,
}

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

    /**
     * Wall time of the most recent [presentOnSurface] call in nanoseconds (lock + draw + post).
     * Zero when the call bailed out before locking. Written on the present thread, read by the
     * frame loop after the present coroutine completes (happens-before via structured
     * concurrency; [Volatile] kept as defensive documentation).
     */
    @Volatile
    var lastPresentNanos: Long = 0L
        private set

    /**
     * Time spent inside [SurfaceHolder.lockCanvas] in the most recent [presentOnSurface] call.
     */
    @Volatile
    var lastLockCanvasNanos: Long = 0L
        private set

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
     * Draws [frameBitmap] onto the surface and posts it. Safe to call from the emulation loop
     * thread (classic render-thread pattern); bails out without locking when the surface is not
     * valid (e.g. BLAST buffer starvation or a destroyed SurfaceView) so the caller never blocks
     * the main thread on [SurfaceHolder.lockCanvas]. Note that [Surface.isValid] is not a
     * zero-wait guarantee: [SurfaceHolder.lockCanvas] can still be throttled while the surface
     * is available, and a slow lock blocks this (non-main) thread. Returns
     * [PresentResult.Posted] only when [SurfaceHolder.unlockCanvasAndPost] actually ran.
     */
    fun presentOnSurface(frameBitmap: Bitmap): PresentResult {
        lastPresentNanos = 0L
        lastLockCanvasNanos = 0L
        val presentStartNanos = System.nanoTime()
        val holder = surfaceHolder ?: return PresentResult.NoHolder
        val surface = holder.surface ?: return PresentResult.NoHolder
        if (!surface.isValid) {
            return PresentResult.InvalidSurface
        }
        val lockStartNanos = System.nanoTime()
        val canvas = holder.lockCanvas() ?: run {
            lastPresentNanos = System.nanoTime() - presentStartNanos
            return PresentResult.LockFailed
        }
        lastLockCanvasNanos = System.nanoTime() - lockStartNanos
        try {
            val viewWidth = holder.surfaceFrame.width()
            val viewHeight = holder.surfaceFrame.height()
            if (viewWidth <= 0 || viewHeight <= 0) {
                return PresentResult.InvalidDimensions
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
            lastPresentNanos = System.nanoTime() - presentStartNanos
        }
        return PresentResult.Posted
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
