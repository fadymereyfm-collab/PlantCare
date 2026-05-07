package com.example.plantcare.data.disease

/**
 * Single disease suggestion returned by the cloud diagnosis pipeline (PlantNet).
 *
 * Replaces the previous TFLite/PlantVillage shape (which always carried a
 * `cropName` field). Cloud responses describe a pathology directly, so the
 * crop label is optional.
 *
 * @property diseaseKey  Stable identifier — scientific name when available,
 *                       otherwise the localized display name. Used by
 *                       `TreatmentPlanBuilder` to pick a plan template.
 * @property displayName User-facing localized name returned by the API.
 * @property cropName    Optional plant common name when the API attaches one.
 * @property isHealthy   True when the suggestion text indicates a healthy plant.
 * @property confidence  Probability between 0.0 and 1.0.
 * @property advice      Optional treatment / care hint from the API.
 */
data class DiseaseResult(
    val diseaseKey: String,
    val displayName: String,
    val cropName: String? = null,
    val isHealthy: Boolean,
    val confidence: Float,
    val advice: String? = null
)
