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
 * - Internally checks the hour to decide morning vs evening window.
 * - Window starts are user-configurable via Settings (Wave 1 — closes
 *   DEFERRED #6). Each window spans the chosen start hour + 4 hours so
 *   the 6-hourly Worker schedule reliably hits each window once a day.
 *   Defaults preserve the historical 7-11 / 17-21 behaviour.
 * - Tracks which slot (morning/evening) was already sent today to avoid
 *   duplicates.
 */
public class PlantReminderWorker extends Worker {

    private static final String PREFS_NAME = "plant_reminder_prefs";
    private static final String KEY_LAST_MORNING = "last_morning_date";
    private static final String KEY_LAST_EVENING = "last_evening_date";

    // Wave 1: user-configurable window start hours (read from app prefs,
    // not the Worker's own pref file — matches where SettingsDialogFragment
    // writes them).
    private static final String APP_PREFS_NAME = "prefs";
    public  static final String KEY_NOTIF_MORNING_HOUR = "notif_morning_hour";
    public  static final String KEY_NOTIF_EVENING_HOUR = "notif_evening_hour";
    public  static final int    DEFAULT_MORNING_HOUR = 7;
    public  static final int    DEFAULT_EVENING_HOUR = 17;
    private static final int    WINDOW_LENGTH_HOURS  = 4;

    public PlantReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences appPrefs = context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE);

        // Clamp to safe ranges so a corrupted pref can never make the windows
        // overlap or wrap past midnight.
        int morningStart = clampHour(appPrefs.getInt(KEY_NOTIF_MORNING_HOUR, DEFAULT_MORNING_HOUR), 5, 12);
        int eveningStart = clampHour(appPrefs.getInt(KEY_NOTIF_EVENING_HOUR, DEFAULT_EVENING_HOUR), 14, 22);

        // Wave 2 — per-type notification gating. The schema doesn't track
        // reminder type yet (all rows are "watering"), so for now the toggles
        // collapse to: if the user disabled EVERY plant-care category, we
        // suppress the daily summary completely. The weather toggle is
        // tracked separately because it gates the welcome-back-from-vacation
        // notification (which only fires the day before vacation ends).
        boolean anyTypeEnabled =
                appPrefs.getBoolean("notif_type_water", true)
             || appPrefs.getBoolean("notif_type_fertilize", true)
             || appPrefs.getBoolean("notif_type_mist", true)
             || appPrefs.getBoolean("notif_type_repot", true);
        boolean weatherTypeEnabled = appPrefs.getBoolean("notif_type_weather", true);

        if (!anyTypeEnabled && !weatherTypeEnabled) {
            // User opted out of every category — respect it silently.
            return Result.success();
        }

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

        // Determine which notification slot applies (4-hour windows centered
        // on the user-chosen start hour).
        boolean isMorningWindow = (hour >= morningStart && hour < morningStart + WINDOW_LENGTH_HOURS);
        boolean isEveningWindow = (hour >= eveningStart && hour < eveningStart + WINDOW_LENGTH_HOURS);

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
            // Welcome-back is treated as a weather/conditions notification —
            // gate by the user's weather toggle.
            if (weatherTypeEnabled
                    && VacationPrefs.shouldFireWelcomeBackNotice(context, userEmail, today)) {
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

        // Wave 2 — schema v15: filter by per-type pref. Reminders created
        // before v15 carry type=NULL, which we treat as the historical
        // default ("water") so a user upgrading sees identical behaviour
        // until they explicitly disable it.
        boolean waterOn = appPrefs.getBoolean("notif_type_water", true);
        boolean fertOn  = appPrefs.getBoolean("notif_type_fertilize", true);
        boolean mistOn  = appPrefs.getBoolean("notif_type_mist", true);
        boolean repotOn = appPrefs.getBoolean("notif_type_repot", true);

        if (userEmail != null && !userEmail.isEmpty()) {
            try {
                List<WateringReminder> reminders = com.example.plantcare.data.repository
                        .ReminderRepository.getInstance(context)
                        .getTodayAndOverdueRemindersForUserBlocking(todayStr, userEmail);
                if (reminders != null) {
                    for (WateringReminder r : reminders) {
                        String t = r.type;
                        boolean keep;
                        if (t == null || "water".equalsIgnoreCase(t)) keep = waterOn;
                        else if ("fertilize".equalsIgnoreCase(t)) keep = fertOn;
                        else if ("mist".equalsIgnoreCase(t)) keep = mistOn;
                        else if ("repot".equalsIgnoreCase(t)) keep = repotOn;
                        else keep = waterOn; // unknown legacy types → bucket with water
                        if (keep) pendingCount++;
                    }
                }
            } catch (Exception e) {
                // Database error — still show a generic notification
                pendingCount = 0;
            }
        }

        // The morning / evening summary is the consolidated reminder
        // notification across all plant-care types. If the user opted out
        // of every plant-care type, suppress it (the welcome-back-from-
        // vacation path above already returned its own Result earlier and
        // doesn't reach here).
        if (!anyTypeEnabled) {
            return Result.success();
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

    private static int clampHour(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
