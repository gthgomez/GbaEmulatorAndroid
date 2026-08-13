package com.gba.emulator.shell

/**
 * Oboe-backed playback for stereo PCM16 batches at [SAMPLE_RATE_HZ] (GBA APU contract).
 * Emulation pushes PCM into a native ring buffer; Oboe pulls via [AudioStreamDataCallback].
 */
class ApuAudioEngine {
    private var running = false

    fun start(): Boolean {
        if (running) {
            return true
        }
        val ok = nativeStart()
        if (ok) {
            preRollSilence()
        }
        running = ok
        return ok
    }

    fun stop() {
        if (!running) {
            return
        }
        nativeStop()
        running = false
    }

    fun enqueueBatch(stereoPcm16Interleaved: ShortArray, size: Int = stereoPcm16Interleaved.size) {
        if (!running || stereoPcm16Interleaved.isEmpty() || size <= 0) {
            return
        }
        val expectedSize = if (size < stereoPcm16Interleaved.size) size else stereoPcm16Interleaved.size
        require(expectedSize % 2 == 0) {
            "stereo PCM16 batch must have an even number of shorts"
        }
        nativeEnqueueBatch(stereoPcm16Interleaved, expectedSize)
    }

    /** @deprecated Use [enqueueBatch]. */
    fun queueBatch(stereoPcm16Interleaved: ShortArray) = enqueueBatch(stereoPcm16Interleaved)

    fun clear() {
        if (running) {
            nativeClear()
        }
    }

    fun preRollSilence() {
        if (!running) {
            return
        }
        val silence = ShortArray(SAMPLES_PER_GBA_FRAME * PRE_ROLL_GBA_FRAMES * CHANNEL_COUNT)
        nativeEnqueueBatch(silence, silence.size)
    }

    val availableFrames: Int
        get() = if (running) nativeAvailableFrames() else 0

    val playbackUnderruns: Int
        get() = if (running) nativePlaybackUnderruns() else 0

    fun setSteadyMusicEnabled(enabled: Boolean) {
        if (running) {
            nativeSetSteadyMusicEnabled(enabled)
        }
    }

    fun setPlaybackRateMultiplier(multiplier: Float) {
        if (running) {
            nativeSetPlaybackRateMultiplier(multiplier)
        }
    }

    private external fun nativeStart(): Boolean
    private external fun nativeStop()
    private external fun nativeEnqueueBatch(stereoPcm16Interleaved: ShortArray, size: Int)
    private external fun nativeClear()
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
