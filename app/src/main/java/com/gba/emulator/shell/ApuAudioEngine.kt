package com.gba.emulator.shell

/**
 * Oboe-backed playback for stereo PCM16 batches at [SAMPLE_RATE_HZ] (GBA APU contract).
 * Call [queueBatch] once per emulated frame after [GbaRuntimeBridge.drainAudioBatch].
 */
class ApuAudioEngine {
    private var running = false

    fun start(): Boolean {
        if (running) {
            return true
        }
        val ok = nativeStart()
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

    fun queueBatch(stereoPcm16Interleaved: ShortArray) {
        if (!running || stereoPcm16Interleaved.isEmpty()) {
            return
        }
        require(stereoPcm16Interleaved.size % 2 == 0) {
            "stereo PCM16 batch must have an even number of shorts"
        }
        nativeWriteBatch(stereoPcm16Interleaved)
    }

    val playbackUnderruns: Int
        get() = if (running) nativePlaybackUnderruns() else 0

    private external fun nativeStart(): Boolean
    private external fun nativeStop()
    private external fun nativeWriteBatch(stereoPcm16Interleaved: ShortArray)
    private external fun nativePlaybackUnderruns(): Int

    companion object {
        const val SAMPLE_RATE_HZ = 32_768
        const val CHANNEL_COUNT = 2
    }
}
