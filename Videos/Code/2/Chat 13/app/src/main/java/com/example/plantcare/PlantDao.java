package com.example.plantcare;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Full Plant DAO with all original queries retained and
 * added explicit profile image update helpers (updateProfileImage / clearProfileImage).
 */
@Dao
public interface PlantDao {

    // -------------------------
    // Core retrieval
    // -------------------------

    @Query("SELECT * FROM plant")
    List<Plant> getAll();

    @Query("SELECT COUNT(*) FROM plant")
    int countAll();

    @Query("SELECT * FROM plant WHERE id = :id LIMIT 1")
    Plant findById(int id);

    @Query("SELECT * FROM plant WHERE id IN (:ids)")
    List<Plant> getPlantsByIds(List<Integer> ids);

    // -------------------------
    // Name / nickname lookups
    // -------------------------

    @Query("SELECT * FROM plant WHERE name = :name LIMIT 1")
    Plant findByName(@Nullable String name);

    @Query("SELECT * FROM plant WHERE nickname = :nickname LIMIT 1")
    Plant findByNickname(@Nullable String nickname);

    // -------------------------
    // User plant filtering (global)
    // -------------------------

    @Query("SELECT * FROM plant WHERE isUserPlant = 1")
    List<Plant> getAllUserPlants();

    @Query("SELECT * FROM plant WHERE isUserPlant = 0")
    List<Plant> getAllNonUserPlants();

    /**
     * Catalog plants filtered by category (indoor / outdoor / herbal / cacti).
     * Used by the Chip filter in AllPlantsFragment.
     */
    @Query("SELECT * FROM plant WHERE isUserPlant = 0 AND category = :category")
    List<Plant> getCatalogPlantsByCategory(@Nullable String category);

    /**
     * Catalog plants that have no category yet — for the one-off classification
     * pass after MIGRATION_6_7.
     */
    @Query("SELECT * FROM plant WHERE isUserPlant = 0 AND (category IS NULL OR category = '')")
    List<Plant> getCatalogPlantsWithoutCategory();

    /**
     * Update the category column without rewriting the whole row.
     */
    @Query("UPDATE plant SET category = :category WHERE id = :id")
    void updateCategory(int id, @Nullable String category);

    /**
     * Case‑insensitive‑Suche im Katalog (isUserPlant = 0) anhand des Namens.
     * Wird vom PlantNet‑Flow benutzt, um nach der Erkennung die vier Pflege‑Felder
     * (Licht, Boden, Düngung, Bewässerung) aus dem vorhandenen 506er‑Katalog zu übernehmen,
     * statt den Nutzer mit leeren Feldern zu lassen.
     */
    @Query("SELECT * FROM plant WHERE LOWER(name) = LOWER(:name) AND isUserPlant = 0 LIMIT 1")
    Plant findCatalogByName(@Nullable String name);

    /**
     * Partielle Katalog‑Suche: nützlich, wenn PlantNet einen zusammengesetzten Trivialnamen liefert
     * (z. B. „Vielblütiges Salomonssiegel"), im Katalog aber die Kurzform („Salomonssiegel") steht.
     */
    @Query("SELECT * FROM plant WHERE LOWER(name) LIKE LOWER(:pattern) AND isUserPlant = 0 LIMIT 1")
    Plant findCatalogByNameLike(@Nullable String pattern);

    @Query("SELECT * FROM plant WHERE name = :name AND isUserPlant = 1")
    List<Plant> getAllUserPlantsWithName(@Nullable String name);

    // -------------------------
    // User-specific filtering (by userEmail)
    // -------------------------

    @Query("SELECT * FROM plant WHERE name = :name AND isUserPlant = 1 AND userEmail = :userEmail LIMIT 1")
    Plant findUserPlantByNameAndUser(@Nullable String name, @Nullable String userEmail);

    @Query("SELECT * FROM plant WHERE isUserPlant = 1 AND userEmail = :userEmail")
    List<Plant> getAllUserPlantsForUser(@Nullable String userEmail);

    @Query("SELECT * FROM plant WHERE name = :name AND isUserPlant = 1 AND userEmail = :userEmail")
    List<Plant> getAllUserPlantsWithNameAndUser(@Nullable String name, @Nullable String userEmail);

    @Query("SELECT * FROM plant WHERE nickname = :nickname AND isUserPlant = 1 AND userEmail = :userEmail")
    List<Plant> getAllUserPlantsWithNicknameAndUser(@Nullable String nickname, @Nullable String userEmail);

    // -------------------------
    // Room-based filtering (scoped to user)
    // -------------------------

    @Query("SELECT COUNT(*) FROM plant WHERE roomId = :roomId AND userEmail = :userEmail")
    int countPlantsByRoom(int roomId, @Nullable String userEmail);

    @Query("SELECT COUNT(*) FROM plant WHERE isUserPlant = 1 AND userEmail = :userEmail")
    int countUserPlants(@Nullable String userEmail);

    @Query("SELECT * FROM plant WHERE roomId = :roomId AND isUserPlant = 1 AND userEmail = :userEmail")
    List<Plant> getAllUserPlantsInRoom(int roomId, @Nullable String userEmail);

    // -------------------------
    // Profile image control
    // -------------------------

    /**
     * Set/replace the profile image (imageUri) explicitly.
     */
    @Query("UPDATE plant SET imageUri = :imageUri WHERE id = :id")
    void updateProfileImage(int id, @Nullable String imageUri);

    /**
     * Clear (remove) the profile image (sets imageUri = NULL).
     */
    @Query("UPDATE plant SET imageUri = NULL WHERE id = :id")
    void clearProfileImage(int id);

    // -------------------------
    // CRUD
    // -------------------------

    @Insert
    long insert(Plant plant);

    @Update
    void update(Plant plant);

    /**
     * Get all catalog (non-user) plants that have no profile image yet.
     */
    @Query("SELECT * FROM plant WHERE isUserPlant = 0 AND (imageUri IS NULL OR imageUri = '')")
    List<Plant> getCatalogPlantsWithoutImage();

    @Delete
    void delete(Plant plant);

    @Query("DELETE FROM plant WHERE userEmail = :userEmail")
    void deleteAllUserPlantsForUser(@Nullable String userEmail);

    // ────────────────────────────────────────────────────────────────────
    // Sprint-3 Task 3.1: reactive LiveData<List<...>> read queries
    //
    // Room observes the underlying tables and re-emits whenever the rows
    // matching the query change — so any UI binding these methods updates
    // automatically on insert/update/delete, with no DataChangeNotifier
    // tickle needed. Kept as parallel `observeXxx` methods so existing
    // blocking call sites (workers, sync IO paths) keep their original
    // List<X> contract.
    // ────────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM plant")
    LiveData<List<Plant>> observeAll();

    @Query("SELECT * FROM plant WHERE isUserPlant = 0")
    LiveData<List<Plant>> observeAllNonUserPlants();

    @Query("SELECT * FROM plant WHERE isUserPlant = 1 AND userEmail = :userEmail")
    LiveData<List<Plant>> observeAllUserPlantsForUser(@Nullable String userEmail);

    @Query("SELECT * FROM plant WHERE roomId = :roomId AND isUserPlant = 1 AND userEmail = :userEmail")
    LiveData<List<Plant>> observeAllUserPlantsInRoom(int roomId, @Nullable String userEmail);

    @Query("SELECT * FROM plant WHERE id = :id LIMIT 1")
    LiveData<Plant> observeById(int id);
}