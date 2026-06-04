package com.gba.emulator.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomValidatorTest {
    @Test
    fun complementValid_acceptsSyntheticPersistenceHeader() {
        val rom = SyntheticRom.buildPersistenceTestRomWithValidHeader()
        assertTrue(RomValidator.complementValid(rom))
        assertTrue(RomValidator.validate(rom).isSuccess)
    }

    @Test
    fun complementValid_rejectsFlippedComplement() {
        val rom = SyntheticRom.buildPersistenceTestRomWithValidHeader()
        rom[0xBD] = (rom[0xBD].toInt() xor 0xFF).toByte()
        assertFalse(RomValidator.complementValid(rom))
        assertTrue(RomValidator.validate(rom).isFailure)
    }

    @Test
    fun validate_rejectsInvalidFixedValue() {
        val rom = SyntheticRom.buildPersistenceTestRomWithValidHeader()
        rom[0xB2] = 0x00
        assertTrue(RomValidator.validate(rom).isFailure)
    }
}
