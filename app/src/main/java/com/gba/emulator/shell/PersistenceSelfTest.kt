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

        val hash0 = GbaRuntimeBridge.sessionStateHash(handle)
        val hashBefore = hash0
        val encoded = GbaRuntimeBridge.encodeSaveState(handle)
            ?: return@withHandle Result(false, "encodeSaveState returned null")
        if (encoded.size < 16) {
            return@withHandle Result(false, "encoded state too small (${encoded.size})")
        }

        val frame = GbaRuntimeBridge.stepFrame(handle, maxSteps = 4)
        if (frame.status != GbaRuntimeBridge.RuntimeStatus.Ok) {
            return@withHandle Result(false, "stepFrame failed: ${frame.status}")
        }
        val hashAfter4Steps = frame.stateHash

        val exported = GbaRuntimeBridge.exportCartridgeSave(handle) ?: return@withHandle Result(
            false,
            "export after play returned null",
        )
        if (GbaRuntimeBridge.importCartridgeSave(handle, exported) !=
            GbaRuntimeBridge.PersistenceStatus.Ok
        ) {
            return@withHandle Result(false, "import identical save rejected")
        }
        val hashAfterImport = GbaRuntimeBridge.sessionStateHash(handle)

        val reExported = GbaRuntimeBridge.exportCartridgeSave(handle)
            ?: return@withHandle Result(false, "re-export returned null")
        if (!exported.contentEquals(reExported)) {
            return@withHandle Result(false, "save bytes changed after import")
        }

        GbaRuntimeBridge.setButtonMask(handle, 0x000F)
        val stepped = GbaRuntimeBridge.stepFrame(handle, maxSteps = 2)
        if (stepped.status != GbaRuntimeBridge.RuntimeStatus.Ok) {
            return@withHandle Result(false, "stepFrame before restore failed: ${stepped.status}")
        }
        if (stepped.stateHash == hashBefore) {
            return@withHandle Result(
                false,
                "state hash did not change after input: stepped=${stepped.stateHash}, before=$hashBefore",
            )
        }

        when (GbaRuntimeBridge.loadSaveState(handle, encoded)) {
            GbaRuntimeBridge.SaveStateDecodeStatus.Ok -> Unit
            else -> return@withHandle Result(false, "loadSaveState rejected valid blob")
        }
        val hashAfterRestore = GbaRuntimeBridge.sessionStateHash(handle)
        if (hashAfterRestore != hashBefore) {
            return@withHandle Result(
                false,
                "FAIL: hashAfterRestore=$hashAfterRestore, hashBefore=$hashBefore (hash0=$hash0, hashAfter4Steps=$hashAfter4Steps, hashAfterImport=$hashAfterImport, stepped=${stepped.stateHash})",
            )
        }

        Result(
            passed = true,
            summary = "save ${exported.size} B stable; state ${encoded.size} B round-trip ok",
        )
    }
}
