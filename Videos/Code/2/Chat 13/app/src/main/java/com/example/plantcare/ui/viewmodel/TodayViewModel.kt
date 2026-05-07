package com.example.plantcare.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.plantcare.Plant
import com.example.plantcare.WateringReminder
import com.example.plantcare.data.repository.PlantRepository
import com.example.plantcare.data.repository.ReminderRepository
import com.example.plantcare.data.repository.RoomCategoryRepository
import com.example.plantcare.feature.streak.ChallengeRegistry
import com.example.plantcare.feature.streak.StreakTracker
import com.example.plantcare.feature.vacation.VacationPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*

/**
 * ViewModel for TodayFragment — uses Plant/Reminder/RoomCategory repositories,
 * never AppDatabase directly.
 */
class TodayViewModel(application: Application) : AndroidViewModel(application) {

    private val plantRepo = PlantRepository.getInstance(application)
    private val reminderRepo = ReminderRepository.getInstance(application)
    private val roomRepo = RoomCategoryRepository.getInstance(application)

    private val _todayTasksGroupedByRoom = MutableLiveData<List<RoomGroup>>()
    val todayTasksGroupedByRoom: LiveData<List<RoomGroup>> = _todayTasksGroupedByRoom

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _streakState = MutableLiveData(Pair(0, 0))
    val streakState: LiveData<Pair<Int, Int>> = _streakState

    private val _challenges = MutableLiveData<List<ChallengeRegistry.Challenge>>(emptyList())
    val challenges: LiveData<List<ChallengeRegistry.Challenge>> = _challenges

    private val _vacationBannerText = MutableLiveData<String?>(null)
    val vacationBannerText: LiveData<String?> = _vacationBannerText

    private val _justCompletedChallenge = MutableLiveData<ChallengeRegistry.Challenge?>(null)
    val justCompletedChallenge: LiveData<ChallengeRegistry.Challenge?> = _justCompletedChallenge

    fun consumeCompletedChallenge() {
        _justCompletedChallenge.value = null
    }

    fun loadTodayTasks(email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val groups = buildRoomGroups(email)
                _todayTasksGroupedByRoom.value = groups
            } catch (e: Exception) {
                com.example.plantcare.CrashReporter.log(e)
                _todayTasksGroupedByRoom.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun buildRoomGroups(email: String): List<RoomGroup> {
        // Locale.US for the wire format — Locale.getDefault() emits
        // Eastern-Arabic digits on ar/fa devices and the SQL date
        // comparisons never match the Latin-digit rows we write
        // elsewhere. Same root cause as the worker-layer A2 fix.
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val reminders = reminderRepo.getTodayAllRemindersList(today, email)
        if (reminders.isEmpty()) return emptyList()

        val rooms = roomRepo.getRoomsListForUser(email)
        val roomNameMap = rooms.associate { it.id to it.name }

        val plantRoomMap = mutableMapOf<String, Int>()
        for (r in reminders) {
            val name = r.plantName ?: continue
            if (name in plantRoomMap) continue

            var plant: Plant? = plantRepo.findUserPlantsByName(name, email).firstOrNull()
            if (plant == null) plant = plantRepo.findUserPlantsByNickname(name, email).firstOrNull()
            if (plant == null) plant = plantRepo.findAnyByNickname(name)
            if (plant == null) plant = plantRepo.findAnyByName(name)

            plantRoomMap[name] = plant?.roomId ?: 0
        }

        val grouped = LinkedHashMap<Int, MutableList<WateringReminder>>()
        for (room in rooms) grouped[room.id] = mutableListOf()
        grouped[0] = mutableListOf()

        for (r in reminders) {
            val roomId = plantRoomMap.getOrDefault(r.plantName, 0)
            grouped.getOrPut(roomId) { mutableListOf() }.add(r)
        }

        return grouped.entries
            .filter { it.value.isNotEmpty() }
            .map { (roomId, list) ->
                val roomName = if (roomId == 0) "Sonstige"
                               else roomNameMap.getOrDefault(roomId, "Sonstige")
                RoomGroup(roomId, roomName, list)
            }
    }

    fun markReminderDone(reminder: WateringReminder, email: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                reminder.done = true
                reminder.completedDate = Date()
                reminder.wateredBy = email
                reminderRepo.updateReminder(reminder)
            }
            val ctx = getApplication<Application>()
            val today = LocalDate.now()
            val vacationGap: (LocalDate, LocalDate) -> Boolean = { lastDay, now ->
                val vacStart = VacationPrefs.getStart(ctx, email)
                val vacEnd = VacationPrefs.getEnd(ctx, email)
                if (vacStart != null && vacEnd != null) {
                    !vacEnd.isBefore(lastDay.plusDays(1)) && !vacStart.isAfter(now.minusDays(1))
                } else false
            }
            val newStreak = StreakTracker.recordWateringToday(ctx, email, today, vacationGap)
            val best = StreakTracker.getBestStreak(ctx, email)
            _streakState.postValue(Pair(newStreak, best))

            val completed = ChallengeRegistry.updateProgress(
                ctx, email, "WATER_STREAK_7", newStreak
            )
            if (completed != null) _justCompletedChallenge.postValue(completed)
            _challenges.postValue(ChallengeRegistry.allFor(ctx, email))

            loadTodayTasks(email)
        }
    }

    fun refreshHeader(email: String) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val today = LocalDate.now()
            val vacationGap: (LocalDate, LocalDate) -> Boolean = { lastDay, now ->
                val vacStart = VacationPrefs.getStart(ctx, email)
                val vacEnd = VacationPrefs.getEnd(ctx, email)
                if (vacStart != null && vacEnd != null) {
                    !vacEnd.isBefore(lastDay.plusDays(1)) && !vacStart.isAfter(now.minusDays(1))
                } else false
            }
            val current = StreakTracker.getCurrentStreak(ctx, email, today, vacationGap)
            val best = StreakTracker.getBestStreak(ctx, email)
            _streakState.postValue(Pair(current, best))

            // C1: ADD_FIVE_PLANTS was wired up in the ViewModel but no
            // caller ever invoked recordPlantCountForChallenge — the
            // challenge sat permanently at 0/5 even when the user had
            // dozens of plants. Detect every refresh by re-counting the
            // user's plants on a background thread and feeding the
            // current value into the registry. Idempotent — the
            // registry no-ops if progress hasn't changed.
            //
            // C2 + C4 + C16: same dead-feature problem with
            // MONTHLY_PHOTO. We now check whether the user has any
            // photo on or after the 1st of the current calendar
            // month (the month boundary itself is enforced by the
            // monthly-reset logic in ChallengeRegistry.load) and
            // mark the challenge done if so.
            withContext(Dispatchers.IO) {
                try {
                    val plantCount = plantRepo.countUserPlantsBlocking(email)
                    val plantCompleted = ChallengeRegistry.updateProgress(
                        ctx, email, "ADD_FIVE_PLANTS", plantCount
                    )
                    if (plantCompleted != null) _justCompletedChallenge.postValue(plantCompleted)

                    val monthStartIso = today.withDayOfMonth(1).toString()  // yyyy-MM-dd
                    val photoCount = com.example.plantcare.data.repository.PlantPhotoRepository
                        .getInstance(ctx)
                        .countPhotosForUserSinceBlocking(email, monthStartIso)
                    if (photoCount > 0) {
                        val photoCompleted = ChallengeRegistry.markMonthlyPhotoDone(ctx, email)
                        if (photoCompleted != null) _justCompletedChallenge.postValue(photoCompleted)
                    }
                } catch (e: Exception) {
                    com.example.plantcare.CrashReporter.log(e)
                }
            }
            _challenges.postValue(ChallengeRegistry.allFor(ctx, email))

            val end = VacationPrefs.getEnd(ctx, email)
            _vacationBannerText.postValue(
                if (VacationPrefs.isVacationActive(ctx, email, today) && end != null) {
                    ctx.getString(
                        com.example.plantcare.R.string.vacation_banner_format,
                        end.toString()
                    )
                } else null
            )
        }
    }

    fun recordPlantCountForChallenge(email: String, count: Int) {
        val ctx = getApplication<Application>()
        val completed = ChallengeRegistry.updateProgress(ctx, email, "ADD_FIVE_PLANTS", count)
        if (completed != null) _justCompletedChallenge.postValue(completed)
        _challenges.postValue(ChallengeRegistry.allFor(ctx, email))
    }

    data class RoomGroup(
        val roomId: Int,
        val roomName: String,
        val reminders: List<WateringReminder>
    )
}
