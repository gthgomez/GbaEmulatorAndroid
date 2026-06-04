package com.gba.emulator.shell

/**
 * GBA cartridge header validation for user-supplied ROM bytes (Kotlin-side, pre-native load).
 */
object RomValidator {
    const val MIN_HEADER_BYTES = 0xBE
    const val FIXED_VALUE_OFFSET = 0xB2
    const val EXPECTED_FIXED_VALUE: Int = 0x96

    sealed class ValidationError(val message: String) {
        data object Empty : ValidationError("ROM file is empty")
        data object TooSmall : ValidationError("ROM is too small for a GBA cartridge header")
        data class TooLarge(val limitMiB: Int) :
            ValidationError("ROM exceeds ${limitMiB} MiB limit")

        data object OddSize : ValidationError("ROM size must be a multiple of 2 bytes")
        data object InvalidHeader : ValidationError("Invalid GBA cartridge header (expected 0x96 at 0xB2)")
        data object BadComplement : ValidationError("GBA header complement check failed")
    }

    data class ValidationResult(
        val complementValid: Boolean,
        val fixedValueValid: Boolean,
        val saveTypeHint: SaveTypeHint,
    )

    enum class SaveTypeHint {
        None,
        Sram,
        Flash64,
        Flash128,
        Eeprom,
        Ambiguous,
    }

    fun validate(bytes: ByteArray, maxBytes: Int = RomLoader.MAX_ROM_BYTES): Result<ValidationResult> {
        if (bytes.isEmpty()) {
            return Result.failure(IllegalArgumentException(ValidationError.Empty.message))
        }
        if (bytes.size > maxBytes) {
            return Result.failure(
                IllegalArgumentException(ValidationError.TooLarge(maxBytes / (1024 * 1024)).message),
            )
        }
        if ((bytes.size and 0x1) != 0) {
            return Result.failure(IllegalArgumentException(ValidationError.OddSize.message))
        }
        if (bytes.size < MIN_HEADER_BYTES) {
            return Result.failure(IllegalArgumentException(ValidationError.TooSmall.message))
        }
        val fixedValueValid = (bytes[FIXED_VALUE_OFFSET].toInt() and 0xFF) == EXPECTED_FIXED_VALUE
        if (!fixedValueValid) {
            return Result.failure(IllegalArgumentException(ValidationError.InvalidHeader.message))
        }
        val complementValid = complementValid(bytes)
        if (!complementValid) {
            return Result.failure(IllegalArgumentException(ValidationError.BadComplement.message))
        }
        return Result.success(
            ValidationResult(
                complementValid = true,
                fixedValueValid = true,
                saveTypeHint = detectSaveTypeHint(bytes),
            ),
        )
    }

    fun complementValid(rom: ByteArray): Boolean {
        if (rom.size < MIN_HEADER_BYTES) {
            return false
        }
        var sum = 0x19
        for (offset in 0xA0..0xBD) {
            sum = (sum + (rom[offset].toInt() and 0xFF)) and 0xFF
        }
        return sum == 0
    }

    fun detectSaveTypeHint(rom: ByteArray): SaveTypeHint {
        val hasSram = containsAscii(rom, "SRAM_V")
        val hasFlash128 = containsAscii(rom, "FLASH1M_V")
        val hasFlash64 = containsAscii(rom, "FLASH512_V") || containsAscii(rom, "FLASH_V")
        val hasEeprom = containsAscii(rom, "EEPROM_V")
        val count = listOf(hasSram, hasFlash128, hasFlash64, hasEeprom).count { it }
        return when {
            count == 0 -> SaveTypeHint.None
            count > 1 -> SaveTypeHint.Ambiguous
            hasSram -> SaveTypeHint.Sram
            hasFlash128 -> SaveTypeHint.Flash128
            hasFlash64 -> SaveTypeHint.Flash64
            else -> SaveTypeHint.Eeprom
        }
    }

    private fun containsAscii(rom: ByteArray, marker: String): Boolean {
        if (marker.isEmpty() || rom.size < marker.length) {
            return false
        }
        val needle = marker.encodeToByteArray()
        val limit = rom.size - needle.size
        for (start in 0..limit) {
            var matched = true
            for (index in needle.indices) {
                if (rom[start + index] != needle[index]) {
                    matched = false
                    break
                }
            }
            if (matched) {
                return true
            }
        }
        return false
    }
}
