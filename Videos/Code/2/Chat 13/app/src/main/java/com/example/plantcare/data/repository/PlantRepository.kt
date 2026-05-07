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
 * Sprint-3 Task 3.1: read-side LiveData accessors now hand back the DAO's
 * Room-observable LiveData directly (no `liveData { emit(dao.xxx()) }`
 * one-shot builders), so the UI re-binds whenever the underlying rows
 * change without any DataChangeNotifier nudge.
 */
class PlantRepository private constructor(context: Context) {

    // Sprint-3 cleanup 2026-05-05: take Context as a constructor parameter
    // (not a property) so the Singleton doesn't pin an Activity in memory
    // when getInstance is called from `requireContext()`. Only used here to
    // resolve the DAO; getInstance below additionally normalises to
    // applicationContext as a defence in depth.
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

    /** Catalog (non-user) plants — reactive. */
    fun getAllCatalogPlants(): LiveData<List<Plant>> =
        plantDao.observeAllNonUserPlants()

    /**
     * Search for plants by name. Reactive lookups by name aren't supported
     * by the current DAO surface (Room would need a separate observe method
     * per query shape) — kept as a snapshot via withContext so the search
     * field doesn't hang the main thread. Recompose by calling again on
     * input change rather than wiring a Flow per keystroke.
     */
    suspend fun searchPlants(query: String): List<Plant> =
        withContext(Dispatchers.IO) { plantDao.getAllUserPlantsWithName(query) }

    /** All plants (user + catalog) — reactive. */
    fun getAllPlants(): LiveData<List<Plant>> = plantDao.observeAll()

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

    /** Snapshot lookup by id — for callers that already sit on Dispatchers.IO
     *  (e.g. weekbar ViewModel coroutines, image loaders). */
    suspend fun findPlantById(id: Int): Plant? = withContext(Dispatchers.IO) {
        plantDao.findById(id)
    }

    /** Catalog (non-user) plants snapshot. */
    suspend fun getAllNonUserPlantsList(): List<Plant> = withContext(Dispatchers.IO) {
        plantDao.getAllNonUserPlants()
    }

    /** All plants snapshot (user + catalog). */
    suspend fun getAllPlantsList(): List<Plant> = withContext(Dispatchers.IO) {
        plantDao.getAll()
    }

    // ────────────────────────────────────────────────────────────────────
    // Sprint-3 Task 3.2b: blocking helpers for legacy Java callers.
    //
    // Suspend fun + Java is friction (runBlocking + Continuation noise),
    // and the legacy Java fragments already manage their own threading via
    // `new Thread()` / Executors. These plain `fun` wrappers expose Repo
    // semantics to those callers without forcing them to wrap every line
    // in `BuildersKt.runBlocking`. The caller is responsible for being on
    // a background thread — Room itself will throw on the main thread.
    //
    // These are the only plain `fun` accessors on the Repository; the
    // canonical Kotlin API (above) stays suspend.
    // ────────────────────────────────────────────────────────────────────

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
            // #5 fix: classic double-checked-locking — the inner
            // recheck of INSTANCE was missing, so two threads racing
            // through the outer null-check could each enter the
            // synchronized block in turn and construct duplicate
            // singletons. The first instance would silently lose its
            // observer registrations / lazy DAO refs to the second.
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlantRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
