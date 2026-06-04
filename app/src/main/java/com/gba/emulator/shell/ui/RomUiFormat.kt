package com.gba.emulator.shell.ui

import com.gba.emulator.shell.CartridgeMetadata
import com.gba.emulator.shell.R
import com.gba.emulator.shell.RomValidator

internal fun saveTypeHintLabelRes(hint: RomValidator.SaveTypeHint): Int =
    when (hint) {
        RomValidator.SaveTypeHint.None -> R.string.save_type_none
        RomValidator.SaveTypeHint.Sram -> R.string.save_type_sram
        RomValidator.SaveTypeHint.Flash64 -> R.string.save_type_flash64
        RomValidator.SaveTypeHint.Flash128 -> R.string.save_type_flash128
        RomValidator.SaveTypeHint.Eeprom -> R.string.save_type_eeprom
        RomValidator.SaveTypeHint.Ambiguous -> R.string.save_type_ambiguous
    }

internal fun saveTypeLabelRes(saveType: CartridgeMetadata.SaveType): Int =
    when (saveType) {
        CartridgeMetadata.SaveType.None -> R.string.save_type_none
        CartridgeMetadata.SaveType.Sram32k -> R.string.save_type_sram
        CartridgeMetadata.SaveType.Flash64k -> R.string.save_type_flash64
        CartridgeMetadata.SaveType.Flash128k -> R.string.save_type_flash128
        CartridgeMetadata.SaveType.Eeprom512,
        CartridgeMetadata.SaveType.Eeprom8k,
        -> R.string.save_type_eeprom
        CartridgeMetadata.SaveType.Ambiguous -> R.string.save_type_ambiguous
    }
