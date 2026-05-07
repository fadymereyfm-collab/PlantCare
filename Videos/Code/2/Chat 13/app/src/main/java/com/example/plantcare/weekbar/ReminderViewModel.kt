package com.example.plantcare.weekbar

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.plantcare.EmailContext
import com.example.plantcare.WateringReminder
import com.example.plantcare.data.repository.PlantPhotoRepository
import com.example.plantcare.data.repository.PlantRepository
import com.example.plantcare.data.repository.ReminderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.*

/**
 * Data class for weather info displayed on the main screen.
 */
data class WeatherTip(
    val emoji: String = "",
    val tip: String = "",
    val temp: Float = 0f,
    val city: String = "",
    val description: String = "",
    val factor: Float = 1f
)

class ReminderViewModel(application: Application) : AndroidViewModel(application) {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // Weather tip from SharedPreferences (cached by WeatherAdjustmentWorker)
    private val _weatherTip = MutableStateFlow(WeatherTip())
    val weatherTip: StateFlow<WeatherTip> = _weatherTip.asStateFlow()

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    // NEW: photos for the selected day
    private val _photosForSelectedDate = MutableStateFlow<List<CalendarPhotoItem>>(emptyList())
    val photosForSelectedDate: StateFlow<List<CalendarPhotoItem>> = _photosForSelectedDate.asStateFlow()

    private val _daysWithRemindersThisWeek = MutableStateFlow<List<LocalDate>>(emptyList())
    val daysWithRemindersThisWeek: StateFlow<List<LocalDate>> = _daysWithRemindersThisWeek

    private val _daysWithRemindersThisMonth = MutableStateFlow<List<LocalDate>>(emptyList())
    val daysWithRemindersThisMonth: StateFlow<List<LocalDate>> = _daysWithRemindersThisMonth

    // Map of date -> list of Reminders for the currently displayed month
    // Used by MonthPicker to show the reminder "cloud" popup above days with reminders.
    private val _remindersByDateThisMonth = MutableStateFlow<Map<LocalDate, List<Reminder>>>(emptyMap())
    val remindersByDateThisMonth: StateFlow<Map<LocalDate, List<Reminder>>> = _remindersByDateThisMonth

    val displayedMonthState = mutableStateOf(YearMonth.from(_selectedDate.value))

    init {
        loadRemindersForDate(_selectedDate.value)
        onDisplayedMonthChanged(displayedMonthState.value)
        loadWeatherTip()
    }

    /**
     * Loads cached weather tip from SharedPreferences (written by WeatherAdjustmentWorker).
     */
    fun loadWeatherTip() {
        val context = getApplication<Application>().applicationContext
        val prefs = context.getSharedPreferences("weather_prefs", 0)
        val tip = prefs.getString("last_weather_tip", null)
        if (tip != null) {
            _weatherTip.value = WeatherTip(
                emoji = prefs.getString("last_weather_emoji", "") ?: "",
                tip = tip,
                temp = prefs.getFloat("last_temp", 0f),
                city = prefs.getString("last_city", "") ?: "",
                description = prefs.getString("last_weather_description", "") ?: "",
                factor = prefs.getFloat("last_adjustment_factor", 1f)
            )
        }
    }

    fun getCurrentWeekDays(date: LocalDate = _selectedDate.value): List<LocalDate> {
        val weekFields = WeekFields.of(Locale.GERMAN)
        val firstDayOfWeek = date.with(weekFields.dayOfWeek(), 1)
        return (0..6).map { firstDayOfWeek.plusDays(it.toLong()) }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        displayedMonthState.value = YearMonth.from(date)
        loadRemindersForDate(date)
        loadWeatherTip()
    }

    fun previousWeek() {
        val newDate = _selectedDate.value.minusWeeks(1)
        _selectedDate.value = newDate
        displayedMonthState.value = YearMonth.from(newDate)
        loadRemindersForDate(newDate)
    }

    fun nextWeek() {
        val newDate = _selectedDate.value.plusWeeks(1)
        _selectedDate.value = newDate
        displayedMonthState.value = YearMonth.from(newDate)
        loadRemindersForDate(newDate)
    }

    fun goToDate(date: LocalDate) {
        _selectedDate.value = date
        displayedMonthState.value = YearMonth.from(date)
        loadRemindersForDate(date)
    }

    fun goToToday() {
        _selectedDate.value = LocalDate.now()
        displayedMonthState.value = YearMonth.from(LocalDate.now())
        loadRemindersForDate(LocalDate.now())
    }

    fun setDisplayedMonthToToday() {
        val ym = YearMonth.from(LocalDate.now())
        displayedMonthState.value = ym
        onDisplayedMonthChanged(ym)
    }

    private val reminderRepo by lazy {
        ReminderRepository.getInstance(getApplication<Application>().applicationContext)
    }
    private val plantRepo by lazy {
        PlantRepository.getInstance(getApplication<Application>().applicationContext)
    }
    private val photoRepo by lazy {
        PlantPhotoRepository.getInstance(getApplication<Application>().applicationContext)
    }

    fun onDisplayedMonthChanged(month: YearMonth) {
        displayedMonthState.value = month
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val userEmail = EmailContext.current(context)

            val firstDay = month.atDay(1)
            val lastDay = month.atEndOfMonth()

            val remindersMap: Map<LocalDate, List<Reminder>> = if (userEmail != null) {
                withContext(Dispatchers.IO) {
                    reminderRepo.getAllRemindersForUserList(userEmail)
                        .asSequence()
                        .mapNotNull { wr ->
                            val d = runCatching { LocalDate.parse(wr.date) }.getOrNull()
                            if (d != null && !d.isBefore(firstDay) && !d.isAfter(lastDay)) d to wr else null
                        }
                        .groupBy({ it.first }, { toComposeReminder(it.second) })
                }
            } else emptyMap()

            _remindersByDateThisMonth.value = remindersMap
            _daysWithRemindersThisMonth.value = remindersMap.keys.toList()
        }
    }

    private fun loadRemindersForDate(date: LocalDate) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val userEmail = EmailContext.current(context)
            val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

            // 1) Reminders for day
            val remindersForDay = if (userEmail != null) {
                reminderRepo.getAllRemindersForUserList(userEmail)
                    .filter { it.date == dateStr }
                    .map { toComposeReminder(it) }
            } else emptyList()
            _reminders.value = remindersForDay

            // 2) Photos for day (NEW)
            val photosForDay: List<CalendarPhotoItem> = if (userEmail != null) {
                val raw = photoRepo.getPhotosByDate(dateStr)
                    .asSequence()
                    .filter { it.userEmail == userEmail }
                    .filter { !it.isCover } // exclude cover/title photos
                    .sortedByDescending { it.id }
                    .toList()

                raw.map { p ->
                    val plantName: String? = try {
                        if (p.plantId > 0) {
                            val pl = plantRepo.findPlantById(p.plantId)
                            when {
                                pl == null -> null
                                pl.isUserPlant && !pl.nickname.isNullOrBlank() -> pl.nickname
                                !pl.name.isNullOrBlank() -> pl.name
                                else -> null
                            }
                        } else null
                    } catch (_: Throwable) { null }

                    CalendarPhotoItem(
                        photoId = p.id,
                        plantId = p.plantId.toLong(),
                        plantName = plantName,
                        date = date,
                        uri = runCatching { Uri.parse(p.imagePath) }.getOrNull() ?: Uri.EMPTY,
                        imagePath = p.imagePath
                    )
                }
            } else emptyList()
            _photosForSelectedDate.value = photosForDay

            // 3) Week dots
            val weekDays = getCurrentWeekDays(date)
            val allReminders = if (userEmail != null) {
                reminderRepo.getAllRemindersForUserList(userEmail).map { toComposeReminder(it) }
            } else emptyList()
            val reminderDaysWeek = allReminders.map { it.date }.distinct().filter { weekDays.contains(it) }
            _daysWithRemindersThisWeek.value = reminderDaysWeek

            onDisplayedMonthChanged(displayedMonthState.value)
        }
    }

    private fun toComposeReminder(w: WateringReminder): Reminder {
        return Reminder(
            id = w.id.toString(),
            title = w.description ?: "",
            time = null,
            date = LocalDate.parse(w.date),
            plantName = w.plantName,
            plantId = w.plantId.toLong()
        )
    }
}

/**
 * عنصر صورة لواجهة اليوم في الكالندر (Compose)
 */
data class CalendarPhotoItem(
    val photoId: Int = 0,
    val plantId: Long,
    val plantName: String?,
    val date: LocalDate,
    val uri: Uri,
    val imagePath: String? = null
)