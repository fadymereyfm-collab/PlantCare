package com.example.plantcare.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.example.plantcare.AppDatabase
import com.example.plantcare.Plant
import com.example.plantcare.PlantDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for Plant data access layer. Wraps PlantDao.
 *
 * Sprint-3 Task 3.1: read-side LiveData accessors hand back the DAO\'s
 * Room-observable LiveData directly so the UI re-binds whenever the
 * underlying rows change.
 */
class PlantRepository private constructor(context: Context) {

    private val plantDao: PlantDao = AppDatabase.getInstance(context).plantDao()

    /** User plants for a given email — reactive. */
    fun getAllUserPlants(email: String): LiveData<List<Plant>> =
        plantDao.observeAllUserPlantsForUser(email)

    /** User plants inside a specific room — reactive. */
    fun getPlantsInRoom(roomId: Int, email: String): LiveData<List<Plant>> =
        plantDao.observeAllUserPlantsInRoom(roomId, email)

    /** Single plant by id — reactive. */
    fun getPlantById(id: Int): LiveData<Plant> =
        plantDao.observeById(id)

    suspend fun insertPlant(plant: Plant): Long = withContext(Dispatchers.IO) {
        plantDao.insert(plant)
    }

    suspend fun updatePlant(plant: Plant) = withContext(Dispatchers.IO) {
        plantDao.update(plant)
    }

    suspend fun deletePlant(plant: Plant) = withContext(Dispatchers.IO) {
        plantDao.delete(plant)
    }

    /** Catalog (non-user) plants — reactive. */
    fun getAllCatalogPlants(): LiveData<List<Plant>> =
        plantDao.observeAllNonUserPlants()

    suspend fun searchPlants(query: String): List<Plant> =
        withContext(Dispatchers.IO) { plantDao.getAllUserPlantsWithName(query) }

    /** All plants (user + catalog) — reactive. */
    fun getAllPlants(): LiveData<List<Plant>> = plantDao.observeAll()

    suspend fun getPlantsByIds(ids: List<Int>): List<Plant> = withContext(Dispatchers.IO) {
        plantDao.getPlantsByIds(ids)
    }

    suspend fun findPlantByName(name: String): Plant? = withContext(Dispatchers.IO) {
        plantDao.findByName(name)
    }

    suspend fun findUserPlantByNameAndEmail(name: String, userEmail: String): Plant? =
        withContext(Dispatchers.IO) {
            plantDao.findUserPlantByNameAndUser(name, userEmail)
        }

    suspend fun updateProfileImage(id: Int, imageUri: String) = withContext(Dispatchers.IO) {
        plantDao.updateProfileImage(id, imageUri)
    }

    suspend fun clearProfileImage(id: Int) = withContext(Dispatchers.IO) {
        plantDao.clearProfileImage(id)
    }

    suspend fun getCatalogPlantsWithoutImage(): List<Plant> = withContext(Dispatchers.IO) {
        plantDao.getCatalogPlantsWithoutImage()
    }

    suspend fun deleteAllUserPlantsForUser(userEmail: String) = withContext(Dispatchers.IO) {
        plantDao.deleteAllUserPlantsForUser(userEmail)
    }

    suspend fun countPlantsByRoom(roomId: Int, userEmail: String): Int =
        withContext(Dispatchers.IO) {
            plantDao.countPlantsByRoom(roomId, userEmail)
        }

    // ─── Suspend list accessors used by ViewModels ───
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

    suspend fun findPlantById(id: Int): Plant? = withContext(Dispatchers.IO) {
        plantDao.findById(id)
    }

    suspend fun getAllNonUserPlantsList(): List<Plant> = withContext(Dispatchers.IO) {
        plantDao.getAllNonUserPlants()
    }

    suspend fun getAllPlantsList(): List<Plant> = withContext(Dispatchers.IO) {
        plantDao.getAll()
    }

    // ─── Blocking helpers for legacy Java callers ───

    fun countAllBlocking(): Int = plantDao.countAll()
    fun findByIdBlocking(id: Int): Plant? = plantDao.findById(id)
    fun getAllBlocking(): List<Plant> = plantDao.getAll()
    fun getAllUserPlantsBlocking(): List<Plant> = plantDao.getAllUserPlants()
    fun getPlantsByIdsBlocking(ids: List<Int>): List<Plant> = plantDao.getPlantsByIds(ids)
    fun getCatalogPlantsWithoutImageBlocking(): List<Plant> = plantDao.getCatalogPlantsWithoutImage()
    fun getCatalogPlantsWithoutCategoryBlocking(): List<Plant> = plantDao.getCatalogPlantsWithoutCategory()
    fun updateCategoryBlocking(id: Int, category: String?) = plantDao.updateCategory(id, category)
    fun findByNameBlocking(name: String?): Plant? = plantDao.findByName(name)
    fun findByNicknameBlocking(nickname: String?): Plant? = plantDao.findByNickname(nickname)
    fun findUserPlantByNameAndEmailBlocking(name: String?, email: String?): Plant? =
        plantDao.findUserPlantByNameAndUser(name, email)
    fun findCatalogByNameBlocking(name: String?): Plant? = plantDao.findCatalogByName(name)
    fun findCatalogByNameLikeBlocking(pattern: String?): Plant? =
        plantDao.findCatalogByNameLike(pattern)

    /**
     * v17: catalog lookup by Latin / binomial name.
     */
    fun findCatalogByScientificNameBlocking(scientificName: String?): Plant? =
        plantDao.findCatalogByScientificName(scientificName)

    /**
     * v17: partial Latin-name match (genus prefix fallback).
     */
    fun findCatalogByScientificNameLikeBlocking(pattern: String?): Plant? =
        plantDao.findCatalogByScientificNameLike(pattern)

    fun getAllUserPlantsForUserBlocking(email: String?): List<Plant> =
        plantDao.getAllUserPlantsForUser(email)
    fun getAllUserPlantsInRoomBlocking(roomId: Int, email: String?): List<Plant> =
        plantDao.getAllUserPlantsInRoom(roomId, email)
    fun getAllUserPlantsWithNameBlocking(name: String?): List<Plant> =
        plantDao.getAllUserPlantsWithName(name)
    fun getAllUserPlantsWithNameAndUserBlocking(name: String?, email: String?): List<Plant> =
        plantDao.getAllUserPlantsWithNameAndUser(name, email)
    fun getAllUserPlantsWithNicknameAndUserBlocking(nickname: String?, email: String?): List<Plant> =
        plantDao.getAllUserPlantsWithNicknameAndUser(nickname, email)
    fun getAllNonUserPlantsBlocking(): List<Plant> = plantDao.getAllNonUserPlants()
    fun getCatalogPlantsByCategoryBlocking(category: String?): List<Plant> =
        plantDao.getCatalogPlantsByCategory(category)
    fun countUserPlantsBlocking(email: String?): Int = plantDao.countUserPlants(email)
    fun countPlantsByRoomBlocking(roomId: Int, email: String?): Int =
        plantDao.countPlantsByRoom(roomId, email)

    fun insertBlocking(plant: Plant): Long = plantDao.insert(plant)
    fun updateBlocking(plant: Plant) = plantDao.update(plant)
    fun deleteBlocking(plant: Plant) = plantDao.delete(plant)
    fun deleteAllUserPlantsForUserBlocking(email: String?) =
        plantDao.deleteAllUserPlantsForUser(email)
    fun updateProfileImageBlocking(id: Int, imageUri: String?) =
        plantDao.updateProfileImage(id, imageUri)
    fun clearProfileImageBlocking(id: Int) = plantDao.clearProfileImage(id)

    companion object {
        @Volatile
        private var INSTANCE: PlantRepository? = null

        @JvmStatic
        fun getInstance(context: Context): PlantRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlantRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
