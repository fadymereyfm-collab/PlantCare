package com.example.plantcare.data.plantnet

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface IdentificationCacheDao {

    @Query("SELECT * FROM identification_cache WHERE imageHash = :hash LIMIT 1")
    fun findByHash(hash: String): CachedIdentification?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entry: CachedIdentification)

    @Query("DELETE FROM identification_cache WHERE timestamp < :cutoff")
    fun deleteOlderThan(cutoff: Long)
}
