package com.example.plantcare;

import android.content.Context;

/**
 * <h3>DEPRECATED — use {@link AppDatabase#getInstance(Context)} directly.</h3>
 *
 * <p>Historically the project had two facades to Room:
 * {@code DatabaseClient.getInstance(ctx)} (Java legacy) and
 * {@link AppDatabase#getInstance(Context)} (used by ViewModels/Repositories).
 * Both now resolve to the <b>same</b> singleton — this class is a thin
 * forwarder so existing Java call-sites keep compiling.</p>
 *
 * <p><b>Invariant:</b> there is exactly one Room database instance per process.
 * All new code (Kotlin and Java) must call {@link AppDatabase#getInstance(Context)}
 * directly. Do not add new call-sites using {@code DatabaseClient}.</p>
 *
 * <p>This class will be removed once all Java fragments migrate to the direct
 * AppDatabase accessor.</p>
 */
@Deprecated
public class DatabaseClient {

    private static volatile DatabaseClient instance;
    private final AppDatabase appDatabase;

    private DatabaseClient(Context context) {
        // Single source of truth — delegate to AppDatabase's singleton.
        this.appDatabase = AppDatabase.getInstance(context.getApplicationContext());
    }

    /**
     * @deprecated Use {@link AppDatabase#getInstance(Context)} directly.
     */
    @Deprecated
    public static DatabaseClient getInstance(Context context) {
        if (instance == null) {
            synchronized (DatabaseClient.class) {
                if (instance == null) {
                    instance = new DatabaseClient(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * @deprecated Use {@link AppDatabase#getInstance(Context)} directly.
     */
    @Deprecated
    public AppDatabase getAppDatabase() {
        return appDatabase;
    }

    /**
     * @deprecated Use {@code AppDatabase.getInstance(ctx).reminderDao()} directly.
     */
    @Deprecated
    public ReminderDao reminderDao() {
        return appDatabase.reminderDao();
    }

    /**
     * @deprecated Use {@code AppDatabase.getInstance(ctx).plantDao()} directly.
     */
    @Deprecated
    public PlantDao plantDao() {
        return appDatabase.plantDao();
    }
}
