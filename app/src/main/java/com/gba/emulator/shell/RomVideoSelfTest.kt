package com.gba.emulator.shell

import android.content.Context
import android.util.Log

/**
 * Device parity for workstation `rom_video_smoke`: retail ROM from [PushedRomLoader],
 * 120 frames at 2M steps, diagnostics at frames 1 / 8 / 60 / 120.
 */
object RomVideoSelfTest {
    private const val LOG_TAG = "FlowframeEmu"
    private const val TOTAL_FRAMES = 120
    private const val MAX_STEPS_PER_FRAME = EmulatorSession.DEFAULT_MAX_INSTRUCTIONS_PER_FRAME
    private val CHECK_FRAMES = intArrayOf(1, 8, 60, 120, 150, 180, 200, 210, 215)
    private const val MIN_UNIQUE_COLORS_AFTER_60 = 8
    private const val MIN_BG_DISPCNT_MASK = 0x1F00

    data class Result(
        val passed: Boolean,
        val summary: String,
        val skipped: Boolean = false,
    )

    fun run(context: Context): Result {
        if (!PushedRomLoader.hasPushedRom(context)) {
            return Result(
                passed = false,
                skipped = true,
                summary = "no ${PushedRomLoader.CACHE_ROM_NAME} in cache (adb push first)",
            )
        }

        val loaded = PushedRomLoader.load(context).getOrElse { error ->
            return Result(false, summary = "load failed: ${error.message}")
        }

        return GbaRuntimeBridge.withHandle { handle ->
            when (GbaRuntimeBridge.loadRom(handle, loaded.bytes)) {
                GbaRuntimeBridge.RuntimeStatus.Ok -> Unit
                else -> return@withHandle Result(false, summary = "loadRom rejected")
            }

            var firstFailure: String? = null
            for (frameIndex in 1..TOTAL_FRAMES) {
                val step = GbaRuntimeBridge.stepFrame(handle, MAX_STEPS_PER_FRAME)
                if (step.status != GbaRuntimeBridge.RuntimeStatus.Ok) {
                    return@withHandle Result(false, summary = "frame $frameIndex step failed: ${step.status}")
                }

                if (frameIndex !in CHECK_FRAMES) {
                    continue
                }

                val diag = GbaRuntimeBridge.getVideoDiagnostics(handle)
                    ?: return@withHandle Result(false, summary = "frame $frameIndex: no video diagnostics")
                val pixels = GbaRuntimeBridge.copyFramebuffer(handle)
                val metrics = FramebufferVideoMetrics.analyze(pixels)
                logCheckpoint(frameIndex, step, diag, metrics)

                val failure = evaluateCheckpoint(frameIndex, step, diag, metrics)
                if (failure != null && firstFailure == null) {
                    firstFailure = failure
                }
            }

            if (firstFailure != null) {
                Result(false, summary = firstFailure)
            } else {
                Result(
                    passed = true,
                    summary = "rom video ok: ${loaded.displayName} frames ${CHECK_FRAMES.joinToString("/")}",
                )
            }
        }
    }

    private fun evaluateCheckpoint(
        frameIndex: Int,
        frame: GbaRuntimeBridge.FrameResult,
        diag: GbaRuntimeBridge.VideoDiagnostics,
        metrics: FramebufferVideoMetrics.Metrics,
    ): String? {
        if (!frame.frameComplete) {
            return "frame $frameIndex: not frame-complete (cycles Δ=${frame.schedulerCyclesDelta})"
        }
        if (frame.renderedScanlines != GbaRuntimeBridge.SCREEN_HEIGHT) {
            return "frame $frameIndex: expected ${GbaRuntimeBridge.SCREEN_HEIGHT} scanlines, got ${frame.renderedScanlines}"
        }
        if (frameIndex >= 60 && FramebufferVideoMetrics.isUniformBackdrop(metrics, diag.sampleRgb565)) {
            return "frame $frameIndex: uniform backdrop (unique=${metrics.uniqueColorCount} " +
                "dominant=0x${metrics.dominantColorRgb565.toString(16)} " +
                "ratio=${"%.3f".format(metrics.dominantColorRatio)})"
        }
        if (frameIndex >= 60) {
            val bgEnabled = (diag.dispcnt and MIN_BG_DISPCNT_MASK) != 0
            if (!bgEnabled && metrics.uniqueColorCount < MIN_UNIQUE_COLORS_AFTER_60) {
                return "frame $frameIndex: DISPCNT=0x${diag.dispcnt.toString(16)} " +
                    "unique_colors=${metrics.uniqueColorCount} (need BG mask or >=$MIN_UNIQUE_COLORS_AFTER_60)"
            }
        }
        return null
    }

    private fun logCheckpoint(
        frameIndex: Int,
        frame: GbaRuntimeBridge.FrameResult,
        diag: GbaRuntimeBridge.VideoDiagnostics,
        metrics: FramebufferVideoMetrics.Metrics,
    ) {
        Log.i(
            LOG_TAG,
            "rom_video_smoke frame=$frameIndex " +
                "frame_complete=${frame.frameComplete} " +
                "scanlines=${frame.renderedScanlines} " +
                "cycles_delta=${frame.schedulerCyclesDelta} " +
                "dispcnt=0x${diag.dispcnt.toString(16)} " +
                "forced_blank=${diag.forcedBlank} " +
                "bg_mask=0x${diag.bgEnabledMask.toString(16)} " +
                "non_zero=${diag.nonZeroPixelCount} " +
                "sample=0x${diag.sampleRgb565.toString(16)} " +
                "unique_colors=${metrics.uniqueColorCount} " +
                "dominant=0x${metrics.dominantColorRgb565.toString(16)} " +
                "dominant_ratio=${"%.4f".format(metrics.dominantColorRatio)} " +
                "framebuffer_crc32=0x${metrics.framebufferCrc32.toString(16)}",
        )
    }
}
