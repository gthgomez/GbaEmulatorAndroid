package com.gba.emulator.shell.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Flowframe brand tokens — Arctic shell + sky-neon action accent (Palette A variant).
 * Accent is reserved for primary actions (FAB, filled buttons), not lists or controls.
 */
object FlowframeColors {
    val PlayStageBlack = Color(0xFF000000)

    val SkyNeon = Color(0xFF2EC8FF)
    val OnSkyNeon = Color(0xFF061018)
    val SkyWash = Color(0xFFE0F4FF)

    val ShellBackground = Color(0xFFBBE1FA)
    val ShellSurface = Color(0xFFFFFFFF)
    val ShellSurfaceVariant = Color(0xFFE8F2FA)
    val Ink = Color(0xFF14181C)
    val Muted = Color(0xFF5A6572)
    val Outline = Color(0xFFC5D8E8)
    val Error = Color(0xFFB3261E)
    val OnError = Color(0xFFFFFFFF)
}

object FlowframeColorSchemes {
    val light = lightColorScheme(
        primary = FlowframeColors.SkyNeon,
        onPrimary = FlowframeColors.OnSkyNeon,
        primaryContainer = FlowframeColors.SkyWash,
        onPrimaryContainer = FlowframeColors.Ink,
        background = FlowframeColors.ShellBackground,
        onBackground = FlowframeColors.Ink,
        surface = FlowframeColors.ShellSurface,
        onSurface = FlowframeColors.Ink,
        surfaceVariant = FlowframeColors.ShellSurfaceVariant,
        onSurfaceVariant = FlowframeColors.Muted,
        outline = FlowframeColors.Outline,
        error = FlowframeColors.Error,
        onError = FlowframeColors.OnError,
    )

    /** High-contrast light shell; play stage stays true black. */
    val highContrastLight = lightColorScheme(
        primary = Color(0xFF0095C8),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFB8EBFF),
        onPrimaryContainer = Color(0xFF000000),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF000000),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF000000),
        surfaceVariant = Color(0xFFE8E8E8),
        onSurfaceVariant = Color(0xFF1A1A1A),
        outline = Color(0xFF000000),
        error = Color(0xFF8C0000),
        onError = Color(0xFFFFFFFF),
    )

    /** Dark system bars during play (status/nav), not the Compose shell theme. */
    val playChrome = darkColorScheme(
        background = FlowframeColors.PlayStageBlack,
        onBackground = Color(0xFFE8EEF3),
        surface = FlowframeColors.PlayStageBlack,
        onSurface = Color(0xFFE8EEF3),
    )
}
