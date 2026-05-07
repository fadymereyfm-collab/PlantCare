package com.example.plantcare;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

import com.example.plantcare.data.repository.ReminderRepository;
import com.example.plantcare.util.BgExecutor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * One-tap "Mark all watered" action handler attached to the daily reminder
 * notification. Tapping the action fires this broadcast; we mark every
 * today-or-overdue reminder for the signed-in user as done, post a
 * widget refresh, and dismiss the notification — without the user
 * having to open the app at all.
 *
 * This is what every professional plant-care app (Planta, Greg, Vera)
 * has had for years and what F10/F11 callers explicitly called out as
 * the biggest UX gap in our notification.
 */
public class NotificationActionReceiver extends BroadcastReceiver {

    public static final String ACTION_MARK_ALL_WATERED =
            "com.example.plantcare.action.MARK_ALL_WATERED";

    public static final String EXTRA_NOTIFICATION_ID = "notification_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_MARK_ALL_WATERED.equals(intent.getAction())) return;
        Context appCtx = context.getApplicationContext();
        final int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);

        // Dismiss the notification immediately so the tap feels responsive
        // — the DB write happens off-thread below. If the write fails,
        // the next worker run will resurface it.
        if (notificationId > 0) {
            try {
                NotificationManagerCompat.from(appCtx).cancel(notificationId);
            } catch (Throwable t) {
                CrashReporter.INSTANCE.log(t);
            }
        }

        // goAsync() keeps the receiver alive past onReceive return. Without
        // it, Android can kill our process the instant onReceive returns —
        // which on a tap-and-lock-screen flow happens before BgExecutor has
        // even started its first DB write, and the user sees the
        // notification dismiss but nothing actually marked as watered.
        // The receiver has up to ~10s of execution time; cap our work
        // accordingly (one bulk update + one DataChange notify, no
        // network blocking — Firestore syncs are fire-and-forget).
        final BroadcastReceiver.PendingResult pendingResult = goAsync();
        BgExecutor.io(() -> {
            try {
                String email = EmailContext.current(appCtx);
                if (email == null || email.isEmpty()) return;
                // Locale.US for the date string we send to SQLite — on an
                // ar/fa device Locale.getDefault() formats yyyy-MM-dd with
                // Eastern-Arabic numerals (٢٠٢٦-٠٥-٠٦), which never matches
                // the rows we wrote with Latin numerals on a German device,
                // and the action silently no-ops on every reminder. See
                // A2 fix in PlantReminderWorker for the same root cause.
                String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                ReminderRepository repo = ReminderRepository.getInstance(appCtx);
                java.util.List<WateringReminder> due =
                        repo.getTodayAndOverdueRemindersForUserBlocking(today, email);
                if (due == null || due.isEmpty()) return;
                long nowMillis = System.currentTimeMillis();
                int markedCount = 0;
                for (WateringReminder r : due) {
                    if (r == null || r.done) continue;
                    r.done = true;
                    // Defensive: a fresh Date per reminder so a future
                    // mutation of one row's completedDate can't ripple
                    // through every other row that shared the instance.
                    r.completedDate = new Date(nowMillis);
                    r.wateredBy = email;
                    try {
                        repo.updateBlocking(r);
                        try { FirebaseSyncManager.get().syncReminder(r); }
                        catch (Throwable __ce) { CrashReporter.INSTANCE.log(__ce); }
                        markedCount++;
                    } catch (Throwable t) {
                        CrashReporter.INSTANCE.log(t);
                    }
                }
                // Z1: a tap on the notification action is the user
                // marking reminders done — same gamification effect as
                // an in-app tap. Without this call the streak counter
                // and the WATER_STREAK_7 challenge silently ignore the
                // notification path, punishing the user for using the
                // feature. One-streak update per receiver run (not per
                // reminder) because the streak tracks DAYS, not events.
                if (markedCount > 0) {
                    try {
                        com.example.plantcare.feature.streak.StreakBridge
                                .onReminderMarkedDone(appCtx, email);
                    } catch (Throwable __ce) { CrashReporter.INSTANCE.log(__ce); }
                }
                try { DataChangeNotifier.notifyChange(); }
                catch (Throwable __ce) { CrashReporter.INSTANCE.log(__ce); }
            } catch (Throwable t) {
                CrashReporter.INSTANCE.log(t);
            } finally {
                // MUST always run — letting the pendingResult leak holds a
                // wake lock and prevents the system from finalising the
                // receiver, which Android logs as ANR-class. Defensive
                // null check: `goAsync()` is documented non-null in
                // current Android, but historic builds (pre-21) and
                // some OEM forks have returned null when the receiver
                // is being torn down concurrently.
                if (pendingResult != null) {
                    try { pendingResult.finish(); }
                    catch (Throwable __ce) { CrashReporter.INSTANCE.log(__ce); }
                }
            }
        });
    }
}
