package com.example.plantcare.data.repository

import android.content.Context
import com.example.plantcare.AppDatabase
import com.example.plantcare.data.journal.JournalEntry
import com.example.plantcare.data.journal.JournalMemo
import com.example.plantcare.data.journal.JournalSnapshot
import com.example.plantcare.data.journal.JournalSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Aggregates the three timeline sources for the Plant Journal feature
 * (Functional Report §4): completed waterings, photos, disease diagnoses.
 *
 * One coroutine round-trip per snapshot — the ViewModel fans-in here rather
 * than coordinating three flows.
 */
class PlantJournalRepository private constructor(context: Context) {

    private val db: AppDatabase = AppDatabase.getInstance(context)
    private val plantDao = db.plantDao()
    private val photoDao = db.plantPhotoDao()
    private val reminderDao = db.reminderDao()
    private val diagnosisDao = db.diseaseDiagnosisDao()
    private val roomDao = db.roomCategoryDao()
    private val memoDao = db.journalMemoDao()

    /**
     * Build the journal snapshot for [plantId]. All filtering by user, sort and
     * derived counters happen here — the ViewModel stays presentation-only.
     */
    suspend fun getJournalForPlant(plantId: Int, userEmail: String?): JournalSnapshot =
        withContext(Dispatchers.IO) {
            val plant = plantDao.findById(plantId)

            val photos = photoDao.getPhotosForPlant(plantId)
                .filter { !it.isCover }
                .filter { userEmail.isNullOrBlank() || it.userEmail == userEmail }

            val reminders = reminderDao.getRemindersForPlant(plantId)
                .filter { it.done }
                .filter { userEmail.isNullOrBlank() || it.userEmail == userEmail }

            // No suspend variant exists — fetch synchronously off the IO thread we're already on.
            val diagnoses = diagnosisDao.getForPlantSync(plantId)
                .filter { userEmail.isNullOrBlank() || it.userEmail == userEmail }

            // Dedupe: when the user accepted "save photo to plant archive" after a
            // diagnosis, the same image lives in BOTH `plant_photo` (with
            // `diagnosisId` set) AND in `disease_diagnosis`. Render only the
            // diagnosis card — it already shows the thumbnail + the disease name —
            // so the user sees one timeline event per real-world action.
            val diagnosisIds = diagnoses.map { it.id.toInt() }.toHashSet()
            val photoEntries = photos
                .filter { p ->
                    val linkedId = p.diagnosisId
                    linkedId == null || linkedId !in diagnosisIds
                }
                .mapNotNull { p ->
                    val ts = parseDateMillis(p.dateTaken) ?: return@mapNotNull null
                    JournalEntry.PhotoEntry(
                        photo = p,
                        timestamp = ts,
                        dateString = p.dateTaken ?: ""
                    )
                }
            val wateringEntries = reminders.mapNotNull { r ->
                // Prefer completedDate (millis) if set; otherwise fall back to scheduled date.
                val ts = r.completedDate?.time ?: parseDateMillis(r.date) ?: return@mapNotNull null
                val ds = r.completedDate?.let { formatDate(it.time) } ?: r.date ?: ""
                JournalEntry.WateringEvent(reminder = r, timestamp = ts, dateString = ds)
            }
            val diagnosisEntries = diagnoses.map { d ->
                JournalEntry.DiagnosisEntry(
                    diagnosis = d,
                    timestamp = d.createdAt,
                    dateString = formatDate(d.createdAt)
                )
            }

            val memos = memoDao.getForPlant(plantId)
                .filter { userEmail.isNullOrBlank() || it.userEmail == userEmail }
            val memoEntries = memos.map { m ->
                JournalEntry.MemoEntry(
                    memo = m,
                    // Sort by updatedAt so an edit bumps the memo back to the top.
                    timestamp = m.updatedAt,
                    dateString = formatDate(m.updatedAt)
                )
            }

            val merged = (photoEntries + wateringEntries + diagnosisEntries + memoEntries)
                .sortedByDescending { it.timestamp }

            val nickname = plant?.nickname?.takeIf { it.isNotBlank() }
            val displayName = nickname ?: plant?.name
            val roomName = plant?.roomId
                ?.takeIf { it > 0 }
                ?.let { rid -> runCatching { roomDao.findById(rid) }.getOrNull()?.name }
            val startMillis = plant?.startDate?.time
            val daysSinceStart = startMillis?.let {
                val diff = System.currentTimeMillis() - it
                TimeUnit.MILLISECONDS.toDays(diff).coerceAtLeast(0)
            }
            val lastWateringDate = wateringEntries.maxByOrNull { it.timestamp }?.dateString

            val summary = JournalSummary(
                plantId = plantId,
                plantDisplayName = displayName,
                roomName = roomName,
                daysSinceStart = daysSinceStart,
                lastWateringDate = lastWateringDate,
                completedWateringCount = wateringEntries.size,
                photoCount = photoEntries.size,
                diagnosisCount = diagnosisEntries.size,
                memoCount = memoEntries.size
            )

            JournalSnapshot(entries = merged, summary = summary)
        }

    /**
     * Sprint-2 / F15: blocking variant of [getJournalForPlant] for the PDF
     * builder, which runs on a plain `BgExecutor.io` Java thread (no
     * coroutine context). The work is identical — three table reads + an
     * in-memory merge — just expressed as a plain `fun` so Java callers
     * don't need to wrap it in `runBlocking`. Caller is responsible for
     * being on a background thread; Room itself enforces this.
     */
    fun getJournalForPlantBlocking(plantId: Int, userEmail: String?): JournalSnapshot {
        val plant = plantDao.findById(plantId)

        val photos = photoDao.getPhotosForPlant(plantId)
            .filter { !it.isCover }
            .filter { userEmail.isNullOrBlank() || it.userEmail == userEmail }

        val reminders = reminderDao.getRemindersForPlant(plantId)
            .filter { it.done }
            .filter { userEmail.isNullOrBlank() || it.userEmail == userEmail }

        val diagnoses = diagnosisDao.getForPlantBlocking(plantId)
            .filter { userEmail.isNullOrBlank() || it.userEmail == userEmail }

        val diagnosisIds = diagnoses.map { it.id.toInt() }.toHashSet()
        val photoEntries = photos
            .filter { p ->
                val linkedId = p.diagnosisId
                linkedId == null || linkedId !in diagnosisIds
            }
            .mapNotNull { p ->
                val ts = parseDateMillis(p.dateTaken) ?: return@mapNotNull null
                JournalEntry.PhotoEntry(p, ts, p.dateTaken ?: "")
            }
        val wateringEntries = reminders.mapNotNull { r ->
            val ts = r.completedDate?.time ?: parseDateMillis(r.date) ?: return@mapNotNull null
            val ds = r.completedDate?.let { formatDate(it.time) } ?: r.date ?: ""
            JournalEntry.WateringEvent(r, ts, ds)
        }
        val diagnosisEntries = diagnoses.map {
            JournalEntry.DiagnosisEntry(it, it.createdAt, formatDate(it.createdAt))
        }
        val memos = memoDao.getForPlant(plantId)
            .filter { userEmail.isNullOrBlank() || it.userEmail == userEmail }
        val memoEntries = memos.map {
            JournalEntry.MemoEntry(it, it.updatedAt, formatDate(it.updatedAt))
        }

        val merged = (photoEntries + wateringEntries + diagnosisEntries + memoEntries)
            .sortedByDescending { it.timestamp }

        val nickname = plant?.nickname?.takeIf { it.isNotBlank() }
        val displayName = nickname ?: plant?.name
        val roomName = plant?.roomId
            ?.takeIf { it > 0 }
            ?.let { rid -> runCatching { roomDao.findById(rid) }.getOrNull()?.name }
        val startMillis = plant?.startDate?.time
        val daysSinceStart = startMillis?.let {
            val diff = System.currentTimeMillis() - it
            TimeUnit.MILLISECONDS.toDays(diff).coerceAtLeast(0)
        }
        val lastWateringDate = wateringEntries.maxByOrNull { it.timestamp }?.dateString

        val summary = JournalSummary(
            plantId = plantId,
            plantDisplayName = displayName,
            roomName = roomName,
            daysSinceStart = daysSinceStart,
            lastWateringDate = lastWateringDate,
            completedWateringCount = wateringEntries.size,
            photoCount = photoEntries.size,
            diagnosisCount = diagnosisEntries.size,
            memoCount = memoEntries.size
        )

        return JournalSnapshot(merged, summary)
    }

    /**
     * Persist a brand-new memo. Returns the assigned row id so the caller can
     * select it in the timeline if desired.
     */
    suspend fun addMemo(plantId: Int, userEmail: String?, text: String): Long =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val memo = JournalMemo(
                plantId = plantId,
                userEmail = userEmail,
                text = text.trim(),
                createdAt = now,
                updatedAt = now
            )
            val newId = memoDao.insert(memo)
            // Mirror to Firestore so the memo survives reinstall + multi-device.
            // Best-effort: a sync failure must not roll back the local insert —
            // the user expects "I tapped save → it's saved" even when offline.
            try {
                com.example.plantcare.FirebaseSyncManager.get().syncJournalMemo(
                    memo.copy(id = newId.toInt())
                )
            } catch (t: Throwable) {
                com.example.plantcare.CrashReporter.log(t)
            }
            newId
        }

    /**
     * Update an existing memo's body. Bumps `updatedAt` so the edited memo
     * floats back to the top of the timeline. Returns false if the memo no
     * longer exists (the user could have deleted it from another surface
     * between long-press and confirm) — caller can decide whether to surface
     * a Toast or silently no-op.
     */
    suspend fun updateMemo(memoId: Int, newText: String): Boolean =
        withContext(Dispatchers.IO) {
            val existing = memoDao.findById(memoId) ?: return@withContext false
            val updated = existing.copy(
                text = newText.trim(),
                updatedAt = System.currentTimeMillis()
            )
            memoDao.update(updated)
            try {
                com.example.plantcare.FirebaseSyncManager.get().syncJournalMemo(updated)
            } catch (t: Throwable) {
                com.example.plantcare.CrashReporter.log(t)
            }
            true
        }

    /**
     * Delete a memo by id. Hard delete — no soft-delete column on
     * `journal_memo` because memos are user-authored content and the
     * affordance to recover one would require a "trash" UI we don't have.
     */
    suspend fun deleteMemo(memoId: Int) =
        withContext(Dispatchers.IO) {
            memoDao.deleteById(memoId)
            try {
                com.example.plantcare.FirebaseSyncManager.get().deleteJournalMemo(memoId)
            } catch (t: Throwable) {
                com.example.plantcare.CrashReporter.log(t)
            }
        }

    private fun parseDateMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return runCatching { ISO_FORMAT.get()!!.parse(iso)?.time }.getOrNull()
    }

    private fun formatDate(millis: Long): String =
        ISO_FORMAT.get()!!.format(java.util.Date(millis))

    companion object {
        @Volatile
        private var INSTANCE: PlantJournalRepository? = null

        // SimpleDateFormat is not thread-safe — give each calling thread its own copy.
        private val ISO_FORMAT: ThreadLocal<SimpleDateFormat> = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                    timeZone = TimeZone.getDefault()
                }
        }

        fun getInstance(context: Context): PlantJournalRepository {
            // #5 fix: inner recheck.
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlantJournalRepository(context.applicationContext)
                    .also { INSTANCE = it }
            }
        }
    }
}
