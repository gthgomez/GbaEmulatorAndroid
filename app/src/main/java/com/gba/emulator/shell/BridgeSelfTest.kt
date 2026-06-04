package com.gba.emulator.shell

/**
 * In-memory self-test mirroring tests/android_core_bridge_test.cpp.
 */
object BridgeSelfTest {
    data class Result(
        val passed: Boolean,
        val summary: String,
    )

    fun run(): Result = GbaCoreBridge.withHandle { handle ->
        val loadStatus = GbaCoreBridge.loadRom(handle, SyntheticRom.build12ByteTestRom())
        if (loadStatus != GbaCoreBridge.BridgeStatus.Ok) {
            return@withHandle Result(false, "load_rom failed: $loadStatus")
        }

        val loadedHash = GbaCoreBridge.stateHash(handle)
        val run = GbaCoreBridge.run(handle, maxSteps = 3)
        if (run.status != GbaCoreBridge.BridgeStatus.Ok) {
            return@withHandle Result(false, "run failed: ${run.status}")
        }
        if (run.executedSteps != 3) {
            return@withHandle Result(false, "expected 3 steps, got ${run.executedSteps}")
        }
        if (run.finalPc != 0x0800000C) {
            return@withHandle Result(
                false,
                "expected final_pc=0x0800000C, got 0x${run.finalPc.toString(16)}",
            )
        }
        if (run.stateHash == 0L || run.stateHash == loadedHash) {
            return@withHandle Result(false, "state hash did not advance")
        }
        if (GbaCoreBridge.reset(handle) != GbaCoreBridge.BridgeStatus.Ok) {
            return@withHandle Result(false, "reset failed")
        }

        Result(
            passed = true,
            summary = "bridge ok: steps=${run.executedSteps}, pc=0x${run.finalPc.toString(16)}, hash=${run.stateHash}",
        )
    }
}
