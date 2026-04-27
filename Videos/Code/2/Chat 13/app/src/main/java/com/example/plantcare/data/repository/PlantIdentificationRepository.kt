package com.example.plantcare.data.repository

import android.content.Context
import com.example.plantcare.data.plantnet.IdentificationResult
import com.example.plantcare.BuildConfig
import com.example.plantcare.data.plantnet.PlantNetError
import com.example.plantcare.data.plantnet.PlantNetOutcome
import com.example.plantcare.data.plantnet.PlantNetService
import java.io.File

/**
 * Repository for plant identification via PlantNet API.
 * Singleton pattern, consistent with other repositories in the project.
 */
class PlantIdentificationRepository private constructor(context: Context) {

    private val plantNetService = PlantNetService()

    /**
     * Ergebnis­typ für die UI‑Schicht. Trennt klar zwischen
     * „Netz/Schlüssel/Quote kaputt" und „Anfrage lief, aber keine Pflanze gefunden".
     */
    sealed class IdentifyResult {
        data class Found(val results: List<IdentificationResult>) : IdentifyResult()
        data object NoMatch : IdentifyResult()
        data class Error(val type: PlantNetError) : IdentifyResult()
    }

    /**
     * Identify a plant from an image file.
     *
     * @param imageFile JPEG/PNG image file of the plant
     * @param organ Plant organ visible in the image (leaf, flower, fruit, bark, auto)
     */
    suspend fun identifyPlant(
        imageFile: File,
        organ: String = "auto"
    ): IdentifyResult {
        val outcome = plantNetService.identify(
            imageFile = imageFile,
            organ = organ,
            apiKey = BuildConfig.PLANTNET_API_KEY
        )

        return when (outcome) {
            is PlantNetOutcome.Failure -> IdentifyResult.Error(outcome.error)
            is PlantNetOutcome.Success -> {
                val mapped = outcome.response.results?.take(3)?.map { result ->
                    // Pick the first image that has a usable URL. PlantNet
                    // typically returns several; we prefer the "m" (medium) size
                    // for the list thumbnail and keep "o" as a fall‑back / larger
                    // preview. Any field may be null if the account/tier doesn't
                    // return related images.
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
                if (mapped.isEmpty()) IdentifyResult.NoMatch else IdentifyResult.Found(mapped)
            }
        }
    }

    companion object {
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
