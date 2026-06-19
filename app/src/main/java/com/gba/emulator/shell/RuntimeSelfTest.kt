package com.gba.emulator.shell

/**
 * Mirrors tests/android_runtime_test.cpp; returns RGB565 framebuffer on success.
 */
object RuntimeSelfTest {
    data class Result(
        val passed: Boolean,
        val summary: String,
        val framebufferRgb565: ShortArray? = null,
    )

    fun run(): Result = GbaRuntimeBridge.withHandle { handle ->
        if (GbaRuntimeBridge.loadRom(handle, ByteArray(0)) != GbaRuntimeBridge.RuntimeStatus.InvalidArgument) {
            return@withHandle Result(false, "empty ROM should reject")
        }
        if (GbaRuntimeBridge.setButtonMask(handle, 0x0400) !=
            GbaRuntimeBridge.RuntimeStatus.InputRejected
        ) {
            return@withHandle Result(false, "invalid button mask should reject")
        }

        val loadStatus = GbaRuntimeBridge.loadRom(handle, SyntheticRom.build12ByteTestRom())
        if (loadStatus != GbaRuntimeBridge.RuntimeStatus.Ok) {
            return@withHandle Result(false, "load_rom failed: $loadStatus")
        }
        if (GbaRuntimeBridge.setButtonMask(handle, 0x0009) != GbaRuntimeBridge.RuntimeStatus.Ok) {
            return@withHandle Result(false, "set_button_mask failed")
        }
        if (GbaRuntimeBridge.seedDemoEnvironment(handle) != GbaRuntimeBridge.RuntimeStatus.Ok) {
            return@withHandle Result(false, "seed demo environment failed")
        }

        val bounded = GbaRuntimeBridge.stepFrame(handle, maxSteps = 3)
        if (bounded.status != GbaRuntimeBridge.RuntimeStatus.Ok) {
            return@withHandle Result(false, "bounded step_frame failed: ${bounded.status}")
        }
        if (bounded.executedSteps != 3) {
            return@withHandle Result(
                false,
                "bounded frame expected 3 steps, got ${bounded.executedSteps}",
            )
        }
        if (bounded.renderedScanlines != 0) {
            return@withHandle Result(
                false,
                "bounded frame expected 0 scanlines, got ${bounded.renderedScanlines}",
            )
        }

        val frame = GbaRuntimeBridge.stepFrame(
            handle,
            maxSteps = EmulatorSession.DEFAULT_MAX_INSTRUCTIONS_PER_FRAME,
        )
        if (frame.status != GbaRuntimeBridge.RuntimeStatus.Ok) {
            return@withHandle Result(false, "full-frame step_frame failed: ${frame.status}")
        }
        if (frame.renderedScanlines != GbaRuntimeBridge.SCREEN_HEIGHT) {
            return@withHandle Result(
                false,
                "expected ${GbaRuntimeBridge.SCREEN_HEIGHT} scanlines, got ${frame.renderedScanlines}",
            )
        }
        val cornerPixel = GbaRuntimeBridge.pixel(handle, 0, 0)
        if (cornerPixel != 0x1234) {
            return@withHandle Result(
                false,
                "expected pixel(0,0)=0x1234, got 0x${cornerPixel.toString(16)}",
            )
        }
        if (frame.audioSamples < 3) {
            return@withHandle Result(false, "expected >=3 audio samples, got ${frame.audioSamples}")
        }
        if (frame.audioUnderruns != 0) {
            return@withHandle Result(false, "expected no audio underrun on first full frame")
        }

        val underrun = GbaRuntimeBridge.stepFrame(handle, maxSteps = 1)
        if (underrun.audioUnderruns != 1) {
            return@withHandle Result(false, "expected audio underrun on empty buffer frame")
        }

        val framebuffer = GbaRuntimeBridge.copyFramebuffer(handle)
        Result(
            passed = true,
            summary = "runtime ok: bounded 0 scanlines, full ${frame.renderedScanlines} scanlines",
            framebufferRgb565 = framebuffer,
        )
    }
}
