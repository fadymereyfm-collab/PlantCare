package com.example.plantcare.feature.vacation

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Leichter SharedPreferences-Wrapper für den Urlaubsmodus.
 *
 * Verhalten im aktiven Fenster (heute zwischen startDate und endDate inklusiv):
 *   • PlantReminderWorker springt still ab (keine Push-Benachrichtigung).
 *   • TodayFragment zeigt einen "Ich bin im Urlaub"-Banner statt der Liste.
 *   • Am Tag VOR endDate (oder am endDate selbst, falls das schon vorbei ist)
 *     feuert der Worker genau eine "Willkommen zurück"-Vorwarnung.
 *
 * Keine Reminder werden gelöscht — nur die Benachrichtigung wird unterdrückt.
 * So bleiben historische Gieß­daten konsistent, sobald der Urlaub endet.
 *
 * Daten werden pro Nutzer getrennt gespeichert (Key enthält email), damit
 * beim Account-Wechsel nichts vermischt wird.
 */
object VacationPrefs {

    private const val PREFS_NAME = "vacation_prefs"
    private const val KEY_START = "start_"
    private const val KEY_END = "end_"
    private const val KEY_WELCOME_FIRED = "welcome_fired_"
    private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @JvmStatic
    fun setVacation(context: Context, email: String, start: LocalDate, end: LocalDate) {
        prefs(context).edit()
            .putString(KEY_START + email, start.format(FORMATTER))
            .putString(KEY_END + email, end.format(FORMATTER))
            .remove(KEY_WELCOME_FIRED + email)
            .apply()
        // Mirror to Firestore best-effort. A sync failure must not
        // unwind the local save — offline scheduling has to keep
        // working. Cloud catches up the next time we have signal.
        try {
            com.example.plantcare.FirebaseSyncManager.get().syncVacation(
                VacationDoc(
                    start = start.format(FORMATTER),
                    end = end.format(FORMATTER),
                    welcomeFired = false
                )
            )
        } catch (t: Throwable) {
            com.example.plantcare.CrashReporter.log(t)
        }
    }

    @JvmStatic
    fun clearVacation(context: Context, email: String) {
        prefs(context).edit()
            .remove(KEY_START + email)
            .remove(KEY_END + email)
            .remove(KEY_WELCOME_FIRED + email)
            .apply()
        try {
            com.example.plantcare.FirebaseSyncManager.get().clearVacationCloud()
        } catch (t: Throwable) {
            com.example.plantcare.CrashReporter.log(t)
        }
    }

    /**
     * Local-only wipe used by the cloud-restore flow in MainActivity.
     * Skips the Firestore mirror call so we don't accidentally clear the
     * cloud doc we're about to read from. Plain [clearVacation] would
     * also delete the cloud copy — race-prone here because the import is
     * an async snapshot.
     */
    @JvmStatic
    fun clearLocalOnly(context: Context, email: String) {
        prefs(context).edit()
            .remove(KEY_START + email)
            .remove(KEY_END + email)
            .remove(KEY_WELCOME_FIRED + email)
            .apply()
    }

    /**
     * Used after a cloud import to seed local prefs without re-firing
     * the Firestore upload (which would just overwrite the doc we just
     * read with the same content). Preserves the cloud `welcomeFired`
     * flag so a fresh device doesn't replay a notification the original
     * device already showed.
     *
     * Validates the ISO strings before writing them. A corrupted cloud
     * doc (e.g. start = "" or "garbage") would otherwise be persisted
     * locally; later getStart()/getEnd() would silently return null via
     * runCatching and the user's vacation would just disappear without
     * any error path. Reject the restore entirely if either field
     * doesn't parse — the user keeps the local state they already had.
     */
    @JvmStatic
    fun restoreFromCloud(
        context: Context,
        email: String,
        startIso: String,
        endIso: String,
        welcomeFired: Boolean
    ) {
        val parsedStart = runCatching { LocalDate.parse(startIso, FORMATTER) }.getOrNull()
        val parsedEnd = runCatching { LocalDate.parse(endIso, FORMATTER) }.getOrNull()
        if (parsedStart == null || parsedEnd == null) {
            com.example.plantcare.CrashReporter.log(
                IllegalArgumentException(
                    "Vacation cloud doc has invalid dates; skipping restore (start='$startIso', end='$endIso')"
                )
            )
            return
        }
        // Also reject ranges where end is before start — that would
        // mean isVacationActive returns false for every conceivable
        // date and the import would silently no-op.
        if (parsedEnd.isBefore(parsedStart)) {
            com.example.plantcare.CrashReporter.log(
                IllegalArgumentException(
                    "Vacation cloud doc has end before start; skipping ($startIso → $endIso)"
                )
            )
            return
        }
        prefs(context).edit()
            .putString(KEY_START + email, startIso)
            .putString(KEY_END + email, endIso)
            .putBoolean(KEY_WELCOME_FIRED + email, welcomeFired)
            .apply()
    }

    @JvmStatic
    fun getStart(context: Context, email: String): LocalDate? {
        val s = prefs(context).getString(KEY_START + email, null) ?: return null
        return runCatching { LocalDate.parse(s, FORMATTER) }.getOrNull()
    }

    @JvmStatic
    fun getEnd(context: Context, email: String): LocalDate? {
        val s = prefs(context).getString(KEY_END + email, null) ?: return null
        return runCatching { LocalDate.parse(s, FORMATTER) }.getOrNull()
    }

    /** true, wenn heute innerhalb [start, end] liegt (beide inklusiv). */
    @JvmStatic
    fun isVacationActive(context: Context, email: String, today: LocalDate = LocalDate.now()): Boolean {
        val start = getStart(context, email) ?: return false
        val end = getEnd(context, email) ?: return false
        return !today.isBefore(start) && !today.isAfter(end)
    }

    /** Tage bis zur Rückkehr; negativ, wenn Urlaub schon vorbei ist. */
    @JvmStatic
    fun daysUntilReturn(context: Context, email: String, today: LocalDate = LocalDate.now()): Long {
        val end = getEnd(context, email) ?: return 0L
        return ChronoUnit.DAYS.between(today, end)
    }

    /**
     * True, wenn wir heute (genau einmal) die "Willkommen zurück"-Vorwarnung
     * senden sollen.
     *
     * Original semantics: fire only on the exact day before endDate. That's
     * brittle — WorkManager may not run the worker on that exact day
     * (battery saver, doze, user offline, app force-stopped during
     * vacation), and missing the trigger meant the notification never
     * fired at all.
     *
     * Catch-up semantics (current): fire whenever the worker first runs on
     * or after `end.minusDays(1)`, as long as the welcome-back hasn't
     * already been recorded for this vacation. The user always gets the
     * heads-up — at worst slightly late instead of never.
     *
     * The fired flag is cleared in [setVacation] when a new vacation is
     * configured, so the next vacation starts fresh.
     */
    @JvmStatic
    fun shouldFireWelcomeBackNotice(
        context: Context,
        email: String,
        today: LocalDate = LocalDate.now()
    ): Boolean {
        val end = getEnd(context, email) ?: return false
        val trigger = end.minusDays(1)
        // today < trigger → too early. today >= trigger → fire if we haven't already.
        if (today.isBefore(trigger)) return false
        val p = prefs(context)
        if (p.getBoolean(KEY_WELCOME_FIRED + email, false)) return false
        p.edit().putBoolean(KEY_WELCOME_FIRED + email, true).apply()
        // Mirror the fired flag into the cloud doc — without this, a
        // second device pulling the vacation on cloud restore would
        // see welcomeFired=false and replay the notification the
        // original device already showed.
        try {
            val start = getStart(context, email)
            if (start != null) {
                com.example.plantcare.FirebaseSyncManager.get().syncVacation(
                    VacationDoc(
                        start = start.format(FORMATTER),
                        end = end.format(FORMATTER),
                        welcomeFired = true
                    )
                )
            }
        } catch (t: Throwable) {
            com.example.plantcare.CrashReporter.log(t)
        }
        return true
    }
}
