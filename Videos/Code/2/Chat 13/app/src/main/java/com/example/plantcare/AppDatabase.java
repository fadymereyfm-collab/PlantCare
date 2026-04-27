package com.example.plantcare;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.example.plantcare.data.Converters;
import com.example.plantcare.data.db.DatabaseMigrations;
import com.example.plantcare.data.disease.DiseaseDiagnosis;
import com.example.plantcare.data.disease.DiseaseDiagnosisDao;
import com.example.plantcare.data.plantnet.CachedIdentification;
import com.example.plantcare.data.plantnet.IdentificationCacheDao;

/**
 * Unified Room database including all entities actually used across the codebase.
 * Version 5 is the first production-ready schema.
 *
 * MIGRATION POLICY:
 * - Versions 1–4 (dev-only) → destructive fallback (no production users on those).
 * - Version 5+              → proper Migration objects in DatabaseMigrations.
 */
@Database(
        entities = {
                Plant.class,
                PlantPhoto.class,
                WateringReminder.class,
                RoomCategory.class,
                User.class,
                DiseaseDiagnosis.class,
                CachedIdentification.class
        },
        version = 10,
        exportSchema = true
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract PlantDao plantDao();
    public abstract PlantPhotoDao plantPhotoDao();
    public abstract ReminderDao reminderDao();
    public abstract RoomCategoryDao roomCategoryDao();
    public abstract UserDao userDao();
    public abstract DiseaseDiagnosisDao diseaseDiagnosisDao();
    public abstract IdentificationCacheDao identificationCacheDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "app-db"
                            )
                            // Register all proper migrations (version 5+)
                            .addMigrations(DatabaseMigrations.ALL_MIGRATIONS)
                            .fallbackToDestructiveMigrationFrom(1, 2, 3, 4)
                            .addCallback(new RoomDatabase.Callback() {
                                @Override
                                public void onOpen(@NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
                                    db.execSQL("PRAGMA foreign_keys=ON");
                                }
                            })
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
