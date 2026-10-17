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
 * Wikipedia / Wikimedia Commons reference-image client.
 *
 * Strategy — manually-curated title map, then Gemini display-name fallback:
 *  1. Look up the disease key in [DISEASE_TITLES]. The map was built by
 *     hitting the live `de.wikipedia.org/api/rest_v1/page/summary` endpoint
 *     for every key in the prompt schema and inspecting which titles
 *     return a *symptom-relevant* lead image (vs. redirects to unrelated
 *     articles). Several Gemini display-names map to article titles that
 *     redirect to wrong topics — for example "Wurzelfäule" → "Holzfäule"
 *     (wood rot, not root rot), or EN "Bacterial leaf spot" → "Bacterial
 *     leaf scorch" (different disease on oak trees). The curated map
 *     short-circuits all such pitfalls.
 *  2. If the key is unknown (Gemini emitted something off-schema), fall
 *     back to the German display-name as the Wikipedia title — the
 *     legacy behaviour, only used for unmapped keys.
 *  3. Try DE first, then EN, then give up.
 *
 * License: every Wikipedia / Commons image carries an open licence
 * (CC-BY-SA / CC-BY / CC0 / public domain). Attribution is rendered in
 * the carousel's caption so the user sees the source.
 *
 * Etiquette: Wikimedia rate-limits anonymous requests heavily. The
 * descriptive User-Agent ("PlantCare/<versionName>") categorises us
 * correctly so we don't get globally throttled.
 */
class WikimediaCommonsClient(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val userAgent: String
) {

    suspend fun fetchImages(
        diseaseKey: String?,
        displayNameDe: String?
    ): List<ReferenceImageDto> = withContext(Dispatchers.IO) {
        val key = diseaseKey?.lowercase()?.takeIf { it.isNotBlank() }

        // 1. Direct Commons file overrides — used for keys where ALL
        //    article-based lookups give wrong images (root rot's
        //    Phytophthora article shows leek leaf spots, not roots;
        //    Wurzelfäule article redirects to wood rot).
        key?.let { COMMONS_FILE_OVERRIDES[it] }?.let { filename ->
            return@withContext listOf(buildCommonsFileDto(filename))
        }

        // 2. Curated article titles per key.
        val curated = key?.let { DISEASE_TITLES[it] }
        if (curated != null) {
            curated.deTitle?.let { title ->
                val r = fetchSummary("de", title)
                if (r.isNotEmpty()) return@withContext r
            }
            curated.enTitle?.let { title ->
                val r = fetchSummary("en", title)
                if (r.isNotEmpty()) return@withContext r
            }
            return@withContext emptyList()
        }

        // 3. Unmapped key (shouldn't happen — prompt enforces a closed enum,
        //    but Gemini occasionally hallucinates a key not in the schema).
        //    Fall back to the German display-name as a best-effort title.
        if (!displayNameDe.isNullOrBlank()) {
            return@withContext fetchSummary("de", displayNameDe)
        }
        emptyList()
    }

    /**
     * Build a Commons direct-file DTO using `Special:FilePath/<filename>` —
     * the documented stable resolver that 301-redirects to the actual
     * upload.wikimedia.org URL. Glide follows redirects automatically, so
     * the user sees the image with no extra round-trip on our side.
     */
    private fun buildCommonsFileDto(filename: String): ReferenceImageDto {
        val encoded = URLEncoder.encode(filename, "UTF-8").replace("+", "%20")
        val url = "https://commons.wikimedia.org/wiki/Special:FilePath/$encoded"
        val pageUrl = "https://commons.wikimedia.org/wiki/File:$encoded"
        return ReferenceImageDto(
            imageUrl = url,
            thumbnailUrl = url,
            source = SOURCE,
            attribution = "Wikimedia Commons — CC BY-SA",
            pageUrl = pageUrl
        )
    }

    private fun fetchSummary(lang: String, title: String): List<ReferenceImageDto> {
        return try {
            val encoded = URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
            val url = "https://$lang.wikipedia.org/api/rest_v1/page/summary/$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.d(TAG, "summary $lang/$title -> HTTP ${resp.code}")
                    return emptyList()
                }
                val body = resp.body?.string() ?: return emptyList()
                val json = gson.fromJson(body, JsonObject::class.java) ?: return emptyList()

                // Gracefully ignore disambiguation / missing-page responses.
                val type = json.get("type")?.asString
                if (type == "disambiguation" || type == "no-extract") {
                    return emptyList()
                }

                val original = json.getAsJsonObject("originalimage")
                    ?.get("source")?.asString
                val thumb = json.getAsJsonObject("thumbnail")
                    ?.get("source")?.asString
                val pageUrl = json
                    .getAsJsonObject("content_urls")
                    ?.getAsJsonObject("desktop")
                    ?.get("page")?.asString

                val effective = original ?: thumb
                if (effective.isNullOrBlank()) return emptyList()
                listOf(
                    ReferenceImageDto(
                        imageUrl = effective,
                        thumbnailUrl = thumb,
                        source = SOURCE,
                        attribution = "Wikipedia ($lang) — CC BY-SA",
                        pageUrl = pageUrl
                    )
                )
            }
        } catch (t: Throwable) {
            CrashReporter.log(t)
            emptyList()
        }
    }

    /**
     * Pair of pre-verified Wikipedia article titles for one disease key.
     * `null` means "the language's article is unreliable / 404 / wrong
     * redirect — skip this language". A pair where both fields are null
     * means "Wikipedia has no good source for this key" — caller should
     * fall back to PlantVillage / iNaturalist only.
     */
    data class WikiTitlePair(val deTitle: String?, val enTitle: String?)

    companion object {
        private const val TAG = "WikimediaClient"
        const val SOURCE = "wikimedia"

        /**
         * Curated map of disease keys → known-good Wikipedia article titles.
         * Each entry was verified live by hitting the REST summary endpoint
         * during the 2026-05-03 audit. Entries with `null` for one language
         * mean that language's article either 404s, redirects to the wrong
         * topic, or has no lead image.
         *
         * Verification log (2026-05-03):
         *  - root_rot:           DE Wurzelfäule REDIRECTS to Holzfäule (wood rot photo, wrong).
         *                        EN Root_rot has no lead image. → use Phytophthora (root-rot pathogen genus).
         *  - bacterial_leaf_spot: EN redirects to "Bacterial leaf scorch" (different disease, oak photo).
         *                        → use Xanthomonas (bacterial-leaf-spot pathogen genus).
         *  - anthracnose:         EN redirects to Canker (different topic).
         *                        DE Anthraknose article exists but has no lead image.
         *                        → use Colletotrichum (anthracnose-causing genus).
         *  - downy_mildew:       DE Falsche_Mehltaupilze 404. Use singular: Falscher_Mehltau.
         *  - leaf_spot:          DE Blattfleckenkrankheit redirects to corn-specific, no image.
         *                        → use EN Leaf_spot.
         *  - edema:              No reliable Wikipedia article in either language. Skip.
         *  - thrips:             DE Thripse redirects to Fransenflügler (correct family); image OK.
         */
        private val DISEASE_TITLES: Map<String, WikiTitlePair> = mapOf(
            // Species-pest keys (DE Wikipedia is reliable for all)
            "spider_mites"     to WikiTitlePair("Spinnmilben",        "Spider_mite"),
            "mealybugs"        to WikiTitlePair("Schmierläuse",       "Mealybug"),
            "scale_insects"    to WikiTitlePair("Schildläuse",        "Scale_insect"),
            "aphids"           to WikiTitlePair("Blattläuse",         "Aphid"),
            "thrips"           to WikiTitlePair("Thripse",            "Thrips"),
            "whiteflies"       to WikiTitlePair("Mottenschildläuse",  "Whitefly"),
            "fungus_gnats"     to WikiTitlePair("Trauermücken",       "Sciaridae"),
            "leaf_miners"      to WikiTitlePair("Minierfliegen",      "Leaf_miner"),

            // Pathogen-name diseases (DE preferred)
            "powdery_mildew"   to WikiTitlePair("Echte Mehltaupilze", "Powdery_mildew"),
            "downy_mildew"     to WikiTitlePair("Falscher Mehltau",   "Downy_mildew"),
            "rust"             to WikiTitlePair("Rostpilze",          "Rust_(fungus)"),
            "botrytis"         to WikiTitlePair("Grauschimmelfäule",  "Botrytis_cinerea"),
            "chlorosis_nutrient" to WikiTitlePair("Chlorose",         "Chlorosis"),

            // EN-only — DE article is missing or wrongly redirects
            // leaf_spot: EN "Leaf_spot" article's lead image filename is
            // 'Cercospora_capsici.jpg (note leading apostrophe) — visual
            // audit on 2026-05-03 confirmed the actual binary content is
            // a scale-insect photo (Icerya-like), NOT a leaf with spots.
            // Genus article "Septoria" returns a real tomato leaf with
            // Septoria leaf-spot symptoms — much better.
            "leaf_spot"        to WikiTitlePair(null, "Septoria"),
            "anthracnose"      to WikiTitlePair(null, "Colletotrichum"),
            // bacterial_leaf_spot: generic Xanthomonas article shows just a
            // microscope culture (useless for visual ID). The species-level
            // article Xanthomonas_campestris carries an actual plant-disease
            // photo (Black_rot_of_crucifers) — use that instead.
            "bacterial_leaf_spot" to WikiTitlePair(null, "Xanthomonas_campestris"),

            // No reliable Wikipedia source — handled gracefully (empty
            // carousel + "Keine Referenzbilder verfügbar" caption).
            "edema"            to WikiTitlePair(null, null),

            // root_rot uses [COMMONS_FILE_OVERRIDES] below — every
            // article-based lookup is misleading: Phytophthora article shows
            // a leek leaf with paper-spot, not roots; Wurzelfäule (DE)
            // redirects to Holzfäule (wood rot); Root_rot (EN) has no lead
            // image at all. The override points to a real chickpea-root-rot
            // photo from the Commons "Plant rot" category.
            "root_rot"         to WikiTitlePair(null, null)
        )

        /**
         * For keys where every Wikipedia article-based lookup gives a
         * misleading or absent image, point directly at a Commons file
         * that we've manually verified represents the disease as it
         * appears on a plant. These were chosen by browsing the
         * "Plant rot" / similar Commons categories during the
         * 2026-05-03 audit.
         *
         * Resolution path: Special:FilePath/<filename> → 301 redirect to
         * upload.wikimedia.org/... (Glide follows redirects natively).
         */
        private val COMMONS_FILE_OVERRIDES: Map<String, String> = mapOf(
            "root_rot" to "Root_rot_in_cicer_arietinum_(hydro-grown).jpg"
        )
    }
}
