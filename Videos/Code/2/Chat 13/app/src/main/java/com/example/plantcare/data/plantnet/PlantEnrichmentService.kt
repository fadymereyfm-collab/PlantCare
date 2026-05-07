package com.example.plantcare.data.plantnet

import android.util.Log
import com.example.plantcare.WikiImageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Ergänzt eine frisch erkannte Pflanze (PlantNet‑Ergebnis) mit einer
 * Thumbnail‑URL und einem kurzen deutsch­sprachigen Beschreibungs­text,
 * gezogen aus der Wikipedia‑REST‑API.
 *
 * Motivation:
 * Nach der Bild­erkennung landen Pflanzen bisher ohne Bild und ohne
 * Beschreibung in „Meine Pflanzen". Im Katalog ist aber für dieselbe
 * Art längst ein Bild & ein Text vorhanden. Dieses Modul überbrückt
 * genau diese Lücke: wir fragen Wikipedia nach dem wissenschaftlichen
 * Namen (stabilster Schlüssel) und benutzen das Ergebnis als Start­füllung,
 * die der Nutzer hinterher im Detail­dialog frei überschreiben kann.
 */
data class PlantEnrichment(
    /** HTTP(S)‑URL eines Thumbnails von Wikipedia, oder null. */
    val imageUrl: String?,
    /** Kurz­beschreibung (erster Abschnitt des Artikels) oder null. */
    val summary: String?
) {
    val isEmpty: Boolean get() = imageUrl.isNullOrBlank() && summary.isNullOrBlank()
}

object PlantEnrichmentService {

    private const val TAG = "PlantEnrichment"
    private const val TIMEOUT_MS = 8000
    private const val USER_AGENT = "PlantCareApp/1.0 (Android)"

    /**
     * Zieht Bild‑URL & Beschreibung parallel. Netz‑Aufrufe laufen auf IO.
     *
     * Die Reihenfolge der Abfragen:
     *  1. Bild über [WikiImageHelper.fetchImageUrl] (kennt bereits 300+ deutsche
     *     Pflanzen­namen und fällt auf Englisch zurück).
     *  2. Beschreibung zuerst auf Deutsch, dann Englisch — für eine UI auf Deutsch
     *     ist ein deutscher Text wertvoller.
     */
    suspend fun enrich(
        scientificName: String?,
        commonName: String? = null
    ): PlantEnrichment = withContext(Dispatchers.IO) {
        // Bildsuche: bevorzugt den wissenschaftlichen Namen (präziser),
        // sonst den Trivialnamen.
        val imageKey = scientificName?.takeIf { it.isNotBlank() } ?: commonName
        val imageUrl = imageKey?.let {
            try {
                WikiImageHelper.fetchImageUrl(it)
            } catch (t: Throwable) {
                Log.d(TAG, "fetchImageUrl failed for '$it': ${t.message}")
                null
            }
        }

        // Beschreibung: deutsch zuerst, dann englisch.
        val summary = fetchSummary(scientificName, "de")
            ?: fetchSummary(scientificName, "en")
            ?: fetchSummary(commonName, "de")
            ?: fetchSummary(commonName, "en")

        PlantEnrichment(imageUrl, summary)
    }

    private fun fetchSummary(title: String?, lang: String): String? {
        if (title.isNullOrBlank()) return null
        return try {
            val encoded = URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
            val url = "https://$lang.wikipedia.org/api/rest_v1/page/summary/$encoded"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            try {
                if (conn.responseCode != 200) return null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                // `extract` ist der Klar­text, `description` ist eine Kurz­phrase.
                val extract = json.optString("extract").takeIf { it.isNotBlank() }
                val desc = json.optString("description").takeIf { it.isNotBlank() }
                when {
                    extract != null && desc != null -> "$desc\n\n$extract"
                    extract != null -> extract
                    desc != null -> desc
                    else -> null
                }
            } finally {
                conn.disconnect()
            }
        } catch (c: kotlinx.coroutines.CancellationException) {
            // Cooperate with structured concurrency — never swallow.
            throw c
        } catch (t: Throwable) {
            Log.d(TAG, "fetchSummary failed for '$title' ($lang): ${t.message}")
            null
        }
    }
}
