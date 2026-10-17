package com.example.plantcare.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.plantcare.AppDatabase
import com.example.plantcare.data.db.DatabaseMigrations
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate5To6() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL("INSERT INTO plant (id, name, wateringInterval, isUserPlant, isFavorite, roomId) VALUES (1, 'Aloe', 7, 1, 0, 0)")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 6, true, DatabaseMigrations.MIGRATION_5_6).apply {
            val cursor = query("SELECT name FROM plant WHERE id = 1")
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("Aloe")
            cursor.close()
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate6To7() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL("INSERT INTO plant (id, name, wateringInterval, isUserPlant, isFavorite, roomId) VALUES (1, 'Ficus', 5, 1, 0, 0)")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 7, true, DatabaseMigrations.MIGRATION_6_7).apply {
            val cursor = query("SELECT name, category FROM plant WHERE id = 1")
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("Ficus")
            cursor.close()
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate7To8() {
        helper.createDatabase(TEST_DB, 7).apply {
            execSQL("INSERT INTO plant (id, name, wateringInterval, isUserPlant, isFavorite, roomId, category) VALUES (1, 'Kaktus', 14, 1, 0, 0, 'cacti')")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 8, true, DatabaseMigrations.MIGRATION_7_8).apply {
            val cursor = query("SELECT name FROM plant WHERE id = 1")
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("Kaktus")
            cursor.close()
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate8To9() {
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL("INSERT INTO plant (id, name, wateringInterval, isUserPlant, isFavorite, roomId) VALUES (1, 'Orchidee', 3, 1, 0, 0)")
            execSQL("INSERT INTO plant_photo (id, plantId, isCover, isProfile) VALUES (1, 1, 0, 0)")
            execSQL("INSERT INTO WateringReminder (id, plantId, done, isTreatment) VALUES (1, 1, 0, 0)")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 9, true, DatabaseMigrations.MIGRATION_8_9).apply {
            val cursorPhoto = query("SELECT plantId FROM plant_photo WHERE id = 1")
            assertThat(cursorPhoto.moveToFirst()).isTrue()
            assertThat(cursorPhoto.getInt(0)).isEqualTo(1)
            cursorPhoto.close()

            val cursorReminder = query("SELECT plantId FROM WateringReminder WHERE id = 1")
            assertThat(cursorReminder.moveToFirst()).isTrue()
            assertThat(cursorReminder.getInt(0)).isEqualTo(1)
            cursorReminder.close()
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate9To10() {
        helper.createDatabase(TEST_DB, 9).close()
        helper.runMigrationsAndValidate(TEST_DB, 10, true, DatabaseMigrations.MIGRATION_9_10).apply {
            // identification_cache table should exist after the migration
            val cursor = query("SELECT name FROM sqlite_master WHERE type='table' AND name='identification_cache'")
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.close()
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11() {
        helper.createDatabase(TEST_DB, 10).apply {
            execSQL("INSERT INTO plant (id, name, wateringInterval, isUserPlant, isFavorite, roomId) VALUES (1, 'Pothos', 7, 1, 0, 0)")
            execSQL("INSERT INTO plant_photo (id, plantId, isCover, isProfile) VALUES (1, 1, 0, 0)")
            execSQL("INSERT INTO WateringReminder (id, plantId, done, isTreatment) VALUES (1, 1, 0, 0)")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 11, true, DatabaseMigrations.MIGRATION_10_11).apply {
            // Existing photo row gets photoType default 'regular' and null diagnosisId
            val photoCursor = query("SELECT photoType, diagnosisId FROM plant_photo WHERE id = 1")
            assertThat(photoCursor.moveToFirst()).isTrue()
            assertThat(photoCursor.getString(0)).isEqualTo("regular")
            assertThat(photoCursor.isNull(1)).isTrue()
            photoCursor.close()
            // Reminder gets the new `notes` column, defaulting to null
            val reminderCursor = query("SELECT notes FROM WateringReminder WHERE id = 1")
            assertThat(reminderCursor.moveToFirst()).isTrue()
            assertThat(reminderCursor.isNull(0)).isTrue()
            reminderCursor.close()
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate11To12() {
        helper.createDatabase(TEST_DB, 11).close()
        helper.runMigrationsAndValidate(TEST_DB, 12, true, DatabaseMigrations.MIGRATION_11_12).apply {
            val cursor = query("SELECT name FROM sqlite_master WHERE type='table' AND name='disease_reference_image'")
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.close()
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate12To13() {
        // Sprint-1 Task 1.2: Plant Journal Memo schema migration.
        helper.createDatabase(TEST_DB, 12).apply {
            // Seed a plant so the FK on journal_memo has a valid parent.
            execSQL("INSERT INTO plant (id, name, wateringInterval, isUserPlant, isFavorite, roomId) VALUES (42, 'Einblatt', 7, 1, 0, 0)")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 13, true, DatabaseMigrations.MIGRATION_12_13).apply {
            // New table exists
            val tableCursor = query("SELECT name FROM sqlite_master WHERE type='table' AND name='journal_memo'")
            assertThat(tableCursor.moveToFirst()).isTrue()
            tableCursor.close()

            // Insert + read back to confirm column types and FK accept valid input
            execSQL("INSERT INTO journal_memo (plantId, userEmail, text, createdAt, updatedAt) VALUES (42, 'guest@local', 'erstes Blatt', 1700000000000, 1700000000000)")
            val readCursor = query("SELECT plantId, text, createdAt FROM journal_memo")
            assertThat(readCursor.moveToFirst()).isTrue()
            assertThat(readCursor.getInt(0)).isEqualTo(42)
            assertThat(readCursor.getString(1)).isEqualTo("erstes Blatt")
            assertThat(readCursor.getLong(2)).isEqualTo(1700000000000L)
            readCursor.close()

            // All 3 indexes were created
            val indexCursor = query(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='journal_memo' " +
                "AND name IN ('index_journal_memo_plantId','index_journal_memo_userEmail','index_journal_memo_plantId_updatedAt')"
            )
            var indexCount = 0
            while (indexCursor.moveToNext()) indexCount++
            indexCursor.close()
            assertThat(indexCount).isEqualTo(3)
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrateAllVersions() {
        // Full chain 5 → 13 — the production upgrade path.
        helper.createDatabase(TEST_DB, 5).close()
        helper.runMigrationsAndValidate(
            TEST_DB, 13, true,
            *DatabaseMigrations.ALL_MIGRATIONS
        ).close()
    }
}
