package com.example.plantcare.data.repository

import android.content.Context
import com.example.plantcare.AppDatabase
import com.example.plantcare.BuildConfig
import com.example.plantcare.data.plantnet.CachedIdentification
import com.example.plantcare.data.plantnet.IdentificationResult
import com.example.plantcare.data.plantnet.PlantNetError
import com.example.plantcare.data.plantnet.PlantNetOutcome
import com.example.plantcare.data.plantnet.PlantNetService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Repository for plant identification via PlantNet API.
 * Caches results by SHA-256 image hash (7-day TTL) to conserve API quota.
 */
class PlantIdentificationRepository private constructor(context: Context) {

    private val plantNetService = PlantNetService()
    private val cacheDao = AppDatabase.getInstance(context).identificationCacheDao()
    private val gson = Gson()
    private val resultListType = object : TypeToken<List<IdentificationResult>>() {}.type

    sealed class IdentifyResult {
        data class Found(val results: List<IdentificationResult>) : IdentifyResult()
        data object NoMatch : IdentifyResult()
        data class Error(val type: PlantNetError) : IdentifyResult()
    }

    suspend fun identifyPlant(imageFile: File, organ: String = "auto"): IdentifyResult {
        return withContext(Dispatchers.IO) {
            val hash = sha256(imageFile)

            // Check cache first — valid for 7 days
            val cached = cacheDao.findByHash(hash)
            if (cached != null) {
                val age = System.currentTimeMillis() - cached.timestamp
                if (age < CACHE_TTL_MS) {
                    val results = gson.fromJson<List<IdentificationResult>>(cached.responseJson, resultListType)
                    return@withContext if (results.isEmpty()) IdentifyResult.NoMatch
                    else IdentifyResult.Found(results)
                }
            }

            // Not in cache — call PlantNet
            val outcome = plantNetService.identify(
                imageFile = imageFile,
                organ = organ,
                apiKey = BuildConfig.PLANTNET_API_KEY
            )

            when (outcome) {
                is PlantNetOutcome.Failure -> IdentifyResult.Error(outcome.error)
                is PlantNetOutcome.Success -> {
                    val mapped = outcome.response.results?.take(3)?.map { result ->
                        val firstImage = result.images?.firstOrNull { img ->
                            !img.url?.medium.isNullOrBlank() ||
                                    !img.url?.small.isNullOrBlank() ||
                                    !img.url?.original.isNullOrBlank()
                        }
                        val thumbUrl = firstImage?.url?.medium
                            ?: firstImage?.url?.small
                            ?: firstImage?.url?.original
                        val largeUrl = firstImage?.url?.original
                            ?: firstImage?.url?.medium
                            ?: firstImage?.url?.small

                        IdentificationResult(
                            scientificName = result.species.scientificName ?: "Unbekannt",
                            commonName = result.species.commonNames?.firstOrNull(),
                            family = result.species.family?.scientificName,
                            confidence = result.score,
                            gbifId = result.gbif?.id,
                            imageUrl = thumbUrl,
                            largeImageUrl = largeUrl
                        )
                    } ?: emptyList()

                    // Persist to cache regardless of hit/miss
                    cacheDao.upsert(CachedIdentification(
                        imageHash = hash,
                        responseJson = gson.toJson(mapped),
                        timestamp = System.currentTimeMillis()
                    ))

                    if (mapped.isEmpty()) IdentifyResult.NoMatch else IdentifyResult.Found(mapped)
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000 // 7 days

        @Volatile
        private var INSTANCE: PlantIdentificationRepository? = null

        fun getInstance(context: Context): PlantIdentificationRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = PlantIdentificationRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
