package com.example.plantcare.ui.util

import android.content.Context
import android.view.View
import com.example.plantcare.EmailContext
import androidx.fragment.app.Fragment
import com.example.plantcare.FirebaseSyncManager
import com.example.plantcare.Plant
import com.example.plantcare.R
import com.example.plantcare.ReminderUtils
import com.example.plantcare.RoomCategory
import com.example.plantcare.DataChangeNotifier
import com.example.plantcare.data.repository.PlantRepository
import com.example.plantcare.data.repository.ReminderRepository
import com.example.plantcare.data.repository.RoomCategoryRepository
import com.google.android.material.snackbar.Snackbar
import java.util.Calendar
import java.util.Date

/**
 * Quick-add for catalog plants: one-tap copy into "My Plants" using the
 * last-used room and today's date as defaults. Shows a Snackbar with Undo.
 *
 * Falls back to the user's first room when no last-used room is remembered.
 * Stays inert for guest mode — caller must check login status first.
 */
object QuickAddHelper {

    private const val PREFS_NAME = "prefs"

    /** Per-user last room stored so Quick-Add lands in the same place as last time. */
    private fun keyLastRoom(email: String) = "last_used_room_id_$email"

    @JvmStatic
    fun getCurrentUserEmail(context: Context): String? = EmailContext.current(context)

    /** Called from the regular add flow whenever a plant is placed into a room. */
    @JvmStatic
    fun rememberLastUsedRoom(context: Context, email: String?, roomId: Int) {
        if (email.isNullOrBlank() || roomId <= 0) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(keyLastRoom(email), roomId)
            .apply()
    }

    @JvmStatic
    fun readLastUsedRoom(context: Context, email: String?): Int {
        if (email.isNullOrBlank()) return 0
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(keyLastRoom(email), 0)
    }

    /**
     * Quick-add a catalog plant into the user's list.
     *
     * @param fragment host fragment (used for lifecycle-scoped IO)
     * @param sourcePlant the catalog plant to copy
     * @param anchor view used to anchor the Snackbar (usually the dialog's root or an activity view)
     * @param onComplete invoked on UI thread after the insert finishes (success or undo)
     */
    @JvmStatic
    fun quickAdd(
        fragment: Fragment,
        sourcePlant: Plant,
        anchor: View,
        onComplete: Runnable?
    ) {
        val ctx = fragment.requireContext().applicationContext
        val email = getCurrentUserEmail(ctx)
        if (email.isNullOrBlank()) {
            Snackbar.make(anchor, R.string.quick_add_needs_login, Snackbar.LENGTH_SHORT).show()
            return
        }

        val today = Calendar.getInstance().time

        val preferredRoomId = readLastUsedRoom(ctx, email)

        val plantRepo = PlantRepository.getInstance(ctx)
        val reminderRepo = ReminderRepository.getInstance(ctx)
        val roomRepo = RoomCategoryRepository.getInstance(ctx)

        val defaultRoomName = DefaultRooms.get(ctx).firstOrNull() ?: "Wohnzimmer"

        FragmentBg.runWithResult<QuickAddResult>(fragment,
            {
                val roomId = resolveTargetRoom(roomRepo, email, preferredRoomId, defaultRoomName)

                val p = Plant()
                p.name = sourcePlant.name
                p.lighting = sourcePlant.lighting
                p.soil = sourcePlant.soil
                p.fertilizing = sourcePlant.fertilizing
                p.watering = sourcePlant.watering
                p.imageUri = sourcePlant.imageUri
                p.personalNote = sourcePlant.personalNote
                // Kategorie vom Katalog-Eintrag übernehmen (kann null sein,
                // dann fällt classify(...) auf "indoor" zurück).
                p.category = sourcePlant.category
                    ?: PlantCategoryUtil.classify(sourcePlant.name, sourcePlant.lighting, sourcePlant.watering)
                p.nickname = deriveNickname(plantRepo, sourcePlant.name, email)
                p.isUserPlant = true
                p.userEmail = email
                p.startDate = today
                p.roomId = roomId

                val interval = ReminderUtils.parseWateringInterval(p.watering)
                p.wateringInterval = if (interval > 0) interval else 5

                val newId = plantRepo.insertBlocking(p).toInt()
                p.setId(newId)

                try { FirebaseSyncManager.get().syncPlant(p) }
                catch (t: Throwable) { com.example.plantcare.CrashReporter.log(t) }

                val reminders = ReminderUtils.generateReminders(p) ?: emptyList()
                for (r in reminders) {
                    r.userEmail = email
                    r.plantId = newId
                    reminderRepo.insertBlocking(r)
                    try { FirebaseSyncManager.get().syncReminder(r) }
                    catch (t: Throwable) { com.example.plantcare.CrashReporter.log(t) }
                }

                QuickAddResult(
                    plantId = newId,
                    roomId = roomId,
                    displayName = p.nickname ?: p.name ?: ""
                )
            },
            { result ->
                // Remember for next quick-add / normal add.
                rememberLastUsedRoom(ctx, email, result.roomId)
                DataChangeNotifier.notifyChange()

                val msg = fragment.getString(R.string.quick_add_added, result.displayName)
                Snackbar.make(anchor, msg, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_undo) {
                        undoQuickAdd(fragment, result, anchor)
                    }
                    .show()

                onComplete?.run()
            })
    }

    private fun undoQuickAdd(fragment: Fragment, result: QuickAddResult, anchor: View) {
        val ctx = fragment.requireContext().applicationContext
        val email = getCurrentUserEmail(ctx)
        val plantRepo = PlantRepository.getInstance(ctx)
        val reminderRepo = ReminderRepository.getInstance(ctx)
        FragmentBg.runIO(fragment,
            {
                // Reminders first — one query instead of per-id loop.
                try {
                    reminderRepo.deleteRemindersForPlantBlocking(result.plantId)
                    try {
                        FirebaseSyncManager.get().deleteRemindersForPlant(email, result.plantId)
                    } catch (t: Throwable) { com.example.plantcare.CrashReporter.log(t) }
                } catch (t: Throwable) { com.example.plantcare.CrashReporter.log(t) }

                try {
                    val plant = plantRepo.findByIdBlocking(result.plantId)
                    if (plant != null) {
                        plantRepo.deleteBlocking(plant)
                        try { FirebaseSyncManager.get().deletePlant(plant) }
                        catch (t: Throwable) { com.example.plantcare.CrashReporter.log(t) }
                    }
                } catch (t: Throwable) { com.example.plantcare.CrashReporter.log(t) }
            },
            {
                DataChangeNotifier.notifyChange()
                Snackbar.make(anchor, R.string.quick_add_undone, Snackbar.LENGTH_SHORT).show()
            })
    }

    private fun resolveTargetRoom(
        roomRepo: RoomCategoryRepository,
        email: String,
        preferredRoomId: Int,
        defaultRoomName: String
    ): Int {
        // Ensure at least one room exists for the user.
        val existing = roomRepo.getAllRoomsForUserBlocking(email)
        if (existing.isNullOrEmpty()) {
            val first = RoomCategory()
            first.name = defaultRoomName
            first.userEmail = email
            val newId = roomRepo.insertBlocking(first).toInt()
            first.id = newId
            try { FirebaseSyncManager.get().syncRoom(first) }
            catch (t: Throwable) { com.example.plantcare.CrashReporter.log(t) }
            return newId
        }

        // Prefer the last-used room if it still exists.
        if (preferredRoomId > 0) {
            for (r in existing) if (r.id == preferredRoomId) return preferredRoomId
        }
        return existing.first().id
    }

    /** Avoid duplicates like "Einblatt" / "Einblatt 1" — append a counter when needed. */
    private fun deriveNickname(plantRepo: PlantRepository, baseName: String?, email: String): String {
        val base = baseName?.takeIf { it.isNotBlank() } ?: "Pflanze"
        val existing = try {
            plantRepo.getAllUserPlantsWithNameAndUserBlocking(base, email)
        } catch (_: Throwable) { emptyList() }
        val count = (existing?.size ?: 0) + 1
        return "$base $count"
    }

    private data class QuickAddResult(
        val plantId: Int,
        val roomId: Int,
        val displayName: String
    )
}
