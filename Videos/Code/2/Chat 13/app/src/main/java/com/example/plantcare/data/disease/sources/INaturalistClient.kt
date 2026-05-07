package com.example.plantcare.data.disease.sources

import android.util.Log
import com.example.plantcare.CrashReporter
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * iNaturalist reference-image client.
 *
 * Search strategy:
 *  1. `/v1/observations` with `q=<term>`, `photo_license=cc0,cc-by,cc-by-sa`,
 *     `quality_grade=research`, `per_page=10`. The CC license filter is
 *     **mandatory** — iNaturalist's default `CC-BY-NC` license forbids
 *     commercial use, and PlantCare ships with a Pro tier (commercial).
 *  2. Each observation can have multiple photos; we take the first
 *     "medium" rendition per observation, capped at 4 photos total.
 *
 * Search term: prefer the German display name (e.g. "Spinnmilben") because
 * iNaturalist's full-text search matches German common names too. If empty,
 * fall back to the English snake_case key transformed to spaces.
 *
 * Etiquette: iNaturalist asks for a User-Agent identifying the app, with
 * a contact email or URL. We pass `userAgent` from the caller.
 */
class INaturalistClient(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val userAgent: String
) {

    suspend fun fetchImages(
        searchTermDe: String?,
        fallbackEnglish: String?
    ): List<ReferenceImageDto> = withContext(Dispatchers.IO) {
        val term = listOfNotNull(
            searchTermDe?.takeIf { it.isNotBlank() },
            fallbackEnglish?.takeIf { it.isNotBlank() }?.replace('_', ' ')
        ).firstOrNull() ?: return@withContext emptyList()

        try {
            val encoded = URLEncoder.encode(term, "UTF-8")
            val url = "https://api.inaturalist.org/v1/observations" +
                    "?q=$encoded" +
                    "&photo_license=cc0%2Ccc-by%2Ccc-by-sa" +
                    "&quality_grade=research" +
                    "&per_page=10" +
                    "&order=desc&order_by=votes"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.d(TAG, "observations '$term' -> HTTP ${resp.code}")
                    return@withContext emptyList<ReferenceImageDto>()
                }
                val body = resp.body?.string() ?: return@withContext emptyList()
                val root = gson.fromJson(body, JsonObject::class.java)
                    ?: return@withContext emptyList()

                val results = root.getAsJsonArray("results")
                    ?: return@withContext emptyList()

                val out = mutableListOf<ReferenceImageDto>()
                for (entry in results) {
                    val obs = entry as? JsonObject ?: continue
                    val obsId = obs.get("id")?.asLong
                    val photos = obs.getAsJsonArray("photos") ?: continue
                    for (photo in photos) {
                        val p = photo as? JsonObject ?: continue
                        // iNaturalist serves a "square" / "small" / "medium"
                        // / "large" / "original" series via URL substitution.
                        // Their API only exposes the square URL directly —
                        // larger sizes are derived by replacing the path
                        // segment, which is the documented pattern.
                        val baseUrl = p.get("url")?.asString ?: continue
                        val mediumUrl = baseUrl.replace("/square.", "/medium.")
                        val largeUrl = baseUrl.replace("/square.", "/large.")

                        val attribution = p.get("attribution")?.asString
                            ?: "iNaturalist contributor"
                        val pageUrl = obsId?.let { "https://www.inaturalist.org/observations/$it" }

                        out += ReferenceImageDto(
                            imageUrl = largeUrl,
                            thumbnailUrl = mediumUrl,
                            source = SOURCE,
                            attribution = attribution,
                            pageUrl = pageUrl
                        )
                        if (out.size >= MAX_RESULTS) return@withContext out
                    }
                }
                out
            }
        } catch (t: Throwable) {
            CrashReporter.log(t)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "INaturalistClient"
        const val SOURCE = "inaturalist"
        private const val MAX_RESULTS = 4
    }
}
