package com.example.plantcare.data.disease.sources

/**
 * Source-agnostic shape produced by each reference-image client. The
 * repository merges these into the persisted [com.example.plantcare.data.disease.DiseaseReferenceImage]
 * cache rows after attaching the disease key + fetch timestamp.
 *
 * @property imageUrl     Full-size JPEG/PNG URL — used by Glide in the carousel.
 * @property thumbnailUrl Optional smaller URL for fast list rendering.
 * @property source       One of "wikimedia" / "inaturalist" / "plantvillage".
 * @property attribution  Author/license credit shown under the image.
 * @property pageUrl      Optional source page URL (Wikipedia article,
 *                        iNaturalist observation) for "Quelle anzeigen".
 */
data class ReferenceImageDto(
    val imageUrl: String,
    val thumbnailUrl: String? = null,
    val source: String,
    val attribution: String? = null,
    val pageUrl: String? = null
)
