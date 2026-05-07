package com.example.plantcare.data.db;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Room database migrations.
 *
 * STRATEGY:
 * - Versions 1-4 were development-only; no production users exist on those versions.
 *   AppDatabase uses fallbackToDestructiveMigrationFrom(1,2,3,4) for those.
 * - Version 5 is the first production-ready schema.
 * - All future migrations (5→6, 6→7, etc.) MUST be defined here as proper
 *   Migration objects so existing user data is never lost.
 *
 * CURRENT SCHEMA (v5):
 *   plant         (id, name, nickname, startDate, wateringInterval, isUserPlant,
 *                  lighting, soil, fertilizing, watering, imageUri, isFavorite,
 *                  personalNote, userEmail, roomId)
 *   plant_photo   (id, plantId, imagePath, dateTaken, isCover, userEmail, isProfile)
 *   WateringReminder (id, plantId, plantName, date, done, completedDate, repeat,
 *                     description, userEmail)
 *   RoomCategory  (id, name, userEmail)
 *   User          (email, name, passwordHash)
 *
 * HOW TO ADD A NEW MIGRATION:
 * 1. Bump the version in AppDatabase @Database annotation.
 * 2. Add a new Migration constant below (e.g., MIGRATION_5_6).
 * 3. Register it in AppDatabase.getInstance() via addMigrations(...).
 * 4. NEVER use fallbackToDestructiveMigration() again.
 */
public final class DatabaseMigrations {

    private DatabaseMigrations() {}

    /**
     * v5 → v6: Tabelle `disease_diagnosis` für die lokale TFLite-Krankheitsdiagnose.
     * Keine vorhandenen Tabellen werden verändert; daher reiner additiver Schritt.
     */
    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `disease_diagnosis` (" +
                            "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                            "`imagePath` TEXT NOT NULL, " +
                            "`diseaseKey` TEXT NOT NULL, " +
                            "`displayName` TEXT NOT NULL, " +
                            "`confidence` REAL NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "`plantId` INTEGER NOT NULL DEFAULT 0, " +
                            "`userEmail` TEXT, " +
                            "`note` TEXT" +
                            ")"
            );
        }
    };

    /**
     * v6 → v7: Fügt der `plant`-Tabelle die Spalte `category` hinzu
     * (indoor / outdoor / herbal / cacti). Reiner additiver Schritt,
     * Bestandsdaten bleiben unverändert (Spalte startet NULL und wird
     * lazy über {@link com.example.plantcare.ui.util.PlantCategoryUtil}
     * beim nächsten Start klassifiziert).
     */
    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE plant ADD COLUMN category TEXT");
        }
    };

    /**
     * v7 → v8: Unterstützung für Familien­freigabe & Pflegeverlauf.
     *
     *  • plant.sharedWith           — Komma-getrennte Liste von E-Mails,
     *                                 die Zugriff auf diese Pflanze haben
     *                                 (nullable, Standard NULL).
     *  • WateringReminder.wateredBy — E-Mail desjenigen Familien­mitglieds,
     *                                 der die Pflanze zuletzt gegossen hat
     *                                 (nullable; bei done=true gesetzt).
     *  • WateringReminder.isTreatment — 1 = Behandlungs­plan-Schritt
     *                                  (z. B. Fungizid-Anwendung), 0 = normal.
     *                                  Damit lassen sich Krankheits­­­verläufe
     *                                  separat aggregieren.
     *
     * Reiner additiver Schritt — Bestands­daten bleiben unverändert.
     */
    public static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE plant ADD COLUMN sharedWith TEXT");
            db.execSQL("ALTER TABLE WateringReminder ADD COLUMN wateredBy TEXT");
            db.execSQL("ALTER TABLE WateringReminder ADD COLUMN isTreatment INTEGER NOT NULL DEFAULT 0");
        }
    };

    /**
     * v8 → v9: Adds Foreign Keys (CASCADE) on plant_photo and WateringReminder,
     * and Indexes on plantId + userEmail columns for query performance.
     * SQLite requires full table recreation to add FK constraints.
     * disease_diagnosis gets indexes only (plantId=0 means "unlinked", no FK).
     */
    public static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {

            // ── plant_photo ──────────────────────────────────────────
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `plant_photo_new` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "`plantId` INTEGER NOT NULL, " +
                "`imagePath` TEXT, " +
                "`dateTaken` TEXT, " +
                "`isCover` INTEGER NOT NULL, " +
                "`userEmail` TEXT, " +
                "`isProfile` INTEGER NOT NULL, " +
                "FOREIGN KEY(`plantId`) REFERENCES `plant`(`id`) ON DELETE CASCADE)"
            );
            db.execSQL(
                "INSERT INTO `plant_photo_new` " +
                "SELECT `id`,`plantId`,`imagePath`,`dateTaken`,`isCover`,`userEmail`,`isProfile` " +
                "FROM `plant_photo`"
            );
            db.execSQL("DROP TABLE `plant_photo`");
            db.execSQL("ALTER TABLE `plant_photo_new` RENAME TO `plant_photo`");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_plant_photo_plantId` ON `plant_photo`(`plantId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_plant_photo_userEmail` ON `plant_photo`(`userEmail`)");

            // ── WateringReminder ─────────────────────────────────────
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `WateringReminder_new` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "`plantId` INTEGER NOT NULL, " +
                "`plantName` TEXT, " +
                "`date` TEXT, " +
                "`done` INTEGER NOT NULL, " +
                "`completedDate` INTEGER, " +
                "`repeat` TEXT, " +
                "`description` TEXT, " +
                "`userEmail` TEXT, " +
                "`wateredBy` TEXT, " +
                "`isTreatment` INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(`plantId`) REFERENCES `plant`(`id`) ON DELETE CASCADE)"
            );
            db.execSQL(
                "INSERT INTO `WateringReminder_new` " +
                "SELECT `id`,`plantId`,`plantName`,`date`,`done`,`completedDate`," +
                "`repeat`,`description`,`userEmail`,`wateredBy`,`isTreatment` " +
                "FROM `WateringReminder`"
            );
            db.execSQL("DROP TABLE `WateringReminder`");
            db.execSQL("ALTER TABLE `WateringReminder_new` RENAME TO `WateringReminder`");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_WateringReminder_plantId` ON `WateringReminder`(`plantId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_WateringReminder_userEmail` ON `WateringReminder`(`userEmail`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_WateringReminder_userEmail_done` ON `WateringReminder`(`userEmail`,`done`)");

            // ── disease_diagnosis (indexes only — no FK since plantId=0 is valid) ──
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_disease_diagnosis_plantId` ON `disease_diagnosis`(`plantId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_disease_diagnosis_userEmail` ON `disease_diagnosis`(`userEmail`)");
        }
    };

    /**
     * v9 → v10: Adds identification_cache table for PlantNet API response caching.
     * imageHash = SHA-256 of the image file (primary key).
     * Avoids redundant API calls for previously identified images (7-day TTL).
     */
    public static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `identification_cache` (" +
                "`imageHash` TEXT NOT NULL PRIMARY KEY, " +
                "`responseJson` TEXT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL)"
            );
        }
    };

    /**
     * v10 → v11: Plant Journal feature (Functional Report §4).
     *
     *  • plant_photo.photoType       — "regular" | "inspection" | "cover".
     *                                   Defaults to "regular" so existing rows are
     *                                   correctly classified without a backfill query.
     *  • plant_photo.diagnosisId     — optional FK-by-convention to disease_diagnosis.id.
     *                                   No DB-level FK so a deleted diagnosis doesn't
     *                                   cascade-delete the photo evidence.
     *  • WateringReminder.notes      — free-text note the user can attach when
     *                                   ticking a reminder ("looked thirsty").
     *
     * Reiner additiver Schritt — Bestandsdaten bleiben unverändert.
     */
    public static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `plant_photo` ADD COLUMN `photoType` TEXT DEFAULT 'regular'");
            db.execSQL("ALTER TABLE `plant_photo` ADD COLUMN `diagnosisId` INTEGER");
            db.execSQL("ALTER TABLE `WateringReminder` ADD COLUMN `notes` TEXT");
        }
    };

    /**
     * v11 → v12: Cache table for disease reference images (Wikimedia Commons,
     * iNaturalist, PlantVillage CDN). Keyed by (diseaseKey, imageUrl) so
     * re-fetches are idempotent. Pure additive — no existing data touched.
     */
    public static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `disease_reference_image` (" +
                            "`diseaseKey` TEXT NOT NULL, " +
                            "`imageUrl` TEXT NOT NULL, " +
                            "`thumbnailUrl` TEXT, " +
                            "`source` TEXT NOT NULL, " +
                            "`attribution` TEXT, " +
                            "`pageUrl` TEXT, " +
                            "`fetchedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`diseaseKey`, `imageUrl`))"
            );
            db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_disease_reference_image_diseaseKey` " +
                            "ON `disease_reference_image`(`diseaseKey`)"
            );
            db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_disease_reference_image_fetchedAt` " +
                            "ON `disease_reference_image`(`fetchedAt`)"
            );
        }
    };

    /**
     * v12 → v13: Plant Journal write-side (Sprint-1 Task 1.2). Adds the
     * `journal_memo` table for free-text notes the user attaches to a plant
     * from the journal screen — distinct from `WateringReminder.notes` which
     * stays bound to a specific watering event.
     *
     * Schema:
     *  • id          — autogen primary key
     *  • plantId     — FK to plant(id), CASCADE so deleting a plant doesn't
     *                  leave orphan memos the journal screen would silently hide
     *  • userEmail   — nullable, mirrors every other table's segregation pattern
     *  • text        — free-text body
     *  • createdAt   — epoch millis
     *  • updatedAt   — epoch millis; the repository orders by this so editing
     *                  an old memo bumps it back to the top of the timeline
     *
     * Indexes match @Index declarations in the entity: plantId, userEmail,
     * and a compound (plantId, updatedAt) for the per-plant ORDER BY query.
     *
     * Pure additive — no existing data touched.
     */
    public static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `journal_memo` (" +
                            "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                            "`plantId` INTEGER NOT NULL, " +
                            "`userEmail` TEXT, " +
                            "`text` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`plantId`) REFERENCES `plant`(`id`) ON DELETE CASCADE)"
            );
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_memo_plantId` ON `journal_memo`(`plantId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_memo_userEmail` ON `journal_memo`(`userEmail`)");
            db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_journal_memo_plantId_updatedAt` " +
                            "ON `journal_memo`(`plantId`, `updatedAt`)"
            );
        }
    };

    /**
     * v13 → v14: User-defined room ordering. Adds `position INTEGER NOT NULL
     * DEFAULT 0` so existing rooms keep their alphabetical fallback until
     * the user explicitly drags them. Pure additive — no data migration.
     */
    public static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `RoomCategory` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0");
        }
    };

    /**
     * All migrations that AppDatabase should register.
     * Add new entries here as you create them.
     */
    public static final Migration[] ALL_MIGRATIONS = {
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
    };

    // ──────────────────────────────────────────────────────────────
    //  TEMPLATE for future migrations — copy, rename, and fill in.
    // ──────────────────────────────────────────────────────────────
    //
    //  public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
    //      @Override
    //      public void migrate(@NonNull SupportSQLiteDatabase db) {
    //          // Example: add a new column
    //          // db.execSQL("ALTER TABLE plant ADD COLUMN lastWateredDate TEXT");
    //      }
    //  };
    //
    // After creating it, add it to ALL_MIGRATIONS above and bump
    // the version in AppDatabase.
    // ──────────────────────────────────────────────────────────────
}
