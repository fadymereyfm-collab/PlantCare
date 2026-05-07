package com.example.plantcare.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.example.plantcare.AppDatabase
import com.example.plantcare.RoomCategory
import com.example.plantcare.RoomCategoryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Repository for RoomCategory data access layer.
 * Wraps RoomCategoryDao and provides coroutine-based methods with LiveData support.
 */
class RoomCategoryRepository private constructor(context: Context) {

    // Sprint-3 cleanup 2026-05-05: Context as parameter (not property) +
    // applicationContext normalisation in getInstance — same fix as the
    // sibling Repos.
    private val roomCategoryDao: RoomCategoryDao = AppDatabase.getInstance(context).roomCategoryDao()

    /**
     * Get all rooms for a specific user — reactive.
     * Hands back the DAO's Room-observable LiveData directly so the UI
     * re-binds whenever the underlying rows change. The previous
     * `liveData { emit(dao.xxx()) }` builder violated the project rule
     * documented in CLAUDE.md (one-shot LiveData that never updates).
     */
    fun getRoomsForUser(email: String): LiveData<List<RoomCategory>> =
        roomCategoryDao.observeAllRoomsForUser(email)

    /**
     * Insert a new room into the database.
     * Runs on IO dispatcher.
     *
     * Throws IllegalArgumentException on a blank name — the entity declares
     * `name` as `@NonNull` but the no-arg constructor seeds it as "" so
     * Room would happily persist that without complaint. Failing loudly here
     * gives the caller a chance to surface a validation message instead of
     * silently creating an unnamed row.
     */
    suspend fun insertRoom(room: RoomCategory): Long = withContext(Dispatchers.IO) {
        require(room.name.isNotBlank()) { "RoomCategory.name must not be blank" }
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

    // ────────────────────────────────────────────────────────────────────
    // Sprint-3 Task 3.2b: blocking helpers for legacy Java callers.
    // ────────────────────────────────────────────────────────────────────

    fun findByIdBlocking(id: Int): RoomCategory? = roomCategoryDao.findById(id)
    fun findByNameBlocking(name: String?, email: String?): RoomCategory? =
        roomCategoryDao.findByName(name, email)
    fun getAllRoomsForUserBlocking(email: String?): List<RoomCategory> =
        roomCategoryDao.getAllRoomsForUser(email)
    fun insertBlocking(room: RoomCategory): Long {
        require(room.name.isNotBlank()) { "RoomCategory.name must not be blank" }
        return roomCategoryDao.insert(room)
    }
    fun updateBlocking(room: RoomCategory) {
        require(room.name.isNotBlank()) { "RoomCategory.name must not be blank" }
        roomCategoryDao.update(room)
    }
    fun deleteBlocking(room: RoomCategory) = roomCategoryDao.delete(room)
    fun updatePositionBlocking(id: Int, position: Int) =
        roomCategoryDao.updatePosition(id, position)

    /**
     * Persist the user-defined ordering after a drag-to-reorder. Each room's
     * position is set to its index in the supplied list — gaps don't matter
     * since the SQL ORDER BY is just `ASC` and ties fall through to name.
     */
    fun reorderBlocking(orderedIds: List<Int>) {
        for ((idx, id) in orderedIds.withIndex()) {
            roomCategoryDao.updatePosition(id, idx)
        }
    }

    /**
     * Sprint-3 cleanup 2026-05-05: race-safe replacement for the previous
     * `db.runInTransaction { ... }` blocks in AddToMyPlantsDialogFragment
     * and MyPlantsFragment. Two screens entered concurrently from
     * different lifecycle moments could each see "no Wohnzimmer yet" and
     * both insert it — duplicate rows. The `synchronized` here makes the
     * read+insert sequence atomic within the process, which matches what
     * Room's transaction did and avoids needing a UNIQUE index migration.
     *
     * Returns the resulting room list (after defaults are guaranteed to
     * exist) so the caller can render in one round-trip.
     */
    @Synchronized
    fun ensureDefaultsForUserBlocking(email: String?, defaults: List<String>): List<RoomCategory> {
        // Sync barrier: when a Firestore-driven import is in progress for
        // this user we must NOT insert local defaults — the cloud rooms
        // arrive with preserved IDs (1..N) that would otherwise collide
        // with whatever auto-increment hands out for fresh defaults,
        // making 5/6 cloud rooms fail to import on a new-device sign-in.
        // Hand back whatever rooms exist right now; the import will fill
        // in the rest, and the LiveData on the DAO will re-emit then.
        if (CLOUD_IMPORT_IN_PROGRESS.get()) {
            return roomCategoryDao.getAllRoomsForUser(email).orEmpty()
        }
        val current = roomCategoryDao.getAllRoomsForUser(email).orEmpty()
        val existing = current.mapNotNull { it.name }.toHashSet()
        // Track inserted rooms so we can sync them to Firestore in one pass
        // outside the synchronized critical section — sync work has nothing
        // to do with our duplicate-prevention invariant and shouldn't block
        // a second screen waiting to read the room list.
        val freshlyInserted = mutableListOf<RoomCategory>()
        for (def in defaults) {
            if (def !in existing) {
                val rc = RoomCategory()
                rc.name = def
                rc.userEmail = email
                rc.id = roomCategoryDao.insert(rc).toInt()
                freshlyInserted += rc
            }
        }
        for (r in freshlyInserted) {
            try { com.example.plantcare.FirebaseSyncManager.get().syncRoom(r) }
            catch (t: Throwable) { com.example.plantcare.CrashReporter.log(t) }
        }
        return roomCategoryDao.getAllRoomsForUser(email).orEmpty()
    }

    companion object {
        @Volatile
        private var INSTANCE: RoomCategoryRepository? = null

        /**
         * Sync barrier toggled by [com.example.plantcare.MainActivity.importCloudDataForUser].
         * While true, [ensureDefaultsForUserBlocking] becomes a no-op so the
         * UI can render immediately with whatever rooms already exist
         * without inserting defaults that would collide with cloud-preserved
         * IDs landing a few hundred ms later.
         */
        @JvmField
        val CLOUD_IMPORT_IN_PROGRESS = AtomicBoolean(false)

        @JvmStatic
        fun getInstance(context: Context): RoomCategoryRepository {
            // #5 fix: inner recheck.
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RoomCategoryRepository(context.applicationContext)
                    .also { INSTANCE = it }
            }
        }
    }
}
