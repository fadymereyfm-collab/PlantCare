package com.example.plantcare.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.example.plantcare.AppDatabase
import com.example.plantcare.ReminderDao
import com.example.plantcare.WateringReminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for WateringReminder data access layer.
 * Wraps ReminderDao and provides coroutine-based methods with LiveData support.
 */
class ReminderRepository private constructor(private val context: Context) {

    private val reminderDao: ReminderDao = AppDatabase.getInstance(context).reminderDao()

    /**
     * Get today's and overdue reminders for a specific user (only incomplete ones).
     * Returns a LiveData that observes database changes.
     */
    fun getTodayReminders(email: String, today: String): LiveData<List<WateringReminder>> = liveData {
        val reminders = withContext(Dispatchers.IO) {
            reminderDao.getTodayAndOverdueRemindersForUser(today, email)
        }
        emit(reminders)
    }

    /**
     * Get all today's and overdue reminders for a user (including completed ones).
     * Returns a LiveData that observes database changes.
     */
    fun getTodayAllReminders(email: String, today: String): LiveData<List<WateringReminder>> = liveData {
        val reminders = withContext(Dispatchers.IO) {
            reminderDao.getTodayAndOverdueAllRemindersForUser(today, email)
        }
        emit(reminders)
    }

    /**
     * Get reminders within a specific date range.
     * Returns a LiveData that observes database changes.
     */
    fun getRemindersForDateRange(
        start: String,
        end: String
    ): LiveData<List<WateringReminder>> = liveData {
        val reminders = withContext(Dispatchers.IO) {
            reminderDao.getRemindersBetween(start, end)
        }
        emit(reminders)
    }

    /**
     * Get all reminders for a specific user.
     * Returns a LiveData that observes database changes.
     */
    fun getAllRemindersForUser(email: String): LiveData<List<WateringReminder>> = liveData {
        val reminders = withContext(Dispatchers.IO) {
            reminderDao.getAllRemindersForUser(email)
        }
        emit(reminders)
    }

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

    /**
     * Get reminders for a specific plant.
     * Returns a LiveData that observes database changes.
     */
    fun getRemindersForPlant(plantId: Int): LiveData<List<WateringReminder>> = liveData {
        val reminders = withContext(Dispatchers.IO) {
            reminderDao.getRemindersForPlant(plantId)
        }
        emit(reminders)
    }

    /**
     * Get reminder by ID.
     */
    suspend fun getReminderById(id: Int): WateringReminder? = withContext(Dispatchers.IO) {
        reminderDao.getReminderById(id)
    }

    /**
     * Get all reminders.
     * Returns a LiveData that observes database changes.
     */
    fun getAllReminders(): LiveData<List<WateringReminder>> = liveData {
        val reminders = withContext(Dispatchers.IO) {
            reminderDao.getAllReminders()
        }
        emit(reminders)
    }

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

    companion object {
        @Volatile
        private var INSTANCE: ReminderRepository? = null

        fun getInstance(context: Context): ReminderRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = ReminderRepository(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
