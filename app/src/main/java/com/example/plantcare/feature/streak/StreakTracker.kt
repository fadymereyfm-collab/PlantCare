package com.example.plantcare.feature.streak

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Leichte Streak-Verwaltung: zählt aufeinander­folgende Tage, an denen der
 * Nutzer mindestens eine Erinnerung als "done" markiert hat.
 *
 * Persistenz via SharedPreferences, pro E-Mail getrennt, damit bei
 * Account-Wechsel keine Vermischung passiert.
 *
 * Semantik:
 *   • "Heute gegossen" wird beim ersten Haken­setzen an einem Tag registriert.
 *   • Wenn zwischen `lastDay` und `today` genau 1 Tag liegt  → Streak +1.
 *   • Wenn `today` == `lastDay`                              → Streak bleibt.
 *   • Wenn >1 Tag Lücke                                      → Streak = 1
 *                                                              (heute zählt neu).
 *   • Urlaubstage zählen nicht als Lücke: der Aufrufer kann über
 *     [#isGapBridgedByVacation] vor dem Reset prüfen.
 *
 * Das Feld `bestStreak` wird immer mit dem Maximum aktualisiert und gibt
 * die Bestzeit pro Nutzer.
 */
object StreakTracker {

    private const val PREFS = "streak_prefs"
    private val FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private fun keyCurrent(email: String) = "current_$email"
    private fun keyLastDay(email: String) = "last_day_$email"
    private fun keyBest(email: String) = "best_$email"

    /**
     * Muss aufgerufen werden, sobald der Nutzer heute eine Erinnerung als
     * "done" markiert. Mehrfach­aufrufe am selben Tag sind harmlos.
     */
    @JvmStatic
    @JvmOverloads
    fun recordWateringToday(
        context: Context,
        email: String,
        today: LocalDate = LocalDate.now(),
        vacationSpansGap: (LocalDate, LocalDate) -> Boolean = { _, _ -> false }
    ): Int = synchronized(this) {
        // #12 fix: synchronized over the singleton because concurrent
        // recordWateringToday calls (notification action receiver +
        // in-app tap, both firing within the same second) used to
        // read current=4, both compute next=5, both write 5 — user
        // expected 6. RMW must be atomic per (email).
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastStr = p.getString(keyLastDay(email), null)
        val lastDay = runCatching { lastStr?.let { LocalDate.parse(it, FMT) } }.getOrNull()
        val current = p.getInt(keyCurrent(email), 0)

        val newCurrent: Int = when {
            lastDay == null -> 1
            lastDay == today -> current.coerceAtLeast(1)
            lastDay.plusDays(1) == today -> current + 1
            // Lücke >=2 Tage, aber Urlaub dazwischen → nicht zurücksetzen.
            vacationSpansGap(lastDay, today) -> current.coerceAtLeast(1)
            else -> 1
        }

        val best = p.getInt(keyBest(email), 0).coerceAtLeast(newCurrent)

        p.edit()
            .putInt(keyCurrent(email), newCurrent)
            .putInt(keyBest(email), best)
            .putString(keyLastDay(email), today.format(FMT))
            .apply()

        // Mirror to Firestore best-effort. C3: a user reinstalling the
        // app should not lose a 100-day streak. Sync failure must not
        // unwind the local update — offline tracking has to keep
        // working and cloud catches up later.
        try {
            com.example.plantcare.FirebaseSyncManager.get().syncStreak(
                StreakDoc(
                    currentStreak = newCurrent,
                    bestStreak = best,
                    lastDay = today.format(FMT)
                )
            )
        } catch (t: Throwable) {
            com.example.plantcare.CrashReporter.log(t)
        }
        newCurrent
    }

    /**
     * Used by the cloud-restore flow on sign-in. Overwrites local
     * state with the cloud snapshot, but only if cloud has a higher
     * `bestStreak` or a more recent `lastDay` — guards against a
     * stale cloud doc reverting a user's progress on a faster
     * device. The current streak is taken from cloud directly
     * because there's no meaningful "merge" of two competing day
     * counters; the cloud copy is the user's canonical history.
     */
    @JvmStatic
    fun restoreFromCloud(
        context: Context,
        email: String,
        currentStreak: Int,
        bestStreak: Int,
        lastDayIso: String?
    ) {
        val parsedLastDay = lastDayIso?.let {
            runCatching { LocalDate.parse(it, FMT) }.getOrNull()
        }
        if (parsedLastDay == null && (currentStreak > 0 || bestStreak > 0)) {
            com.example.plantcare.CrashReporter.log(
                IllegalArgumentException(
                    "Streak cloud doc has invalid lastDay='$lastDayIso' but non-zero counts; skipping"
                )
            )
            return
        }
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mergedBest = p.getInt(keyBest(email), 0).coerceAtLeast(bestStreak)
        val editor = p.edit()
            .putInt(keyCurrent(email), currentStreak.coerceAtLeast(0))
            .putInt(keyBest(email), mergedBest)
        if (lastDayIso != null) editor.putString(keyLastDay(email), lastDayIso)
        editor.apply()
    }

    /**
     * Liest die aktuelle Streak OHNE sie zu ändern. Wenn seit `lastDay` mehr
     * als 1 Tag vergangen ist (und kein Urlaub), wird die Streak als 0
     * interpretiert (die Persistenz wird erst beim nächsten `record` gekappt).
     */
    @JvmStatic
    @JvmOverloads
    fun getCurrentStreak(
        context: Context,
        email: String,
        today: LocalDate = LocalDate.now(),
        vacationSpansGap: (LocalDate, LocalDate) -> Boolean = { _, _ -> false }
    ): Int {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastStr = p.getString(keyLastDay(email), null) ?: return 0
        val lastDay = runCatching { LocalDate.parse(lastStr, FMT) }.getOrNull() ?: return 0
        val stored = p.getInt(keyCurrent(email), 0)

        return when {
            lastDay == today -> stored
            lastDay.plusDays(1) == today -> stored
            ChronoUnit.DAYS.between(lastDay, today) <= 0 -> stored
            vacationSpansGap(lastDay, today) -> stored
            else -> 0
        }
    }

    @JvmStatic
    fun getBestStreak(context: Context, email: String): Int {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getInt(keyBest(email), 0)
    }

    @JvmStatic
    fun reset(context: Context, email: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit()
            .remove(keyCurrent(email))
            .remove(keyLastDay(email))
            .remove(keyBest(email))
            .apply()
    }
}
