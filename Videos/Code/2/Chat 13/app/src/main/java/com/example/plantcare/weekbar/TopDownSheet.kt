package com.example.plantcare.weekbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.plantcare.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TopDownSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    sheetHeight: Float = 320f,
    content: @Composable ColumnScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(if (expanded) 0f else -sheetHeight) }

    LaunchedEffect(expanded) {
        offsetY.animateTo(if (expanded) 0f else -sheetHeight, animationSpec = tween(350))
    }

    if (expanded || offsetY.value > -sheetHeight + 1) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(sheetHeight.dp)
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .background(Color.Transparent)
        ) {
            Surface(
                Modifier
                    .fillMaxWidth()
                    .height(sheetHeight.dp)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            val newOffset = offsetY.value + delta
                            if (newOffset <= 0f && newOffset >= -sheetHeight) {
                                scope.launch { offsetY.snapTo(newOffset) }
                            }
                        },
                        onDragStopped = {
                            scope.launch {
                                if (offsetY.value > -sheetHeight * 0.3f) {
                                    offsetY.animateTo(0f)
                                } else {
                                    offsetY.animateTo(-sheetHeight)
                                    onDismiss()
                                }
                            }
                        }
                    ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                elevation = 16.dp,
                color = colorResource(R.color.pc_surface)
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(top = 12.dp)
                ) {
                    // Handle at the top of the sheet
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(40.dp)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(colorResource(R.color.pc_secondary))
                    )
                    Spacer(Modifier.height(8.dp))
                    content()
                }
            }
        }
    }
}