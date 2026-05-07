package com.example.plantcare.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.example.plantcare.AppDatabase
import com.example.plantcare.PlantPhoto
import com.example.plantcare.PlantPhotoDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for [PlantPhoto] access. Created in Sprint-3 Task 3.2 to give
 * the weekbar Compose layer (and any future caller) a clean surface that
 * doesn't need to call `AppDatabase.getInstance(...).plantPhotoDao()` each
 * time.
 *
 * Reactive accessors return Room-observable LiveData (Sprint-3 Task 3.1
 * convention) so a UI bound to "photos for this date" updates automatically
 * when the user takes a new photo from any other surface.
 */
class PlantPhotoRepository private constructor(context: Context) {

    private val photoDao: PlantPhotoDao = AppDatabase.getInstance(context).plantPhotoDao()

    /** Photos for a plant — reactive. */
    fun observePhotosForPlant(plantId: Int): LiveData<List<PlantPhoto>> =
        photoDao.observePhotosForPlant(plantId)

    /** Photos for a calendar date (yyyy-MM-dd) — reactive. */
    fun observePhotosByDate(date: String): LiveData<List<PlantPhoto>> =
        photoDao.observePhotosByDate(date)

    /** Snapshot photos for a date — used by ViewModels that need a one-shot read. */
    suspend fun getPhotosByDate(date: String): List<PlantPhoto> =
        withContext(Dispatchers.IO) { photoDao.getPhotosByDate(date) }

    /** Snapshot photos for a plant — one-shot read (e.g. for collage builder). */
    suspend fun getPhotosForPlant(plantId: Int): List<PlantPhoto> =
        withContext(Dispatchers.IO) { photoDao.getPhotosForPlant(plantId) }

    suspend fun getCoverPhoto(plantId: Int): PlantPhoto? =
        withContext(Dispatchers.IO) { photoDao.getCoverPhoto(plantId) }

    suspend fun insert(photo: PlantPhoto): Long =
        withContext(Dispatchers.IO) { photoDao.insert(photo) }

    suspend fun update(photo: PlantPhoto) =
        withContext(Dispatchers.IO) { photoDao.update(photo) }

    suspend fun deleteById(id: Int) =
        withContext(Dispatchers.IO) { photoDao.deleteById(id) }

    suspend fun unsetCoverForPlant(plantId: Int) =
        withContext(Dispatchers.IO) { photoDao.unsetCoverForPlant(plantId) }

    suspend fun getPhotoById(id: Int): PlantPhoto? =
        withContext(Dispatchers.IO) { photoDao.getPhotoById(id) }

    // ────────────────────────────────────────────────────────────────────
    // Sprint-3 Task 3.2b: blocking helpers for legacy Java callers.
    // Same rationale as PlantRepository — callers manage their own
    // background thread.
    // ────────────────────────────────────────────────────────────────────

    fun insertBlocking(photo: PlantPhoto): Long = photoDao.insert(photo)
    fun updateBlocking(photo: PlantPhoto) = photoDao.update(photo)
    fun deleteBlocking(photo: PlantPhoto) = photoDao.delete(photo)
    fun deleteByIdBlocking(id: Int) = photoDao.deleteById(id)
    fun getPhotoByIdBlocking(id: Int): PlantPhoto? = photoDao.getPhotoById(id)
    fun getPhotosForPlantBlocking(plantId: Int): List<PlantPhoto> =
        photoDao.getPhotosForPlant(plantId)
    fun getPhotosByDateBlocking(date: String?): List<PlantPhoto> =
        photoDao.getPhotosByDate(date)
    fun getCoverPhotoBlocking(plantId: Int): PlantPhoto? = photoDao.getCoverPhoto(plantId)
    fun unsetCoverForPlantBlocking(plantId: Int) = photoDao.unsetCoverForPlant(plantId)
    fun deleteAllForPlantBlocking(plantId: Int) = photoDao.deleteAllForPlant(plantId)
    fun deleteAllPhotosForUserBlocking(email: String?) = photoDao.deleteAllPhotosForUser(email)

    /** Used by the MONTHLY_PHOTO challenge to detect any photo this month. */
    fun countPhotosForUserSinceBlocking(email: String?, sinceDate: String?): Int =
        photoDao.countPhotosForUserSince(email, sinceDate)

    companion object {
        @Volatile
        private var INSTANCE: PlantPhotoRepository? = null

        @JvmStatic
        fun getInstance(context: Context): PlantPhotoRepository =
            // #5 fix: inner recheck (was missing), so the synchronized
            // block can no longer construct duplicates if two threads
            // race past the outer null-check.
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlantPhotoRepository(context.applicationContext)
                    .also { INSTANCE = it }
            }
    }
}
