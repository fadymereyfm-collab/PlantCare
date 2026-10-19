package com.example.plantcare.ui.home

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import com.example.plantcare.Analytics
import com.example.plantcare.CrashReporter
import com.example.plantcare.DataChangeNotifier
import com.example.plantcare.EditManualReminderDialogFragment
import com.example.plantcare.EmailContext
import com.example.plantcare.FirebaseSyncManager
import com.example.plantcare.Plant
import com.example.plantcare.PlantDetailDialogFragment
import com.example.plantcare.R
import com.example.plantcare.ReminderUtils
import com.example.plantcare.WateringReminder
import com.example.plantcare.billing.PaywallDialogFragment
import com.example.plantcare.billing.ProStatusManager
import com.example.plantcare.data.repository.PlantRepository
import com.example.plantcare.data.repository.ReminderRepository
import com.example.plantcare.feature.streak.ChallengeRegistry
import com.example.plantcare.feature.streak.StreakBridge
import com.example.plantcare.feature.vacation.VacationPrefs
import com.example.plantcare.ui.calendar.FullCalendarBottomSheet
import com.example.plantcare.ui.catalog.CatalogActivity
import com.example.plantcare.ui.theme.PlantCareTheme
import com.example.plantcare.ui.util.ActionListDialogFragment
import com.example.plantcare.ui.viewmodel.TodayViewModel
import com.example.plantcare.util.BgExecutor
import com.example.plantcare.weekbar.PlantImageLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import java.util.Date

/**
 * Hosts the Compose [HomeScreen] inside the (Java) TodayFragment and carries
 * the actions that used to live in DailyWateringAdapter + TodayFragment:
 * mark done / undo (vacation-guarded), overdue → reschedule dialog,
 * "Alle erledigen", manual reminder edit/delete (ActionListDialogFragment),
 * the read-only plant detail popup with the task highlight, catalog search,
 * the full calendar sheet and the paywall.
 *
 * The fragment forwards TodayViewModel's LiveData into the setters below;
 * every setter rebuilds the immutable [HomeUiState]. Toggles update the
 * in-memory reminder first (instant UI feedback), then persist on
 * [BgExecutor] and fire [DataChangeNotifier] so the ViewModel reloads.
 */
class HomeScreenController(private val fragment: Fragment) {

    private val context: Context get() = fragment.requireContext()

    private var groups: List<TodayViewModel.RoomGroup>? = null
    private var streakDays = 0
    private var bestStreak = 0
    private var challenges: List<ChallengeRegistry.Challenge> = emptyList()
    private var vacationText: String? = null

    /** Reminders deleted in this session, hidden until the next reload confirms it. */
    private val hiddenReminderIds = HashSet<Int>()

    /** Same purpose as ClickGuard on the old Views: swallow accidental double taps. */
    private var lastActionAt = 0L

    private var uiState by mutableStateOf(HomeUiState(isLoading = true))

    private val callbacks = HomeCallbacks(
        onToggleDone = { task -> guarded { onToggleDone(task) } },
        onOpenPlant = { task -> guarded { findReminder(task)?.let(::openPlantDetails) } },
        onTaskLongPress = { task -> guarded { onTaskLongPress(task) } },
        onWaterAll = { section -> guarded { confirmWaterAll(section) } },
        onSearch = { guarded { openSearch() } },
        onOpenCalendar = { guarded { openCalendar() } },
        onGoPro = { guarded { openPaywall() } }
    )

    fun createView(context: Context): View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            PlantCareTheme {
                HomeScreen(
                    state = uiState,
                    callbacks = callbacks,
                    thumbnail = { task, modifier -> HomePlantPhoto(task, modifier) }
                )
            }
        }
    }

    // ── Inputs from TodayFragment (LiveData observers) ─────────────

    fun setRoomGroups(newGroups: List<TodayViewModel.RoomGroup>?) {
        groups = newGroups.orEmpty()
        hiddenReminderIds.clear()
        rebuild()
    }

    fun setStreak(current: Int, best: Int) {
        streakDays = current
        bestStreak = best
        rebuild()
    }

    fun setChallenges(list: List<ChallengeRegistry.Challenge>?) {
        challenges = list.orEmpty()
        rebuild()
    }

    fun setVacationText(text: String?) {
        vacationText = text
        rebuild()
    }

    /** Re-reads Pro status (called from onResume, e.g. after a purchase). */
    fun refreshProStatus() = rebuild()

    private fun rebuild() {
        if (!fragment.isAdded) return
        uiState = HomeStateMapper.build(
            context = context,
            groups = groups,
            hiddenReminderIds = hiddenReminderIds,
            streakDays = streakDays,
            bestStreak = bestStreak,
            challenges = challenges,
            vacationText = vacationText,
            showProCard = !ProStatusManager.isPro(context)
        )
    }

    private inline fun guarded(action: () -> Unit) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastActionAt < DOUBLE_TAP_MS || !fragment.isAdded) return
        lastActionAt = now
        action()
    }

    private fun userEmail(): String? = EmailContext.current(context)

    private fun findReminder(task: HomeTask): WateringReminder? =
        groups.orEmpty().asSequence().flatMap { it.reminders.asSequence() }.firstOrNull { it.id == task.reminderId }

    private fun canShowUi(): Boolean = fragment.isAdded && !fragment.parentFragmentManager.isStateSaved

    /**
     * Bug #8 (2026-05-14): during an active vacation, marking tasks done is
     * suppressed so habitual taps don't burn the streak. Fails open.
     */
    private fun blockedByVacation(): Boolean {
        val email = userEmail()
        if (email.isNullOrEmpty()) return false
        val onVacation = try {
            VacationPrefs.isVacationActive(context, email, LocalDate.now())
        } catch (t: Throwable) {
            CrashReporter.log(t)
            false
        }
        if (onVacation) Toast.makeText(context, R.string.vacation_tap_suppressed, Toast.LENGTH_SHORT).show()
        return onVacation
    }

    // ── Mark done / undo ───────────────────────────────────────────

    private fun onToggleDone(task: HomeTask) {
        val reminder = findReminder(task) ?: return
        if (reminder.done) {
            // Undo is always allowed, also during vacation.
            reminder.done = false
            reminder.completedDate = null
            rebuild()
            persist(reminder)
            return
        }
        if (blockedByVacation()) return
        val overdue = reminder.daysOverdue
        if (overdue > 0) {
            showRescheduleDialog(reminder, overdue)
        } else {
            markDone(reminder)
        }
    }

    private fun markDone(reminder: WateringReminder) {
        val ctx = context
        val email = userEmail()
        reminder.done = true
        reminder.completedDate = Date()
        reminder.wateredBy = email
        rebuild()
        persist(reminder)
        StreakBridge.onReminderMarkedDone(ctx, email)
        Analytics.logReminderCompleted(ctx)
    }

    private fun persist(reminder: WateringReminder) {
        val appContext = context.applicationContext
        BgExecutor.io {
            ReminderRepository.getInstance(appContext).updateBlocking(reminder)
            fragment.activity?.runOnUiThread { DataChangeNotifier.notifyChange() }
        }
    }

    /** Same custom dialog (dialog_reschedule.xml) and semantics as before the redesign. */
    private fun showRescheduleDialog(reminder: WateringReminder, overdue: Int) {
        if (!canShowUi()) return
        val ctx = context
        val content = LayoutInflater.from(ctx).inflate(R.layout.dialog_reschedule, null, false)
        content.findViewById<TextView>(R.id.title)?.text = ctx.getString(R.string.dialog_reschedule_title)
        content.findViewById<TextView>(R.id.message)?.text =
            ctx.getString(R.string.dialog_reschedule_message, overdue)

        val dialog = MaterialAlertDialogBuilder(ctx).setView(content).create()
        content.findViewById<Button>(R.id.btnYes)?.setOnClickListener {
            it.isEnabled = false
            val appContext = ctx.applicationContext
            BgExecutor.io {
                ReminderUtils.rescheduleFromToday(reminder, appContext)
                fragment.activity?.runOnUiThread {
                    dialog.dismiss()
                    DataChangeNotifier.notifyChange()
                }
            }
        }
        content.findViewById<Button>(R.id.btnNo)?.setOnClickListener {
            it.isEnabled = false
            markDone(reminder)
            dialog.dismiss()
        }
        content.findViewById<Button>(R.id.btnClose)?.setOnClickListener { dialog.dismiss() }
        dialog.show()
        // Transparent window so only the rounded card of the layout is visible.
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    // ── Bulk done (room header button) ─────────────────────────────

    private fun confirmWaterAll(section: HomeRoomSection) {
        if (!canShowUi() || blockedByVacation()) return
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_water_all_title)
            .setMessage(context.getString(R.string.dialog_water_all_message, section.roomName))
            .setPositiveButton(R.string.action_yes) { _, _ -> waterAll(section) }
            .setNegativeButton(R.string.action_no, null)
            .show()
    }

    private fun waterAll(section: HomeRoomSection) {
        if (!fragment.isAdded) return
        val appContext = context.applicationContext
        val email = userEmail()
        val ids = section.tasks.filter { !it.done }.map { it.reminderId }.toSet()
        val toUpdate = groups.orEmpty().flatMap { it.reminders }.filter { it.id in ids && !it.done }
        if (toUpdate.isEmpty()) return
        val now = Date()
        toUpdate.forEach {
            it.done = true
            it.completedDate = now
            it.wateredBy = email
        }
        rebuild()
        BgExecutor.io {
            val repo = ReminderRepository.getInstance(appContext)
            toUpdate.forEach { repo.updateBlocking(it) }
            // Streak/Challenge update is idempotent per day — one call is enough.
            StreakBridge.onReminderMarkedDone(appContext, email)
            fragment.activity?.runOnUiThread { DataChangeNotifier.notifyChange() }
        }
    }

    // ── Long-press: manual reminder actions / plant details ────────

    private fun onTaskLongPress(task: HomeTask) {
        val reminder = findReminder(task) ?: return
        // Auto reminders are regenerated by ReminderTopUpWorker from the
        // plant's interval, so edit/delete is offered for manual ones only.
        if (HomeStateMapper.isManual(reminder)) showManualReminderActions(reminder) else openPlantDetails(reminder)
    }

    private fun showManualReminderActions(reminder: WateringReminder) {
        if (!canShowUi()) return
        val fm = fragment.parentFragmentManager
        val items = listOf(
            ActionListDialogFragment.Item(context.getString(R.string.reminder_action_edit), false) {
                EditManualReminderDialogFragment.newInstance(reminder).show(fm, "edit_manual_reminder")
            },
            ActionListDialogFragment.Item(context.getString(R.string.reminder_action_delete), true) {
                deleteReminder(reminder)
            }
        )
        ActionListDialogFragment()
            .configure(context.getString(R.string.reminder_manage_title), items)
            .show(fm, ActionListDialogFragment.TAG)
    }

    private fun deleteReminder(reminder: WateringReminder) {
        if (!fragment.isAdded) return
        val appContext = context.applicationContext
        hiddenReminderIds.add(reminder.id)
        rebuild()
        BgExecutor.io {
            ReminderRepository.getInstance(appContext).deleteBlocking(reminder)
            try {
                FirebaseSyncManager.get().deleteReminder(reminder)
            } catch (t: Throwable) {
                CrashReporter.log(t)
            }
            fragment.activity?.runOnUiThread { DataChangeNotifier.notifyChange() }
        }
    }

    // ── Plant details ──────────────────────────────────────────────

    private fun openPlantDetails(reminder: WateringReminder) {
        val name = reminder.plantName ?: return
        val appContext = context.applicationContext
        val email = userEmail()
        BgExecutor.io {
            val plant = resolvePlant(appContext, name, email) ?: return@io
            fragment.activity?.runOnUiThread {
                if (!canShowUi()) return@runOnUiThread
                val dialog = PlantDetailDialogFragment.newInstance(plant, true)
                dialog.setReadOnlyMode(true)
                // Bold task highlight at the top, same as the calendar tab.
                dialog.setHighlightTaskType(reminder.type)
                dialog.show(fragment.parentFragmentManager, "plant_detail_popup")
            }
        }
    }

    /** Same fallback chain as before: user copy by name → by nickname → any match. */
    private fun resolvePlant(appContext: Context, plantName: String, email: String?): Plant? {
        val repo = PlantRepository.getInstance(appContext)
        val userCopies = repo.getAllUserPlantsWithNameAndUserBlocking(plantName, email)
        var plant: Plant? = if (userCopies.isNotEmpty()) repo.findByIdBlocking(userCopies[0].id) else null
        if (plant == null) plant = repo.findByNicknameBlocking(plantName)
        if (plant == null) plant = repo.findByNameBlocking(plantName)
        return plant
    }

    // ── Search / calendar / Pro ────────────────────────────────────

    private fun openSearch() {
        fragment.startActivity(Intent(context, CatalogActivity::class.java))
    }

    private fun openCalendar() {
        if (!canShowUi()) return
        try {
            FullCalendarBottomSheet().show(fragment.parentFragmentManager, "full_calendar")
        } catch (t: Throwable) {
            CrashReporter.log(t)
        }
    }

    private fun openPaywall() {
        if (!canShowUi()) return
        try {
            PaywallDialogFragment.newInstance("home_pro_card").show(fragment.parentFragmentManager, "paywall")
        } catch (t: Throwable) {
            CrashReporter.log(t)
        }
    }

    private companion object {
        const val DOUBLE_TAP_MS = 600L
    }
}

/**
 * Plant photo via the app's Glide pipeline ([PlantImageLoader], which
 * circle-crops and picks the deterministic placeholder). The tag guard
 * stops a reload — and its placeholder flash — when a row merely
 * recomposes, e.g. after being marked done.
 */
@Composable
private fun HomePlantPhoto(task: HomeTask, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        },
        update = { imageView ->
            val key = "${task.plantId}|${task.plantName}"
            if (imageView.tag != key) {
                imageView.tag = key
                PlantImageLoader.loadInto(
                    imageView.context,
                    imageView,
                    task.plantId.toLong(),
                    task.plantName,
                    EmailContext.current(imageView.context)
                )
            }
        }
    )
}
