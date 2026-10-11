package com.gba.emulator.shell

/**
 * Oboe-backed playback for stereo PCM16 batches at [SAMPLE_RATE_HZ] (GBA APU contract).
 * Emulation pushes PCM into a native ring buffer; Oboe pulls it via a data callback.
 *
 * Every method fans out to the single process-wide native stream (a raw holder pointer plus an
 * SPSC ring owned by the Oboe data callback). The frame loop pushes batches from a background
 * dispatcher while the lifecycle observer starts/stops the stream on the main thread, so all
 * calls are serialized on [lock]: without it a background `enqueueBatch` can race a main-thread
 * [stop] that frees the native holder mid-copy. The stream is torn down on pause and rebuilt on
 * resume rather than resetting the ring under the live data callback.
 */
class ApuAudioEngine internal constructor(
    private val driver: Driver?,
) {
    constructor() : this(null)

    /** Seam over the native audio entry points so the serialization contract is unit-testable. */
    internal interface Driver {
        fun startStream(): Boolean
        fun stopStream()
        fun enqueue(samples: ShortArray, size: Int)
        fun availableFrames(): Int
        fun playbackUnderruns(): Int
        fun setSteadyMusicEnabled(enabled: Boolean)
        fun setPlaybackRateMultiplier(multiplier: Float)
    }

    private val lock = Any()
    private var running = false

    // Last requested stream settings. stop() destroys the native holder (and its settings), so
    // start() re-applies these to the rebuilt stream — otherwise pause/resume would silently
    // reset the user's Steady Music toggle and playback-rate multiplier.
    private var steadyMusicEnabled = false
    private var rateMultiplier = 1f

    private val nativeDriver: Driver = object : Driver {
        override fun startStream(): Boolean = nativeStart()
        override fun stopStream() = nativeStop()
        override fun enqueue(samples: ShortArray, size: Int) = nativeEnqueueBatch(samples, size)
        override fun availableFrames(): Int = nativeAvailableFrames()
        override fun playbackUnderruns(): Int = nativePlaybackUnderruns()
        override fun setSteadyMusicEnabled(enabled: Boolean) = nativeSetSteadyMusicEnabled(enabled)
        override fun setPlaybackRateMultiplier(multiplier: Float) =
            nativeSetPlaybackRateMultiplier(multiplier)
    }

    private fun activeDriver(): Driver = driver ?: nativeDriver

    fun start(): Boolean = synchronized(lock) {
        if (running) {
            true
        } else {
            val ok = activeDriver().startStream()
            if (ok) {
                activeDriver().setSteadyMusicEnabled(steadyMusicEnabled)
                activeDriver().setPlaybackRateMultiplier(rateMultiplier)
                enqueuePrerollLocked()
            }
            running = ok
            ok
        }
    }

    fun stop() {
        synchronized(lock) {
            if (!running) {
                return@synchronized
            }
            activeDriver().stopStream()
            running = false
        }
    }

    fun enqueueBatch(stereoPcm16Interleaved: ShortArray, size: Int = stereoPcm16Interleaved.size) {
        synchronized(lock) {
            if (!running || stereoPcm16Interleaved.isEmpty() || size <= 0) {
                return@synchronized
            }
            val expectedSize = if (size < stereoPcm16Interleaved.size) size else stereoPcm16Interleaved.size
            require(expectedSize % 2 == 0) {
                "stereo PCM16 batch must have an even number of shorts"
            }
            activeDriver().enqueue(stereoPcm16Interleaved, expectedSize)
        }
    }

    /** @deprecated Use [enqueueBatch]. */
    fun queueBatch(stereoPcm16Interleaved: ShortArray) = enqueueBatch(stereoPcm16Interleaved)

    val availableFrames: Int
        get() = synchronized(lock) { if (running) activeDriver().availableFrames() else 0 }

    val playbackUnderruns: Int
        get() = synchronized(lock) { if (running) activeDriver().playbackUnderruns() else 0 }

    fun setSteadyMusicEnabled(enabled: Boolean) {
        synchronized(lock) {
            steadyMusicEnabled = enabled
            if (running) {
                activeDriver().setSteadyMusicEnabled(enabled)
            }
        }
    }

    fun setPlaybackRateMultiplier(multiplier: Float) {
        synchronized(lock) {
            rateMultiplier = multiplier
            if (running) {
                activeDriver().setPlaybackRateMultiplier(multiplier)
            }
        }
    }

    private fun enqueuePrerollLocked() {
        val silence = ShortArray(SAMPLES_PER_GBA_FRAME * PRE_ROLL_GBA_FRAMES * CHANNEL_COUNT)
        activeDriver().enqueue(silence, silence.size)
    }

    private external fun nativeStart(): Boolean
    private external fun nativeStop()
    private external fun nativeEnqueueBatch(stereoPcm16Interleaved: ShortArray, size: Int)
    private external fun nativeAvailableFrames(): Int
    private external fun nativeSetSteadyMusicEnabled(enabled: Boolean)
    private external fun nativeSetPlaybackRateMultiplier(multiplier: Float)
    private external fun nativePlaybackUnderruns(): Int

    companion object {
        const val SAMPLE_RATE_HZ = 32_768
        const val CHANNEL_COUNT = 2
        const val SAMPLES_PER_GBA_FRAME = 549
        private const val PRE_ROLL_GBA_FRAMES = 2
    }
}
