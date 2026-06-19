package com.gba.emulator.shell.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gba.emulator.shell.PlaybackSpeedController
import com.gba.emulator.shell.R

@Composable
fun SpeedSelectorRow(
    selected: PlaybackSpeedController.PlaybackSpeed,
    onSelect: (PlaybackSpeedController.PlaybackSpeed) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.game_speed_label),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFB8C4D0),
        )
        PlaybackSpeedController.PlaybackSpeed.entries.forEach { speed ->
            val chipA11y = stringResource(playbackSpeedContentDescription(speed))
            FilterChip(
                selected = selected == speed,
                onClick = { onSelect(speed) },
                label = { Text(speed.label) },
                modifier = Modifier.semantics {
                    contentDescription = chipA11y
                },
            )
        }
    }
}

fun playbackSpeedContentDescription(speed: PlaybackSpeedController.PlaybackSpeed): Int =
    when (speed) {
        PlaybackSpeedController.PlaybackSpeed.One -> R.string.game_speed_chip_1x_a11y
        PlaybackSpeedController.PlaybackSpeed.Two -> R.string.game_speed_chip_2x_a11y
        PlaybackSpeedController.PlaybackSpeed.Three -> R.string.game_speed_chip_3x_a11y
        PlaybackSpeedController.PlaybackSpeed.Four -> R.string.game_speed_chip_4x_a11y
        PlaybackSpeedController.PlaybackSpeed.Max -> R.string.game_speed_chip_max_a11y
    }
