package com.gba.emulator.shell.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/** Play viewport surround; default is matte black per product decision. */
enum class PlaySurround {
    Black,
    SubtleGradient,
}

val LocalPlaySurround = staticCompositionLocalOf { PlaySurround.Black }
val LocalDarkModeOverride = staticCompositionLocalOf<Boolean?> { null }

private val FlowframeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

/**
 * Flowframe shell theme — supports light and dark modes with system defaults and toggle overrides.
 */
@Composable
fun GbaEmulatorTheme(
    highContrast: Boolean = false,
    darkModeOverride: Boolean? = null,
    playSurround: PlaySurround = PlaySurround.Black,
    content: @Composable () -> Unit,
) {
    val isSystemDark = isSystemInDarkTheme()
    val useDark = when (darkModeOverride) {
        null -> isSystemDark
        else -> darkModeOverride
    }

    val colorScheme = when {
        highContrast -> FlowframeColorSchemes.highContrastLight
        useDark -> FlowframeColorSchemes.dark
        else -> FlowframeColorSchemes.light
    }

    val context = LocalContext.current
    SideEffect {
        val activity = context.findActivity()
        val window = activity?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !useDark
                isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    CompositionLocalProvider(
        LocalPlaySurround provides playSurround,
        LocalDarkModeOverride provides darkModeOverride
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = flowframeTypography(),
            shapes = FlowframeShapes,
            content = content,
        )
    }
}

/** True when shell should follow system dark. */
@Composable
fun flowframeUseDarkShell(): Boolean {
    val override = LocalDarkModeOverride.current
    return override ?: isSystemInDarkTheme()
}
