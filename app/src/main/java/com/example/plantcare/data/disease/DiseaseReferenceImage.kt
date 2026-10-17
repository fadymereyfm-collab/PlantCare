package com.example.plantcare.data.disease

import androidx.room.Entity
import androidx.room.Index

/**
 * Cached reference image for a disease key, used by the differential-diagnosis
 * UI to show users 3-5 example photos per candidate so they can visually verify
 * which Gemini suggestion actually matches their plant.
 *
 * Sources (in order of preference):
 *  - "wikimedia"     — Wikipedia Commons (highest reliability, CC-clean)
 *  - "inaturalist"   — community observations (CC-BY/CC0 only, real-world shots)
 *  - "plantvillage"  — curated dataset (crop-specific, lab quality)
 *
 * Composite primary key on (diseaseKey, imageUrl) means cache writes are
 * idempotent: re-fetching the same disease and seeing the same URL is a no-op.
 *
 * No FK to `disease_diagnosis` — these are reference photos, not user data,
 * and a deleted diagnosis row must not cascade-delete the cached references
 * other users will reuse.
 */
@Entity(
    tableName = "disease_reference_image",
    primaryKeys = ["diseaseKey", "imageUrl"],
    indices = [Index("diseaseKey"), Index("fetchedAt")]
)
data class DiseaseReferenceImage(
    val diseaseKey: String,
    val imageUrl: String,
    val thumbnailUrl: String? = null,
    val source: String,
    val attribution: String? = null,
    val pageUrl: String? = null,
    val fetchedAt: Long
)
