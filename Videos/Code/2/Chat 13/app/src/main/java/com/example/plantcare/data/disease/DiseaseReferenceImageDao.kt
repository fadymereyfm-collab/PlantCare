package com.example.plantcare.data.disease

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DiseaseReferenceImageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<DiseaseReferenceImage>)

    @Query(
        "SELECT * FROM disease_reference_image " +
                "WHERE diseaseKey = :diseaseKey AND fetchedAt >= :minFetchedAt " +
                "ORDER BY " +
                "  CASE source " +
                "    WHEN 'plantvillage' THEN 0 " +
                "    WHEN 'wikimedia'    THEN 1 " +
                "    WHEN 'inaturalist'  THEN 2 " +
                "    ELSE 3 END, " +
                "  fetchedAt DESC " +
                "LIMIT :limit"
    )
    suspend fun getFreshForKey(
        diseaseKey: String,
        minFetchedAt: Long,
        limit: Int = 8
    ): List<DiseaseReferenceImage>

    @Query("DELETE FROM disease_reference_image WHERE fetchedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM disease_reference_image")
    suspend fun deleteAll()
}
