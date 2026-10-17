package com.example.plantcare.data.disease

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DiseaseDiagnosisDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(diagnosis: DiseaseDiagnosis): Long

    @Query(
        "SELECT * FROM disease_diagnosis " +
                "WHERE (:userEmail IS NULL OR userEmail = :userEmail) " +
                "ORDER BY createdAt DESC"
    )
    fun observeAllForUser(userEmail: String?): LiveData<List<DiseaseDiagnosis>>

    @Query(
        "SELECT * FROM disease_diagnosis " +
                "WHERE plantId = :plantId " +
                "ORDER BY createdAt DESC"
    )
    fun observeForPlant(plantId: Int): LiveData<List<DiseaseDiagnosis>>

    /**
     * Synchronous (suspend) variant of [observeForPlant] for the Plant Journal
     * snapshot in PlantJournalRepository — the journal merges three sources in a
     * single round-trip rather than orchestrating three observers.
     */
    @Query(
        "SELECT * FROM disease_diagnosis " +
                "WHERE plantId = :plantId " +
                "ORDER BY createdAt DESC"
    )
    suspend fun getForPlantSync(plantId: Int): List<DiseaseDiagnosis>

    /**
     * Java-freundliche Variante von [getForPlantSync] — blockierend, daher
     * NUR von einem Hintergrund-Thread aufrufen (z. B. [com.example.plantcare.ui.util.FragmentBg]).
     */
    @Query(
        "SELECT * FROM disease_diagnosis " +
                "WHERE plantId = :plantId " +
                "ORDER BY createdAt DESC"
    )
    fun getForPlantBlocking(plantId: Int): List<DiseaseDiagnosis>

    /**
     * 2026-05-20 Feature 4 (Enriched history): update the two user-editable
     * fields on an existing diagnosis. The Gemini-emitted fields
     * (disease key, confidence, severity, etc.) are immutable.
     */
    @Query(
        "UPDATE disease_diagnosis SET note = :note, appliedTreatment = :appliedTreatment " +
                "WHERE id = :id"
    )
    suspend fun updateUserFields(id: Long, note: String?, appliedTreatment: String?)

    @Query("DELETE FROM disease_diagnosis WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM disease_diagnosis")
    suspend fun deleteAll()

    /**
     * Every `imagePath` value currently referenced by a saved diagnosis,
     * across all users. Used by [com.example.plantcare.ui.disease.DiseaseDiagnosisActivity]
     * during the 30-day temp-file cleanup so saved-diagnosis photos are
     * not accidentally deleted. Pre-fix the cleanup task was unscoped and
     * stripped images out from under the diagnosis-history + Plant
     * Journal cards after one month, leaving placeholders in their place.
     */
    @Query("SELECT imagePath FROM disease_diagnosis WHERE imagePath IS NOT NULL")
    fun getAllImagePathsBlocking(): List<String>
}
