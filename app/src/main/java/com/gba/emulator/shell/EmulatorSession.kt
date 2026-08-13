package com.gba.emulator.shell

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Long-lived native [AndroidRuntime] handle for gameplay (not one-shot self-tests).
 * All native entry points are serialized to avoid JNI races with the frame loop.
 */
class EmulatorSession {
    private var handle: Long = 0L
    private val lock = Any()
    private val closed = AtomicBoolean(false)

    var onEmulationPaused: (() -> Unit)? = null

    var lastPauseFlushedSave: ByteArray? = null
        private set

    var lastLoadedRom: RomLoader.LoadedRom? = null
        private set

    val isActive: Boolean
        get() = synchronized(lock) { !closed.get() && handle != 0L }

    fun ensureCreated() {
        synchronized(lock) {
            check(!closed.get()) { "EmulatorSession is closed" }
            if (handle == 0L) {
                handle = GbaRuntimeBridge.nativeCreate()
                check(handle != 0L) { "nativeCreate failed" }
            }
        }
    }

    fun notifyEmulationPaused() {
        synchronized(lock) {
            if (!closed.get() && handle != 0L) {
                lastPauseFlushedSave = GbaRuntimeBridge.exportCartridgeSave(handle)
            }
            onEmulationPaused?.invoke()
        }
    }

    fun destroy() {
        synchronized(lock) {
            closed.set(true)
            if (handle != 0L) {
                GbaRuntimeBridge.nativeDestroy(handle)
                handle = 0L
            }
            lastLoadedRom = null
        }
    }

    fun loadRom(loaded: RomLoader.LoadedRom): GbaRuntimeBridge.RuntimeStatus {
        synchronized(lock) {
            if (closed.get()) {
                return GbaRuntimeBridge.RuntimeStatus.InvalidArgument
            }
            ensureCreated()
            val status = GbaRuntimeBridge.loadRom(handle, loaded.bytes)
            if (status == GbaRuntimeBridge.RuntimeStatus.Ok) {
                lastLoadedRom = loaded
            }
            return status
        }
    }

    fun reloadLastRom(): GbaRuntimeBridge.RuntimeStatus {
        synchronized(lock) {
            val rom = lastLoadedRom ?: return GbaRuntimeBridge.RuntimeStatus.InvalidArgument
            if (closed.get() || handle == 0L) {
                return GbaRuntimeBridge.RuntimeStatus.InvalidArgument
            }
            return GbaRuntimeBridge.loadRom(handle, rom.bytes)
        }
    }

    fun resetRuntime(): GbaRuntimeBridge.RuntimeStatus {
        synchronized(lock) {
            if (closed.get() || handle == 0L) {
                return GbaRuntimeBridge.RuntimeStatus.InvalidArgument
            }
            return GbaRuntimeBridge.reset(handle)
        }
    }

    fun getCartridgeMetadata(): CartridgeMetadata? {
        synchronized(lock) {
            if (closed.get() || handle == 0L) {
                return null
            }
            return GbaRuntimeBridge.getCartridgeMetadata(handle)
        }
    }

    fun setButtonMask(mask: Int): GbaRuntimeBridge.RuntimeStatus {
        synchronized(lock) {
            if (closed.get() || handle == 0L) {
                return GbaRuntimeBridge.RuntimeStatus.InvalidArgument
            }
            return GbaRuntimeBridge.setButtonMask(handle, mask)
        }
    }

    fun stepFrame(
        maxInstructionSteps: Int = DEFAULT_MAX_INSTRUCTIONS_PER_FRAME,
    ): GbaRuntimeBridge.FrameResult? {
        synchronized(lock) {
            if (closed.get() || handle == 0L) {
                return null
            }
            return GbaRuntimeBridge.stepFrame(handle, maxInstructionSteps)
        }
    }

    fun copyFramebuffer(): ShortArray? {
        synchronized(lock) {
            if (closed.get() || handle == 0L) {
                return null
            }
            return GbaRuntimeBridge.copyFramebuffer(handle)
        }
    }

    fun getVideoDiagnostics(): GbaRuntimeBridge.VideoDiagnostics? {
        synchronized(lock) {
            if (closed.get() || handle == 0L) {
                return null
            }
            return GbaRuntimeBridge.getVideoDiagnostics(handle)
        }
    }

    fun exportCartridgeSave(): ByteArray? {
        synchronized(lock) {
            if (closed.get() || handle == 0L) {
                return null
            }
            return GbaRuntimeBridge.exportCartridgeSave(handle)
        }
    }

    fun importCartridgeSave(saveBytes: ByteArray): GbaRuntimeBridge.PersistenceStatus {
        synchronized(lock) {
            if (closed.get() || handle == 0L) {
                return GbaRuntimeBridge.PersistenceStatus.InvalidArgument
            }
            return GbaRuntimeBridge.importCartridgeSave(handle, saveBytes)
        }
    }

    fun encodeSaveState(): ByteArray? {
        synchronized(lock) {
            if (closed.get() || handle == 0L) {
                return null
            }
            return GbaRuntimeBridge.encodeSaveState(handle)
        }
    }

    fun loadSaveState(stateBytes: ByteArray): GbaRuntimeBridge.SaveStateDecodeStatus {
        synchronized(lock) {
            if (closed.get() || handle == 0L) {
                return GbaRuntimeBridge.SaveStateDecodeStatus.TooSmall
            }
            return GbaRuntimeBridge.loadSaveState(handle, stateBytes)
        }
    }

    fun stepFrameAndCopy(
        maxInstructionSteps: Int = DEFAULT_MAX_INSTRUCTIONS_PER_FRAME,
    ): Pair<GbaRuntimeBridge.FrameResult, ShortArray>? {
        synchronized(lock) {
            if (closed.get() || handle == 0L) {
                return null
            }
            val frame = GbaRuntimeBridge.stepFrame(handle, maxInstructionSteps)
            val pixels = GbaRuntimeBridge.copyFramebuffer(handle)
            return frame to pixels
        }
    }

    data class PresentedFrameStep(
        val frame: GbaRuntimeBridge.FrameResult,
        val pixels: ShortArray,
        val audioBatch: ShortArray,
        val audioBatchSize: Int,
    )

    fun stepFrameAndPresent(
        pixelsDest: ShortArray,
        audioDest: ShortArray,
        maxInstructionSteps: Int = DEFAULT_MAX_INSTRUCTIONS_PER_FRAME,
    ): PresentedFrameStep? {
        synchronized(lock) {
            if (closed.get() || handle == 0L) {
                return null
            }
            val frame = GbaRuntimeBridge.stepFrame(handle, maxInstructionSteps)
            GbaRuntimeBridge.copyFramebuffer(handle, pixelsDest)
            val audioLength = GbaRuntimeBridge.drainAudioBatch(handle, audioDest)
            return PresentedFrameStep(frame, pixelsDest, audioDest, audioLength)
        }
    }

    fun drainAudioBatch(): ShortArray? {
        synchronized(lock) {
            if (closed.get() || handle == 0L) {
                return null
            }
            return GbaRuntimeBridge.drainAudioBatch(handle)
        }
    }

    companion object {
        const val DEFAULT_MAX_INSTRUCTIONS_PER_FRAME = 2_000_000
    }
}
