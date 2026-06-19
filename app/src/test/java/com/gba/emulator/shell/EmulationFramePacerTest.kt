package com.gba.emulator.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulationFramePacerTest {
    @Test
    fun firstWakeConsumesOneSlot() {
        val pacer = EmulationFramePacer()
        val prime = pacer.onWake(0L)
        assertEquals(0, prime.slotsDue)
        val wake = pacer.onWake(EmulationFramePacer.GBA_FRAME_NS)
        assertEquals(1, wake.slotsDue)
        assertFalse(wake.behindSchedule)
    }

    @Test
    fun accumulatesPartialWallTimeBeforeSlot() {
        val pacer = EmulationFramePacer()
        pacer.onWake(0L)
        val half = EmulationFramePacer.GBA_FRAME_NS / 2
        val first = pacer.onWake(half)
        assertEquals(0, first.slotsDue)

        val second = pacer.onWake(half + EmulationFramePacer.GBA_FRAME_NS)
        assertEquals(1, second.slotsDue)
    }

    @Test
    fun highRefreshCatchesUpWithTwoSlots() {
        val pacer = EmulationFramePacer()
        pacer.onWake(0L)
        val wake = pacer.onWake(EmulationFramePacer.GBA_FRAME_NS * 2)
        assertEquals(2, wake.slotsDue)
        assertTrue(wake.behindSchedule)
    }

    @Test
    fun resetClearsAccumulator() {
        val pacer = EmulationFramePacer()
        pacer.onWake(0L)
        pacer.onWake(EmulationFramePacer.GBA_FRAME_NS / 2)
        pacer.reset()
        pacer.onWake(0L)
        val wake = pacer.onWake(EmulationFramePacer.GBA_FRAME_NS)
        assertEquals(1, wake.slotsDue)
    }
}
