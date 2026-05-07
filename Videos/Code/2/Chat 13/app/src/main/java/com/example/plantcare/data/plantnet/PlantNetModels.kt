package com.example.plantcare.data.plantnet

import com.google.gson.annotations.SerializedName

/**
 * PlantNet API v2 response models.
 * API docs: https://my-api.plantnet.org/
 */

data class PlantNetResponse(
    @SerializedName("query")
    val query: PlantNetQuery?,
    @SerializedName("language")
    val language: String?,
    @SerializedName("preferedReferential")
    val preferredReferential: String?,
    @SerializedName("results")
    val results: List<PlantNetResult>?,
    @SerializedName("remainingIdentificationRequests")
    val remainingRequests: Int?
)

data class PlantNetQuery(
    @SerializedName("project")
    val project: String?,
    @SerializedName("images")
    val images: List<String>?,
    @SerializedName("organs")
    val organs: List<String>?
)

data class PlantNetResult(
    @SerializedName("score")
    val score: Double,
    @SerializedName("species")
    val species: PlantNetSpecies,
    @SerializedName("gbif")
    val gbif: PlantNetGbif?,
    @SerializedName("images")
    val images: List<PlantNetImage>? = null
)

data class PlantNetImage(
    @SerializedName("organ")
    val organ: String?,
    @SerializedName("author")
    val author: String?,
    @SerializedName("license")
    val license: String?,
    @SerializedName("citation")
    val citation: String?,
    @SerializedName("url")
    val url: PlantNetImageUrls?
)

data class PlantNetImageUrls(
    @SerializedName("o")
    val original: String?,
    @SerializedName("m")
    val medium: String?,
    @SerializedName("s")
    val small: String?
)

data class PlantNetSpecies(
    @SerializedName("scientificNameWithoutAuthor")
    val scientificName: String?,
    @SerializedName("scientificNameAuthorship")
    val authorship: String?,
    @SerializedName("genus")
    val genus: PlantNetTaxon?,
    @SerializedName("family")
    val family: PlantNetTaxon?,
    @SerializedName("commonNames")
    val commonNames: List<String>?
)

data class PlantNetTaxon(
    @SerializedName("scientificNameWithoutAuthor")
    val scientificName: String?,
    @SerializedName("scientificNameAuthorship")
    val authorship: String?
)

data class PlantNetGbif(
    @SerializedName("id")
    val id: String?
)

/**
 * UI-friendly model for displaying identification results.
 */
data class IdentificationResult(
    val scientificName: String,
    val commonName: String?,
    val family: String?,
    val confidence: Double, // 0.0 - 1.0
    val gbifId: String?,
    /**
     * Small representative image from PlantNet for this suggestion.
     * Null when the API didn't return any related image (e.g. an old cache
     * from when include-related-images was disabled).
     */
    val imageUrl: String? = null,
    /**
     * Optional larger version of the same thumbnail, usable for a zoomed preview
     * if we ever add a tap‑to‑enlarge gesture on the suggestion list.
     */
    val largeImageUrl: String? = null
) {
    val confidencePercent: Int
        get() = (confidence * 100).toInt()
}
