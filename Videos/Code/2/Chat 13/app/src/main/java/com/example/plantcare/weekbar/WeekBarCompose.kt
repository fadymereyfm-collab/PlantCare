package com.example.plantcare.weekbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.plantcare.R
import java.time.LocalDate
import java.time.format.TextStyle
import androidx.compose.foundation.clickable
import java.util.*
import kotlin.math.abs

@Composable
fun WeekBarWithMonthPicker(
    viewModel: ReminderViewModel,
    showMonthSheet: Boolean,
    onShowMonthSheet: (Boolean) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onGoToToday: () -> Unit,
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val weekDays = viewModel.getCurrentWeekDays(selectedDate)
    val daysWithReminders by viewModel.daysWithRemindersThisWeek.collectAsState()

    val dragOffsetInternal = remember { Animatable(0f) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var animateToAction by remember { mutableStateOf<Int?>(null) }
    var weekSwitch by remember { mutableStateOf<Int?>(null) }
    val haptics = LocalHapticFeedback.current
    var horizontalDragActive by remember { mutableStateOf(false) }
    var pendingSnapX by remember { mutableStateOf<Float?>(null) }

    // Snap animation when requested
    LaunchedEffect(pendingSnapX) {
        pendingSnapX?.let { x ->
            dragOffsetInternal.snapTo(x)
            pendingSnapX = null
        }
    }

    // Animation: respond to swipe intent
    LaunchedEffect(animateToAction) {
        when (animateToAction) {
            1 -> {
                dragOffsetInternal.animateTo(0f, animationSpec = tween(250))
                weekSwitch = 1
            }
            2 -> {
                dragOffsetInternal.animateTo(0f, animationSpec = tween(250))
                weekSwitch = 2
            }
            0 -> {
                dragOffsetInternal.animateTo(0f, animationSpec = tween(180))
            }
        }
        if (animateToAction != null) {
            dragOffsetX = 0f
            animateToAction = null
        }
    }
    LaunchedEffect(weekSwitch) {
        when (weekSwitch) {
            1 -> {
                viewModel.previousWeek()
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            }
            2 -> {
                viewModel.nextWeek()
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            }
        }
        weekSwitch = null
    }

    val primaryColor = colorResource(R.color.pc_primary)
    val onSurfaceColor = colorResource(R.color.pc_onSurface)
    val onSurfaceSecondaryColor = colorResource(R.color.pc_onSurfaceSecondary)
    val reminderDotColor = colorResource(R.color.pc_accent2)
    val dividerColor = colorResource(R.color.pc_outlineVariant)
    val weekBarBg = colorResource(R.color.pc_surfaceContainerLow)

    // Horizontal week swipe
    Box(
        Modifier
            .fillMaxWidth()
            .background(weekBarBg)
            .padding(top = 12.dp, bottom = 2.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        horizontalDragActive = true
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragOffsetX += dragAmount
                        pendingSnapX = dragOffsetX
                    },
                    onDragEnd = {
                        val threshold = 80f
                        when {
                            dragOffsetX > threshold -> animateToAction = 1
                            dragOffsetX < -threshold -> animateToAction = 2
                            else -> animateToAction = 0
                        }
                        horizontalDragActive = false
                    },
                    onDragCancel = {
                        animateToAction = 0
                        horizontalDragActive = false
                    }
                )
            }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .graphicsLayer {
                    translationX = dragOffsetInternal.value
                },
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weekDays.forEach { date ->
                val isSelected = date == selectedDate
                val isToday = date == LocalDate.now()
                val hasReminder = daysWithReminders.contains(date)
                val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN)
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .padding(horizontal = 2.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isSelected -> primaryColor.copy(alpha = 0.30f)
                                isToday -> Color.Transparent
                                else -> Color.Transparent
                            }
                        )
                        .then(
                            if (isToday && !isSelected)
                                Modifier.border(2.dp, primaryColor, CircleShape)
                            else
                                Modifier
                        )
                        .clickable { onSelectDate(date) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = dayName,
                            fontSize = 13.sp,
                            color = if (isSelected) onSurfaceColor else onSurfaceSecondaryColor
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = date.dayOfMonth.toString(),
                            fontSize = 16.sp,
                            color = onSurfaceColor
                        )
                        if (hasReminder && !isSelected) {
                            Spacer(Modifier.height(2.dp))
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(reminderDotColor)
                            )
                        }
                    }
                }
            }
        }
    }

    Divider(
        color = dividerColor,
        thickness = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp)
    )
}