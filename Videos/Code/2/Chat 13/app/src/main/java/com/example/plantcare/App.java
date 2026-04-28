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

    @Override
    public void onCreate() {
        super.onCreate();
        SecurePrefsHelper.INSTANCE.migrateIfNeeded(this);
        ConsentManager.INSTANCE.applyStoredConsent(this);
        applySavedTheme();
        MobileAds.initialize(this, initializationStatus -> {});

        // Create notification channel (safe to call multiple times)
        PlantNotificationHelper.createNotificationChannel(this);

        // Schedule periodic reminder checks
        scheduleReminderWorker();

        // Schedule weather-based reminder adjustments
        scheduleWeatherWorker();

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

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
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
        PeriodicWorkRequest weatherRequest = new PeriodicWorkRequest.Builder(
                WeatherAdjustmentWorker.class,
                12, TimeUnit.HOURS
        ).build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                WEATHER_WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                weatherRequest
        );
    }
}
