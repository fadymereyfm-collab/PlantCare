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

    private PlantNotificationHelper() {}

    /**
     * Creates the notification channel (required for Android 8+).
     * Safe to call multiple times — the system ignores duplicates.
     */
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
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
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(context)
                    .notify(NOTIFICATION_ID_WELCOME_BACK, builder.build());
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS-Permission fehlt — still ignorieren.
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
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build());
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS permission not granted on Android 13+
        }
    }

    private static String pickRandom(Resources res, @ArrayRes int arrayId, Random random) {
        String[] arr = res.getStringArray(arrayId);
        if (arr.length == 0) return "";
        return arr[random.nextInt(arr.length)];
    }
}
