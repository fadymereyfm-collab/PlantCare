package com.example.plantcare.feature.streak

import android.content.Context
import com.example.plantcare.R
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Sanfte "Challenges" ohne Push-Aggressivität:
 *   • WATER_STREAK_7  — 7 Tage nacheinander gegossen
 *   • ADD_FIVE_PLANTS — 5 verschiedene Pflanzen im Konto
 *   • MONTHLY_PHOTO   — mindestens 1 Foto je Pflanze pro Monat (manuelles Ziel)
 *
 * Fortschritt wird in SharedPrefs (JSON pro Nutzer) gespeichert.
 * Bewusst ohne Room, weil:
 *   • Daten werden nur auf dieser Oberfläche konsumiert.
 *   • Migration (v7 → v8) bleibt additiv und ruhig.
 *   • Keine Abfragen nötig, nur einfaches Lesen/Schreiben.
 */
object ChallengeRegistry {

    private const val PREFS = "challenge_prefs"
    private fun key(email: String) = "challenges_$email"

    private val MONTH_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    private fun currentMonthKey(): String = LocalDate.now().format(MONTH_FMT)

    /** Challenges that reset every calendar month — completion only counts within its month. */
    private val MONTHLY_CHALLENGE_IDS = setOf("MONTHLY_PHOTO")

    data class Challenge(
        val id: String,
        val titleRes: Int,
        val target: Int,
        var progress: Int,
        var completedAtEpochMs: Long
    ) {
        val isComplete: Boolean get() = completedAtEpochMs > 0L
        val displayProgress: Int get() = progress.coerceAtMost(target)
    }

    @JvmStatic
    fun allFor(context: Context, email: String): List<Challenge> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(email), null)
        val stored: JSONObject = if (raw.isNullOrBlank()) JSONObject() else runCatching {
            JSONObject(raw)
        }.getOrDefault(JSONObject())

        return listOf(
            load(stored, "WATER_STREAK_7", R.string.challenge_water_7_days, target = 7),
            load(stored, "ADD_FIVE_PLANTS", R.string.challenge_add_5_plants, target = 5),
            load(stored, "MONTHLY_PHOTO", R.string.challenge_monthly_photo, target = 1)
        )
    }

    private fun load(stored: JSONObject, id: String, titleRes: Int, target: Int): Challenge {
        val o = stored.optJSONObject(id) ?: JSONObject()
        // Monthly challenges expire at the start of every new calendar
        // month — without this MONTHLY_PHOTO would be marked "completed
        // forever" the first time a user took a photo, and every
        // subsequent month would silently show the green check with no
        // way to retrigger the celebration. The persisted `monthKey`
        // tags WHEN the completion happened; if it's not the current
        // month, the loader returns a fresh zero-state.
        val isMonthly = id in MONTHLY_CHALLENGE_IDS
        if (isMonthly) {
            val storedMonth = o.optString("monthKey", "")
            if (storedMonth.isNotEmpty() && storedMonth != currentMonthKey()) {
                return Challenge(id, titleRes, target, 0, 0L)
            }
        }
        return Challenge(
            id = id,
            titleRes = titleRes,
            target = target,
            progress = o.optInt("progress", 0),
            completedAtEpochMs = o.optLong("completedAt", 0L)
        )
    }

    private fun writeBack(context: Context, email: String, list: List<Challenge>) {
        val month = currentMonthKey()
        val out = JSONObject()
        list.forEach { c ->
            out.put(c.id, JSONObject().apply {
                put("progress", c.progress)
                put("completedAt", c.completedAtEpochMs)
                // Stamp the month on monthly challenges so the loader can
                // detect a stale completion and reset on the 1st of the
                // next month. Non-monthly entries get the field too —
                // it's harmless and keeps the JSON shape uniform.
                if (c.id in MONTHLY_CHALLENGE_IDS) put("monthKey", month)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(email), out.toString()).apply()

        // C3: mirror to cloud. Best-effort, sync failure is logged
        // but doesn't unwind the local write — same pattern as
        // memos/vacation. The cloud doc is the canonical copy on a
        // fresh install or new device.
        try {
            val month2 = month
            com.example.plantcare.FirebaseSyncManager.get().syncChallenges(
                ChallengesDoc(
                    waterStreak7 = list.firstOrNull { it.id == "WATER_STREAK_7" }?.toDto(""),
                    addFivePlants = list.firstOrNull { it.id == "ADD_FIVE_PLANTS" }?.toDto(""),
                    monthlyPhoto = list.firstOrNull { it.id == "MONTHLY_PHOTO" }?.toDto(month2)
                )
            )
        } catch (t: Throwable) {
            com.example.plantcare.CrashReporter.log(t)
        }
    }

    private fun Challenge.toDto(monthKey: String): ChallengeProgressDto =
        ChallengeProgressDto(
            progress = progress,
            completedAtEpochMs = completedAtEpochMs,
            monthKey = monthKey
        )

    /**
     * Cloud-restore entry point used by MainActivity.importCloudDataForUser.
     * Replaces the local JSON blob with a serialised version of the
     * cloud doc. Validates the monthKey on monthly challenges — a
     * cloud doc tagged for a previous month should not "complete"
     * this month's challenge.
     */
    @JvmStatic
    fun restoreFromCloud(context: Context, email: String, doc: ChallengesDoc) {
        val out = JSONObject()
        val currentMonth = currentMonthKey()
        fun put(id: String, dto: ChallengeProgressDto?, monthly: Boolean) {
            if (dto == null) return
            // For monthly challenges, drop a stale completion before
            // restoring. "Stale" means EITHER:
            //  - monthKey is set and points to a previous month, OR
            //  - monthKey is empty AND completedAtEpochMs > 0 (an
            //    older cloud schema before the monthKey field
            //    existed; treat the un-tagged completion as stale
            //    because we can't prove it's this month, and showing
            //    a trophy that the user can't re-earn is worse than
            //    showing in-progress).
            val isStale = monthly && (
                (dto.monthKey.isNotEmpty() && dto.monthKey != currentMonth) ||
                (dto.monthKey.isEmpty() && dto.completedAtEpochMs > 0L)
            )
            val keepProgress = if (isStale) 0 else dto.progress
            val keepCompleted = if (isStale) 0L else dto.completedAtEpochMs
            out.put(id, JSONObject().apply {
                put("progress", keepProgress)
                put("completedAt", keepCompleted)
                if (monthly) put("monthKey", if (keepCompleted > 0) currentMonth else "")
            })
        }
        put("WATER_STREAK_7", doc.waterStreak7, false)
        put("ADD_FIVE_PLANTS", doc.addFivePlants, false)
        put("MONTHLY_PHOTO", doc.monthlyPhoto, true)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(email), out.toString()).apply()
    }

    /**
     * Aktualisiert eine Challenge auf den gegebenen Fortschritt. Wenn der
     * Fortschritt das Ziel erreicht und die Challenge noch nicht abgeschlossen
     * war, wird sie als complete markiert und zurückgegeben (damit der Aufrufer
     * eine Toast-Gratulation zeigen kann). Sonst null.
     */
    @JvmStatic
    fun updateProgress(context: Context, email: String, id: String, newProgress: Int): Challenge? =
        // #11 fix: synchronized over the singleton because the read-
        // modify-write of the JSON blob in SharedPreferences was racey.
        // Two concurrent callers (worker + UI tap, or notification
        // action + in-app tap) would each load the same baseline,
        // mutate independently, and one's write silently overwrote
        // the other → user finishes a challenge but the trophy never
        // appears because the racing write reset its completedAt.
        synchronized(this) {
            val list = allFor(context, email).toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx < 0) return@synchronized null
            val c = list[idx]
            val wasComplete = c.isComplete
            val capped = newProgress.coerceAtLeast(0)
            c.progress = capped
            var justCompleted: Challenge? = null
            if (!wasComplete && capped >= c.target) {
                c.completedAtEpochMs = System.currentTimeMillis()
                justCompleted = c
            }
            list[idx] = c
            writeBack(context, email, list)
            justCompleted
        }

    /** Für MONTHLY_PHOTO: setzt complete, wenn in diesem Monat schon min. 1 Foto. */
    @JvmStatic
    fun markMonthlyPhotoDone(context: Context, email: String): Challenge? =
        updateProgress(context, email, "MONTHLY_PHOTO", 1)

    /**
     * Wipes every challenge progress entry for [email]. Called from
     * logout / account-delete so the next user on the same device
     * doesn't inherit "5 plants added" or a finished MONTHLY_PHOTO.
     */
    @JvmStatic
    fun reset(context: Context, email: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(key(email))
            .apply()
    }
}
