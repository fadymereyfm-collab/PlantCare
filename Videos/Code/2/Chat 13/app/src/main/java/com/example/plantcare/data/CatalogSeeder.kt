package com.example.plantcare.data

import android.content.Context
import com.example.plantcare.CrashReporter
import com.example.plantcare.Plant
import com.example.plantcare.data.repository.PlantRepository
import com.example.plantcare.ui.util.PlantCategoryUtil
import com.example.plantcare.util.BgExecutor
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Seeds the catalog table from `assets/plants.csv` on first launch.
 *
 * Invoked from App.onCreate(), so the catalog is available to every
 * Activity from process start (the catalog browser inside MainActivity,
 * AllPlantsFragment, the PlantNet match-back, etc). The countAll == 0
 * guard keeps the call idempotent on subsequent launches.
 *
 * Pre-fix: this logic lived inside MainActivity, which meant the catalog
 * was only populated after the user passed through OnboardingActivity.
 */
object CatalogSeeder {

    private const val ASSET_NAME = "plants.csv"

    /** Fire-and-forget: schedules the seed on the shared IO pool. */
    @JvmStatic
    fun seedIfEmptyAsync(context: Context) {
        val appCtx = context.applicationContext
        BgExecutor.io {
            try {
                seedIfEmptyBlocking(appCtx)
            } catch (t: Throwable) {
                CrashReporter.log(t)
            }
        }
    }

    /** Synchronous variant for callers that already run on IO. */
    fun seedIfEmptyBlocking(context: Context) {
        val plantRepo = PlantRepository.getInstance(context)
        if (plantRepo.countAllBlocking() == 0) {
            try {
                BufferedReader(
                    InputStreamReader(context.assets.open(ASSET_NAME))
                ).use { reader ->
                    val iterator = reader.lineSequence().iterator()
                    if (!iterator.hasNext()) return@use
                    // First line is the header. We use it to detect the
                    // CSV schema and pick the right column indices.
                    val header = parseCsvLine(iterator.next())
                        .map { it.trim().lowercase() }
                    val idx = ColumnIndex.from(header)
                    while (iterator.hasNext()) {
                        val line = iterator.next()
                        val parts = parseCsvLine(line)
                        // Skip blank rows. Names are mandatory.
                        val plantName = parts.getOrNull(idx.name)?.trim().orEmpty()
                        if (plantName.isEmpty()) continue
                        val plant = Plant().apply {
                            name = plantName
                            // v17: read scientificName + family + category
                            // straight from the CSV when present.
                            scientificName = parts.getOrNull(idx.scientificName)
                                ?.trim()?.takeIf { it.isNotEmpty() }
                            family = parts.getOrNull(idx.family)
                                ?.trim()?.takeIf { it.isNotEmpty() }
                            // The 4 care-text columns are now optional.
                            // When the CSV cell is blank, we leave the
                            // field null; AddToMyPlantsDialog falls back
                            // to PlantCareDefaults.forFamily().
                            lighting = parts.getOrNull(idx.lighting)
                                ?.trim()?.takeIf { it.isNotEmpty() }
                            soil = parts.getOrNull(idx.soil)
                                ?.trim()?.takeIf { it.isNotEmpty() }
                            fertilizing = parts.getOrNull(idx.fertilizing)
                                ?.trim()?.takeIf { it.isNotEmpty() }
                            watering = parts.getOrNull(idx.watering)
                                ?.trim()?.takeIf { it.isNotEmpty() }
                            // Image column is optional.
                            imageUri = idx.imageUri?.let { i ->
                                parts.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }
                            }
                            isUserPlant = false
                            userEmail = null
                            // Prefer explicit category from CSV.
                            // Fall back to heuristic for legacy 5-col rows.
                            category = idx.category
                                ?.let { i -> parts.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() } }
                                ?: PlantCategoryUtil.classify(name, lighting, watering)
                            // Backfill family / scientificName from the
                            // curated lookup table when the CSV did not
                            // supply them.
                            if (family == null || scientificName == null) {
                                CatalogFamilyMap.lookup(name)?.let { info ->
                                    if (family == null) family = info.family
                                    if (scientificName == null) scientificName = info.scientificName
                                }
                            }
                        }
                        plantRepo.insertBlocking(plant)
                    }
                }
            } catch (t: Throwable) {
                CrashReporter.log(t)
            }
        }

        // Backfill categories for older catalog rows pre-MIGRATION_6_7.
        try {
            PlantCategoryUtil.classifyAllUnclassified(
                com.example.plantcare.AppDatabase.getInstance(context)
            )
        } catch (t: Throwable) {
            CrashReporter.log(t)
        }
    }

    /**
     * Resolves CSV column positions from the header row so the seeder
     * works with both the legacy 5-column schema and the v17 8-column
     * schema without code branching.
     */
    private data class ColumnIndex(
        val name: Int,
        val scientificName: Int,
        val family: Int,
        val category: Int?,
        val lighting: Int,
        val soil: Int,
        val fertilizing: Int,
        val watering: Int,
        val imageUri: Int?
    ) {
        companion object {
            fun from(header: List<String>): ColumnIndex {
                fun find(vararg names: String): Int =
                    names.firstNotNullOfOrNull { n ->
                        header.indexOf(n.lowercase()).takeIf { it >= 0 }
                    } ?: -1

                val nameIdx = find("name").coerceAtLeast(0)
                val sciIdx = find("scientificname", "scientific_name")
                val famIdx = find("family")
                val catIdx = find("category").takeIf { it >= 0 }
                val lightIdx = find("lighting").let { if (it >= 0) it else 1 }
                val soilIdx = find("soil").let { if (it >= 0) it else 2 }
                val fertIdx = find("fertilizing").let { if (it >= 0) it else 3 }
                val waterIdx = find("watering").let { if (it >= 0) it else 4 }
                val imageIdx = find("imageuri", "image_uri", "image").takeIf { it >= 0 }
                return ColumnIndex(
                    name = nameIdx,
                    scientificName = sciIdx,
                    family = famIdx,
                    category = catIdx,
                    lighting = lightIdx,
                    soil = soilIdx,
                    fertilizing = fertIdx,
                    watering = waterIdx,
                    imageUri = imageIdx
                )
            }
        }
    }

    /**
     * Minimal RFC-4180-ish CSV line parser. Supports double-quoted fields
     * with embedded commas.
     */
    private fun parseCsvLine(line: String): Array<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inQuotes = false
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    out.add(cur.toString())
                    cur.setLength(0)
                }
                else -> cur.append(c)
            }
        }
        out.add(cur.toString())
        return out.toTypedArray()
    }
}
