package com.gba.emulator.shell

/**
 * Thin JNI wrapper over [gba::core] C API from android_core_bridge.hpp.
 * No ROM assets ship with the app; callers supply explicit byte buffers only.
 */
object GbaCoreBridge {
    init {
        System.loadLibrary("gbaemulator")
    }

    enum class BridgeStatus(val code: Int) {
        Ok(0),
        NullHandle(1),
        InvalidArgument(2),
        RomRejected(3),
        ;

        companion object {
            fun fromCode(code: Int): BridgeStatus =
                entries.firstOrNull { it.code == code } ?: InvalidArgument
        }
    }

    data class RunResult(
        val status: BridgeStatus,
        val executedSteps: Int,
        val finalPc: Int,
        val stateHash: Long,
    )

    external fun nativeCreate(): Long
    external fun nativeDestroy(handle: Long)
    external fun nativeReset(handle: Long): Int
    external fun nativeLoadRom(handle: Long, rom: ByteArray): Int
    external fun nativeRun(handle: Long, maxSteps: Int): LongArray
    external fun nativeStateHash(handle: Long): Long

    inline fun <T> withHandle(block: (Long) -> T): T {
        val handle = nativeCreate()
        check(handle != 0L) { "gba_android_core_create returned null" }
        try {
            return block(handle)
        } finally {
            nativeDestroy(handle)
        }
    }

    fun reset(handle: Long): BridgeStatus = BridgeStatus.fromCode(nativeReset(handle))

    fun loadRom(handle: Long, rom: ByteArray): BridgeStatus =
        BridgeStatus.fromCode(nativeLoadRom(handle, rom))

    fun run(handle: Long, maxSteps: Int): RunResult {
        val values = nativeRun(handle, maxSteps)
        require(values.size == 4) { "nativeRun returned ${values.size} values" }
        return RunResult(
            status = BridgeStatus.fromCode(values[0].toInt()),
            executedSteps = values[1].toInt(),
            finalPc = values[2].toInt(),
            stateHash = values[3],
        )
    }

    fun stateHash(handle: Long): Long = nativeStateHash(handle)
}
