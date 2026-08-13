package com.gba.emulator.shell

/**
 * Tiny synthetic ROM used by bridge/runtime self-tests only (no copyrighted assets).
 */
object SyntheticRom {
    private val addR0R0Imm1: Int = 0xE2800001u.toInt()

    fun build12ByteTestRom(): ByteArray {
        // Mirrors C++ android_runtime_test.cpp: 4096-byte looping ROM
        val rom = ByteArray(4096)
        for (offset in 0 until 4092 step 4) {
            writeWord(rom, offset, addR0R0Imm1)
        }
        writeWord(rom, 4092, 0xEAFFFFFBu.toInt()) // branches back to 0x08000000
        return rom
    }

    /** 256-byte ROM with SRAM_V marker for persistence / save export self-tests. */
    fun buildPersistenceTestRom(): ByteArray = buildPersistenceTestRomWithValidHeader()

    fun buildPersistenceTestRomWithValidHeader(): ByteArray {
        val rom = ByteArray(256)
        writeWord(rom, 0, addR0R0Imm1)
        writeWord(rom, 4, 0xEAFFFFFDu.toInt())
        putAscii(rom, 0xA0, "PERSISTTEST")
        putAscii(rom, 0xAC, "ABCD")
        putAscii(rom, 0xB0, "01")
        rom[0xB2] = 0x96.toByte()
        rom[0xBC] = 0x02
        rom[0xBD] = computeComplementByte(rom)
        putAscii(rom, 0xC0, "SRAM_V")
        return rom
    }

    private fun computeComplementByte(rom: ByteArray): Byte {
        var sum = 0x19
        for (offset in 0xA0..0xBC) {
            sum = (sum + (rom[offset].toInt() and 0xFF)) and 0xFF
        }
        return ((0x100 - sum) and 0xFF).toByte()
    }

    private fun putAscii(bytes: ByteArray, offset: Int, text: String) {
        for (i in text.indices) {
            bytes[offset + i] = text[i].code.toByte()
        }
    }

    private fun writeWord(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
}
