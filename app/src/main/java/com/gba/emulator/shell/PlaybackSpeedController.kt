package com.gba.emulator.shell

/**
 * Fast-forward speed selection for gameplay. [stepsForWallSlots] maps pacer slots to emulated
 * frame steps; [Max] runs a tight loop capped by [MAX_STEPS_PER_WALL_TICK].
 */
class PlaybackSpeedController(
    initialSpeed: PlaybackSpeed = PlaybackSpeed.One,
) {
    var speed: PlaybackSpeed = initialSpeed
        private set

    fun cycle(): PlaybackSpeed {
        speed = speed.next()
        return speed
    }

    fun setSpeed(next: PlaybackSpeed) {
        speed = next
    }

    fun stepsForWallSlots(slotsDue: Int): Int {
        if (slotsDue <= 0) {
            return 0
        }
        return when (speed) {
            PlaybackSpeed.Max -> MAX_STEPS_PER_WALL_TICK
            else -> speed.multiplier * slotsDue
        }
    }

    val playbackRateMultiplier: Float
        get() = when (speed) {
            PlaybackSpeed.One -> 1f
            PlaybackSpeed.Two -> 2f
            PlaybackSpeed.Three -> 3f
            PlaybackSpeed.Four -> 4f
            PlaybackSpeed.Max -> 8f
        }

    enum class PlaybackSpeed(val multiplier: Int, val label: String) {
        One(1, "1×"),
        Two(2, "2×"),
        Three(3, "3×"),
        Four(4, "4×"),
        Max(Int.MAX_VALUE, "Max"),
        ;

        fun next(): PlaybackSpeed = when (this) {
            One -> Two
            Two -> Three
            Three -> Four
            Four -> Max
            Max -> One
        }
    }

    companion object {
        const val MAX_STEPS_PER_WALL_TICK = 120
    }
}
