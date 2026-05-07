package com.example.plantcare;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * DAO aligned with all usages scattered in the project.
 */
@Dao
public interface PlantPhotoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(PlantPhoto photo);

    @Update
    void update(PlantPhoto photo);

    @Delete
    void delete(PlantPhoto photo);

    @Query("SELECT * FROM plant_photo WHERE id = :id LIMIT 1")
    PlantPhoto getPhotoById(int id);

    @Query("DELETE FROM plant_photo WHERE id = :id")
    void deleteById(int id);

    @Query("SELECT * FROM plant_photo WHERE plantId = :plantId ORDER BY dateTaken DESC, id DESC")
    List<PlantPhoto> getPhotosForPlant(int plantId);

    // Alias used in some legacy code paths (keep both)
    @Query("SELECT * FROM plant_photo WHERE plantId = :plantId ORDER BY dateTaken DESC, id DESC")
    List<PlantPhoto> getAllForPlant(int plantId);

    @Query("SELECT * FROM plant_photo WHERE plantId = :plantId AND isCover = 1 LIMIT 1")
    PlantPhoto getCoverPhoto(int plantId);

    @Query("UPDATE plant_photo SET isCover = 0 WHERE plantId = :plantId")
    void unsetCoverForPlant(int plantId);

    // Photos for a specific calendar date (yyyy-MM-dd)
    @Query("SELECT * FROM plant_photo WHERE dateTaken = :date ORDER BY id DESC")
    List<PlantPhoto> getPhotosByDate(@Nullable String date);

    @Query("DELETE FROM plant_photo WHERE plantId = :plantId")
    void deleteAllForPlant(int plantId);

    @Query("DELETE FROM plant_photo WHERE userEmail = :userEmail")
    void deleteAllPhotosForUser(@Nullable String userEmail);

    /**
     * Count of photos this user took on or after [sinceDate]. Used by the
     * MONTHLY_PHOTO challenge to detect "did the user take any photo this
     * month yet" without loading every photo into memory.
     */
    @Query("SELECT COUNT(*) FROM plant_photo WHERE userEmail = :userEmail AND dateTaken >= :sinceDate")
    int countPhotosForUserSince(@Nullable String userEmail, @Nullable String sinceDate);

    // ────────────────────────────────────────────────────────────────────
    // Sprint-3 Task 3.1: reactive LiveData<List<...>> read queries.
    // ────────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM plant_photo WHERE plantId = :plantId ORDER BY dateTaken DESC, id DESC")
    LiveData<List<PlantPhoto>> observePhotosForPlant(int plantId);

    @Query("SELECT * FROM plant_photo WHERE dateTaken = :date ORDER BY id DESC")
    LiveData<List<PlantPhoto>> observePhotosByDate(@Nullable String date);
}