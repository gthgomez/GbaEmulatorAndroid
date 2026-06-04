package com.gba.emulator.shell.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Play viewport surround; default is matte black per product decision. */
enum class PlaySurround {
    Black,
    SubtleGradient,
}

val LocalPlaySurround = staticCompositionLocalOf { PlaySurround.Black }

private val FlowframeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

/**
 * Flowframe shell theme — light cool shell by default (not dynamic Material You).
 * Set [highContrast] for accessibility toggle; play stage uses [LocalPlaySurround].
 */
@Composable
fun GbaEmulatorTheme(
    highContrast: Boolean = false,
    playSurround: PlaySurround = PlaySurround.Black,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        highContrast -> FlowframeColorSchemes.highContrastLight
        else -> FlowframeColorSchemes.light
    }

    CompositionLocalProvider(LocalPlaySurround provides playSurround) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = flowframeTypography(),
            shapes = FlowframeShapes,
            content = content,
        )
    }
}

/** True when shell should follow system dark (future); currently always light shell. */
@Composable
fun flowframeUseDarkShell(): Boolean = isSystemInDarkTheme()
