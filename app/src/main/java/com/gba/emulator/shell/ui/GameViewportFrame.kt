package com.gba.emulator.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GameViewportFrame(
    screenWidth: Int,
    screenHeight: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val surround = LocalPlaySurround.current
    val backgroundModifier = when (surround) {
        PlaySurround.Black -> Modifier.background(FlowframeColors.PlayStageBlack)
        PlaySurround.SubtleGradient -> Modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0A0E14),
                    Color(0xFF141C26),
                ),
            ),
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(backgroundModifier)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(screenWidth.toFloat() / screenHeight.toFloat()),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
