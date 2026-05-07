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

    @Query("DELETE FROM disease_diagnosis WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM disease_diagnosis")
    suspend fun deleteAll()
}
