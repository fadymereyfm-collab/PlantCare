package com.example.plantcare;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.Date;
import java.util.List;

@Dao
public interface ReminderDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(WateringReminder reminder);

    /**
     * IGNORE-on-conflict so a single colliding row (e.g. cloud-import landing
     * a reminder we already inserted locally) doesn't blow up the entire
     * batch. The caller still gets the rest of the rows persisted instead
     * of having Room throw out everything from this `insertAll` call.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<WateringReminder> reminders);

    @Update
    void update(WateringReminder reminder);

    @Delete
    void delete(WateringReminder reminder);

    @Query("SELECT * FROM WateringReminder")
    List<WateringReminder> getAllReminders();

    // جلب كل التذكيرات الخاصة بمستخدم معين فقط
    @Query("SELECT * FROM WateringReminder WHERE userEmail = :userEmail")
    List<WateringReminder> getAllRemindersForUser(@Nullable String userEmail);

    @Query("DELETE FROM WateringReminder WHERE plantId = :plantId AND date >= :fromDateStr")
    void deleteFutureRemindersForPlant(int plantId, @Nullable String fromDateStr);

    @Query("DELETE FROM WateringReminder WHERE plantId = :plantId")
    void deleteRemindersForPlant(int plantId);

    @Query("SELECT * FROM WateringReminder WHERE date BETWEEN :startDate AND :endDate")
    List<WateringReminder> getRemindersBetween(@Nullable String startDate, @Nullable String endDate);

    @Query("SELECT * FROM WateringReminder WHERE plantId = :plantId")
    List<WateringReminder> getRemindersForPlant(int plantId);

    @Query("SELECT * FROM WateringReminder WHERE date <= :today AND userEmail = :userEmail AND done = 0")
    List<WateringReminder> getTodayAndOverdueRemindersForUser(@Nullable String today, @Nullable String userEmail);

    @Query("SELECT * FROM WateringReminder WHERE date <= :today AND userEmail = :userEmail")
    List<WateringReminder> getTodayAndOverdueAllRemindersForUser(@Nullable String today, @Nullable String userEmail);

    @Query("SELECT * FROM WateringReminder WHERE id = :id")
    WateringReminder getReminderById(int id);

    @Query("SELECT * FROM WateringReminder WHERE plantName = :plantName AND date = :date AND userEmail = :userEmail")
    List<WateringReminder> getRemindersByPlantAndDate(@Nullable String plantName, @Nullable String date, @Nullable String userEmail);

    @Query("DELETE FROM WateringReminder WHERE plantId = :plantId AND userEmail = :userEmail AND date > :fromDate AND description = :oldDesc AND repeat = :oldRepeat")
    void deleteFutureManualRepeats(int plantId, @Nullable String userEmail, @Nullable String fromDate, @Nullable String oldDesc, @Nullable String oldRepeat);

    // حذف كل تذكيرات مستخدم معين (حتى Allgemein)
    @Query("DELETE FROM WateringReminder WHERE userEmail = :userEmail")
    void deleteAllRemindersForUser(@Nullable String userEmail);

    // حذف كل التذكيرات المرتبطة بالنبتة (بـid أو بـname + userEmail)
    @Query("DELETE FROM WateringReminder WHERE plantId = :plantId OR (plantName = :plantName AND userEmail = :userEmail)")
    void deleteRemindersForPlantAndUser(int plantId, @Nullable String plantName, @Nullable String userEmail);

    // ────────────────────────────────────────────────────────────────────
    // Sprint-3 Task 3.1: reactive LiveData<List<...>> read queries.
    // See PlantDao for the rationale (parallel to existing blocking
    // accessors so workers keep their List<X> contract).
    // ────────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM WateringReminder")
    LiveData<List<WateringReminder>> observeAllReminders();

    @Query("SELECT * FROM WateringReminder WHERE userEmail = :userEmail")
    LiveData<List<WateringReminder>> observeAllRemindersForUser(@Nullable String userEmail);

    @Query("SELECT * FROM WateringReminder WHERE date BETWEEN :startDate AND :endDate")
    LiveData<List<WateringReminder>> observeRemindersBetween(@Nullable String startDate, @Nullable String endDate);

    @Query("SELECT * FROM WateringReminder WHERE plantId = :plantId")
    LiveData<List<WateringReminder>> observeRemindersForPlant(int plantId);

    @Query("SELECT * FROM WateringReminder WHERE date <= :today AND userEmail = :userEmail AND done = 0")
    LiveData<List<WateringReminder>> observeTodayAndOverdueRemindersForUser(@Nullable String today, @Nullable String userEmail);

    @Query("SELECT * FROM WateringReminder WHERE date <= :today AND userEmail = :userEmail")
    LiveData<List<WateringReminder>> observeTodayAndOverdueAllRemindersForUser(@Nullable String today, @Nullable String userEmail);
}