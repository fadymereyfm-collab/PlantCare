package com.example.plantcare.weekbar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import com.example.plantcare.R
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthPicker(
    currentDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    daysWithReminders: List<LocalDate> = emptyList(),
    remindersByDate: Map<LocalDate, List<Reminder>> = emptyMap(),
    onConfirmDate: ((LocalDate) -> Unit)? = null,
    displayedMonthState: MutableState<YearMonth>? = null,
    onMonthChanged: ((YearMonth) -> Unit)? = null
) {
    val yearsBefore = 10
    val yearsAfter = 10
    val totalMonths = (yearsBefore + yearsAfter + 1) * 12

    val todayMonth = YearMonth.from(currentDate)
    val monthsList = remember {
        List(totalMonths) { idx ->
            todayMonth.minusMonths((yearsBefore * 12).toLong()).plusMonths(idx.toLong())
        }
    }

    val initialIndex = yearsBefore * 12
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { monthsList.size }
    )
    val coroutineScope = rememberCoroutineScope()
    var selectedMonth by remember { mutableStateOf(todayMonth) }

    // Tracks which date has its reminder-cloud popup currently open.
    // Only one popup at a time across the whole pager.
    var popupForDate by remember { mutableStateOf<LocalDate?>(null) }

    // Close any open popup when the displayed month changes via swipe.
    LaunchedEffect(pagerState.currentPage) {
        val ym = monthsList[pagerState.currentPage]
        selectedMonth = ym
        displayedMonthState?.value = ym
        onMonthChanged?.invoke(ym)
        popupForDate = null
    }

    // External request to jump to a month (e.g. "go to today").
    LaunchedEffect(displayedMonthState?.value) {
        displayedMonthState?.value?.let { monthFromState ->
            val idx = monthsList.indexOfFirst { it == monthFromState }
            if (idx >= 0 && pagerState.currentPage != idx) {
                coroutineScope.launch { pagerState.scrollToPage(idx) }
                selectedMonth = monthFromState
                popupForDate = null
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp, max = 360.dp)
        ) { page ->
            val ym = monthsList[page]
            MonthGrid(
                month = ym,
                selectedDate = currentDate,
                daysWithReminders = daysWithReminders.filter { date -> YearMonth.from(date) == ym },
                remindersByDate = remindersByDate.filterKeys { YearMonth.from(it) == ym },
                popupForDate = popupForDate,
                onDayClick = { date, hasReminder ->
                    if (!hasReminder) {
                        // Day without reminders: original behaviour — navigate to it.
                        popupForDate = null
                        onSelectDate(date)
                        selectedMonth = ym
                    } else {
                        // Day with reminders: first tap opens the cloud,
                        // second tap on the same date confirms and closes the month list.
                        if (popupForDate == date) {
                            popupForDate = null
                            if (onConfirmDate != null) onConfirmDate(date) else onSelectDate(date)
                            selectedMonth = ym
                        } else {
                            popupForDate = date
                        }
                    }
                },
                onPopupClick = { date ->
                    // Tapping the cloud itself confirms the date and dismisses the month list.
                    popupForDate = null
                    if (onConfirmDate != null) onConfirmDate(date) else onSelectDate(date)
                    selectedMonth = ym
                },
                onEmptyAreaClick = {
                    // Tapping any empty area inside the month list dismisses the cloud.
                    popupForDate = null
                }
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    daysWithReminders: List<LocalDate> = emptyList(),
    remindersByDate: Map<LocalDate, List<Reminder>> = emptyMap(),
    popupForDate: LocalDate? = null,
    onDayClick: (LocalDate, Boolean) -> Unit,
    onPopupClick: (LocalDate) -> Unit,
    onEmptyAreaClick: () -> Unit
) {
    val accent = colorResource(R.color.pc_primary)
    val todayOutline = colorResource(R.color.pc_secondary)
    val reminderDot = colorResource(R.color.pc_accent2)
    val onSurfaceColor = colorResource(R.color.pc_onSurface)
    val onSurfaceSecondaryColor = colorResource(R.color.pc_onSurfaceSecondary)

    val firstDayOfMonth = month.atDay(1)
    val daysInMonth = month.lengthOfMonth()
    val firstDayOfWeek = (firstDayOfMonth.dayOfWeek.value + 6) % 7

    // No-ripple interaction source for the background "empty-area" tap target.
    val noRippleInteraction = remember { MutableInteractionSource() }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(vertical = 8.dp, horizontal = 12.dp)
            .clickable(
                interactionSource = noRippleInteraction,
                indication = null
            ) { onEmptyAreaClick() }
    ) {
        Text(
            text = "${month.month.name.lowercase().replaceFirstChar { it.titlecase() }} ${month.year}",
            fontWeight = FontWeight.Bold,
            color = onSurfaceColor,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach {
                Text(text = it, fontSize = 13.sp, color = onSurfaceSecondaryColor)
            }
        }
        Spacer(Modifier.height(6.dp))

        repeat(6) { weekIndex ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                (1..7).forEach { dayOfWeek ->
                    val dayNumber = (weekIndex * 7 + dayOfWeek) - firstDayOfWeek
                    if (dayNumber in 1..daysInMonth) {
                        val date = month.atDay(dayNumber)
                        val isSelected = date == selectedDate
                        val isToday = date == LocalDate.now()
                        val hasReminder = daysWithReminders.contains(date)
                        val showPopup = popupForDate == date
                        val remindersForDate = remindersByDate[date].orEmpty()

                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) accent.copy(alpha = 0.18f) else Color.Transparent)
                                .then(if (isToday) Modifier.border(2.dp, todayOutline, CircleShape) else Modifier)
                                .clickable { onDayClick(date, hasReminder) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = dayNumber.toString(),
                                    color = onSurfaceColor,
                                    fontSize = 16.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                if (hasReminder) {
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(reminderDot)
                                    )
                                }
                            }

                            // Reminder "cloud" popup, positioned above the day cell.
                            if (showPopup && remindersForDate.isNotEmpty()) {
                                ReminderCloudPopup(
                                    reminders = remindersForDate,
                                    onClick = { onPopupClick(date) },
                                    onDismissRequest = onEmptyAreaClick
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.size(40.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ReminderCloudPopup(
    reminders: List<Reminder>,
    onClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val popupBg = colorResource(R.color.pc_surface)
    val popupBorder = colorResource(R.color.pc_outlineVariant)
    val popupAccent = colorResource(R.color.pc_primary)
    val popupAccentBg = colorResource(R.color.pc_secondaryContainer)
    // A compact floating card with up to ~5 plant thumbnails, rendered above the day cell.
    val maxThumbs = 5
    val shown = reminders.take(maxThumbs)
    val extra = (reminders.size - shown.size).coerceAtLeast(0)

    val thumbSize = 28.dp
    val horizontalPadding = 8.dp
    val verticalPadding = 6.dp
    val gap = 4.dp

    // Positions the popup horizontally centred above the day cell, clamped to the
    // window so it never slides off-screen near the calendar edges. Using a custom
    // PopupPositionProvider is more reliable than Popup(alignment=…, offset=…)
    // when the anchor is small (our day cell is only 40.dp wide).
    val positionProvider = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val gapPx = 8
                val desiredX = anchorBounds.left +
                        (anchorBounds.width - popupContentSize.width) / 2
                val clampedX = desiredX
                    .coerceAtLeast(0)
                    .coerceAtMost((windowSize.width - popupContentSize.width).coerceAtLeast(0))
                // Prefer placing above the cell. If there isn't enough space upward
                // (e.g. for the first row of the month), drop it just below instead
                // so it's never clipped by the top of the window.
                val y = if (anchorBounds.top - popupContentSize.height - gapPx >= 0) {
                    anchorBounds.top - popupContentSize.height - gapPx
                } else {
                    anchorBounds.bottom + gapPx
                }
                return IntOffset(clampedX, y)
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = false)
    ) {
        Row(
            modifier = Modifier
                .shadow(6.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(popupBg)
                .border(1.dp, popupBorder, RoundedCornerShape(16.dp))
                .clickable { onClick() }
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            shown.forEach { reminder ->
                Box(
                    Modifier
                        .size(thumbSize)
                        .clip(CircleShape)
                        .border(1.dp, popupBorder, CircleShape)
                ) {
                    PlantThumbnail(
                        plantId = reminder.plantId,
                        plantName = reminder.plantName,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            if (extra > 0) {
                Box(
                    Modifier
                        .size(thumbSize)
                        .clip(CircleShape)
                        .background(popupAccentBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+$extra",
                        color = popupAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
