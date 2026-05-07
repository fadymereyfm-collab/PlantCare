package com.example.plantcare;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.plantcare.feature.vacation.VacationPrefs;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * WorkManager Worker that checks for pending reminders and shows
 * Duolingo-style funny notifications — max 2 per day (morning + evening).
 *
 * Scheduling:
 * - Runs periodically (every ~6 hours).
 * - Internally checks the hour to decide if it's morning (7-11) or evening (17-21).
 * - Tracks which slot (morning/evening) was already sent today to avoid duplicates.
 */
public class PlantReminderWorker extends Worker {

    private static final String PREFS_NAME = "plant_reminder_prefs";
    private static final String KEY_LAST_MORNING = "last_morning_date";
    private static final String KEY_LAST_EVENING = "last_evening_date";

    public PlantReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        // Locale.US (not getDefault) for the wire format — on Arabic/
        // Persian devices Locale.getDefault() emits yyyy-MM-dd with
        // Eastern-Arabic digits (٢٠٢٦-٠٥-٠٦), and the SQL query
        // `WHERE date <= today` never matches the Latin-digit rows the
        // app writes on every other locale. Result: the user gets an
        // empty pendingCount, sees the cheery "no reminders today"
        // notification copy, and forgets to water their plants. The
        // wire format is locale-invariant by design — only display
        // strings should track the user's locale.
        String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        // Determine which notification slot applies
        boolean isMorningWindow = (hour >= 7 && hour <= 11);
        boolean isEveningWindow = (hour >= 17 && hour <= 21);

        if (!isMorningWindow && !isEveningWindow) {
            // Outside notification windows — skip silently
            return Result.success();
        }

        // Check if we already sent this slot today
        if (isMorningWindow) {
            String lastMorning = prefs.getString(KEY_LAST_MORNING, "");
            if (todayStr.equals(lastMorning)) {
                return Result.success(); // Already sent morning notification today
            }
        } else {
            String lastEvening = prefs.getString(KEY_LAST_EVENING, "");
            if (todayStr.equals(lastEvening)) {
                return Result.success(); // Already sent evening notification today
            }
        }

        String userEmail = EmailContext.current(context);

        // ── Urlaubsmodus: während aktiver Phase unterdrücken wir die Push-
        //   Benachrichtigung komplett. Am Tag VOR Urlaubs­ende feuert einmal
        //   eine "Willkommen zurück"-Vorwarnung, damit der Nutzer nicht kalt
        //   erwischt wird. Die Erinnerungen selbst bleiben in der DB — nur
        //   die Benachrichtigung wird stummgeschaltet.
        if (userEmail != null && !userEmail.isEmpty()) {
            LocalDate today = LocalDate.now();
            if (VacationPrefs.shouldFireWelcomeBackNotice(context, userEmail, today)) {
                PlantNotificationHelper.showWelcomeBackNotification(context);
                // Slot markieren, damit wir später heute nicht auch noch die
                // normale Routine­nachricht schicken.
                if (isMorningWindow) {
                    prefs.edit().putString(KEY_LAST_MORNING, todayStr).apply();
                } else {
                    prefs.edit().putString(KEY_LAST_EVENING, todayStr).apply();
                }
                return Result.success();
            }
            if (VacationPrefs.isVacationActive(context, userEmail, today)) {
                // Still aussteigen — kein Push während Urlaub.
                return Result.success();
            }
        }

        int pendingCount = 0;

        if (userEmail != null && !userEmail.isEmpty()) {
            try {
                List<WateringReminder> reminders = com.example.plantcare.data.repository
                        .ReminderRepository.getInstance(context)
                        .getTodayAndOverdueRemindersForUserBlocking(todayStr, userEmail);
                pendingCount = (reminders != null) ? reminders.size() : 0;
            } catch (Exception e) {
                // Database error — still show a generic notification
                pendingCount = 0;
            }
        }

        // Show the notification
        if (isMorningWindow) {
            PlantNotificationHelper.showMorningNotification(context, pendingCount);
            prefs.edit().putString(KEY_LAST_MORNING, todayStr).apply();
        } else {
            PlantNotificationHelper.showEveningNotification(context, pendingCount);
            prefs.edit().putString(KEY_LAST_EVENING, todayStr).apply();
        }

        return Result.success();
    }
}
