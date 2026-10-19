package com.example.plantcare.ui.home

import android.content.Context
import com.example.plantcare.R
import com.example.plantcare.WateringReminder
import com.example.plantcare.feature.streak.ChallengeRegistry
import com.example.plantcare.ui.viewmodel.TodayViewModel
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Builds [HomeUiState] from TodayViewModel's outputs. Pure apart from
 * string lookups, so the Compose screen never touches Room entities.
 */
object HomeStateMapper {

    fun build(
        context: Context,
        groups: List<TodayViewModel.RoomGroup>?,
        hiddenReminderIds: Set<Int>,
        streakDays: Int,
        bestStreak: Int,
        challenges: List<ChallengeRegistry.Challenge>,
        vacationText: String?,
        showProCard: Boolean,
        now: Date = Date()
    ): HomeUiState {
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
        val sections = groups.orEmpty().mapNotNull { group ->
            val tasks = group.reminders
                .filter { it.id !in hiddenReminderIds && isRelevantToday(it, todayIso, now) }
                .sortedWith(TASK_ORDER)
                .map { toTask(it, group.plantIds) }
            if (tasks.isEmpty()) null else HomeRoomSection(group.roomId, group.roomName, tasks)
        }
        return HomeUiState(
            dateLabel = dateLabel(now),
            greeting = greeting(context, now),
            weekDays = weekDays(),
            sections = sections,
            streakDays = streakDays,
            bestStreak = bestStreak,
            challenges = challenges.map {
                HomeChallenge(
                    id = it.id,
                    title = context.getString(it.titleRes),
                    progress = it.displayProgress,
                    target = it.target,
                    complete = it.isComplete
                )
            },
            vacationText = vacationText,
            showProCard = showProCard,
            isLoading = groups == null
        )
    }

    /**
     * Manual reminders carry a description or have no repeat interval —
     * same rule DailyWateringAdapter used to hide the type icon and to
     * offer edit/delete on long-press.
     */
    fun isManual(reminder: WateringReminder): Boolean {
        val repeat = reminder.repeat
        return !reminder.description.isNullOrBlank() ||
            repeat == null || repeat.isEmpty() || repeat == "0"
    }

    /**
     * The DAO query behind the Today list (`date <= today`) also returns
     * every reminder completed on earlier days. Those belong to history,
     * not to today's checklist: keep open ones (due or overdue) and the
     * ones finished today, so the progress ring shows today's work only.
     */
    private fun isRelevantToday(r: WateringReminder, todayIso: String, now: Date): Boolean {
        if (!r.done) return true
        if (r.date == todayIso) return true
        val completed = r.completedDate ?: return false
        return sameDay(completed, now)
    }

    private fun toTask(r: WateringReminder, plantIds: Map<String, Int>): HomeTask {
        val name = r.plantName.orEmpty()
        return HomeTask(
            reminderId = r.id,
            plantId = plantIds[name] ?: r.plantId,
            plantName = name,
            type = HomeTaskType.from(r.type),
            overdueDays = if (r.done) 0 else r.daysOverdue,
            done = r.done,
            isManual = isManual(r),
            note = r.description?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    /** Open first (most overdue first, then by date), done last. */
    private val TASK_ORDER = Comparator<WateringReminder> { a, b ->
        when {
            a.done != b.done -> if (a.done) 1 else -1
            a.daysOverdue != b.daysOverdue -> b.daysOverdue.compareTo(a.daysOverdue)
            else -> a.date.orEmpty().compareTo(b.date.orEmpty())
        }
    }

    /** Monday-to-Sunday of the current week (same rule as the old week strip). */
    private fun weekDays(): List<HomeWeekDay> {
        val today = LocalDate.now()
        val monday = today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
        return (0 until 7).map { i ->
            val d = monday.plusDays(i.toLong())
            HomeWeekDay(
                label = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).removeSuffix("."),
                dayOfMonth = d.dayOfMonth,
                isToday = d == today
            )
        }
    }

    private fun dateLabel(now: Date): String {
        val locale = Locale.getDefault()
        val pattern = if (locale.language == "de") "EEEE, d. MMMM" else "EEEE, MMMM d"
        return SimpleDateFormat(pattern, locale).format(now)
    }

    private fun greeting(context: Context, now: Date): String {
        val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)
        val res = when (hour) {
            in 5..10 -> R.string.today_greeting_morning
            in 11..17 -> R.string.today_greeting_afternoon
            else -> R.string.today_greeting_evening
        }
        return context.getString(res)
    }

    private fun sameDay(a: Date, b: Date): Boolean {
        val ca = Calendar.getInstance().apply { time = a }
        val cb = Calendar.getInstance().apply { time = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }
}
