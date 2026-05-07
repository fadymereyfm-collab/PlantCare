package com.example.plantcare.feature.streak;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.plantcare.EmailContext;
import com.example.plantcare.feature.vacation.VacationPrefs;

import java.time.LocalDate;

/**
 * Java-freundliche Fassade für {@link StreakTracker} und {@link ChallengeRegistry}.
 *
 * Grund: Die Kotlin-Objekte nutzen funktionale Typen (lambdas mit 2 Parametern),
 * was aus Java aufrufbar ist, aber umständlich. Diese Klasse kapselt die
 * Standard-Vacation-Gap-Logik, sodass Java-Code (TodayAdapter, DailyWateringAdapter)
 * nur eine Zeile pro Done-Event braucht.
 *
 * Idempotent: Mehrfachaufrufe am selben Tag mit demselben email erhöhen die
 * Streak nicht mehrfach.
 */
public final class StreakBridge {

    private StreakBridge() {}

    /**
     * Muss nach jedem "reminder.done = true"-Commit aufgerufen werden.
     * Keine Wirkung, wenn email null/leer.
     *
     * @return Die neue Streak-Länge, oder 0 wenn nicht registriert.
     */
    public static int onReminderMarkedDone(@NonNull Context context, @Nullable String email) {
        if (email == null || email.isEmpty()) return 0;
        LocalDate today = LocalDate.now();
        int newStreak = StreakTracker.recordWateringToday(
                context,
                email,
                today,
                (lastDay, now) -> {
                    LocalDate vacStart = VacationPrefs.getStart(context, email);
                    LocalDate vacEnd = VacationPrefs.getEnd(context, email);
                    if (vacStart == null || vacEnd == null) return false;
                    return !vacEnd.isBefore(lastDay.plusDays(1))
                            && !vacStart.isAfter(now.minusDays(1));
                }
        );
        // Challenge WATER_STREAK_7 aktualisieren.
        ChallengeRegistry.updateProgress(context, email, "WATER_STREAK_7", newStreak);
        return newStreak;
    }

    /**
     * Hilfs­methode für aktuelle Streak ohne Seiteneffekt.
     */
    public static int currentStreak(@NonNull Context context, @Nullable String email) {
        if (email == null || email.isEmpty()) return 0;
        LocalDate today = LocalDate.now();
        return StreakTracker.getCurrentStreak(
                context,
                email,
                today,
                (lastDay, now) -> {
                    LocalDate vacStart = VacationPrefs.getStart(context, email);
                    LocalDate vacEnd = VacationPrefs.getEnd(context, email);
                    if (vacStart == null || vacEnd == null) return false;
                    return !vacEnd.isBefore(lastDay.plusDays(1))
                            && !vacStart.isAfter(now.minusDays(1));
                }
        );
    }

    /**
     * Gibt die E-Mail aus "prefs"/current_user_email.
     */
    @Nullable
    public static String getCurrentEmail(@NonNull Context context) {
        return EmailContext.current(context);
    }
}
