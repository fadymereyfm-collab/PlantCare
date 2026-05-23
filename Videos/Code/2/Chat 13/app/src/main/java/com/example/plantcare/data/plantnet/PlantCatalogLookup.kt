package com.example.plantcare.data.plantnet

import android.content.Context
import com.example.plantcare.Plant
import com.example.plantcare.ReminderUtils
import com.example.plantcare.WikiImageHelper
import com.example.plantcare.data.repository.PlantRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridge between PlantNet identification results and the local plant
 * catalog (`plants.csv` → Room with `isUserPlant = 0`).
 *
 * v17 rewrite — primary lookup now goes through `scientificName` because
 * the catalog CSV carries it as a first-class column. The pre-v17 flow
 * tried 4 fragile name-based heuristics with low recall on the expanded
 * 1500+ row catalog. The new flow:
 *
 * 1. Latin name exact match — deterministic, 1:1 against the CSV column.
 * 2. Latin name partial match — covers PlantNet returning a fully
 *    qualified binomial when the catalog only carries the genus
 *    (e.g. "Monstera deliciosa" → catalog row "Monstera").
 * 3. German common name exact match — kept as a fallback for legacy CSV
 *    rows that were imported before the schema upgrade and still don't
 *    have a Latin name populated.
 * 4. Reverse mapping via `WikiImageHelper.germanNameForScientific` —
 *    last-resort guard for the same legacy gap.
 *
 * Two outputs:
 *   - `findMatch(...)` returns the full catalog `Plant` (used by the
 *     Identify UI to render the "In unserem Katalog" badge AND to
 *     pre-fill the Add dialog with curated catalog data).
 *   - `findByIdentification(...)` is a back-compat shim that wraps
 *     `findMatch` into the legacy `CareInfo` view; kept for any caller
 *     that still wants only the 4 care-text fields.
 */
object PlantCatalogLookup {

    /**
     * Full catalog match — exposes the matched [Plant] so callers can
     * render badges, show the curated common name, and decide whether
     * to fill from catalog vs PlantCareDefaults.
     */
    data class CatalogMatch(
        val plant: Plant,
        /** Which lookup branch fired — for analytics / debugging. */
        val matchedBy: MatchSource
    )

    enum class MatchSource {
        /** Direct hit on `scientificName` column. Highest confidence. */
        SCIENTIFIC_EXACT,
        /** LIKE on `scientificName` (genus → binomial fallback). */
        SCIENTIFIC_PARTIAL,
        /** Direct hit on the German common `name` column. */
        COMMON_NAME_EXACT,
        /** WikiImageHelper.germanNameForScientific reverse mapping. */
        REVERSE_GERMAN
    }

    /**
     * Legacy view: only the 4 care-text fields. Kept for callers that
     * existed before v17 (PlantIdentifyActivity now uses [findMatch]
     * directly, but the project still has tests / scripts referencing
     * this shape).
     */
    data class CareInfo(
        val lighting: String?,
        val soil: String?,
        val fertilizing: String?,
        val watering: String?,
        /**
         * Aus dem watering‑Text extrahierter Tageswert (z. B. „Alle 14 Tage" → 14).
         * 0, wenn der Text keinen Zahlenwert enthält.
         */
        val wateringIntervalDays: Int,
        val matchedName: String?
    ) {
        val isEmpty: Boolean
            get() = lighting.isNullOrBlank() && soil.isNullOrBlank() &&
                    fertilizing.isNullOrBlank() && watering.isNullOrBlank()
    }

    /**
     * Find a catalog row that corresponds to a PlantNet identification
     * result. Returns the full catalog `Plant` plus which branch fired.
     * Returns null when nothing matches → caller should fall back to
     * `PlantCareDefaults.forFamily(family)` for sensible defaults.
     *
     * Runs on Dispatchers.IO (Room forbids main-thread queries).
     */
    suspend fun findMatch(
        context: Context,
        scientificName: String?,
        commonName: String?
    ): CatalogMatch? = withContext(Dispatchers.IO) {
        val repo = PlantRepository.getInstance(context.applicationContext)

        // 1) Latin-name exact — the primary key now that the CSV carries
        //    scientificName. Catches "Monstera deliciosa" → curated row
        //    in one query, no string heuristics involved.
        val sci = scientificName?.trim()?.takeIf { it.isNotEmpty() }
        if (sci != null) {
            repo.findCatalogByScientificNameBlocking(sci)?.let {
                return@withContext CatalogMatch(it, MatchSource.SCIENTIFIC_EXACT)
            }

            // 2) Latin-name partial — PlantNet returned the full binomial,
            //    catalog only carries the genus. Try genus-only first
            //    (first whitespace-separated word) since Latin binomials
            //    are <Genus> <species>.
            val genus = sci.split(' ').firstOrNull()?.takeIf { it.length >= 4 }
            if (genus != null) {
                repo.findCatalogByScientificNameLikeBlocking("$genus%")?.let {
                    return@withContext CatalogMatch(it, MatchSource.SCIENTIFIC_PARTIAL)
                }
            }
        }

        // 3) German common-name exact — covers legacy CSV rows that
        //    haven't been backfilled with scientificName yet, plus rare
        //    cases where PlantNet's `commonName` is the German trivial
        //    name itself (the API returns localised commons when they
        //    exist).
        val common = commonName?.trim()?.takeIf { it.isNotEmpty() }
        if (common != null) {
            repo.findCatalogByNameBlocking(common)?.let {
                return@withContext CatalogMatch(it, MatchSource.COMMON_NAME_EXACT)
            }
        }

        // 4) Reverse mapping — last-resort safety net for legacy rows.
        if (sci != null) {
            val reverseGerman = WikiImageHelper.germanNameForScientific(sci)
            if (reverseGerman != null) {
                repo.findCatalogByNameBlocking(reverseGerman)?.let {
                    return@withContext CatalogMatch(it, MatchSource.REVERSE_GERMAN)
                }
            }
        }

        null
    }

    /**
     * Back-compat wrapper — returns the legacy `CareInfo` view. Prefer
     * [findMatch] for any new caller that needs the matched plant or
     * the badge state.
     */
    suspend fun findByIdentification(
        context: Context,
        scientificName: String?,
        commonName: String?
    ): CareInfo? {
        val match = findMatch(context, scientificName, commonName) ?: return null
        return match.plant.toCareInfo()
    }

    private fun Plant.toCareInfo(): CareInfo {
        val wateringText = watering?.takeIf { it.isNotBlank() }
        return CareInfo(
            lighting = lighting?.takeIf { it.isNotBlank() },
            soil = soil?.takeIf { it.isNotBlank() },
            fertilizing = fertilizing?.takeIf { it.isNotBlank() },
            watering = wateringText,
            wateringIntervalDays = wateringText?.let { ReminderUtils.parseWateringInterval(it) } ?: 0,
            matchedName = name
        )
    }
}
