package com.example.plantcare.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.example.plantcare.AppDatabase
import com.example.plantcare.RoomCategory
import com.example.plantcare.RoomCategoryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for RoomCategory data access layer.
 * Wraps RoomCategoryDao and provides coroutine-based methods with LiveData support.
 */
class RoomCategoryRepository private constructor(private val context: Context) {

    private val roomCategoryDao: RoomCategoryDao = AppDatabase.getInstance(context).roomCategoryDao()

    /**
     * Get all rooms for a specific user.
     * Returns a LiveData that observes database changes.
     */
    fun getRoomsForUser(email: String): LiveData<List<RoomCategory>> = liveData {
        val rooms = withContext(Dispatchers.IO) {
            roomCategoryDao.getAllRoomsForUser(email)
        }
        emit(rooms)
    }

    /**
     * Insert a new room into the database.
     * Runs on IO dispatcher.
     */
    suspend fun insertRoom(room: RoomCategory): Long = withContext(Dispatchers.IO) {
        roomCategoryDao.insert(room)
    }

    /**
     * Update an existing room in the database.
     * Runs on IO dispatcher.
     */
    suspend fun updateRoom(room: RoomCategory) = withContext(Dispatchers.IO) {
        roomCategoryDao.update(room)
    }

    /**
     * Delete a room from the database.
     * Runs on IO dispatcher.
     */
    suspend fun deleteRoom(room: RoomCategory) = withContext(Dispatchers.IO) {
        roomCategoryDao.delete(room)
    }

    /**
     * Get a room by ID.
     */
    suspend fun getRoomById(id: Int): RoomCategory? = withContext(Dispatchers.IO) {
        roomCategoryDao.findById(id)
    }

    /**
     * Find a room by name and user email.
     */
    suspend fun getRoomByName(name: String, userEmail: String): RoomCategory? =
        withContext(Dispatchers.IO) {
            roomCategoryDao.findByName(name, userEmail)
        }

    suspend fun getRoomsListForUser(email: String): List<RoomCategory> =
        withContext(Dispatchers.IO) {
            roomCategoryDao.getAllRoomsForUser(email)
        }

    companion object {
        @Volatile
        private var INSTANCE: RoomCategoryRepository? = null

        fun getInstance(context: Context): RoomCategoryRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = RoomCategoryRepository(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
