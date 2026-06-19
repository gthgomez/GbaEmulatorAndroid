package com.gba.emulator.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSpeedControllerTest {
    @Test
    fun oneXRunsSingleStepPerSlot() {
        val controller = PlaybackSpeedController(PlaybackSpeedController.PlaybackSpeed.One)
        assertEquals(2, controller.stepsForWallSlots(2))
    }

    @Test
    fun fourXScalesWithSlots() {
        val controller = PlaybackSpeedController(PlaybackSpeedController.PlaybackSpeed.Four)
        assertEquals(8, controller.stepsForWallSlots(2))
    }

    @Test
    fun maxUsesSafetyCap() {
        val controller = PlaybackSpeedController(PlaybackSpeedController.PlaybackSpeed.Max)
        assertEquals(
            PlaybackSpeedController.MAX_STEPS_PER_WALL_TICK,
            controller.stepsForWallSlots(1),
        )
    }

    @Test
    fun zeroSlotsRunsZeroSteps() {
        val controller = PlaybackSpeedController(PlaybackSpeedController.PlaybackSpeed.Three)
        assertEquals(0, controller.stepsForWallSlots(0))
    }

    @Test
    fun cycleWrapsFromMaxToOne() {
        val controller = PlaybackSpeedController(PlaybackSpeedController.PlaybackSpeed.Max)
        assertEquals(PlaybackSpeedController.PlaybackSpeed.One, controller.cycle())
    }
}
