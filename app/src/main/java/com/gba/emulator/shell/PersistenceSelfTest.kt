package com.gba.emulator.shell

/**
 * Workstation-parity checks for cartridge save export/import and save-state codec round-trip.
 */
object PersistenceSelfTest {
    data class Result(val passed: Boolean, val summary: String)

    fun run(): Result = GbaRuntimeBridge.withHandle { handle ->
        val rom = SyntheticRom.buildPersistenceTestRom()
        if (GbaRuntimeBridge.loadRom(handle, rom) != GbaRuntimeBridge.RuntimeStatus.Ok) {
            return@withHandle Result(false, "loadRom failed")
        }

        val initialExport = GbaRuntimeBridge.exportCartridgeSave(handle)
        if (initialExport == null || initialExport.isEmpty()) {
            return@withHandle Result(false, "SRAM save backing missing after loadRom")
        }

        val frame = GbaRuntimeBridge.stepFrame(handle, maxSteps = 4)
        if (frame.status != GbaRuntimeBridge.RuntimeStatus.Ok) {
            return@withHandle Result(false, "stepFrame failed: ${frame.status}")
        }

        val exported = GbaRuntimeBridge.exportCartridgeSave(handle) ?: return@withHandle Result(
            false,
            "export after play returned null",
        )
        if (GbaRuntimeBridge.importCartridgeSave(handle, exported) !=
            GbaRuntimeBridge.PersistenceStatus.Ok
        ) {
            return@withHandle Result(false, "import identical save rejected")
        }
        val reExported = GbaRuntimeBridge.exportCartridgeSave(handle)
            ?: return@withHandle Result(false, "re-export returned null")
        if (!exported.contentEquals(reExported)) {
            return@withHandle Result(false, "save bytes changed after import")
        }

        val encoded = GbaRuntimeBridge.encodeSaveState(handle)
            ?: return@withHandle Result(false, "encodeSaveState returned null")
        if (encoded.size < 16) {
            return@withHandle Result(false, "encoded state too small (${encoded.size})")
        }

        val hashBefore = frame.stateHash
        GbaRuntimeBridge.setButtonMask(handle, 0x000F)
        val stepped = GbaRuntimeBridge.stepFrame(handle, maxSteps = 2)
        if (stepped.stateHash == hashBefore) {
            return@withHandle Result(false, "state hash did not change after input")
        }

        when (GbaRuntimeBridge.loadSaveState(handle, encoded)) {
            GbaRuntimeBridge.SaveStateDecodeStatus.Ok -> Unit
            else -> return@withHandle Result(false, "loadSaveState rejected valid blob")
        }
        val afterRestore = GbaRuntimeBridge.stepFrame(handle, maxSteps = 1)
        if (afterRestore.stateHash != hashBefore) {
            return@withHandle Result(
                false,
                "state hash after restore ${afterRestore.stateHash} != before $hashBefore",
            )
        }

        Result(
            passed = true,
            summary = "save ${exported.size} B stable; state ${encoded.size} B round-trip ok",
        )
    }
}
