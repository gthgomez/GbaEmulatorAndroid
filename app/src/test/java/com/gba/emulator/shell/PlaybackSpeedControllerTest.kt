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
    fun catchUpStepsAreCappedPerIteration() {
        val oneX = PlaybackSpeedController(PlaybackSpeedController.PlaybackSpeed.One)
        assertEquals(
            PlaybackSpeedController.MAX_CATCHUP_SLOTS_PER_ITERATION,
            oneX.stepsForWallSlots(4),
        )
        val fourX = PlaybackSpeedController(PlaybackSpeedController.PlaybackSpeed.Four)
        assertEquals(
            4 * PlaybackSpeedController.MAX_CATCHUP_SLOTS_PER_ITERATION,
            fourX.stepsForWallSlots(4),
        )
    }

    @Test
    fun cycleWrapsFromMaxToOne() {
        val controller = PlaybackSpeedController(PlaybackSpeedController.PlaybackSpeed.Max)
        assertEquals(PlaybackSpeedController.PlaybackSpeed.One, controller.cycle())
    }
}
