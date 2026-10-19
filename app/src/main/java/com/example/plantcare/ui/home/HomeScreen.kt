package com.example.plantcare.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.rounded.Yard
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.plantcare.R
import com.example.plantcare.ui.theme.PlantCareColors
import com.example.plantcare.ui.theme.PlantCareRadius
import com.example.plantcare.ui.theme.PlantCareSpacing

/**
 * Home tab — redesigned screen (2026-10).
 *
 * Layout, top to bottom:
 *   greeting + date → search → progress hero (ring, open count, streak) →
 *   week strip → vacation banner → room sections with task cards →
 *   compact Pro card (free users) → challenges.
 *
 * Interaction model (was: tap anywhere on the row toggled "done"):
 *   • round button on the right = mark done / undo (icon = reminder type)
 *   • tap card = open plant details
 *   • long-press card = manage manual reminder (edit / delete)
 *   • "Alle erledigen" in a room header = bulk done (was a hidden long-press)
 *
 * Stateless: everything comes from [state]; actions go to [callbacks].
 * [thumbnail] renders the plant photo (Glide-backed PlantThumbnail in the
 * app, static images in previews / screenshot tests).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    callbacks: HomeCallbacks,
    thumbnail: @Composable (task: HomeTask, modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = PlantCareSpacing.gutter,
            end = PlantCareSpacing.gutter,
            top = PlantCareSpacing.md,
            bottom = PlantCareSpacing.huge
        )
    ) {
        item(key = "header") {
            HomeHeader(greeting = state.greeting, dateLabel = state.dateLabel)
        }
        item(key = "search") {
            SearchPill(onClick = callbacks.onSearch, modifier = Modifier.padding(top = PlantCareSpacing.lg))
        }
        item(key = "hero") {
            ProgressHero(
                state = state,
                modifier = Modifier.padding(top = PlantCareSpacing.lg)
            )
        }
        if (state.weekDays.isNotEmpty()) {
            item(key = "week") {
                WeekStripCard(
                    days = state.weekDays,
                    onOpenCalendar = callbacks.onOpenCalendar,
                    modifier = Modifier.padding(top = PlantCareSpacing.md)
                )
            }
        }
        state.vacationText?.let { text ->
            item(key = "vacation") {
                VacationBanner(text = text, modifier = Modifier.padding(top = PlantCareSpacing.md))
            }
        }
        if (state.totalCount == 0 && !state.isLoading) {
            item(key = "empty") {
                EmptyHome(modifier = Modifier.padding(top = PlantCareSpacing.huge))
            }
        }
        state.sections.forEach { section ->
            item(key = "room-${section.roomId}") {
                RoomSectionHeader(
                    section = section,
                    onWaterAll = { callbacks.onWaterAll(section) },
                    modifier = Modifier
                        .animateItemPlacement()
                        .padding(top = PlantCareSpacing.section, bottom = PlantCareSpacing.sm)
                )
            }
            items(section.tasks, key = { task -> "task-${section.roomId}-${task.reminderId}" }) { task ->
                TaskCard(
                    task = task,
                    callbacks = callbacks,
                    thumbnail = thumbnail,
                    modifier = Modifier
                        .animateItemPlacement()
                        .padding(bottom = PlantCareSpacing.md)
                )
            }
        }
        if (state.showProCard) {
            item(key = "pro") {
                ProCard(onGoPro = callbacks.onGoPro, modifier = Modifier.padding(top = PlantCareSpacing.section - PlantCareSpacing.md + PlantCareSpacing.sm))
            }
        }
        if (state.challenges.isNotEmpty()) {
            item(key = "challenges") {
                ChallengesCard(
                    challenges = state.challenges,
                    modifier = Modifier.padding(top = PlantCareSpacing.section - PlantCareSpacing.md + PlantCareSpacing.sm)
                )
            }
        }
    }
}

// ── Shared helpers ──────────────────────────────────────────────────

/** True when the active scheme is dark (works for previews and the app alike). */
@Composable
@ReadOnlyComposable
private fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Card fill: pure white on the warm light background; in dark mode a tone
 * *above* the background (lowest would sink below it and read inverted).
 */
@Composable
@ReadOnlyComposable
private fun cardColor(): Color =
    if (isDarkScheme()) MaterialTheme.colorScheme.surfaceContainer
    else MaterialTheme.colorScheme.surfaceContainerLowest

// ── Header ──────────────────────────────────────────────────────────

@Composable
private fun HomeHeader(greeting: String, dateLabel: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (dateLabel.isNotEmpty()) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (greeting.isNotEmpty()) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = PlantCareSpacing.xxs)
            )
        }
    }
}

// ── Progress hero ───────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ProgressHero(state: HomeUiState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    // Deep sage card with white text in light mode; in dark mode the
    // light-sage primary would glare, so the container tone is used.
    val isDark = isDarkScheme()
    val container = if (isDark) colors.primaryContainer else colors.primary
    val content = if (isDark) colors.onPrimaryContainer else colors.onPrimary

    val total = state.totalCount
    val done = state.doneCount
    val open = state.openCount
    val headline = when {
        total == 0 -> stringResource(R.string.home_progress_nothing)
        open == 0 -> stringResource(R.string.home_progress_all_done)
        else -> pluralStringResource(R.plurals.home_progress_remaining, open, open)
    }
    val summary = if (total == 0) {
        stringResource(R.string.home_progress_rest)
    } else {
        stringResource(R.string.home_progress_summary, done, total)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlantCareRadius.xl),
        color = container,
        contentColor = content
    ) {
        Box {
            // Oversized leaf watermark in the corner — quiet brand texture.
            Icon(
                imageVector = Icons.Rounded.Spa,
                contentDescription = null,
                tint = content.copy(alpha = 0.07f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 28.dp, y = (-24).dp)
                    .size(150.dp)
            )
            Row(
                modifier = Modifier.padding(PlantCareSpacing.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProgressRing(done = done, total = total, color = content)
                Spacer(Modifier.width(PlantCareSpacing.xl))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleLarge,
                        color = content
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = 0.78f),
                        modifier = Modifier.padding(top = PlantCareSpacing.xxs)
                    )
                    StreakRow(
                        streakDays = state.streakDays,
                        bestStreak = state.bestStreak,
                        content = content,
                        modifier = Modifier.padding(top = PlantCareSpacing.md)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressRing(done: Int, total: Int, color: Color) {
    val target = if (total == 0) 1f else done.toFloat() / total
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 700),
        label = "todayProgress"
    )
    Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(80.dp),
            color = color,
            strokeWidth = 7.dp,
            trackColor = color.copy(alpha = 0.18f),
            strokeCap = StrokeCap.Round
        )
        if (total == 0 || done == total) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(34.dp)
            )
        } else {
            Text(
                text = stringResource(R.string.home_progress_fraction, done, total),
                style = MaterialTheme.typography.titleLarge,
                color = color
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun StreakRow(streakDays: Int, bestStreak: Int, content: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = content.copy(alpha = 0.14f),
            contentColor = content
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = if (streakDays > 0) PlantCareColors.accent2 else content.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(PlantCareSpacing.xs))
                Text(
                    text = if (streakDays > 0) {
                        pluralStringResource(R.plurals.home_streak_days, streakDays, streakDays)
                    } else {
                        stringResource(R.string.home_streak_none)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = content
                )
            }
        }
        if (bestStreak > 0 && streakDays > 0) {
            Text(
                text = stringResource(R.string.home_streak_best, bestStreak),
                style = MaterialTheme.typography.labelMedium,
                color = content.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = PlantCareSpacing.sm)
            )
        }
    }
}

// ── Vacation banner ────────────────────────────────────────────────

@Composable
private fun VacationBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlantCareRadius.lg),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PlantCareSpacing.lg, vertical = PlantCareSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.BeachAccess,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(PlantCareSpacing.md))
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ── Room section ───────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RoomSectionHeader(
    section: HomeRoomSection,
    onWaterAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title + badge take all space left of the button; the title only
        // ellipsizes when the room name genuinely doesn't fit.
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = section.roomName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (section.openCount > 0) {
                Spacer(Modifier.width(PlantCareSpacing.sm))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.home_section_open, section.openCount, section.openCount),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }
        if (section.openCount > 1) {
            TextButton(
                onClick = onWaterAll,
                contentPadding = PaddingValues(horizontal = PlantCareSpacing.md, vertical = PlantCareSpacing.xs)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WaterDrop,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(PlantCareSpacing.xs))
                Text(
                    text = stringResource(R.string.home_water_all),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// ── Task card ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskCard(
    task: HomeTask,
    callbacks: HomeCallbacks,
    thumbnail: @Composable (task: HomeTask, modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(PlantCareRadius.lg)
    val container by animateColorAsState(
        targetValue = if (task.done) colors.surfaceContainerLow else cardColor(),
        animationSpec = tween(durationMillis = 300),
        label = "taskContainer"
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = container,
        shadowElevation = if (task.done) 0.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClick = { callbacks.onOpenPlant(task) },
                    onLongClick = { callbacks.onTaskLongPress(task) }
                )
                .padding(start = PlantCareSpacing.md, end = PlantCareSpacing.sm, top = PlantCareSpacing.md, bottom = PlantCareSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.secondaryContainer)
                    .graphicsLayer { alpha = if (task.done) 0.6f else 1f }
            ) {
                thumbnail(task, Modifier.size(56.dp).clip(CircleShape))
            }
            Spacer(Modifier.width(PlantCareSpacing.md + PlantCareSpacing.xxs))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.plantName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (task.done) colors.onSurfaceVariant else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TaskStatusLine(task = task, modifier = Modifier.padding(top = PlantCareSpacing.xxs))
            }
            Spacer(Modifier.width(PlantCareSpacing.sm))
            WaterCheckButton(
                done = task.done,
                type = task.type,
                plantName = task.plantName,
                onToggle = { callbacks.onToggleDone(task) }
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TaskStatusLine(task: HomeTask, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        when {
            task.done -> {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(PlantCareSpacing.xs))
                Text(
                    text = stringResource(R.string.home_task_done),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary
                )
            }
            task.overdueDays > 0 -> {
                Surface(
                    shape = CircleShape,
                    color = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.home_task_overdue, task.overdueDays, task.overdueDays),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = PlantCareSpacing.sm, vertical = 2.dp)
                    )
                }
                task.note?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = PlantCareSpacing.sm)
                    )
                }
            }
            else -> {
                Icon(
                    imageVector = task.type.icon,
                    contentDescription = null,
                    tint = task.type.tint(),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(PlantCareSpacing.xs))
                Text(
                    text = task.note ?: stringResource(task.type.labelRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Round "water" toggle: outlined drop while open, filled primary with a
 * check once done, with a small spring "pop" and haptic tick on completion.
 */
@Composable
private fun WaterCheckButton(done: Boolean, type: HomeTaskType, plantName: String, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val fill by animateColorAsState(
        targetValue = if (done) colors.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 220),
        label = "checkFill"
    )
    val stroke by animateColorAsState(
        targetValue = if (done) colors.primary else colors.outlineVariant,
        animationSpec = tween(durationMillis = 220),
        label = "checkStroke"
    )
    val scale = remember { Animatable(1f) }
    // Pop only on an actual open → done change, not when an already-done
    // task first appears on screen.
    var lastDone by remember { mutableStateOf(done) }
    LaunchedEffect(done) {
        if (done && !lastDone) {
            scale.snapTo(0.82f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
        lastDone = done
    }
    val label = if (done) {
        stringResource(R.string.home_cd_mark_undone, plantName)
    } else {
        stringResource(R.string.home_cd_mark_done, plantName)
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .toggleable(
                value = done,
                role = Role.Checkbox,
                onValueChange = {
                    if (!done) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                }
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .background(fill, CircleShape)
                .border(2.dp, stroke, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = done,
                transitionSpec = { (fadeIn(tween(160)) + scaleIn(initialScale = 0.6f)) togetherWith fadeOut(tween(90)) },
                label = "checkIcon"
            ) { isDone ->
                if (isDone) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(
                        imageVector = type.icon,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── Empty state ────────────────────────────────────────────────────

@Composable
private fun EmptyHome(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.empty_plant_pot_3d),
                contentDescription = null,
                modifier = Modifier.size(92.dp)
            )
        }
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = PlantCareSpacing.xl)
        )
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = PlantCareSpacing.xs)
                .widthIn(max = 280.dp)
        )
    }
}

// ── Challenges ─────────────────────────────────────────────────────

@Composable
private fun ChallengesCard(challenges: List<HomeChallenge>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_challenges_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = PlantCareSpacing.sm)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(PlantCareRadius.lg),
            color = cardColor(),
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(PlantCareSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(PlantCareSpacing.lg)
            ) {
                challenges.forEach { challenge -> ChallengeRow(challenge) }
            }
        }
    }
}

@Composable
private fun ChallengeRow(challenge: HomeChallenge) {
    val colors = MaterialTheme.colorScheme
    val progress by animateFloatAsState(
        targetValue = challenge.fraction,
        animationSpec = tween(durationMillis = 700),
        label = "challengeProgress"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (challenge.complete) colors.primaryContainer else colors.surfaceContainerHigh,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (challenge.complete) Icons.Rounded.Check else Icons.Rounded.EmojiEvents,
                contentDescription = null,
                tint = if (challenge.complete) colors.onPrimaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(PlantCareSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = challenge.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.challenge_progress_format, challenge.progress.coerceAtMost(challenge.target), challenge.target),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = PlantCareSpacing.sm)
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .padding(top = PlantCareSpacing.sm)
                    .fillMaxWidth()
                    .height(6.dp),
                color = colors.primary,
                trackColor = colors.surfaceContainerHighest,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

// ── Reminder type visuals ──────────────────────────────────────────

private val HomeTaskType.icon: ImageVector
    get() = when (this) {
        HomeTaskType.WATER -> Icons.Rounded.WaterDrop
        HomeTaskType.FERTILIZE -> Icons.Rounded.Eco
        HomeTaskType.MIST -> Icons.Rounded.Grain
        HomeTaskType.REPOT -> Icons.Rounded.Yard
    }

private val HomeTaskType.labelRes: Int
    get() = when (this) {
        HomeTaskType.WATER -> R.string.home_task_water
        HomeTaskType.FERTILIZE -> R.string.home_task_fertilize
        HomeTaskType.MIST -> R.string.home_task_mist
        HomeTaskType.REPOT -> R.string.home_task_repot
    }

/** Same per-type colours as ReminderTypeUi (reminder_type_* in colors.xml). */
@Composable
@ReadOnlyComposable
private fun HomeTaskType.tint(): Color = when (this) {
    HomeTaskType.WATER -> colorResource(R.color.reminder_type_water)
    HomeTaskType.FERTILIZE -> colorResource(R.color.reminder_type_fertilize)
    HomeTaskType.MIST -> colorResource(R.color.reminder_type_mist)
    HomeTaskType.REPOT -> colorResource(R.color.reminder_type_repot)
}

// ── Search ─────────────────────────────────────────────────────────

@Composable
private fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = CircleShape,
        color = cardColor(),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = PlantCareSpacing.lg, vertical = PlantCareSpacing.md + 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(PlantCareSpacing.md))
            Text(
                text = stringResource(R.string.search_plant_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant
            )
        }
    }
}

// ── Week strip ─────────────────────────────────────────────────────

@Composable
private fun WeekStripCard(days: List<HomeWeekDay>, onOpenCalendar: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlantCareRadius.lg),
        color = cardColor(),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(start = PlantCareSpacing.lg, end = PlantCareSpacing.xs, top = PlantCareSpacing.xs, bottom = PlantCareSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_week_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onOpenCalendar,
                    contentPadding = PaddingValues(horizontal = PlantCareSpacing.md, vertical = PlantCareSpacing.xs)
                ) {
                    Text(text = stringResource(R.string.home_week_calendar), style = MaterialTheme.typography.labelLarge)
                    Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(end = PlantCareSpacing.md)) {
                days.forEach { day ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = day.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (day.isToday) colors.primary else colors.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = PlantCareSpacing.xs)
                                .size(36.dp)
                                .background(if (day.isToday) colors.primary else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (day.isToday) colors.onPrimary else colors.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Pro upsell (free users) ───────────────────────────────────────

@Composable
private fun ProCard(onGoPro: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlantCareRadius.lg),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onGoPro)
                .padding(PlantCareSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.WorkspacePremium,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(PlantCareSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.home_pro_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.home_pro_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            Spacer(Modifier.width(PlantCareSpacing.sm))
            Surface(shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary) {
                Text(
                    text = stringResource(R.string.hero_go_pro),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = PlantCareSpacing.lg, vertical = PlantCareSpacing.sm)
                )
            }
        }
    }
}
