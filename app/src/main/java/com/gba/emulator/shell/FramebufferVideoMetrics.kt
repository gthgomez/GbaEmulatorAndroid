package com.gba.emulator.shell

import java.util.zip.CRC32

/**
 * RGB565 framebuffer stats for ROM video smoke (workstation [rom_video_smoke] parity).
 * Prefer [GbaRuntimeBridge.VideoDiagnostics] when JNI is available; this helper still
 * analyzes the RGB565 buffer for presentation-layer checks.
 */
object FramebufferVideoMetrics {
    const val DOMINANT_RATIO_FAIL_THRESHOLD = 0.99

    data class Metrics(
        val uniqueColorCount: Int,
        val dominantColorRgb565: Int,
        val dominantColorRatio: Double,
        val framebufferCrc32: Long,
    )

    fun analyze(pixels: ShortArray): Metrics {
        require(pixels.size == GbaRuntimeBridge.FRAMEBUFFER_PIXELS) {
            "expected ${GbaRuntimeBridge.FRAMEBUFFER_PIXELS} pixels, got ${pixels.size}"
        }

        val crc = CRC32()
        val colorCounts = HashMap<Int, Int>(256)
        var dominantRgb = 0
        var dominantCount = 0

        for (raw in pixels) {
            val rgb = raw.toInt() and 0xFFFF
            crc.update((rgb and 0xFF).toByte().toInt())
            crc.update((rgb shr 8).toByte().toInt())
            val count = (colorCounts[rgb] ?: 0) + 1
            colorCounts[rgb] = count
            if (count > dominantCount) {
                dominantCount = count
                dominantRgb = rgb
            }
        }

        val total = pixels.size.toDouble()
        return Metrics(
            uniqueColorCount = colorCounts.size,
            dominantColorRgb565 = dominantRgb,
            dominantColorRatio = dominantCount / total,
            framebufferCrc32 = crc.value,
        )
    }

    fun isUniformBackdrop(metrics: Metrics, sampleRgb565: Int): Boolean {
        if (metrics.uniqueColorCount <= 1) {
            return true
        }
        return metrics.dominantColorRatio >= DOMINANT_RATIO_FAIL_THRESHOLD &&
            metrics.dominantColorRgb565 == (sampleRgb565 and 0xFFFF)
    }
}
