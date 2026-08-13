package com.gba.emulator.shell

/**
 * JNI wrapper over [gba::core::AndroidRuntime] (frame step + RGB565 framebuffer).
 */
object GbaRuntimeBridge {
    init {
        System.loadLibrary("gbaemulator")
    }

    const val SCREEN_WIDTH = 240
    const val SCREEN_HEIGHT = 160
    const val FRAMEBUFFER_PIXELS = SCREEN_WIDTH * SCREEN_HEIGHT
    const val AUDIO_SAMPLE_RATE_HZ = 32_768

    enum class RuntimeStatus(val code: Int) {
        Ok(0),
        InvalidArgument(1),
        RomRejected(2),
        InputRejected(3),
        ;

        companion object {
            fun fromCode(code: Int): RuntimeStatus =
                entries.firstOrNull { it.code == code } ?: InvalidArgument
        }
    }

    enum class PersistenceStatus(val code: Int) {
        Ok(0),
        InvalidArgument(1),
        NoSaveConfigured(2),
        ImportRejected(3),
        ;

        companion object {
            fun fromCode(code: Int): PersistenceStatus =
                entries.firstOrNull { it.code == code } ?: InvalidArgument
        }
    }

    enum class SaveStateDecodeStatus(val code: Int) {
        Ok(0),
        TooSmall(1),
        BadMagic(2),
        UnsupportedVersion(3),
        CorruptPayload(4),
        RestoreRejected(5),
        ;

        companion object {
            fun fromCode(code: Int): SaveStateDecodeStatus =
                entries.firstOrNull { it.code == code } ?: CorruptPayload
        }
    }

    enum class StopReason(val code: Int) {
        MaxSteps(0),
        FetchFailed(1),
        UnsupportedInstruction(2),
        ;

        companion object {
            fun fromCode(code: Int): StopReason =
                entries.firstOrNull { it.code == code } ?: MaxSteps
        }
    }

    data class FrameResult(
        val status: RuntimeStatus,
        val executedSteps: Int,
        val renderedScanlines: Int,
        val audioSamples: Int,
        val audioUnderruns: Int,
        val stateHash: Long,
        val stopReason: StopReason,
        val unsupportedSteps: Int,
        val finalPc: Int,
        val schedulerCyclesDelta: Long,
    ) {
        val frameComplete: Boolean
            get() = status == RuntimeStatus.Ok &&
                stopReason != StopReason.FetchFailed &&
                stopReason != StopReason.UnsupportedInstruction &&
                schedulerCyclesDelta >= EmulationFramePacer.CYCLES_PER_FRAME
    }

    data class VideoDiagnostics(
        val dispcnt: Int,
        val forcedBlank: Boolean,
        val bgEnabledMask: Int,
        val nonZeroPixelCount: Int,
        val sampleRgb565: Int,
        val uniqueColorCount: Int,
        val dominantColorRatio: Float,
        val framebufferCrc32: Int,
    )

    external fun nativeCreate(): Long
    external fun nativeDestroy(handle: Long)
    external fun nativeReset(handle: Long): Int
    external fun nativeLoadRom(handle: Long, rom: ByteArray): Int
    external fun nativeSetButtonMask(handle: Long, pressedMask: Int): Int
    external fun nativeSeedDemoEnvironment(handle: Long): Int
    external fun nativeStepFrame(handle: Long, maxSteps: Int): LongArray
    external fun nativePixel(handle: Long, x: Int, y: Int): Int
    external fun nativeCopyFramebuffer(handle: Long, outPixels: ShortArray)
    external fun nativeGetVideoDiagnostics(handle: Long): LongArray
    external fun nativeDrainAudioBatch(handle: Long, outAudio: ShortArray): Int
    external fun nativeExportSave(handle: Long): ByteArray?
    external fun nativeImportSave(handle: Long, saveBytes: ByteArray): Int
    external fun nativeSaveState(handle: Long): ByteArray?
    external fun nativeLoadState(handle: Long, stateBytes: ByteArray): Int
    external fun nativeGetCartridgeMetadata(handle: Long): Array<String>?
    external fun nativeSessionStateHash(handle: Long): Long

    inline fun <T> withHandle(block: (Long) -> T): T {
        val handle = nativeCreate()
        check(handle != 0L) { "AndroidRuntime nativeCreate returned null" }
        try {
            return block(handle)
        } finally {
            nativeDestroy(handle)
        }
    }

    fun reset(handle: Long): RuntimeStatus = RuntimeStatus.fromCode(nativeReset(handle))

    fun loadRom(handle: Long, rom: ByteArray): RuntimeStatus =
        RuntimeStatus.fromCode(nativeLoadRom(handle, rom))

    fun setButtonMask(handle: Long, pressedMask: Int): RuntimeStatus =
        RuntimeStatus.fromCode(nativeSetButtonMask(handle, pressedMask))

    fun seedDemoEnvironment(handle: Long): RuntimeStatus =
        RuntimeStatus.fromCode(nativeSeedDemoEnvironment(handle))

    fun stepFrame(handle: Long, maxSteps: Int): FrameResult {
        val values = nativeStepFrame(handle, maxSteps)
        require(values.size == 10) { "nativeStepFrame returned ${values.size} values" }
        return FrameResult(
            status = RuntimeStatus.fromCode(values[0].toInt()),
            executedSteps = values[1].toInt(),
            renderedScanlines = values[2].toInt(),
            audioSamples = values[3].toInt(),
            audioUnderruns = values[4].toInt(),
            stateHash = values[5],
            stopReason = StopReason.fromCode(values[6].toInt()),
            unsupportedSteps = values[7].toInt(),
            finalPc = values[8].toInt(),
            schedulerCyclesDelta = values[9],
        )
    }

    fun copyFramebuffer(handle: Long): ShortArray {
        val pixels = ShortArray(FRAMEBUFFER_PIXELS)
        nativeCopyFramebuffer(handle, pixels)
        return pixels
    }

    fun copyFramebuffer(handle: Long, outPixels: ShortArray) {
        nativeCopyFramebuffer(handle, outPixels)
    }

    fun getVideoDiagnostics(handle: Long): VideoDiagnostics? {
        val values = nativeGetVideoDiagnostics(handle)
        require(values.size == 8) { "nativeGetVideoDiagnostics returned ${values.size} values" }
        return VideoDiagnostics(
            dispcnt = values[0].toInt() and 0xFFFF,
            forcedBlank = values[1] != 0L,
            bgEnabledMask = values[2].toInt() and 0xFF,
            nonZeroPixelCount = values[3].toInt(),
            sampleRgb565 = values[4].toInt() and 0xFFFF,
            uniqueColorCount = values[5].toInt(),
            dominantColorRatio = values[6].toInt() / 1_000_000f,
            framebufferCrc32 = values[7].toInt(),
        )
    }

    fun pixel(handle: Long, x: Int, y: Int): Int = nativePixel(handle, x, y)

    fun drainAudioBatch(handle: Long): ShortArray {
        val audio = ShortArray(1098)
        val len = nativeDrainAudioBatch(handle, audio)
        return if (len == 1098) audio else audio.copyOf(len)
    }

    fun drainAudioBatch(handle: Long, outAudio: ShortArray): Int =
        nativeDrainAudioBatch(handle, outAudio)

    fun exportCartridgeSave(handle: Long): ByteArray? = nativeExportSave(handle)

    fun importCartridgeSave(handle: Long, saveBytes: ByteArray): PersistenceStatus =
        PersistenceStatus.fromCode(nativeImportSave(handle, saveBytes))

    fun encodeSaveState(handle: Long): ByteArray? = nativeSaveState(handle)

    fun loadSaveState(handle: Long, stateBytes: ByteArray): SaveStateDecodeStatus =
        SaveStateDecodeStatus.fromCode(nativeLoadState(handle, stateBytes))

    fun getCartridgeMetadata(handle: Long): CartridgeMetadata? {
        val values = nativeGetCartridgeMetadata(handle) ?: return null
        return CartridgeMetadata.fromNative(values)
    }

    fun sessionStateHash(handle: Long): Long = nativeSessionStateHash(handle)
}
