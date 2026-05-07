package com.example.plantcare.data.plantnet

import android.content.Context
import com.example.plantcare.Plant
import com.example.plantcare.ReminderUtils
import com.example.plantcare.WikiImageHelper
import com.example.plantcare.data.repository.PlantRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Versucht, eine per PlantNet erkannte Pflanze im lokalen 506‑Einträge‑Katalog
 * (plants.csv → Room mit isUserPlant = 0) wiederzufinden.
 *
 * Warum das wichtig ist:
 * Nach der Erkennung lieferte das UI bisher leere Pflege‑Felder („Bewässerung: —").
 * Der Katalog hat aber für viele Pflanzen bereits geprüfte Texte für Licht, Boden,
 * Düngung und Bewässerung. Diese Klasse macht den Brückenschlag.
 *
 * Reihenfolge der Versuche (erster Treffer gewinnt):
 * 1. Exakt: Katalog‑Name == commonName (case‑insensitive).
 * 2. Rückwärts‑Mapping über [WikiImageHelper.germanNameForScientific]: scientificName →
 *    deutscher Trivialname → Katalog.
 * 3. Partiell: das letzte Wort des commonName via LIKE (z. B. „Vielblütiges Salomonssiegel"
 *    → "%Salomonssiegel%"). Beugt dem Fall vor, dass der Katalog nur die Kurzform führt.
 */
object PlantCatalogLookup {

    /**
     * Ergebnis der Katalog‑Suche — nur die vier Pflege‑Texte, keine IDs/Bilder,
     * damit der Aufrufer entscheiden kann, welche Felder er übernimmt.
     */
    data class CareInfo(
        val lighting: String?,
        val soil: String?,
        val fertilizing: String?,
        val watering: String?,
        /**
         * Aus dem watering‑Text extrahierter Tageswert (z. B. „Alle 14 Tage" → 14).
         * 0, wenn der Text keinen Zahlenwert enthält. Der Aufrufer entscheidet, ob
         * er stattdessen den familienbasierten Default aus [PlantCareDefaults] nimmt
         * (Functional Report §1.4).
         */
        val wateringIntervalDays: Int,
        /** Der gefundene Katalog‑Name (zum Debuggen und als menschlich lesbarer Hinweis). */
        val matchedName: String?
    ) {
        val isEmpty: Boolean
            get() = lighting.isNullOrBlank() && soil.isNullOrBlank() &&
                    fertilizing.isNullOrBlank() && watering.isNullOrBlank()
    }

    /**
     * Sucht in Dispatchers.IO (Room erlaubt keine Abfragen auf dem UI‑Thread).
     *
     * @param scientificName z. B. "Polygonatum multiflorum"
     * @param commonName     z. B. "Vielblütiges Salomonssiegel" (kann null sein)
     */
    suspend fun findByIdentification(
        context: Context,
        scientificName: String?,
        commonName: String?
    ): CareInfo? = withContext(Dispatchers.IO) {
        val plantRepo = PlantRepository.getInstance(context.applicationContext)

        // 1) Exakt‑Treffer auf dem Trivialnamen
        val exact = commonName?.takeIf { it.isNotBlank() }?.let { plantRepo.findCatalogByNameBlocking(it.trim()) }
        if (exact != null) return@withContext exact.toCareInfo()

        // 2) Rückwärts‑Mapping aus SEARCH_OVERRIDES: scientificName → deutscher Name
        val reverseGerman = scientificName?.let { WikiImageHelper.germanNameForScientific(it) }
        if (reverseGerman != null) {
            val match = plantRepo.findCatalogByNameBlocking(reverseGerman)
            if (match != null) return@withContext match.toCareInfo()
        }

        // 3) Partieller Treffer: letztes Wort des Trivialnamens als LIKE‑Muster
        //    („Vielblütiges Salomonssiegel" → "%Salomonssiegel%").
        //    Wir nehmen das letzte Wort, weil im Deutschen der charakteristische
        //    Gattungsname üblicherweise am Ende steht.
        val lastWord = commonName?.trim()?.split(' ')?.lastOrNull { it.isNotBlank() }
        if (!lastWord.isNullOrBlank() && lastWord.length >= 4) {
            val partial = plantRepo.findCatalogByNameLikeBlocking("%$lastWord%")
            if (partial != null) return@withContext partial.toCareInfo()
        }

        // 4) Letzter Versuch: letzte Worte des wissenschaftlichen Namens — manchmal steht
        //    der Gattungsname (z. B. "Monstera") als eigenständiger Katalog‑Eintrag.
        val sciLastWord = scientificName?.trim()?.split(' ')?.firstOrNull { it.isNotBlank() }
        if (!sciLastWord.isNullOrBlank() && sciLastWord.length >= 4) {
            val partial = plantRepo.findCatalogByNameLikeBlocking("%$sciLastWord%")
            if (partial != null) return@withContext partial.toCareInfo()
        }

        null
    }

    private fun Plant.toCareInfo(): CareInfo {
        val wateringText = watering?.takeIf { it.isNotBlank() }
        return CareInfo(
            lighting = lighting?.takeIf { it.isNotBlank() },
            soil = soil?.takeIf { it.isNotBlank() },
            fertilizing = fertilizing?.takeIf { it.isNotBlank() },
            watering = wateringText,
            // Catalog rows come from plants.csv where watering reads e.g. "Alle 14 Tage. ...".
            // The same regex used by AddToMyPlantsDialogFragment is reused here so the
            // PlantNet draft already carries a meaningful interval (Functional Report §1.4).
            wateringIntervalDays = wateringText?.let { ReminderUtils.parseWateringInterval(it) } ?: 0,
            matchedName = name
        )
    }
}
