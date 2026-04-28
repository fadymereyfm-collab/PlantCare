package com.example.plantcare.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.example.plantcare.AppDatabase
import com.example.plantcare.Plant
import com.example.plantcare.PlantDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for Plant data access layer.
 * Wraps PlantDao and provides coroutine-based methods with LiveData/Flow support.
 */
class PlantRepository private constructor(private val context: Context) {

    private val plantDao: PlantDao = AppDatabase.getInstance(context).plantDao()

    /**
     * Get all user plants for a specific email.
     * Returns a LiveData that observes database changes.
     */
    fun getAllUserPlants(email: String): LiveData<List<Plant>> = liveData {
        val plants = withContext(Dispatchers.IO) {
            plantDao.getAllUserPlantsForUser(email)
        }
        emit(plants)
    }

    /**
     * Get plants in a specific room for a user.
     * Returns a LiveData that observes database changes.
     */
    fun getPlantsInRoom(roomId: Int, email: String): LiveData<List<Plant>> = liveData {
        val plants = withContext(Dispatchers.IO) {
            plantDao.getAllUserPlantsInRoom(roomId, email)
        }
        emit(plants)
    }

    /**
     * Get a single plant by ID.
     * Returns a LiveData that observes database changes.
     */
    fun getPlantById(id: Int): LiveData<Plant?> = liveData {
        val plant = withContext(Dispatchers.IO) {
            plantDao.findById(id)
        }
        emit(plant)
    }

    /**
     * Insert a new plant into the database.
     * Runs on IO dispatcher.
     */
    suspend fun insertPlant(plant: Plant): Long = withContext(Dispatchers.IO) {
        plantDao.insert(plant)
    }

    /**
     * Update an existing plant in the database.
     * Runs on IO dispatcher.
     */
    suspend fun updatePlant(plant: Plant) = withContext(Dispatchers.IO) {
        plantDao.update(plant)
    }

    /**
     * Delete a plant from the database.
     * Runs on IO dispatcher.
     */
    suspend fun deletePlant(plant: Plant) = withContext(Dispatchers.IO) {
        plantDao.delete(plant)
    }

    /**
     * Get all catalog (non-user) plants.
     * Returns a LiveData that observes database changes.
     */
    fun getAllCatalogPlants(): LiveData<List<Plant>> = liveData {
        val plants = withContext(Dispatchers.IO) {
            plantDao.getAllNonUserPlants()
        }
        emit(plants)
    }

    /**
     * Search for plants by name.
     * Returns a LiveData that observes database changes.
     */
    fun searchPlants(query: String): LiveData<List<Plant>> = liveData {
        val plants = withContext(Dispatchers.IO) {
            plantDao.getAllUserPlantsWithName(query)
        }
        emit(plants)
    }

    /**
     * Get all plants (both user and catalog).
     * Returns a LiveData that observes database changes.
     */
    fun getAllPlants(): LiveData<List<Plant>> = liveData {
        val plants = withContext(Dispatchers.IO) {
            plantDao.getAll()
        }
        emit(plants)
    }

    /**
     * Get plants by IDs.
     */
    suspend fun getPlantsByIds(ids: List<Int>): List<Plant> = withContext(Dispatchers.IO) {
        plantDao.getPlantsByIds(ids)
    }

    /**
     * Find a plant by name.
     */
    suspend fun findPlantByName(name: String): Plant? = withContext(Dispatchers.IO) {
        plantDao.findByName(name)
    }

    /**
     * Find a user plant by name and email.
     */
    suspend fun findUserPlantByNameAndEmail(name: String, userEmail: String): Plant? =
        withContext(Dispatchers.IO) {
            plantDao.findUserPlantByNameAndUser(name, userEmail)
        }

    /**
     * Update the profile image for a plant.
     */
    suspend fun updateProfileImage(id: Int, imageUri: String) = withContext(Dispatchers.IO) {
        plantDao.updateProfileImage(id, imageUri)
    }

    /**
     * Clear the profile image for a plant.
     */
    suspend fun clearProfileImage(id: Int) = withContext(Dispatchers.IO) {
        plantDao.clearProfileImage(id)
    }

    /**
     * Get catalog plants without images.
     */
    suspend fun getCatalogPlantsWithoutImage(): List<Plant> = withContext(Dispatchers.IO) {
        plantDao.getCatalogPlantsWithoutImage()
    }

    /**
     * Delete all user plants for a specific email.
     */
    suspend fun deleteAllUserPlantsForUser(userEmail: String) = withContext(Dispatchers.IO) {
        plantDao.deleteAllUserPlantsForUser(userEmail)
    }

    /**
     * Count plants in a room.
     */
    suspend fun countPlantsByRoom(roomId: Int, userEmail: String): Int =
        withContext(Dispatchers.IO) {
            plantDao.countPlantsByRoom(roomId, userEmail)
        }

    // ─── Suspend list accessors used by ViewModels ───────────────────────
    suspend fun getAllCatalogPlantsList(): List<Plant> = withContext(Dispatchers.IO) {
        plantDao.getAllNonUserPlants()
    }

    suspend fun getUserPlantsListForUser(email: String): List<Plant> = withContext(Dispatchers.IO) {
        plantDao.getAllUserPlantsForUser(email)
    }

    suspend fun getUserPlantsInRoomList(roomId: Int, email: String): List<Plant> =
        withContext(Dispatchers.IO) {
            plantDao.getAllUserPlantsInRoom(roomId, email)
        }

    suspend fun findUserPlantsByName(name: String, email: String): List<Plant> =
        withContext(Dispatchers.IO) {
            plantDao.getAllUserPlantsWithNameAndUser(name, email)
        }

    suspend fun findUserPlantsByNickname(nickname: String, email: String): List<Plant> =
        withContext(Dispatchers.IO) {
            plantDao.getAllUserPlantsWithNicknameAndUser(nickname, email)
        }

    suspend fun findAnyByNickname(nickname: String): Plant? = withContext(Dispatchers.IO) {
        plantDao.findByNickname(nickname)
    }

    suspend fun findAnyByName(name: String): Plant? = withContext(Dispatchers.IO) {
        plantDao.findByName(name)
    }

    suspend fun countUserPlants(email: String): Int = withContext(Dispatchers.IO) {
        plantDao.countUserPlants(email)
    }

    companion object {
        @Volatile
        private var INSTANCE: PlantRepository? = null

        fun getInstance(context: Context): PlantRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = PlantRepository(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
