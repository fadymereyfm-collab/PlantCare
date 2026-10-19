package com.example.plantcare.ui.home

import androidx.compose.runtime.Immutable

/**
 * UI state for the redesigned Home tab ([HomeScreen]).
 *
 * Plain values only (no Room entities, no Context) so the screen is a pure
 * function of this state: previewable, screenshot-testable, and renderable
 * without a database. [HomeStateMapper] builds it from TodayViewModel.
 */
@Immutable
data class HomeUiState(
    /** e.g. "Montag, 19. Oktober" — already localized. */
    val dateLabel: String = "",
    /** e.g. "Guten Morgen" — already localized. */
    val greeting: String = "",
    val weekDays: List<HomeWeekDay> = emptyList(),
    val sections: List<HomeRoomSection> = emptyList(),
    val streakDays: Int = 0,
    val bestStreak: Int = 0,
    val challenges: List<HomeChallenge> = emptyList(),
    /** Non-null while vacation mode is active. */
    val vacationText: String? = null,
    /** Free users see the compact Pro card. */
    val showProCard: Boolean = false,
    /** True until the first load finished — avoids flashing the empty state. */
    val isLoading: Boolean = false
) {
    val totalCount: Int get() = sections.sumOf { it.tasks.size }
    val doneCount: Int get() = sections.sumOf { section -> section.tasks.count { it.done } }
    val openCount: Int get() = totalCount - doneCount
}

@Immutable
data class HomeWeekDay(
    /** Short weekday, e.g. "Mo". */
    val label: String,
    val dayOfMonth: Int,
    val isToday: Boolean
)

@Immutable
data class HomeRoomSection(
    val roomId: Int,
    val roomName: String,
    /** Open tasks first (most overdue first), done tasks last. */
    val tasks: List<HomeTask>
) {
    val openCount: Int get() = tasks.count { !it.done }
}

/** Reminder kinds (WateringReminder.type, v16). NULL/unknown → [WATER]. */
enum class HomeTaskType { WATER, FERTILIZE, MIST, REPOT;

    companion object {
        fun from(raw: String?): HomeTaskType = when (raw?.lowercase()) {
            "fertilize" -> FERTILIZE
            "mist" -> MIST
            "repot" -> REPOT
            else -> WATER
        }
    }
}

@Immutable
data class HomeTask(
    val reminderId: Int,
    val plantId: Int,
    val plantName: String,
    val type: HomeTaskType,
    /** Days past the due date; 0 when due today or already done. */
    val overdueDays: Int,
    val done: Boolean,
    /** Manual reminder (user-created) vs. auto reminder. */
    val isManual: Boolean,
    /** Free-text description of a manual reminder, e.g. "Blätter abwischen". */
    val note: String? = null
)

@Immutable
data class HomeChallenge(
    val id: String,
    val title: String,
    val progress: Int,
    val target: Int,
    val complete: Boolean
) {
    val fraction: Float get() = if (target <= 0) 0f else (progress.toFloat() / target).coerceIn(0f, 1f)
}

/** User actions raised by [HomeScreen]; wired to the existing logic by [HomeScreenController]. */
class HomeCallbacks(
    val onToggleDone: (HomeTask) -> Unit = {},
    val onOpenPlant: (HomeTask) -> Unit = {},
    val onTaskLongPress: (HomeTask) -> Unit = {},
    val onWaterAll: (HomeRoomSection) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onOpenCalendar: () -> Unit = {},
    val onGoPro: () -> Unit = {}
)
