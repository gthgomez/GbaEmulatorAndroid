package com.gba.emulator.shell

enum class AudioPlaybackMode {
    /** Pitch and rate follow emulation speed (Oboe playback rate multiplier). */
    Sync,

    /** Drain at fixed 32 kHz; drop oldest samples when producer outpaces consumer. */
    SteadyMusic,

    /** Silent fast-forward. */
    Mute,
}
