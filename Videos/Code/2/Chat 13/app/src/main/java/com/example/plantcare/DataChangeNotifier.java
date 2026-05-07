package com.example.plantcare;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

public class DataChangeNotifier {

    public static final String ACTION_CALENDAR_DATA_CHANGED = "com.example.plantcare.ACTION_CALENDAR_DATA_CHANGED";
    public static final String ACTION_REMINDER_DATA_CHANGED = "com.example.plantcare.ACTION_REMINDER_DATA_CHANGED";

    private static final Set<Runnable> listeners = new HashSet<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Application context cached in {@link App#onCreate()} so the
     * arg-less {@link #notifyChange()} signature can also kick the
     * home-screen widget without forcing every caller to thread a
     * Context through. Volatile so cross-thread reads are safe;
     * always the application context (no Activity leak risk).
     */
    private static volatile Context appContext;

    public static void setApplicationContext(Context context) {
        if (context != null) appContext = context.getApplicationContext();
    }

    public static void addListener(Runnable listener) {
        listeners.add(listener);
        Log.d("DataChangeNotifier", "Listener added, total: " + listeners.size());
    }

    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
        Log.d("DataChangeNotifier", "Listener removed, total: " + listeners.size());
    }

    public static void notifyChange() {
        Log.d("DataChangeNotifier", "Notifying " + listeners.size() + " listeners");
        // يتم استدعاء كل المستمعين على الـ UI Thread.
        // Snapshot the listener set BEFORE iterating — a listener whose
        // run() removes itself (common in Fragment teardown helpers)
        // would otherwise mutate the HashSet during iteration and
        // throw ConcurrentModificationException. The snapshot is
        // tiny (typical 1-3 listeners) so the copy cost is noise.
        mainHandler.post(() -> {
            java.util.List<Runnable> snapshot = new java.util.ArrayList<>(listeners);
            for (Runnable r : snapshot) {
                try {
                    r.run();
                } catch (Exception e) {
                    Log.e("DataChangeNotifier", "Error notifying listener", e);
                }
            }
        });
        // W1: kick the home-screen widget too. The widget defines a
        // public updateWidget() but pre-this-fix nothing called it,
        // so the user's widget showed stale data until Android happened
        // to fire its own AppWidgetManager.ACTION_APPWIDGET_UPDATE
        // (rare; tied to the periodic config in widget XML). Best-
        // effort: a refresh failure must not stop in-app listeners.
        Context ctx = appContext;
        if (ctx != null) {
            try {
                com.example.plantcare.widget.PlantCareWidget.Companion.updateWidget(ctx);
            } catch (Throwable t) {
                CrashReporter.INSTANCE.log(t);
            }
        }
    }

    public static void notifyCalendar(Context context) {
        Intent intent = new Intent(ACTION_CALENDAR_DATA_CHANGED);
        context.sendBroadcast(intent);
    }

    public static void notifyReminderList(Context context) {
        Intent intent = new Intent(ACTION_REMINDER_DATA_CHANGED);
        context.sendBroadcast(intent);
    }
}