package com.example.plantcare.ui.util

import com.example.plantcare.AppDatabase
import com.example.plantcare.Plant

/**
 * Simple keyword-based classifier for catalog plants.
 *
 * The catalog has ~500 entries and no category column historically, so the
 * heuristic only needs to be right-enough — the user can override via the
 * chip filter / edit dialog later if needed.
 *
 * Categories:
 *   - cacti   : Kakteen & Sukkulenten
 *   - herbal  : Kräuter & Gewürze
 *   - outdoor : Gartenpflanzen, Bäume, winterhart
 *   - indoor  : everything else (sensible default for Zimmerpflanzen)
 */
object PlantCategoryUtil {

    const val CATEGORY_INDOOR = "indoor"
    const val CATEGORY_OUTDOOR = "outdoor"
    const val CATEGORY_HERBAL = "herbal"
    const val CATEGORY_CACTI = "cacti"

    /** Ordered list used by the UI chip row. */
    @JvmField
    val ALL_CATEGORIES = arrayOf(
        CATEGORY_INDOOR,
        CATEGORY_OUTDOOR,
        CATEGORY_HERBAL,
        CATEGORY_CACTI
    )

    // ──────────────────────────────────────────────────────────────
    //  Keyword buckets — tuned for the German catalog (plants.csv).
    //  Matching is case-insensitive on plant name + lighting hints.
    // ──────────────────────────────────────────────────────────────

    private val CACTI_KEYWORDS = listOf(
        "kaktus", "kakte", "sukkulent", "aloe", "agave", "echeveria",
        "haworthia", "crassula", "sedum", "euphorbia", "opuntia",
        "mammillaria", "yucca", "geldbaum", "pfennigbaum", "jadebaum"
    )

    private val HERBAL_KEYWORDS = listOf(
        "basilikum", "minze", "pfefferminz", "thymian", "oregano",
        "rosmarin", "salbei", "schnittlauch", "petersilie", "koriander",
        "dill", "kerbel", "estragon", "lavendel", "melisse",
        "zitronenmelisse", "bohnenkraut", "majoran", "kamille",
        "ysop", "liebstöckel", "kräuter"
    )

    private val OUTDOOR_KEYWORDS = listOf(
        "baum", "bäume", "strauch", "sträucher", "hecke", "rosen",
        "rose ", "tulpe", "narzisse", "hortensie", "rhododendron",
        "flieder", "forsythie", "obst", "apfel", "birne", "kirsche",
        "garten", "winterhart", "beet", "rasen", "koniferen", "tanne",
        "fichte", "kiefer", "eiche", "buche", "ahorn", "linde",
        "bambus", "wein", "efeu"
    )

    /**
     * Classify a plant based on its name and care properties.
     * Order matters: cacti > herbal > outdoor > indoor (default).
     */
    @JvmStatic
    fun classify(
        name: String?,
        lighting: String? = null,
        watering: String? = null
    ): String {
        val haystack = buildString {
            append(name.orEmpty().lowercase())
            append(' ')
            append(lighting.orEmpty().lowercase())
            append(' ')
            append(watering.orEmpty().lowercase())
        }

        if (containsAny(haystack, CACTI_KEYWORDS)) return CATEGORY_CACTI
        if (containsAny(haystack, HERBAL_KEYWORDS)) return CATEGORY_HERBAL
        if (containsAny(haystack, OUTDOOR_KEYWORDS)) return CATEGORY_OUTDOOR

        // Watering hint: plants that explicitly say "selten gießen" /
        // "wenig Wasser" are almost always succulents/cacti, but we've
        // already checked keywords — safe default is indoor.
        return CATEGORY_INDOOR
    }

    /** Convenience for Plant objects. */
    @JvmStatic
    fun classify(plant: Plant): String =
        classify(plant.name, plant.lighting, plant.watering)

    /**
     * One-off batch classifier run on a background thread.
     * Called at app startup so MIGRATION_6_7 (which leaves category NULL)
     * gets backfilled without blocking the UI.
     *
     * Cheap: single query, bulk update, runs at most once per install.
     */
    @JvmStatic
    fun classifyAllUnclassified(db: AppDatabase) {
        try {
            val pending = db.plantDao().catalogPlantsWithoutCategory
            if (pending.isNullOrEmpty()) return
            db.runInTransaction {
                for (p in pending) {
                    val cat = classify(p)
                    db.plantDao().updateCategory(p.id, cat)
                }
            }
        } catch (t: Throwable) {
            android.util.Log.w("PlantCategoryUtil", "Batch classification failed", t)
        }
    }

    /**
     * Localized label for the UI chip row.
     * Keep labels in German to match the rest of the app.
     */
    @JvmStatic
    fun labelFor(category: String?): String = when (category) {
        CATEGORY_INDOOR -> "Zimmer"
        CATEGORY_OUTDOOR -> "Garten"
        CATEGORY_HERBAL -> "Kräuter"
        CATEGORY_CACTI -> "Kakteen"
        else -> "Alle"
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        for (k in keywords) if (text.contains(k)) return true
        return false
    }
}
