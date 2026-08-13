package com.gba.emulator.shell.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gba.emulator.shell.KeypadButtons

@Composable
fun TouchGameControls(
    onMaskChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val view = LocalView.current
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val buttonBounds = remember { mutableMapOf<Int, Rect>() }
    val pressedButtons = remember { mutableStateOf(emptySet<Int>()) }
    var prevPressedButtons by remember { mutableStateOf(emptySet<Int>()) }

    fun updatePressedKeys(activePointers: Map<PointerId, Offset>) {
        val pressed = mutableSetOf<Int>()
        for (offset in activePointers.values) {
            for ((bit, rect) in buttonBounds) {
                if (rect.contains(offset)) {
                    pressed.add(bit)
                }
            }
        }

        // Trigger haptic feedback when a new button transitions to pressed state
        val newlyPressed = pressed - prevPressedButtons
        if (newlyPressed.isNotEmpty()) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        prevPressedButtons = pressed

        pressedButtons.value = pressed
        onMaskChanged(KeypadButtons.combine(pressed))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(FlowframeColors.PlayStageBlack)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .onGloballyPositioned { rootCoordinates = it }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val activePointers = mutableMapOf<PointerId, Offset>()

                    // Track the first finger down
                    val down = awaitFirstDown(requireUnconsumed = false)
                    activePointers[down.id] = down.position
                    updatePressedKeys(activePointers)

                    while (true) {
                        val event = awaitPointerEvent()
                        
                        // Sync all touch coordinates
                        for (change in event.changes) {
                            if (change.pressed) {
                                activePointers[change.id] = change.position
                            } else {
                                activePointers.remove(change.id)
                            }
                        }

                        updatePressedKeys(activePointers)

                        // End gesture tracker loop when no fingers are down
                        val anyPressed = event.changes.any { it.pressed }
                        if (!anyPressed) {
                            break
                        }
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Row 1: L / R Shoulder Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ControlButton(
                label = "L",
                bit = KeypadButtons.L,
                isPressed = pressedButtons.value.contains(KeypadButtons.L),
                rootCoordinates = rootCoordinates,
                onBoundsChanged = { bit, rect -> buttonBounds[bit] = rect },
                modifier = Modifier.size(width = 80.dp, height = 36.dp),
                shape = RoundedCornerShape(bottomEnd = 16.dp, topEnd = 4.dp),
                contentDesc = "L Shoulder Button"
            )
            ControlButton(
                label = "R",
                bit = KeypadButtons.R,
                isPressed = pressedButtons.value.contains(KeypadButtons.R),
                rootCoordinates = rootCoordinates,
                onBoundsChanged = { bit, rect -> buttonBounds[bit] = rect },
                modifier = Modifier.size(width = 80.dp, height = 36.dp),
                shape = RoundedCornerShape(bottomStart = 16.dp, topStart = 4.dp),
                contentDesc = "R Shoulder Button"
            )
        }

        // Row 2: D-pad / Select-Start / Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // D-Pad Cross Layout
            DpadCross(
                pressedButtons = pressedButtons.value,
                rootCoordinates = rootCoordinates,
                onBoundsChanged = { bit, rect -> buttonBounds[bit] = rect }
            )

            // Select & Start central row
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                ControlButton(
                    label = "SELECT",
                    bit = KeypadButtons.SELECT,
                    isPressed = pressedButtons.value.contains(KeypadButtons.SELECT),
                    rootCoordinates = rootCoordinates,
                    onBoundsChanged = { bit, rect -> buttonBounds[bit] = rect },
                    modifier = Modifier.size(width = 68.dp, height = 24.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentDesc = "Select Button"
                )
                ControlButton(
                    label = "START",
                    bit = KeypadButtons.START,
                    isPressed = pressedButtons.value.contains(KeypadButtons.START),
                    rootCoordinates = rootCoordinates,
                    onBoundsChanged = { bit, rect -> buttonBounds[bit] = rect },
                    modifier = Modifier.size(width = 68.dp, height = 24.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentDesc = "Start Button"
                )
            }

            // Action Cluster (Diagonal A/B)
            ActionClusterDiagonal(
                pressedButtons = pressedButtons.value,
                rootCoordinates = rootCoordinates,
                onBoundsChanged = { bit, rect -> buttonBounds[bit] = rect }
            )
        }
    }
}

@Composable
private fun DpadCross(
    pressedButtons: Set<Int>,
    rootCoordinates: LayoutCoordinates?,
    onBoundsChanged: (Int, Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(136.dp),
        contentAlignment = Alignment.Center
    ) {
        val btnSize = 46.dp
        
        ControlButton(
            label = "▲",
            bit = KeypadButtons.UP,
            isPressed = pressedButtons.contains(KeypadButtons.UP),
            rootCoordinates = rootCoordinates,
            onBoundsChanged = onBoundsChanged,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(btnSize),
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            contentDesc = "D-pad Up"
        )
        
        ControlButton(
            label = "◀",
            bit = KeypadButtons.LEFT,
            isPressed = pressedButtons.contains(KeypadButtons.LEFT),
            rootCoordinates = rootCoordinates,
            onBoundsChanged = onBoundsChanged,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(btnSize),
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
            contentDesc = "D-pad Left"
        )
        
        ControlButton(
            label = "▶",
            bit = KeypadButtons.RIGHT,
            isPressed = pressedButtons.contains(KeypadButtons.RIGHT),
            rootCoordinates = rootCoordinates,
            onBoundsChanged = onBoundsChanged,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(btnSize),
            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
            contentDesc = "D-pad Right"
        )
        
        ControlButton(
            label = "▼",
            bit = KeypadButtons.DOWN,
            isPressed = pressedButtons.contains(KeypadButtons.DOWN),
            rootCoordinates = rootCoordinates,
            onBoundsChanged = onBoundsChanged,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(btnSize),
            shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
            contentDesc = "D-pad Down"
        )

        // Visual intersection block
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color(0xFF2C323D))
                .align(Alignment.Center)
        )
    }
}

@Composable
private fun ActionClusterDiagonal(
    pressedButtons: Set<Int>,
    rootCoordinates: LayoutCoordinates?,
    onBoundsChanged: (Int, Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(130.dp)
            .height(110.dp),
        contentAlignment = Alignment.Center
    ) {
        val size = 52.dp

        ControlButton(
            label = "B",
            bit = KeypadButtons.B,
            isPressed = pressedButtons.contains(KeypadButtons.B),
            rootCoordinates = rootCoordinates,
            onBoundsChanged = onBoundsChanged,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(size),
            shape = CircleShape,
            colorAccent = Color(0xFFFF5252),
            contentDesc = "B Button"
        )

        ControlButton(
            label = "A",
            bit = KeypadButtons.A,
            isPressed = pressedButtons.contains(KeypadButtons.A),
            rootCoordinates = rootCoordinates,
            onBoundsChanged = onBoundsChanged,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(size),
            shape = CircleShape,
            colorAccent = FlowframeColors.SkyNeon,
            contentDesc = "A Button"
        )
    }
}

@Composable
private fun ControlButton(
    label: String,
    bit: Int,
    isPressed: Boolean,
    rootCoordinates: LayoutCoordinates?,
    onBoundsChanged: (Int, Rect) -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    colorAccent: Color? = null,
    contentDesc: String
) {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.08f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "btnScale"
    )

    val activeColor = colorAccent ?: FlowframeColors.SkyNeon
    val backgroundColor = if (isPressed) {
        activeColor.copy(alpha = 0.55f)
    } else {
        Color(0xFF1E2430).copy(alpha = 0.4f)
    }
    
    val borderColor = if (isPressed) {
        activeColor
    } else {
        Color(0xFF334155).copy(alpha = 0.5f)
    }

    val textStyle = if (label.length > 2) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.titleMedium
    }

    Box(
        modifier = modifier
            .scale(scale)
            .graphicsLayer {
                this.shape = shape
                this.clip = true
            }
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .onGloballyPositioned { coords ->
                val root = rootCoordinates
                if (root != null && coords.isAttached) {
                    onBoundsChanged(bit, root.localBoundingBoxOf(coords))
                }
            }
            .semantics {
                contentDescription = contentDesc
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = textStyle,
            fontWeight = FontWeight.Bold,
            color = if (isPressed) Color.White else Color(0xFF94A3B8)
        )
    }
}
