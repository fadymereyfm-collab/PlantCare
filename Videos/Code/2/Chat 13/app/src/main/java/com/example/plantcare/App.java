package com.example.plantcare;

import android.app.Application;
import android.content.SharedPreferences;

import com.example.plantcare.billing.BillingManager;
import com.google.android.gms.ads.MobileAds;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class App extends Application {

    private static final String WORK_TAG = "plant_reminder_work";
    private static final String WEATHER_WORK_TAG = "weather_adjustment_work";
    private static final String TOPUP_WORK_TAG = "reminder_topup_work";

    @Override
    public void onCreate() {
        super.onCreate();
        SecurePrefsHelper.INSTANCE.migrateIfNeeded(this);
        // W1: register the application context with DataChangeNotifier so
        // the singleton can refresh the home-screen widget on every
        // notifyChange() — without this the widget shows stale data
        // until Android's own periodic widget update fires (rare).
        DataChangeNotifier.setApplicationContext(this);
        ConsentManager.INSTANCE.applyStoredConsent(this);
        applySavedTheme();
        MobileAds.initialize(this, initializationStatus -> {});

        // Create notification channel (safe to call multiple times)
        PlantNotificationHelper.createNotificationChannel(this);

        // Schedule periodic reminder checks
        scheduleReminderWorker();

        // Schedule weather-based reminder adjustments
        scheduleWeatherWorker();

        // Roll the auto-reminder generator window forward daily so plants
        // added months ago don't run out of upcoming reminders. Idempotent
        // (skips dates that already exist) so it's cheap when up-to-date.
        scheduleReminderTopUpWorker();

        // Connect to Google Play Billing once at app start (Pro status refresh)
        connectBilling();
    }

    /** Kicks off BillingManager.connect() in a background coroutine; refreshes Pro from Play. */
    private void connectBilling() {
        BillingManager.getInstance(this).connectAsync();
    }

    private void applySavedTheme() {
        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String mode = prefs.getString("theme_mode", "system");
        int night;
        switch (mode) {
            case "light": night = AppCompatDelegate.MODE_NIGHT_NO; break;
            case "dark":  night = AppCompatDelegate.MODE_NIGHT_YES; break;
            default:      night = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(night);
    }

    /**
     * Schedules a periodic WorkManager job that runs roughly every 6 hours.
     * The Worker internally checks the time-of-day to send at most
     * 2 notifications per day (morning window 7-11 AM, evening window 5-9 PM).
     *
     * KEEP_EXISTING ensures we don't duplicate if the app restarts.
     */
    private void scheduleReminderWorker() {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                PlantReminderWorker.class,
                6, TimeUnit.HOURS   // run roughly every 6 hours
        ).build();

        // UPDATE (not KEEP) so a new app version's worker config — period
        // change, added constraints, etc. — actually reaches users who
        // already have the worker enqueued. KEEP would silently lock them
        // on whatever the very first install scheduled, forever. UPDATE
        // preserves run history so we don't trigger an immediate fire
        // storm. Same fix as the weather worker (F11.2 audit).
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
        );
    }

    /**
     * Schedules a periodic weather check that runs roughly every 12 hours.
     * The Worker fetches current weather via OpenWeatherMap and adjusts
     * upcoming watering reminders (postpone if rain, advance if heat).
     * Also caches the latest weather tip for the main screen UI.
     */
    private void scheduleWeatherWorker() {
        // CONNECTED constraint: the worker hits OpenWeather API. Without
        // this constraint WorkManager will run the worker even on airplane
        // mode, producing an UnknownHostException → Result.retry() → backoff.
        // Each retry burns CPU + battery for nothing. Gating on network
        // means WorkManager only schedules execution while online.
        androidx.work.Constraints constraints = new androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest weatherRequest = new PeriodicWorkRequest.Builder(
                WeatherAdjustmentWorker.class,
                12, TimeUnit.HOURS
        ).setConstraints(constraints).build();

        // UPDATE (not KEEP) so the new CONNECTED constraint actually
        // reaches users who already have the worker enqueued from a prior
        // app version — KEEP would leave them on the old constraint-free
        // schedule indefinitely, which is the exact battery drain this
        // change is meant to fix. UPDATE preserves the worker's run
        // history (no immediate re-execution) and just replaces its
        // parameters. WorkManager 2.9 supports UPDATE.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                WEATHER_WORK_TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                weatherRequest
        );
    }

    /**
     * Daily-cadence rolling top-up of the 180-day reminder window.
     * ReminderUtils.generateReminders only seeds the next 180 days of
     * dates when a plant is created, so without this worker a plant added
     * 5 months ago has 0 future reminders left despite being on a
     * perfectly healthy 14-day cycle.
     */
    private void scheduleReminderTopUpWorker() {
        PeriodicWorkRequest topUpRequest = new PeriodicWorkRequest.Builder(
                com.example.plantcare.feature.reminder.ReminderTopUpWorker.class,
                1, TimeUnit.DAYS
        ).build();

        // UPDATE for the same reason as the reminder + weather workers —
        // KEEP would lock existing users on the first install's config.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                TOPUP_WORK_TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                topUpRequest
        );
    }
}
