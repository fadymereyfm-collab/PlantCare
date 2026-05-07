package com.example.plantcare.data.repository

import android.content.Context
import com.example.plantcare.AppDatabase
import com.example.plantcare.BuildConfig
import com.example.plantcare.data.disease.DiseaseReferenceImage
import com.example.plantcare.data.disease.sources.INaturalistClient
import com.example.plantcare.data.disease.sources.PlantVillageCdnSource
import com.example.plantcare.data.disease.sources.ReferenceImageDto
import com.example.plantcare.data.disease.sources.WikimediaCommonsClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reference-image repository for the differential-diagnosis UI.
 *
 * Cache-first ordering: a hit in `disease_reference_image` younger than
 * [CACHE_TTL_MS] short-circuits all network work. On miss, the three
 * source clients run in parallel (PlantVillage + Wikimedia + iNaturalist),
 * the merged set is persisted, and at most [MAX_RETURNED] images are returned
 * to the caller.
 *
 * Source ranking (encoded in the DAO's `getFreshForKey` ORDER BY):
 *   PlantVillage > Wikimedia > iNaturalist
 * Reasoning:
 *   - PlantVillage = lab-grade, expert-labelled, deterministic per disease key.
 *   - Wikimedia    = curated article lead image, license-clean by definition.
 *   - iNaturalist  = real-world community shots, useful but noisier.
 *
 * Network: a dedicated [OkHttpClient] with conservative timeouts so a slow
 * CDN never blocks the candidate cards (the diagnosis itself is already
 * showing — references are progressive enrichment).
 */
class DiseaseReferenceImageRepository private constructor(appContext: Context) {

    private val context: Context = appContext.applicationContext
    private val dao = AppDatabase.getInstance(context).diseaseReferenceImageDao()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val userAgent = "PlantCare/${BuildConfig.VERSION_NAME} " +
            "(${BuildConfig.APPLICATION_ID}; Android disease-reference-fetch)"

    private val wikimedia = WikimediaCommonsClient(httpClient, gson, userAgent)
    private val inaturalist = INaturalistClient(httpClient, gson, userAgent)
    private val plantVillage = PlantVillageCdnSource(httpClient, gson, userAgent)

    /** One-shot guard so the version check only fires on the first fetch per process. */
    private val versionChecked = AtomicBoolean(false)

    /**
     * Fetch up to [MAX_RETURNED] reference images for a disease candidate.
     * Returns cached rows when available; otherwise fans out to the three
     * sources in parallel and persists what comes back.
     *
     * Safe to call repeatedly per session — cache lookups are O(1).
     *
     * @param diseaseKey      Stable Gemini key (e.g. "spider_mites").
     * @param displayNameDe   German display label (e.g. "Spinnmilben") —
     *                        primary search term for Wikipedia / iNaturalist.
     */
    suspend fun fetch(
        diseaseKey: String,
        displayNameDe: String?
    ): List<DiseaseReferenceImage> {
        // First call per process: nuke any cache rows from a previous
        // policy generation. Source-selection rules and curated Wikipedia
        // titles change between iterations of this feature; using a stored
        // SharedPreferences integer as the version sentinel is reliable
        // (unlike wall-clock comparisons, which break when the timestamp
        // is set to a future moment relative to the device's clock and
        // freshly-written rows fail the cutoff).
        if (versionChecked.compareAndSet(false, true)) {
            ensureCachePolicyCurrent()
        }

        if (diseaseKey.isBlank() ||
            diseaseKey.lowercase() in NO_REFERENCE_KEYS
        ) {
            // Reference images are nonsensical for "healthy" / "unclear" /
            // generic physiological-stress states (no Wikipedia article,
            // no iNaturalist taxon, no PlantVillage folder). Short-circuit
            // here so the carousel falls straight to "Keine Referenzbilder
            // verfügbar" instead of wasting a 30s round-trip on each source.
            return emptyList()
        }

        val cutoff = System.currentTimeMillis() - CACHE_TTL_MS
        val cached = withContext(Dispatchers.IO) {
            dao.getFreshForKey(diseaseKey, cutoff, MAX_RETURNED)
        }
        if (cached.isNotEmpty()) return cached

        // 2. Cache miss → fan-out the available sources in parallel.
        //
        // iNaturalist works well for species-level pests (spider mites,
        // mealybugs, aphids — each is a clear taxonomic group) but is
        // pure noise for general plant diseases (root_rot, leaf_spot,
        // mehltau): a `q=Wurzelfäule` search returns observations of
        // wood-rotting fungi, not plant root rot symptoms. Skip iNat for
        // those keys — Wikipedia + PlantVillage cover them better with
        // article-curated images.
        val keyLower = diseaseKey.lowercase()
        val useINaturalist = keyLower !in NO_INATURALIST_KEYS

        val merged = coroutineScope {
            val pvJob = async { plantVillage.fetchImages(diseaseKey) }
            val wmJob = async { wikimedia.fetchImages(diseaseKey, displayNameDe) }
            val inJob = if (useINaturalist) {
                async { inaturalist.fetchImages(displayNameDe, diseaseKey) }
            } else null
            pvJob.await() + wmJob.await() + (inJob?.await().orEmpty())
        }
        if (merged.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val rows = merged.distinctBy { it.imageUrl }.map { it.toEntity(diseaseKey, now) }
        withContext(Dispatchers.IO) { dao.insertAll(rows) }

        // Re-read so the ORDER BY in [DiseaseReferenceImageDao.getFreshForKey]
        // (PlantVillage > Wikimedia > iNaturalist) takes effect on the
        // returned list — keeps source-ranking centralised in the DAO.
        return withContext(Dispatchers.IO) {
            dao.getFreshForKey(diseaseKey, cutoff, MAX_RETURNED)
        }
    }

    /** Periodic cache pruning — invoked opportunistically from the activity. */
    suspend fun pruneStale() {
        val cutoff = System.currentTimeMillis() - CACHE_TTL_MS * 4 // 1 year hard ceiling
        withContext(Dispatchers.IO) { dao.deleteOlderThan(cutoff) }
    }

    /**
     * On the first fetch of each process, compare the on-disk policy
     * generation marker against [SOURCE_POLICY_VERSION]. If they differ,
     * the cached rows were produced by an older source-selection policy
     * (different Wikipedia titles, different iNaturalist filter, etc.)
     * and must be discarded so freshly-fetched rows reflect the current
     * policy. After wiping, the marker is bumped to the current version.
     *
     * Idempotent within a process — guarded by [versionChecked].
     */
    private suspend fun ensureCachePolicyCurrent() {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val stored = prefs.getInt(KEY_POLICY_VERSION, 0)
        if (stored != SOURCE_POLICY_VERSION) {
            withContext(Dispatchers.IO) { dao.deleteAll() }
            prefs.edit().putInt(KEY_POLICY_VERSION, SOURCE_POLICY_VERSION).apply()
        }
    }

    private fun ReferenceImageDto.toEntity(diseaseKey: String, fetchedAt: Long) =
        DiseaseReferenceImage(
            diseaseKey = diseaseKey,
            imageUrl = imageUrl,
            thumbnailUrl = thumbnailUrl,
            source = source,
            attribution = attribution,
            pageUrl = pageUrl,
            fetchedAt = fetchedAt
        )

    companion object {
        private const val MAX_RETURNED = 5
        private const val CACHE_TTL_MS = 90L * 24L * 60L * 60L * 1000L // 90 days

        /**
         * Bump this integer whenever source-selection rules change in a way
         * that should invalidate previously-cached URLs (curated Wikipedia
         * title swap, new iNaturalist exclusion, new Commons direct override).
         *
         * On the first fetch after each bump, the entire cache is wiped and
         * subsequent fetches re-populate it under the new policy. Stored
         * marker: SharedPreferences("prefs") key [KEY_POLICY_VERSION].
         *
         * Generation log:
         *   1 = original release with curated WikiTitles map
         *   2 = added Commons direct-file override for root_rot,
         *       fixed bacterial_leaf_spot to Xanthomonas_campestris,
         *       removed physiological_stress from prompt enum
         *   3 = visual audit fix — leaf_spot swapped from "Leaf_spot"
         *       (whose lead image returned a scale insect, not a leaf)
         *       to "Septoria" (verified tomato Septoria leaf spots)
         */
        private const val SOURCE_POLICY_VERSION = 3
        private const val KEY_POLICY_VERSION = "disease_ref_policy_version"

        /**
         * Disease keys that the prompt schema produces but for which all
         * three reference-image sources will return empty (Wikipedia has
         * no article, iNaturalist has no taxon, PlantVillage has no folder).
         * Short-circuit returns these straight from the repository so the
         * UI can flip to the empty-state immediately.
         */
        private val NO_REFERENCE_KEYS = setOf(
            "healthy",
            "unclear",
            "physiological_stress"
        )

        /**
         * Disease keys that are NOT a single species — they're general
         * plant pathology categories caused by many different fungi /
         * bacteria. iNaturalist's full-text search returns observations
         * of the *causative organism* (often wood-rotting fungi for
         * root_rot, leaf-litter fungi for leaf_spot, etc.) rather than
         * symptom photos on plants. The result is wildly off-topic
         * imagery — a piece of weathered wood instead of a sick root.
         *
         * For these keys, we restrict to Wikipedia (article-curated) and
         * PlantVillage (lab-grade), where the imagery is reliably about
         * the disease as it appears on plants.
         */
        private val NO_INATURALIST_KEYS = setOf(
            "root_rot",
            "leaf_spot",
            "powdery_mildew",
            "downy_mildew",
            "bacterial_leaf_spot",
            "anthracnose",
            "rust",
            "botrytis",
            "chlorosis_nutrient",
            "edema"
        )

        @Volatile
        private var INSTANCE: DiseaseReferenceImageRepository? = null

        fun getInstance(context: Context): DiseaseReferenceImageRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DiseaseReferenceImageRepository(context).also { INSTANCE = it }
            }
        }
    }
}
