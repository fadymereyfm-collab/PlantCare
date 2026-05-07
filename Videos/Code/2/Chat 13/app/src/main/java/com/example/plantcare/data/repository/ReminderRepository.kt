package com.example.plantcare.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.example.plantcare.AppDatabase
import com.example.plantcare.ReminderDao
import com.example.plantcare.WateringReminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for WateringReminder data access layer. Wraps ReminderDao.
 *
 * Sprint-3 Task 3.1: read-side LiveData accessors return Room-observable
 * LiveData directly (no `liveData{}` one-shot builders) so the UI updates
 * reactively when reminders are inserted, ticked done, deleted or shifted
 * by the weather worker.
 */
class ReminderRepository private constructor(context: Context) {

    // Sprint-3 cleanup 2026-05-05: take Context as parameter (not property)
    // to avoid pinning an Activity in the Singleton — getInstance below
    // normalises to applicationContext.
    private val reminderDao: ReminderDao = AppDatabase.getInstance(context).reminderDao()

    /** Today's & overdue undone reminders — reactive. */
    fun getTodayReminders(email: String, today: String): LiveData<List<WateringReminder>> =
        reminderDao.observeTodayAndOverdueRemindersForUser(today, email)

    /** Today's & overdue reminders incl. completed — reactive. */
    fun getTodayAllReminders(email: String, today: String): LiveData<List<WateringReminder>> =
        reminderDao.observeTodayAndOverdueAllRemindersForUser(today, email)

    /** Reminders inside a date range — reactive. */
    fun getRemindersForDateRange(start: String, end: String): LiveData<List<WateringReminder>> =
        reminderDao.observeRemindersBetween(start, end)

    /** All reminders for a user — reactive. */
    fun getAllRemindersForUser(email: String): LiveData<List<WateringReminder>> =
        reminderDao.observeAllRemindersForUser(email)

    /**
     * Insert a new reminder into the database.
     * Runs on IO dispatcher.
     */
    suspend fun insertReminder(reminder: WateringReminder) = withContext(Dispatchers.IO) {
        reminderDao.insert(reminder)
    }

    /**
     * Insert multiple reminders into the database.
     * Runs on IO dispatcher.
     */
    suspend fun insertReminders(reminders: List<WateringReminder>) = withContext(Dispatchers.IO) {
        reminderDao.insertAll(reminders)
    }

    /**
     * Update an existing reminder in the database.
     * Runs on IO dispatcher.
     */
    suspend fun updateReminder(reminder: WateringReminder) = withContext(Dispatchers.IO) {
        reminderDao.update(reminder)
    }

    /**
     * Delete a reminder from the database.
     * Runs on IO dispatcher.
     */
    suspend fun deleteReminder(reminder: WateringReminder) = withContext(Dispatchers.IO) {
        reminderDao.delete(reminder)
    }

    /**
     * Mark a reminder as done by setting its done flag.
     * Runs on IO dispatcher.
     */
    suspend fun markDone(reminder: WateringReminder) = withContext(Dispatchers.IO) {
        reminder.done = true
        reminderDao.update(reminder)
    }

    /** Reminders for a specific plant — reactive. */
    fun getRemindersForPlant(plantId: Int): LiveData<List<WateringReminder>> =
        reminderDao.observeRemindersForPlant(plantId)

    /**
     * Get reminder by ID.
     */
    suspend fun getReminderById(id: Int): WateringReminder? = withContext(Dispatchers.IO) {
        reminderDao.getReminderById(id)
    }

    /** All reminders — reactive. */
    fun getAllReminders(): LiveData<List<WateringReminder>> = reminderDao.observeAllReminders()

    /**
     * Delete future reminders for a specific plant.
     * Runs on IO dispatcher.
     */
    suspend fun deleteFutureRemindersForPlant(plantId: Int, fromDateStr: String) =
        withContext(Dispatchers.IO) {
            reminderDao.deleteFutureRemindersForPlant(plantId, fromDateStr)
        }

    /**
     * Delete all reminders for a specific plant.
     * Runs on IO dispatcher.
     */
    suspend fun deleteRemindersForPlant(plantId: Int) = withContext(Dispatchers.IO) {
        reminderDao.deleteRemindersForPlant(plantId)
    }

    /**
     * Delete all reminders for a specific user.
     * Runs on IO dispatcher.
     */
    suspend fun deleteAllRemindersForUser(userEmail: String) = withContext(Dispatchers.IO) {
        reminderDao.deleteAllRemindersForUser(userEmail)
    }

    /**
     * Get reminders by plant and date.
     */
    suspend fun getRemindersByPlantAndDate(
        plantName: String,
        date: String,
        userEmail: String
    ): List<WateringReminder> = withContext(Dispatchers.IO) {
        reminderDao.getRemindersByPlantAndDate(plantName, date, userEmail)
    }

    /**
     * Delete future manual repeats for a plant.
     * Runs on IO dispatcher.
     */
    suspend fun deleteFutureManualRepeats(
        plantId: Int,
        userEmail: String,
        fromDate: String,
        oldDesc: String,
        oldRepeat: String
    ) = withContext(Dispatchers.IO) {
        reminderDao.deleteFutureManualRepeats(plantId, userEmail, fromDate, oldDesc, oldRepeat)
    }

    /**
     * Delete reminders for a plant and user.
     * Runs on IO dispatcher.
     */
    suspend fun deleteRemindersForPlantAndUser(
        plantId: Int,
        plantName: String,
        userEmail: String
    ) = withContext(Dispatchers.IO) {
        reminderDao.deleteRemindersForPlantAndUser(plantId, plantName, userEmail)
    }

    suspend fun getTodayAllRemindersList(today: String, email: String): List<WateringReminder> =
        withContext(Dispatchers.IO) {
            reminderDao.getTodayAndOverdueAllRemindersForUser(today, email)
        }

    /** Snapshot list of all reminders for a user — for callers that need a
     *  one-shot read inside their own coroutine (weekbar ViewModel, weather
     *  worker). The reactive twin is [getAllRemindersForUser]. */
    suspend fun getAllRemindersForUserList(email: String): List<WateringReminder> =
        withContext(Dispatchers.IO) {
            reminderDao.getAllRemindersForUser(email)
        }

    // ────────────────────────────────────────────────────────────────────
    // Sprint-3 Task 3.2b: blocking helpers for legacy Java callers.
    // See PlantRepository for the rationale (Java + suspend = friction;
    // these `fun` wrappers must be called on a background thread).
    // ────────────────────────────────────────────────────────────────────

    fun getReminderByIdBlocking(id: Int): WateringReminder? = reminderDao.getReminderById(id)
    fun getAllRemindersForUserBlocking(email: String?): List<WateringReminder> =
        reminderDao.getAllRemindersForUser(email)
    fun getRemindersForPlantBlocking(plantId: Int): List<WateringReminder> =
        reminderDao.getRemindersForPlant(plantId)
    fun getRemindersBetweenBlocking(start: String?, end: String?): List<WateringReminder> =
        reminderDao.getRemindersBetween(start, end)
    fun getTodayAndOverdueRemindersForUserBlocking(today: String?, email: String?): List<WateringReminder> =
        reminderDao.getTodayAndOverdueRemindersForUser(today, email)
    fun getTodayAndOverdueAllRemindersForUserBlocking(today: String?, email: String?): List<WateringReminder> =
        reminderDao.getTodayAndOverdueAllRemindersForUser(today, email)
    fun getRemindersByPlantAndDateBlocking(plantName: String?, date: String?, email: String?): List<WateringReminder> =
        reminderDao.getRemindersByPlantAndDate(plantName, date, email)

    fun insertBlocking(reminder: WateringReminder) = reminderDao.insert(reminder)
    fun insertAllBlocking(list: List<WateringReminder>) = reminderDao.insertAll(list)
    fun updateBlocking(reminder: WateringReminder) = reminderDao.update(reminder)
    fun deleteBlocking(reminder: WateringReminder) = reminderDao.delete(reminder)
    fun deleteRemindersForPlantBlocking(plantId: Int) =
        reminderDao.deleteRemindersForPlant(plantId)
    fun deleteFutureRemindersForPlantBlocking(plantId: Int, fromDateStr: String?) =
        reminderDao.deleteFutureRemindersForPlant(plantId, fromDateStr)
    fun deleteAllRemindersForUserBlocking(email: String?) =
        reminderDao.deleteAllRemindersForUser(email)
    fun deleteRemindersForPlantAndUserBlocking(plantId: Int, plantName: String?, email: String?) =
        reminderDao.deleteRemindersForPlantAndUser(plantId, plantName, email)
    fun deleteFutureManualRepeatsBlocking(plantId: Int, email: String?, fromDate: String?, oldDesc: String?, oldRepeat: String?) =
        reminderDao.deleteFutureManualRepeats(plantId, email, fromDate, oldDesc, oldRepeat)

    /**
     * Plant Journal: store a free-text note on a completed reminder. Used by the
     * journal's long-press editor; reminders without notes simply have a NULL
     * column. Returns true when the row existed and was updated, false otherwise.
     */
    suspend fun setNoteForReminder(reminderId: Int, note: String?): Boolean =
        withContext(Dispatchers.IO) {
            val reminder = reminderDao.getReminderById(reminderId) ?: return@withContext false
            reminder.notes = note?.takeIf { it.isNotBlank() }
            reminderDao.update(reminder)
            true
        }

    companion object {
        @Volatile
        private var INSTANCE: ReminderRepository? = null

        @JvmStatic
        fun getInstance(context: Context): ReminderRepository {
            // #5 fix: inner recheck added — see PlantRepository for
            // the full rationale.
            return INSTANCE ?: synchronized(this) {
                val instance = INSTANCE ?: ReminderRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
