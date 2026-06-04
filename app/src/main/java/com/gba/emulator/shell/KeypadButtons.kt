package com.gba.emulator.shell

/** GBA KEYINPUT active-low bitmask (pressed bits set). */
object KeypadButtons {
    const val A = 0x0001
    const val B = 0x0002
    const val SELECT = 0x0004
    const val START = 0x0008
    const val RIGHT = 0x0010
    const val LEFT = 0x0020
    const val UP = 0x0040
    const val DOWN = 0x0080
    const val R = 0x0100
    const val L = 0x0200

    fun combine(pressed: Set<Int>): Int = pressed.fold(0) { mask, bit -> mask or bit }
}
