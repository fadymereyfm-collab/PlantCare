package com.example.plantcare.data.plantnet

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "identification_cache")
data class CachedIdentification(
    @PrimaryKey val imageHash: String,
    val responseJson: String,
    val timestamp: Long
)
