package com.gba.emulator.shell.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gba.emulator.shell.R

private val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
)

private val SplineSans = FontFamily(
    Font(R.font.splinesans_regular, FontWeight.Normal),
    Font(R.font.splinesans_medium, FontWeight.Medium),
)

private val MetadataMono = FontFamily.Monospace

fun flowframeTypography(): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.withOutfit(),
        displayMedium = base.displayMedium.withOutfit(),
        displaySmall = base.displaySmall.withOutfit(),
        headlineLarge = base.headlineLarge.withOutfit(FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.withOutfit(FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.withOutfit(FontWeight.SemiBold),
        titleLarge = base.titleLarge.withOutfit(FontWeight.Medium),
        titleMedium = base.titleMedium.withOutfit(FontWeight.Medium),
        titleSmall = base.titleSmall.withOutfit(FontWeight.Medium),
        bodyLarge = base.bodyLarge.withSpline(),
        bodyMedium = base.bodyMedium.withSpline(),
        bodySmall = base.bodySmall.withSpline(),
        labelLarge = base.labelLarge.withOutfit(FontWeight.Medium),
        labelMedium = base.labelMedium.withOutfit(FontWeight.Medium),
        labelSmall = base.labelSmall.withSpline(FontWeight.Medium),
    )
}

/** Monospace metadata (game code, debug). */
fun flowframeMetadataStyle(): TextStyle =
    TextStyle(
        fontFamily = MetadataMono,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

private fun TextStyle.withOutfit(weight: FontWeight = FontWeight.Normal): TextStyle =
    copy(fontFamily = Outfit, fontWeight = weight)

private fun TextStyle.withSpline(weight: FontWeight = FontWeight.Normal): TextStyle =
    copy(fontFamily = SplineSans, fontWeight = weight)
