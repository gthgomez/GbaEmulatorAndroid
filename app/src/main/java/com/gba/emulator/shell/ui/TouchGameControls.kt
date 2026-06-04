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
            onPress = { bit ->
                pressed.add(bit)
                updateMask()
            },
            onRelease = { bit ->
                pressed.remove(bit)
                updateMask()
            },
        )
        ActionCluster(
            onPress = { bit ->
                pressed.add(bit)
                updateMask()
            },
            onRelease = { bit ->
                pressed.remove(bit)
                updateMask()
            },
        )
    }
}

@Composable
private fun DpadCluster(
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ControlButton("L", KeypadButtons.L, onPress, onRelease)
        ControlButton("UP", KeypadButtons.UP, onPress, onRelease)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlButton("LEFT", KeypadButtons.LEFT, onPress, onRelease)
            ControlButton("RIGHT", KeypadButtons.RIGHT, onPress, onRelease)
        }
        ControlButton("DOWN", KeypadButtons.DOWN, onPress, onRelease)
        ControlButton("SELECT", KeypadButtons.SELECT, onPress, onRelease, wide = true)
    }
}

@Composable
private fun ActionCluster(
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ControlButton("R", KeypadButtons.R, onPress, onRelease)
        ControlButton("B", KeypadButtons.B, onPress, onRelease)
        ControlButton("A", KeypadButtons.A, onPress, onRelease)
        ControlButton("START", KeypadButtons.START, onPress, onRelease, wide = true)
    }
}

@Composable
private fun ControlButton(
    label: String,
    bit: Int,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    wide: Boolean = false,
) {
    val size = if (wide) 72.dp else 56.dp
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF1A1D21).copy(alpha = 0.35f))
            .pointerInput(bit) {
                awaitEachGesture {
                    awaitFirstDown()
                    onPress(bit)
                    waitForUpOrCancellation()
                    onRelease(bit)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}
