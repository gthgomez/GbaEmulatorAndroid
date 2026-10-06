package com.gba.emulator.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM coverage for the RGB565 stats used by the device `RomVideoSelfTest` gate
 * (workstation `rom_video_smoke` parity). Keeps the pure analysis path honest
 * without requiring a device or the native library.
 */
class FramebufferVideoMetricsTest {
    private val pixelCount = 240 * 160

    @Test
    fun analyze_singleColorFrameReportsOneUniqueColor() {
        val metrics = FramebufferVideoMetrics.analyze(ShortArray(pixelCount) { 0x7FFF.toShort() })
        assertEquals(1, metrics.uniqueColorCount)
        assertEquals(0x7FFF, metrics.dominantColorRgb565)
        assertEquals(1.0, metrics.dominantColorRatio, 0.0)
    }

    @Test
    fun analyze_countsDistinctColors() {
        val metrics = FramebufferVideoMetrics.analyze(
            ShortArray(pixelCount) { index -> (index % 4).toShort() },
        )
        assertEquals(4, metrics.uniqueColorCount)
    }

    @Test
    fun analyze_isDeterministicForSamePixels() {
        val pixels = ShortArray(pixelCount) { index -> (index * 31 % 0xFFFF).toShort() }
        assertEquals(
            FramebufferVideoMetrics.analyze(pixels),
            FramebufferVideoMetrics.analyze(pixels.copyOf()),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun analyze_rejectsWrongBufferSize() {
        FramebufferVideoMetrics.analyze(ShortArray(pixelCount - 1))
    }

    @Test
    fun isUniformBackdrop_trueForSingleColor() {
        val metrics = FramebufferVideoMetrics.analyze(ShortArray(pixelCount) { 0x7FFF.toShort() })
        assertTrue(FramebufferVideoMetrics.isUniformBackdrop(metrics, 0x7FFF))
    }

    @Test
    fun isUniformBackdrop_trueWhenDominantColorMatchesSampleAboveThreshold() {
        val pixels = ShortArray(pixelCount) { 0x7FFF.toShort() }
        // Flip a handful of pixels: still under the 1% dominant-ratio threshold.
        for (index in 0 until 10) {
            pixels[index] = 0x001F
        }
        val metrics = FramebufferVideoMetrics.analyze(pixels)
        assertTrue(
            metrics.dominantColorRatio >= FramebufferVideoMetrics.DOMINANT_RATIO_FAIL_THRESHOLD,
        )
        assertTrue(FramebufferVideoMetrics.isUniformBackdrop(metrics, 0x7FFF))
    }

    @Test
    fun isUniformBackdrop_falseForVariedFrame() {
        val metrics = FramebufferVideoMetrics.analyze(
            ShortArray(pixelCount) { index -> (index % 8).toShort() },
        )
        assertFalse(FramebufferVideoMetrics.isUniformBackdrop(metrics, 0x7FFF))
    }
}
