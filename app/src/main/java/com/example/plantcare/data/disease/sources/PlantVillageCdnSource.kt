package com.example.plantcare.data.disease.sources

import android.util.Log
import com.example.plantcare.CrashReporter
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * Crop-disease reference images from the PlantVillage Dataset, served via
 * jsDelivr CDN (free, unlimited).
 *
 * Coverage is **crop-only** (apple, tomato, potato, grape, corn, cherry,
 * strawberry, pepper, peach) — the houseplant majority of our user base
 * misses this fallback, but the photo quality for crops is unmatched
 * (lab-controlled, expert-labelled). This source fires only when the
 * Gemini `disease_key` matches one of the [KEY_TO_FOLDER] entries.
 *
 * Two-step fetch:
 *  1. GitHub Contents API lists JPG filenames inside the matched folder
 *     (60 req/hour anonymous; caching makes this a one-time cost per
 *     disease key, after which the Room cache serves all subsequent users).
 *  2. We hand-pick the first three filenames and synthesise jsDelivr URLs
 *     against `@master` so jsDelivr's CDN serves them — not the GitHub raw
 *     domain (which has its own throttling).
 *
 * Licence: the dataset is published under CC-BY-4.0 (Mohanty et al. 2016
 * https://github.com/spMohanty/PlantVillage-Dataset) — safe for in-app
 * display with credit, which we render via [ReferenceImageDto.attribution].
 */
class PlantVillageCdnSource(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val userAgent: String
) {

    suspend fun fetchImages(diseaseKey: String?): List<ReferenceImageDto> =
        withContext(Dispatchers.IO) {
            if (diseaseKey.isNullOrBlank()) return@withContext emptyList()
            val folder = KEY_TO_FOLDER[diseaseKey] ?: return@withContext emptyList()
            try {
                val encodedFolder = URLEncoder.encode(folder, "UTF-8")
                    .replace("+", "%20")
                val listUrl = "https://api.github.com/repos/$REPO/contents/raw/color/$encodedFolder?ref=master"
                val request = Request.Builder()
                    .url(listUrl)
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/vnd.github+json")
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.d(TAG, "list '$folder' -> HTTP ${resp.code}")
                        return@withContext emptyList<ReferenceImageDto>()
                    }
                    val body = resp.body?.string() ?: return@withContext emptyList()
                    val arr = gson.fromJson(body, JsonArray::class.java)
                        ?: return@withContext emptyList()
                    val out = mutableListOf<ReferenceImageDto>()
                    for (entry in arr) {
                        val obj = entry as? JsonObject ?: continue
                        val name = obj.get("name")?.asString ?: continue
                        if (!name.endsWith(".JPG", ignoreCase = true) &&
                            !name.endsWith(".JPEG", ignoreCase = true) &&
                            !name.endsWith(".PNG", ignoreCase = true)
                        ) continue
                        val encodedName = URLEncoder.encode(name, "UTF-8")
                            .replace("+", "%20")
                        val url = "https://cdn.jsdelivr.net/gh/$REPO@master/raw/color/" +
                                "$encodedFolder/$encodedName"
                        out += ReferenceImageDto(
                            imageUrl = url,
                            thumbnailUrl = url,
                            source = SOURCE,
                            attribution = "PlantVillage / Mohanty et al. — CC BY",
                            pageUrl = "https://github.com/$REPO"
                        )
                        if (out.size >= MAX_RESULTS) break
                    }
                    out
                }
            } catch (t: Throwable) {
                CrashReporter.log(t)
                emptyList()
            }
        }

    companion object {
        private const val TAG = "PlantVillageCdn"
        const val SOURCE = "plantvillage"
        private const val REPO = "spMohanty/PlantVillage-Dataset"
        private const val MAX_RESULTS = 3

        /**
         * Mapping from Gemini's [com.example.plantcare.data.disease.DiseaseResult.diseaseKey]
         * to a PlantVillage folder name. Keep this list small — only entries
         * with a clean botanical analog. Folder names mirror the upstream
         * dataset exactly (incl. parentheses, dots, spaces).
         */
        private val KEY_TO_FOLDER: Map<String, String> = mapOf(
            "spider_mites" to "Tomato___Spider_mites Two-spotted_spider_mite",
            "powdery_mildew" to "Cherry_(including_sour)___Powdery_mildew",
            "early_blight" to "Tomato___Early_blight",
            "late_blight" to "Tomato___Late_blight",
            "leaf_spot" to "Tomato___Septoria_leaf_spot",
            "septoria_leaf_spot" to "Tomato___Septoria_leaf_spot",
            "bacterial_leaf_spot" to "Tomato___Bacterial_spot",
            "bacterial_spot" to "Tomato___Bacterial_spot",
            "common_rust" to "Corn_(maize)___Common_rust_",
            "rust" to "Corn_(maize)___Common_rust_",
            "apple_scab" to "Apple___Apple_scab",
            "scab" to "Apple___Apple_scab",
            "black_rot" to "Apple___Black_rot",
            "cedar_apple_rust" to "Apple___Cedar_apple_rust",
            "leaf_scorch" to "Strawberry___Leaf_scorch",
            "mosaic_virus" to "Tomato___Tomato_mosaic_virus",
            "yellow_leaf_curl_virus" to "Tomato___Tomato_Yellow_Leaf_Curl_Virus"
        )
    }
}
