package com.gba.emulator.shell

/**
 * Wall-clock pacing for GBA frames (~59.727 Hz). [onWake] returns how many emulated frame
 * slots are due; callers decide how many [step_frame] runs per slot (speed multiplier).
 */
class EmulationFramePacer(
    private var lastWakeNanos: Long = UNSET_WAKE,
    private var accumulatorNs: Long = 0L,
) {
    data class WakeResult(
        val slotsDue: Int,
        val wallDeltaNs: Long,
        val behindSchedule: Boolean,
    )

    fun reset() {
        lastWakeNanos = UNSET_WAKE
        accumulatorNs = 0L
    }

    fun onWake(wakeNanos: Long): WakeResult {
        if (lastWakeNanos == UNSET_WAKE) {
            lastWakeNanos = wakeNanos
            return WakeResult(slotsDue = 0, wallDeltaNs = 0L, behindSchedule = false)
        }
        val deltaNs = (wakeNanos - lastWakeNanos).coerceAtMost(GBA_FRAME_NS * MAX_CATCHUP_SLOTS)
        lastWakeNanos = wakeNanos
        accumulatorNs += deltaNs

        var slotsDue = 0
        while (accumulatorNs >= GBA_FRAME_NS) {
            slotsDue += 1
            accumulatorNs -= GBA_FRAME_NS
        }
        return WakeResult(
            slotsDue = slotsDue,
            wallDeltaNs = deltaNs,
            behindSchedule = slotsDue > 1,
        )
    }

    companion object {
        const val GBA_FRAME_NS = 1_000_000_000L / 59_727L
        const val TARGET_FPS = 59.727
        const val CYCLES_PER_FRAME = 280_896L
        private const val MAX_CATCHUP_SLOTS = 4L
        private const val UNSET_WAKE = -1L
    }
}
