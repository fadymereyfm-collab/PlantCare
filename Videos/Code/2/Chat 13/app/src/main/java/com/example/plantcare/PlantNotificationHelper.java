package com.example.plantcare;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;

import androidx.annotation.ArrayRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Random;

/**
 * Helper class for plant care notifications.
 * Strings live in {@code res/values*\/notifications.xml} so the notification copy
 * can be translated per-locale (Per-App Language API picks the right values-* set).
 */
public final class PlantNotificationHelper {

    public static final String CHANNEL_ID = "plant_care_reminders";
    public static final int NOTIFICATION_ID_MORNING = 1001;
    public static final int NOTIFICATION_ID_EVENING = 1002;
    public static final int NOTIFICATION_ID_WELCOME_BACK = 1010;
    public static final int NOTIFICATION_ID_WEATHER_SHIFT = 1020;

    private PlantNotificationHelper() {}

    /**
     * Creates the notification channel (required for Android 8+).
     * Safe to call multiple times — the system ignores duplicates.
     */
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // IMPORTANCE_HIGH (not DEFAULT) so the reminder makes a sound and
            // peeks above the lock screen. Plant care is a periodic
            // commitment — a silent notification gets lost in the morning
            // notification scroll and the plant doesn't get watered. Users
            // who don't want sound can downgrade per-channel in Android
            // system settings; we can't go the other way (DEFAULT can't be
            // upgraded once the channel exists, so getting this wrong means
            // every existing user is permanently stuck on quiet).
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(context.getString(R.string.notif_channel_description));
            channel.enableVibration(true);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static void showMorningNotification(Context context, int pendingCount) {
        showNotification(context, NOTIFICATION_ID_MORNING, pendingCount, true);
    }

    public static void showEveningNotification(Context context, int pendingCount) {
        showNotification(context, NOTIFICATION_ID_EVENING, pendingCount, false);
    }

    /**
     * F8 — single summary notification posted by {@link WeatherAdjustmentWorker}
     * after it shifts at least one reminder. Lets the user know why their watering
     * schedule changed instead of having the dates silently move (Functional
     * Report §2.3 final paragraph).
     *
     * @param shiftedCount how many reminders were moved in this worker run
     * @param dayShift     positive = postponed (rain/cold); negative = advanced (heat)
     * @param weatherDescription short human-readable weather, e.g. "leichter Regen"
     */
    public static void showWeatherShiftNotification(
            Context context, int shiftedCount, int dayShift, String weatherDescription) {
        if (shiftedCount <= 0 || dayShift == 0) return;

        createNotificationChannel(context);

        Resources res = context.getResources();
        int absDays = Math.abs(dayShift);
        String title = res.getString(R.string.notif_weather_shift_title);
        int bodyRes = (dayShift > 0)
                ? R.plurals.notif_weather_shift_body_postponed
                : R.plurals.notif_weather_shift_body_advanced;
        String description = (weatherDescription == null || weatherDescription.isEmpty())
                ? res.getString(R.string.notif_weather_shift_default_reason)
                : weatherDescription;
        String body = res.getQuantityString(bodyRes, shiftedCount, shiftedCount, absDays, description);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_plant)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(context)
                    .notify(NOTIFICATION_ID_WEATHER_SHIFT, builder.build());
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS missing — log so we can see in Crashlytics
            // when users are silently losing notifications. Without the log
            // we have zero signal that the runtime permission flow failed.
            CrashReporter.INSTANCE.log(e);
        }
    }

    public static void showWelcomeBackNotification(Context context) {
        createNotificationChannel(context);

        String title = context.getString(R.string.notif_welcome_back_title);
        String body = context.getString(R.string.notif_welcome_back_body);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_plant)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(context)
                    .notify(NOTIFICATION_ID_WELCOME_BACK, builder.build());
        } catch (SecurityException e) {
            CrashReporter.INSTANCE.log(e);
        }
    }

    private static void showNotification(Context context, int notificationId, int pendingCount, boolean isMorning) {
        Random random = new Random();
        Resources res = context.getResources();
        String title;
        String body;

        if (pendingCount > 0) {
            int titlesArr = isMorning ? R.array.notif_morning_titles : R.array.notif_evening_titles;
            int bodiesArr = isMorning ? R.array.notif_morning_bodies : R.array.notif_evening_bodies;
            title = pickRandom(res, titlesArr, random);
            body = pickRandom(res, bodiesArr, random);
            body += res.getQuantityString(R.plurals.notif_pending_count, pendingCount, pendingCount);
        } else {
            title = pickRandom(res, R.array.notif_no_reminder_titles, random);
            body = pickRandom(res, R.array.notif_no_reminder_bodies, random);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_plant)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        // Mark-all-watered inline action — only meaningful when the
        // user actually has pending reminders. Otherwise the button
        // would be a no-op surprise on the cheery "no reminders today"
        // notification.
        if (pendingCount > 0) {
            Intent markIntent = new Intent(context, NotificationActionReceiver.class);
            markIntent.setAction(NotificationActionReceiver.ACTION_MARK_ALL_WATERED);
            markIntent.putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId);
            // Distinct request code per slot (morning vs evening) so
            // PendingIntent.FLAG_UPDATE_CURRENT doesn't make one notification's
            // action overwrite the other's extras.
            PendingIntent markPending = PendingIntent.getBroadcast(
                    context, notificationId, markIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.addAction(
                    R.drawable.ic_notification_plant,
                    res.getString(R.string.notif_action_mark_all_watered),
                    markPending
            );
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build());
        } catch (SecurityException e) {
            CrashReporter.INSTANCE.log(e);
        }
    }

    private static String pickRandom(Resources res, @ArrayRes int arrayId, Random random) {
        String[] arr = res.getStringArray(arrayId);
        if (arr.length == 0) return "";
        return arr[random.nextInt(arr.length)];
    }
}
