package com.gba.emulator.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Host-level regression coverage for [ApuAudioEngine]'s lifecycle serialization.
 *
 * Emulation pushes audio from a background dispatcher while the lifecycle observer starts/stops
 * the stream on the main thread. These tests use [ApuAudioEngine.Driver] to prove the engine
 * never lets two native calls overlap (the pre-fix race: a concurrent stop() freeing the native
 * holder while enqueue() copies into it), and that a paused engine never pushes into a stopped
 * stream.
 */
class ApuAudioEngineTest {

    /** Records call counts and flags any overlapping (re-entrant) native call. */
    private class FakeDriver : ApuAudioEngine.Driver {
        private val inFlight = AtomicInteger(0)
        @Volatile
        var overlapDetected = false
            private set
        val totalCalls = AtomicInteger(0)
        val startCalls = AtomicInteger(0)
        val stopCalls = AtomicInteger(0)
        val enqueueCalls = AtomicInteger(0)
        @Volatile
        var lastSteadyMusic: Boolean? = null
        @Volatile
        var lastPlaybackRate: Float? = null

        override fun startStream(): Boolean {
            enter()
            startCalls.incrementAndGet()
            return true
        }

        override fun stopStream() {
            enter()
            stopCalls.incrementAndGet()
        }

        override fun enqueue(samples: ShortArray, size: Int) {
            enter()
            enqueueCalls.incrementAndGet()
        }

        override fun availableFrames(): Int {
            enter()
            return 0
        }

        override fun playbackUnderruns(): Int {
            enter()
            return 0
        }

        override fun setSteadyMusicEnabled(enabled: Boolean) {
            enter()
            lastSteadyMusic = enabled
        }

        override fun setPlaybackRateMultiplier(multiplier: Float) {
            enter()
            lastPlaybackRate = multiplier
        }

        private fun enter() {
            totalCalls.incrementAndGet()
            if (inFlight.incrementAndGet() > 1) {
                overlapDetected = true
            }
            try {
                // Widen the race window so an unsynchronized engine reliably overlaps.
                Thread.sleep(0, 200_000)
            } finally {
                inFlight.decrementAndGet()
            }
        }
    }

    @Test
    fun startPrerollsSilenceAndStopTearsDownStream() {
        val driver = FakeDriver()
        val engine = ApuAudioEngine(driver)

        assertTrue(engine.start())
        assertEquals(1, driver.startCalls.get())
        assertTrue("start() must pre-roll silence", driver.enqueueCalls.get() >= 1)

        engine.stop()
        assertEquals(1, driver.stopCalls.get())

        assertTrue(engine.start())
        assertEquals(2, driver.startCalls.get())
    }

    @Test
    fun stoppedEngineRejectsEnqueue() {
        val driver = FakeDriver()
        val engine = ApuAudioEngine(driver)

        // Never started: nothing reaches the native stream.
        engine.enqueueBatch(ShortArray(4) { 1 }, 4)
        assertEquals(0, driver.enqueueCalls.get())

        assertTrue(engine.start())
        val primed = driver.enqueueCalls.get()
        engine.stop()

        engine.enqueueBatch(ShortArray(4) { 1 }, 4)
        assertEquals("no audio may be pushed to a stopped stream", primed, driver.enqueueCalls.get())
    }

    @Test
    fun concurrentLifecycleAndFrameAudioCallsAreSerialized() {
        val driver = FakeDriver()
        val engine = ApuAudioEngine(driver)
        val threads = (0 until 8).map { worker ->
            Thread {
                repeat(300) { i ->
                    when ((worker + i) % 8) {
                        0 -> engine.start()
                        1 -> engine.stop()
                        2 -> engine.enqueueBatch(ShortArray(4) { 7 }, 4)
                        3 -> engine.setSteadyMusicEnabled(i % 2 == 0)
                        4 -> engine.setPlaybackRateMultiplier(1f)
                        5 -> engine.availableFrames
                        6 -> engine.playbackUnderruns
                        else -> engine.start()
                    }
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertTrue("the concurrency test must actually exercise the driver", driver.totalCalls.get() > 0)
        assertFalse("native audio calls must never overlap", driver.overlapDetected)
    }

    @Test
    fun startReappliesConfiguredStreamSettings() {
        val driver = FakeDriver()
        val engine = ApuAudioEngine(driver)

        assertTrue(engine.start())
        engine.setSteadyMusicEnabled(true)
        engine.setPlaybackRateMultiplier(2f)

        // A pause tears the native holder down; the rebuilt stream must inherit the last
        // configured mode instead of silently reverting to defaults.
        engine.stop()
        assertTrue(engine.start())
        assertTrue("steady music must survive a stream restart", driver.lastSteadyMusic == true)
        assertEquals(2f, driver.lastPlaybackRate!!, 0f)
    }

    @Test
    fun enqueueBatchRejectsOddShortCount() {
        val driver = FakeDriver()
        val engine = ApuAudioEngine(driver)
        assertTrue(engine.start())
        try {
            engine.enqueueBatch(ShortArray(4) { 1 }, 3)
            fail("stereo PCM16 requires an even short count")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
