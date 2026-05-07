package com.example.plantcare.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.example.plantcare.AppDatabase
import com.example.plantcare.BuildConfig
import com.example.plantcare.data.disease.DiseaseDiagnosis
import com.example.plantcare.data.disease.DiseaseDiagnosisDao
import com.example.plantcare.data.disease.DiseaseResult
import com.example.plantcare.data.gemini.GeminiDiseaseEntry
import com.example.plantcare.data.gemini.GeminiError
import com.example.plantcare.data.gemini.GeminiOutcome
import com.example.plantcare.data.gemini.GeminiVisionService
import com.example.plantcare.data.plantnet.PlantNetError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repository for cloud-based plant disease diagnosis (Google Gemini 2.5 Flash).
 *
 * Replaces the previous PlantNet `/v2/diseases/identify` integration.
 * On-device testing on 2026-05-01 confirmed PlantNet's disease catalogue is
 * crop-only (wheat smut, potato blight, apple powdery mildew) with zero
 * coverage of the German houseplants this app targets. Gemini's general
 * vision model handles indoor/ornamental plants out of the box and returns
 * localised German names plus a one-sentence treatment hint.
 *
 * Key invariants kept across the migration:
 *  - Returns the same sealed [DiagnoseResult] (Found/NoMatch/PlantNotDetected/Error).
 *  - Reuses [PlantNetError] as the error enum so the UI's `errorStringFor()`
 *    mapping doesn't need to change.
 *  - Saves to the same `disease_diagnosis` Room table — no schema migration.
 *
 * See PROGRESS.md R3 for the pre-launch billing-tier-switch reminder.
 */
class DiseaseDiagnosisRepository private constructor(appContext: Context) {

    private val context: Context = appContext.applicationContext
    private val dao: DiseaseDiagnosisDao =
        AppDatabase.getInstance(context).diseaseDiagnosisDao()
    private val service = GeminiVisionService()

    sealed class DiagnoseResult {
        data class Found(
            val results: List<DiseaseResult>,
            /**
             * Best-effort plant species identification from Gemini, used
             * by the UI to (a) display "Erkannte Pflanze: …" above the
             * candidate cards, and (b) sanity-check against the user's
             * chosen target plant before save.
             */
            val plantSpecies: String? = null
        ) : DiagnoseResult()

        /**
         * Gemini saw a plant but the JSON came back with empty results — i.e.
         * "I see a plant but I don't recognise a disease". `bodyPreview` is
         * the inner JSON, exposed in the UI under "Debug" so we can sanity-
         * check the model's reasoning when triaging.
         */
        data class NoMatch(val bodyPreview: String? = null) : DiagnoseResult()

        /** Gemini set `plant_detected: false` — the photo isn't a plant. */
        data object PlantNotDetected : DiagnoseResult()

        data class Error(val type: PlantNetError, val rawMessage: String? = null) : DiagnoseResult()
    }

    /**
     * Send the image to Gemini 2.5 Flash and translate the structured response.
     *
     * @param imageFile         JPEG produced by the camera or gallery picker.
     * @param organ             Ignored — kept for source-compat with the previous
     *                          PlantNet-based implementation.
     * @param excludeDiseaseKeys When non-empty, Gemini is told the user rejected
     *                          these candidates visually (after seeing reference
     *                          images) and should propose alternatives — drives
     *                          the "Keine passt" re-prompt flow in the UI.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun analyze(
        imageFile: File,
        organ: String = "auto",
        excludeDiseaseKeys: List<String> = emptyList()
    ): DiagnoseResult = withContext(Dispatchers.IO) {
        when (val outcome = service.analyzeDisease(imageFile, BuildConfig.GEMINI_API_KEY, excludeDiseaseKeys)) {
            is GeminiOutcome.Failure -> DiagnoseResult.Error(
                type = outcome.error.toPlantNetError(),
                rawMessage = outcome.rawMessage
            )
            is GeminiOutcome.PlantNotDetected -> DiagnoseResult.PlantNotDetected
            is GeminiOutcome.Success -> {
                val mapped = outcome.payload.results
                    ?.take(3)
                    ?.mapNotNull { it.toDiseaseResult() }
                    .orEmpty()
                val species = outcome.payload.plantSpecies
                    ?.takeIf { it.isNotBlank() && !it.equals("Unbekannt", ignoreCase = true) }
                if (mapped.isEmpty()) DiagnoseResult.NoMatch(outcome.bodyPreview)
                else DiagnoseResult.Found(mapped, plantSpecies = species)
            }
        }
    }

    /** Persist the chosen suggestion in `disease_diagnosis`. */
    suspend fun save(
        imagePath: String,
        result: DiseaseResult,
        plantId: Int = 0,
        userEmail: String? = null,
        note: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        dao.insert(
            DiseaseDiagnosis(
                imagePath = imagePath,
                diseaseKey = result.diseaseKey,
                displayName = result.displayName,
                confidence = result.confidence,
                createdAt = createdAt,
                plantId = plantId,
                userEmail = userEmail,
                note = note ?: result.advice
            )
        )
    }

    /**
     * Convenience proxy so the ViewModel can fetch reference images without
     * holding a second repository instance. Pure delegation — keeps the
     * Activity/VM tied to a single Disease repository surface.
     */
    suspend fun fetchReferenceImages(
        diseaseKey: String,
        displayNameDe: String?
    ): List<com.example.plantcare.data.disease.DiseaseReferenceImage> =
        DiseaseReferenceImageRepository.getInstance(context).fetch(diseaseKey, displayNameDe)

    fun observeHistory(userEmail: String?): LiveData<List<DiseaseDiagnosis>> =
        dao.observeAllForUser(userEmail)

    fun observeHistoryForPlant(plantId: Int): LiveData<List<DiseaseDiagnosis>> =
        dao.observeForPlant(plantId)

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.deleteById(id) }

    suspend fun deleteAll() = withContext(Dispatchers.IO) { dao.deleteAll() }

    // Sprint-3 Task 3.2b: blocking helpers for Java callers.
    fun getForPlantBlocking(plantId: Int): List<DiseaseDiagnosis> = dao.getForPlantBlocking(plantId)

    /** Kept for binary compatibility with the previous TFLite/PlantNet repository — no-op. */
    fun release() {}

    companion object {
        @Volatile
        private var INSTANCE: DiseaseDiagnosisRepository? = null

        @JvmStatic
        fun getInstance(context: Context): DiseaseDiagnosisRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DiseaseDiagnosisRepository(context).also { INSTANCE = it }
            }
        }
    }
}

/**
 * Maps a single Gemini suggestion onto the existing [DiseaseResult] shape
 * consumed by the adapter and the treatment-plan builder.
 *
 * Returns null when both `display_name` and `disease_key` are blank — that
 * row is unusable, but other rows in the same response can still render.
 */
private fun GeminiDiseaseEntry.toDiseaseResult(): DiseaseResult? {
    val display = displayName?.takeIf { it.isNotBlank() }
        ?: diseaseKey?.takeIf { it.isNotBlank() }
        ?: return null
    val key = diseaseKey?.takeIf { it.isNotBlank() } ?: display
    val safeConfidence = confidence
        .coerceIn(0.0, 1.0)
        .toFloat()
    return DiseaseResult(
        diseaseKey = key,
        displayName = display,
        cropName = null,        // Gemini classifies the disease, not the host crop.
        isHealthy = isHealthy,
        confidence = safeConfidence,
        advice = advice?.takeIf { it.isNotBlank() }
    )
}

/**
 * Map Gemini's narrower error vocabulary onto the existing [PlantNetError]
 * the UI already knows how to localise. SAFETY_BLOCKED is rare (we relax
 * the safety thresholds in the request) and falls into UNKNOWN — the raw
 * `blockReason=...` is preserved in `rawMessage` for diagnostics.
 */
private fun GeminiError.toPlantNetError(): PlantNetError = when (this) {
    GeminiError.INVALID_API_KEY -> PlantNetError.INVALID_API_KEY
    GeminiError.QUOTA_EXCEEDED -> PlantNetError.QUOTA_EXCEEDED
    GeminiError.NO_INTERNET -> PlantNetError.NO_INTERNET
    GeminiError.TIMEOUT -> PlantNetError.TIMEOUT
    GeminiError.SERVER_ERROR -> PlantNetError.SERVER_ERROR
    GeminiError.SAFETY_BLOCKED -> PlantNetError.UNKNOWN
    GeminiError.UNKNOWN -> PlantNetError.UNKNOWN
}
