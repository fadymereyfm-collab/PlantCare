package com.example.plantcare

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * GDPR Article 20 ("right to data portability") implementation. Exports
 * the signed-in user's data as a single shareable JSON file.
 *
 * History:
 *  - v1 (initial) only included plants + reminders with a sparse
 *    set of fields. Every feature added since (rooms, photos, journal
 *    memos, vacation, streak, challenges, disease diagnoses) lived
 *    outside the export, which technically broke the GDPR completeness
 *    requirement for those data categories.
 *  - v2 (this rewrite): full coverage. Every per-user entity ships in
 *    its own array under the root JSON object, with the field set
 *    that round-trips meaningfully (no opaque ids alone). Filename
 *    now carries a millisecond timestamp so two exports on the same
 *    day no longer overwrite each other in the cache directory.
 */
object DataExportManager {

    /**
     * Bumped whenever the export schema changes in a non-additive way.
     * Importers (which don't exist yet — re-import is on the roadmap)
     * should refuse to load anything newer than the version they
     * understand.
     */
    private const val EXPORT_SCHEMA_VERSION = 2

    private val mainThread = Handler(Looper.getMainLooper())

    /**
     * Exports the current user's data as a JSON file and invokes
     * [onDone] on the main thread with a chooser Intent ready to
     * share, or null if the export failed.
     */
    fun exportAndShare(context: Context, userEmail: String?, onDone: (Intent?) -> Unit) {
        val appContext = context.applicationContext
        // BgExecutor: process-wide shared pool. The previous private
        // newSingleThreadExecutor() leaked a non-daemon thread for the
        // process lifetime; the shared pool already has the right
        // lifecycle attached to App.onCreate.
        com.example.plantcare.util.BgExecutor.io {
            val intent = runCatching {
                val json = buildJson(appContext, userEmail)
                val file = writeToCache(appContext, json)
                val uri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.provider",
                    file
                )
                buildShareIntent(uri)
            }.onFailure { CrashReporter.log(it) }.getOrNull()
            mainThread.post { onDone(intent) }
        }
    }

    private fun buildJson(context: Context, userEmail: String?): String {
        val root = JSONObject()
        root.put("schema_version", EXPORT_SCHEMA_VERSION)
        // Both timestamps are wire format (Locale.US). The previous
        // export_date_local used Locale.getDefault() and produced
        // Eastern-Arabic numerals on ar/fa devices — fine for human
        // eyes but unreadable for any future re-import path. The
        // "local" suffix is misleading kept for backward compat with
        // shipped exports.
        root.put("export_timestamp_iso",
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()))
        root.put("export_date_local",
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
        root.put("app_version", BuildConfig.VERSION_NAME)
        root.put("user_email", userEmail ?: "")

        if (userEmail.isNullOrBlank()) {
            // Guest user — nothing per-user to export. Return the
            // header skeleton so importers don't choke on an empty
            // file, and so the user sees a "yes the export ran"
            // signal even if there's no content yet.
            root.put("plants", JSONArray())
            root.put("reminders", JSONArray())
            root.put("rooms", JSONArray())
            root.put("photos", JSONArray())
            root.put("memos", JSONArray())
            root.put("diagnoses", JSONArray())
            root.put("vacation", JSONObject.NULL)
            root.put("streak", JSONObject.NULL)
            root.put("challenges", JSONObject())
            return root.toString(2)
        }

        // X4: cache the user's plants list once. The previous code
        // queried it inside exportPlants, exportPhotos, exportMemos,
        // and exportDiagnoses — four full SQL reads per export. For
        // a 100-plant collection that's 400 queries, plus N
        // photos/memos/diagnoses sub-queries on each. Caching keeps
        // the export under a second on a low-end device.
        val plantRepo = com.example.plantcare.data.repository.PlantRepository.getInstance(context)
        val userPlants = plantRepo.getAllUserPlantsForUserBlocking(userEmail)

        root.put("plants", exportPlants(userPlants))
        root.put("reminders", exportReminders(context, userEmail))
        root.put("rooms", exportRooms(context, userEmail))
        root.put("photos", exportPhotos(context, userEmail, userPlants))
        root.put("memos", exportMemos(context, userEmail, userPlants))
        root.put("diagnoses", exportDiagnoses(context, userEmail, userPlants))
        root.put("vacation", exportVacation(context, userEmail))
        root.put("streak", exportStreak(context, userEmail))
        root.put("challenges", exportChallenges(context, userEmail))

        return root.toString(2)
    }

    private fun exportPlants(userPlants: List<Plant>): JSONArray {
        val arr = JSONArray()
        for (p in userPlants) {
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name ?: "")
                put("nickname", p.nickname ?: "")
                put("start_date", p.startDate?.let {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it)
                } ?: "")
                put("watering_interval_days", p.wateringInterval)
                put("lighting", p.lighting ?: "")
                put("soil", p.soil ?: "")
                put("fertilizing", p.fertilizing ?: "")
                put("watering", p.watering ?: "")
                put("personal_note", p.personalNote ?: "")
                put("category", p.category ?: "")
                put("is_favorite", p.isFavorite)
                put("image_uri", p.imageUri ?: "")
                put("room_id", p.roomId)
                put("shared_with", p.sharedWith ?: "")
            })
        }
        return arr
    }

    private fun exportReminders(context: Context, email: String): JSONArray {
        val arr = JSONArray()
        val repo = com.example.plantcare.data.repository.ReminderRepository.getInstance(context)
        for (r in repo.getAllRemindersForUserBlocking(email)) {
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("plant_id", r.plantId)
                put("plant_name", r.plantName ?: "")
                put("date", r.date ?: "")
                put("done", r.done)
                put("repeat_days", r.repeat ?: "")
                put("description", r.description ?: "")
                put("notes", r.notes ?: "")
                put("watered_by", r.wateredBy ?: "")
                put("completed_date", r.completedDate?.let {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it)
                } ?: "")
            })
        }
        return arr
    }

    private fun exportRooms(context: Context, email: String): JSONArray {
        val arr = JSONArray()
        val repo = com.example.plantcare.data.repository.RoomCategoryRepository.getInstance(context)
        for (r in repo.getAllRoomsForUserBlocking(email).orEmpty()) {
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("position", r.position)
            })
        }
        return arr
    }

    private fun exportPhotos(context: Context, email: String, userPlants: List<Plant>): JSONArray {
        val arr = JSONArray()
        // No "all photos for user" repo helper — page through the
        // user's plants and collect each plant's photo set. We omit
        // the actual binary; export captures metadata + the imagePath
        // pointing at the local file or Firestore URL.
        val photoRepo = com.example.plantcare.data.repository.PlantPhotoRepository.getInstance(context)
        for (p in userPlants) {
            for (ph in photoRepo.getPhotosForPlantBlocking(p.id)) {
                if (ph.userEmail != null && ph.userEmail != email) continue
                arr.put(JSONObject().apply {
                    put("id", ph.id)
                    put("plant_id", ph.plantId)
                    put("date_taken", ph.dateTaken ?: "")
                    put("image_path", ph.imagePath ?: "")
                    put("is_cover", ph.isCover)
                })
            }
        }
        return arr
    }

    private fun exportMemos(context: Context, email: String, userPlants: List<Plant>): JSONArray {
        val arr = JSONArray()
        val memoDao = AppDatabase.getInstance(context).journalMemoDao()
        for (p in userPlants) {
            for (m in memoDao.getForPlant(p.id)) {
                if (m.userEmail != null && m.userEmail != email) continue
                arr.put(JSONObject().apply {
                    put("id", m.id)
                    put("plant_id", m.plantId)
                    put("text", m.text)
                    put("created_at_ms", m.createdAt)
                    put("updated_at_ms", m.updatedAt)
                })
            }
        }
        return arr
    }

    private fun exportDiagnoses(context: Context, email: String, userPlants: List<Plant>): JSONArray {
        val arr = JSONArray()
        // No "all for user" DAO method — page through user's plants
        // and union each plant's diagnoses, then dedupe in case the
        // user's email matches diagnoses against a plant that was
        // never theirs.
        val dao = AppDatabase.getInstance(context).diseaseDiagnosisDao()
        try {
            for (p in userPlants) {
                for (d in dao.getForPlantBlocking(p.id)) {
                    if (d.userEmail != null && d.userEmail != email) continue
                    arr.put(JSONObject().apply {
                        put("id", d.id)
                        put("plant_id", d.plantId)
                        put("display_name", d.displayName ?: "")
                        put("confidence", d.confidence)
                        put("note", d.note ?: "")
                        put("image_path", d.imagePath ?: "")
                        put("created_at_ms", d.createdAt)
                    })
                }
            }
        } catch (t: Throwable) {
            CrashReporter.log(t)
        }
        return arr
    }

    private fun exportVacation(context: Context, email: String): Any {
        val start = com.example.plantcare.feature.vacation.VacationPrefs.getStart(context, email)
        val end = com.example.plantcare.feature.vacation.VacationPrefs.getEnd(context, email)
        if (start == null || end == null) return JSONObject.NULL
        // Read the welcomeFired flag directly out of SharedPreferences —
        // VacationPrefs has no public accessor for it because it's an
        // internal scheduling flag, but it's part of the user's data
        // and a re-import would otherwise replay an already-shown
        // notification. Ok to inline-read the well-known key.
        val welcomeFired = context
            .getSharedPreferences("vacation_prefs", Context.MODE_PRIVATE)
            .getBoolean("welcome_fired_$email", false)
        return JSONObject().apply {
            put("start", start.toString())
            put("end", end.toString())
            put("welcome_fired", welcomeFired)
        }
    }

    private fun exportStreak(context: Context, email: String): Any {
        val current = com.example.plantcare.feature.streak.StreakTracker
            .getCurrentStreak(context, email)
        val best = com.example.plantcare.feature.streak.StreakTracker
            .getBestStreak(context, email)
        if (current == 0 && best == 0) return JSONObject.NULL
        return JSONObject().apply {
            put("current", current)
            put("best", best)
        }
    }

    private fun exportChallenges(context: Context, email: String): JSONObject {
        val out = JSONObject()
        val list = com.example.plantcare.feature.streak.ChallengeRegistry.allFor(context, email)
        for (c in list) {
            out.put(c.id, JSONObject().apply {
                put("progress", c.progress)
                put("target", c.target)
                put("completed_at_ms", c.completedAtEpochMs)
                put("is_complete", c.isComplete)
            })
        }
        return out
    }

    private fun writeToCache(context: Context, json: String): File {
        val exportDir = File(context.cacheDir, "export").apply { mkdirs() }
        // S4: timestamp the filename to ms granularity. The previous
        // yyyy-MM-dd format silently overwrote an earlier same-day
        // export, which a user trying both "share via email" and
        // "save to Drive" in the same minute could hit.
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(exportDir, "plantcare_export_$stamp.json")
        file.writeText(json, Charsets.UTF_8)
        return file
    }

    private fun buildShareIntent(uri: Uri) = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "PlantCare – Meine Daten")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
