package com.gba.emulator.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.unit.dp
import com.gba.emulator.shell.KeypadButtons

@Composable
fun TouchGameControls(
    onMaskChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val pressed = remember { mutableStateSetOf<Int>() }

    fun updateMask() {
        onMaskChanged(KeypadButtons.combine(pressed))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DpadCluster(
            enabled = enabled,
            onPress = { bit ->
                if (!enabled) return@DpadCluster
                pressed.add(bit)
                updateMask()
            },
            onRelease = { bit ->
                if (!enabled) return@DpadCluster
                pressed.remove(bit)
                updateMask()
            },
        )
        ActionCluster(
            enabled = enabled,
            onPress = { bit ->
                if (!enabled) return@ActionCluster
                pressed.add(bit)
                updateMask()
            },
            onRelease = { bit ->
                if (!enabled) return@ActionCluster
                pressed.remove(bit)
                updateMask()
            },
        )
    }
}

@Composable
private fun DpadCluster(
    enabled: Boolean,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ControlButton("L", KeypadButtons.L, enabled, onPress, onRelease)
        ControlButton("UP", KeypadButtons.UP, enabled, onPress, onRelease)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlButton("LEFT", KeypadButtons.LEFT, enabled, onPress, onRelease)
            ControlButton("RIGHT", KeypadButtons.RIGHT, enabled, onPress, onRelease)
        }
        ControlButton("DOWN", KeypadButtons.DOWN, enabled, onPress, onRelease)
        ControlButton("SELECT", KeypadButtons.SELECT, enabled, onPress, onRelease, wide = true)
    }
}

@Composable
private fun ActionCluster(
    enabled: Boolean,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ControlButton("R", KeypadButtons.R, enabled, onPress, onRelease)
        ControlButton("B", KeypadButtons.B, enabled, onPress, onRelease)
        ControlButton("A", KeypadButtons.A, enabled, onPress, onRelease)
        ControlButton("START", KeypadButtons.START, enabled, onPress, onRelease, wide = true)
    }
}

@Composable
private fun ControlButton(
    label: String,
    bit: Int,
    enabled: Boolean,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    wide: Boolean = false,
) {
    val size = if (wide) 72.dp else 56.dp
    val alpha = if (enabled) 0.35f else 0.15f
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF1A1D21).copy(alpha = alpha))
            .then(
                if (enabled) {
                    Modifier.pointerInput(bit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            onPress(bit)
                            waitForUpOrCancellation()
                            onRelease(bit)
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}
