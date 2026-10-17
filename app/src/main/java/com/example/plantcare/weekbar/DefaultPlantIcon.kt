package com.example.plantcare.weekbar

import androidx.annotation.DrawableRes
import com.example.plantcare.R
import kotlin.math.abs

/**
 * Wählt anhand des Pflanzennamens (oder der ID) einen von sechs
 * stilisierten Standard-Icons aus — damit der Katalog nicht wie
 * „die gleiche grüne Kachel 505 Mal" wirkt, solange echte Fotos fehlen.
 *
 * Deterministisch: derselbe Name liefert immer dasselbe Icon,
 * sodass eine Pflanze nicht bei jedem Scrollen springt.
 *
 * Siehe Core-Structure-Report (23.04.2026), Abschnitt „بنك النباتات — الصور الخضراء القياسية".
 */
object DefaultPlantIcon {

    @DrawableRes
    private val variants: IntArray = intArrayOf(
        R.drawable.ic_default_plant_1,
        R.drawable.ic_default_plant_2,
        R.drawable.ic_default_plant_3,
        R.drawable.ic_default_plant_4,
        R.drawable.ic_default_plant_5,
        R.drawable.ic_default_plant_6
    )

    /**
     * Rückfalls‑ID, falls weder Name noch plantId verfügbar sind.
     * Identisch zur historischen Standard‑Kachel.
     */
    @DrawableRes
    val fallback: Int = R.drawable.ic_default_plant

    /**
     * Variante für eine gegebene Pflanze:
     * - Bevorzugt den Namen (stabil über Geräte­grenzen, sinnvoll für Katalog‑IDs,
     *   die per Seeding neu vergeben werden).
     * - Fällt auf plantId zurück.
     * - Wenn beides leer ist, liefert es die klassische erste Variante.
     */
    @DrawableRes
    fun forPlant(plantName: String?, plantId: Long = 0L): Int {
        val key = plantName?.trim()?.lowercase()
        val hash = when {
            !key.isNullOrEmpty() -> key.hashCode()
            plantId > 0L -> plantId.hashCode()
            else -> return variants[0]
        }
        val index = abs(hash) % variants.size
        return variants[index]
    }
}
