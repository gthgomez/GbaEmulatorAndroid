package com.gba.emulator.shell

/**
 * Cartridge metadata read from the loaded native runtime (authoritative after [EmulatorSession.loadRom]).
 */
data class CartridgeMetadata(
    val title: String,
    val gameCode: String,
    val makerCode: String,
    val saveType: SaveType,
    val headerValid: Boolean,
    val complementValid: Boolean,
) {
    enum class SaveType(val nativeCode: Int) {
        None(0),
        Sram32k(1),
        Flash64k(2),
        Flash128k(3),
        Eeprom512(4),
        Eeprom8k(5),
        Ambiguous(-1),
        ;

        companion object {
            fun fromNativeCode(code: Int): SaveType =
                entries.firstOrNull { it.nativeCode == code } ?: None
        }
    }

    companion object {
        fun fromNative(values: Array<String>): CartridgeMetadata? {
            if (values.size < 6) {
                return null
            }
            return CartridgeMetadata(
                title = values[0],
                gameCode = values[1],
                makerCode = values[2],
                saveType = SaveType.fromNativeCode(values[3].toIntOrNull() ?: 0),
                headerValid = values[4] == "1",
                complementValid = values[5] == "1",
            )
        }
    }
}
