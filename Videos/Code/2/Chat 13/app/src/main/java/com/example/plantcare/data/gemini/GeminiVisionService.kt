package com.example.plantcare.data.gemini

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Calls Gemini 2.5 Flash with a leaf/plant photo and a German prompt that
 * asks for a structured JSON disease verdict.
 *
 * Uses the public `generativelanguage.googleapis.com` REST endpoint with the
 * same `BuildConfig.GEMINI_API_KEY` regardless of free vs paid tier — billing
 * is toggled via the Google AI Studio dashboard, not code.
 *
 * @see <a href="https://ai.google.dev/api/generate-content">Gemini generateContent docs</a>
 */
class GeminiVisionService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun analyzeDisease(
        imageFile: File,
        apiKey: String,
        excludedDiseaseKeys: List<String> = emptyList()
    ): GeminiOutcome = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext GeminiOutcome.Failure(
                GeminiError.INVALID_API_KEY,
                "GEMINI_API_KEY not set in local.properties"
            )
        }
        try {
            val imageBytes = imageFile.readBytes()
            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            val effectivePrompt = if (excludedDiseaseKeys.isNotEmpty()) {
                // The user rejected one or more candidates from the previous
                // round (visual mismatch). Steer Gemini to alternative
                // diagnoses by listing the keys to exclude, then re-emit the
                // schema. We still cap at 3 to keep the UI consistent.
                val excludeList = excludedDiseaseKeys.joinToString(", ") { "\"$it\"" }
                PROMPT_DE + "\n\nWICHTIG: Der Nutzer hat folgende Diagnosen visuell " +
                        "ausgeschlossen, weil ihre Referenzbilder nicht zum Foto passten: " +
                        "$excludeList. Liefere AUSSCHLIESSLICH alternative Diagnosen, " +
                        "die NICHT in dieser Ausschlussliste stehen. Wenn keine plausible " +
                        "Alternative existiert, gib einen einzigen \"unclear\"-Eintrag zurück."
            } else {
                PROMPT_DE
            }

            val payload = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(
                            GeminiPart(text = effectivePrompt),
                            GeminiPart(
                                inlineData = GeminiInlineData(
                                    mimeType = "image/jpeg",
                                    data = base64
                                )
                            )
                        )
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.2,        // factual, low-variance
                    maxOutputTokens = 2048,   // raised from 1024 after on-device tests showed truncation
                    responseMimeType = "application/json",
                    // Disable Gemini 2.5 Flash's hidden chain-of-thought
                    // reasoning. With thinkingBudget=0 the entire output
                    // budget goes to the actual JSON answer and stops
                    // truncating mid-`disease_key` (real-device bug
                    // observed 2026-05-01).
                    thinkingConfig = GeminiThinkingConfig(thinkingBudget = 0)
                ),
                safetySettings = listOf(
                    // Plant photos with rotting tissue / pest close-ups can trigger
                    // default safety filters. Relax to BLOCK_ONLY_HIGH so legitimate
                    // disease images aren't rejected.
                    GeminiSafetySetting("HARM_CATEGORY_HARASSMENT", "BLOCK_ONLY_HIGH"),
                    GeminiSafetySetting("HARM_CATEGORY_HATE_SPEECH", "BLOCK_ONLY_HIGH"),
                    GeminiSafetySetting("HARM_CATEGORY_SEXUALLY_EXPLICIT", "BLOCK_ONLY_HIGH"),
                    GeminiSafetySetting("HARM_CATEGORY_DANGEROUS_CONTENT", "BLOCK_ONLY_HIGH")
                )
            )
            val bodyJson = gson.toJson(payload)
            val url = "$BASE_URL/v1beta/models/$MODEL:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            Log.d(TAG, "POST ${url.substringBefore("?key=")} (image=${imageBytes.size} bytes)")

            executeWithBackoff(request).use { response ->
                val body = response.body?.string()
                Log.d(TAG, "HTTP ${response.code} (body length=${body?.length ?: 0})")

                when {
                    response.code == 401 || response.code == 403 ->
                        return@withContext GeminiOutcome.Failure(
                            GeminiError.INVALID_API_KEY,
                            "HTTP ${response.code} — ${body?.take(120) ?: "(leer)"}"
                        )
                    response.code == 429 ->
                        return@withContext GeminiOutcome.Failure(GeminiError.QUOTA_EXCEEDED, "HTTP 429")
                    response.code in 500..599 ->
                        return@withContext GeminiOutcome.Failure(
                            GeminiError.SERVER_ERROR,
                            "HTTP ${response.code} — ${body?.take(120) ?: "(leer)"}"
                        )
                    !response.isSuccessful || body == null ->
                        return@withContext GeminiOutcome.Failure(
                            GeminiError.UNKNOWN,
                            "HTTP ${response.code} — ${body?.take(120) ?: "(leer)"}"
                        )
                }

                // Parse Gemini envelope.
                val envelope = runCatching {
                    gson.fromJson(body, GeminiResponse::class.java)
                }.onFailure { Log.w(TAG, "Envelope parse failed", it) }.getOrNull()
                    ?: return@withContext GeminiOutcome.Failure(
                        GeminiError.UNKNOWN,
                        "Antwort-Hülle nicht lesbar — ${body!!.take(160)}"
                    )

                envelope.promptFeedback?.blockReason?.let { reason ->
                    Log.w(TAG, "Safety blocked: $reason")
                    return@withContext GeminiOutcome.Failure(
                        GeminiError.SAFETY_BLOCKED,
                        "blockReason=$reason"
                    )
                }

                val candidate = envelope.candidates?.firstOrNull()
                val text = candidate?.content?.parts
                    ?.mapNotNull { it.text }
                    ?.joinToString(separator = "")
                    ?.trim()

                if (text.isNullOrBlank()) {
                    return@withContext GeminiOutcome.Failure(
                        GeminiError.UNKNOWN,
                        "Leere Antwort — finishReason=${candidate?.finishReason}"
                    )
                }

                // Defensive cleanup: if Gemini ignored `responseMimeType=application/json`
                // and wrapped the payload in a ```json … ``` fence, strip it so
                // gson can parse cleanly.
                val cleaned = stripMarkdownFences(text)

                val payloadParsed = runCatching {
                    gson.fromJson(cleaned, GeminiDiseasePayload::class.java)
                }.onFailure { Log.w(TAG, "Inner JSON parse failed", it) }.getOrNull()
                    ?: return@withContext GeminiOutcome.Failure(
                        GeminiError.UNKNOWN,
                        // Show both ends of the text + finishReason so we can spot
                        // mid-JSON truncation vs. wrapping vs. wrong shape.
                        "Innere JSON nicht lesbar — finishReason=${candidate.finishReason}" +
                                " — head: ${cleaned.take(80)} … tail: ${cleaned.takeLast(80)}"
                    )

                if (!payloadParsed.plantDetected) {
                    GeminiOutcome.PlantNotDetected
                } else {
                    GeminiOutcome.Success(payloadParsed, bodyPreview = cleaned.take(400))
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "timeout", e)
            GeminiOutcome.Failure(GeminiError.TIMEOUT, e.message)
        } catch (e: InterruptedIOException) {
            Log.w(TAG, "interrupted", e)
            GeminiOutcome.Failure(GeminiError.TIMEOUT, e.message)
        } catch (e: UnknownHostException) {
            Log.w(TAG, "unknown host", e)
            GeminiOutcome.Failure(GeminiError.NO_INTERNET, e.message)
        } catch (e: ConnectException) {
            Log.w(TAG, "connect failed", e)
            GeminiOutcome.Failure(GeminiError.NO_INTERNET, e.message)
        } catch (e: IOException) {
            Log.w(TAG, "io error", e)
            GeminiOutcome.Failure(GeminiError.NO_INTERNET, e.message)
        } catch (e: Exception) {
            Log.e(TAG, "unexpected", e)
            GeminiOutcome.Failure(GeminiError.UNKNOWN, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * Strip ``` / ```json fences if Gemini wrapped the JSON in a markdown
     * code block despite the `responseMimeType=application/json` hint.
     * Idempotent — returns the input unchanged when no fences are found.
     */
    private fun stripMarkdownFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        // Skip the opening fence + optional language tag.
        val firstNewline = trimmed.indexOf('\n')
        if (firstNewline < 0) return trimmed
        val afterOpen = trimmed.substring(firstNewline + 1)
        val closeIdx = afterOpen.lastIndexOf("```")
        return if (closeIdx >= 0) afterOpen.substring(0, closeIdx).trim() else afterOpen.trim()
    }

    /**
     * Wrap the OkHttp call with exponential backoff for transient 503
     * "model overloaded" responses — Google's recommended retry pattern
     * for Gemini under load. Backs off 1s → 2s → 4s before giving up
     * and surfacing the 503 to the caller.
     */
    private suspend fun executeWithBackoff(request: Request): Response {
        var attempt = 0
        var backoffMs = 1_000L
        while (true) {
            val response = client.newCall(request).execute()
            if (response.code != 503 || attempt >= MAX_503_RETRIES) {
                return response
            }
            Log.w(TAG, "HTTP 503 overloaded — retry ${attempt + 1}/$MAX_503_RETRIES in ${backoffMs}ms")
            response.close()
            delay(backoffMs)
            backoffMs *= 2
            attempt++
        }
    }

    companion object {
        private const val TAG = "GeminiVision"
        private const val BASE_URL = "https://generativelanguage.googleapis.com"
        // 2.5 Flash is the current free-tier-eligible vision model with the
        // best price/quality at writing (May 2026). Switch to 2.5 Pro for
        // higher accuracy at ~3× cost.
        private const val MODEL = "gemini-2.5-flash"
        // 3 retries on HTTP 503 (overloaded) — total wait 1+2+4 = 7s before giving up.
        private const val MAX_503_RETRIES = 3

        /**
         * Single-shot prompt. Optimised for:
         *  - German output (matches strings_disease.xml)
         *  - Houseplant focus (Salomonssiegel, Monstera, Ficus, Pothos…)
         *  - Calibrated confidence (asks for low scores when uncertain)
         *  - Treatment advice as a separate field (so the existing UI's
         *    "advice" line is populated)
         *  - JSON-mode safety: schema is enforced via `responseMimeType`
         */
        private val PROMPT_DE: String = """
Du bist ein Pflanzenarzt mit Spezialisierung auf Zimmer- und Zierpflanzen
(z. B. Monstera, Ficus, Pothos, Sansevieria, Orchideen, Aloe). Untersuche
das beigefügte Foto und liefere eine Diagnose ausschließlich als JSON nach
folgendem Schema:

{
  "plant_detected": boolean,
  "plant_species": string,
  "results": [
    {
      "disease_key": string,
      "display_name": string,
      "confidence": number,
      "is_healthy": boolean,
      "advice": string
    }
  ]
}

Regeln:
1. "plant_detected" ist false, wenn das Bild keine Pflanze zeigt
   (z. B. zufälliges Objekt, Wand, Person). Dann "results": [] und
   "plant_species": null.
2. "plant_species" ist die wahrscheinlichste Pflanzenart auf Deutsch
   plus lateinischer Name in Klammern, z. B. "Monstera (Monstera deliciosa)",
   "Echte Aloe (Aloe vera)", "Drachenbaum (Dracaena marginata)". Wenn
   nicht eindeutig: "Unbekannt". Niemals leerer String.
3. **Wenn "plant_detected" true ist, MUSS "results" mindestens einen Eintrag
   enthalten.** Niemals leeres "results"-Array bei erkannter Pflanze.
4. Liste höchstens 3 Diagnosen, sortiert nach absteigender Wahrscheinlichkeit.
5. "disease_key" MUSS GENAU einer der folgenden Werte sein
   (snake_case, englisch). Erfinde KEINE neuen Schlüssel:
     "spider_mites", "mealybugs", "root_rot", "leaf_spot",
     "powdery_mildew", "downy_mildew", "bacterial_leaf_spot",
     "scale_insects", "fungus_gnats", "aphids", "whiteflies",
     "thrips", "anthracnose", "rust", "leaf_miners",
     "botrytis", "edema", "chlorosis_nutrient",
     "healthy", "unclear".

   AUSWAHL-PRIORITÄT (von oben nach unten — wähle den ersten passenden Punkt):
   a) Sichtbarer Schädling auf dem Blatt (Insekten, Spinnentiere, Larven,
      feine Gespinste, Wachshäufchen)
      → spider_mites, aphids, mealybugs, scale_insects, thrips,
        whiteflies, fungus_gnats, leaf_miners.
   b) Sichtbares Pathogen-Muster (weißer Belag, dunkle/braune Flecken
      mit Hof, Pilzrasen, schwarze Stiele, fauliger Geruchs-Hinweis,
      Welkenester am Blattrand)
      → powdery_mildew, downy_mildew, leaf_spot, bacterial_leaf_spot,
        anthracnose, rust, botrytis, root_rot.
   c) Klassischer Mineralmangel (Adern grün, Zwischenadern gelb;
      generelle Vergilbung mit grünen Adern; Blattrandnekrosen ohne
      Pilz-Indizien) → chlorosis_nutrient.
   d) Sonnenbrand-Bläschen auf der Sonnen-zugewandten Seite des Blattes
      ODER Edema-Bläschen (kleine Korkpusteln durch Übergießen) →
      "edema".
   e) Pflanze sieht vollständig gesund aus → "healthy".
   f) Bei jedem anderen Befund — welk, sterbend, Symptome unklar oder
      nicht zuordenbar — verwende "unclear" mit einem konkreten advice,
      welches zusätzliche Foto/Information die Diagnose schärfen würde
      (z. B. "Foto der Blattunterseite gegen Licht prüft auf Spinnmilben",
      "Foto der Wurzeln nach Topf-Entnahme klärt Wurzelfäule",
      "Foto der ganzen Pflanze + Standortinfo zeigt Sonnenbrand vs.
      Über-/Unterwässerung").

   WICHTIG: Es gibt KEINEN generischen "Pflegestress"-Schlüssel mehr.
   Wenn du nicht zwischen Über-/Unterwässerung, Lichtmangel und Hitze
   sicher unterscheiden kannst — verwende IMMER "unclear" mit einem
   konkreten Hinweis auf das nächste hilfreiche Foto. Das ist ehrlicher
   und nützlicher als eine vage "check watering, light, temperature"-
   Antwort.

   - Bei freien Texten wie "wassermangel" / "low_humidity" / "physiologischer_stress":
     NICHT erfinden — verwende "unclear".
6. "display_name" ist der deutsche Name (z. B. "Spinnmilben", "Echter Mehltau",
   "Wurzelfäule", "Schmierläuse", "Chlorose (Nährstoffmangel)",
   "Unklarer Befall", "Gesund").
7. "confidence" liegt strikt zwischen 0.0 und 1.0. Sei kalibriert:
   - 0.85+ nur bei eindeutigen, klassischen Symptomen.
   - 0.5–0.8 bei wahrscheinlichen, aber nicht eindeutigen Befunden.
   - 0.2–0.4 bei spekulativen Vermutungen oder "Unklarer Befall".
   Fülle die zweite/dritte Diagnose NUR, wenn confidence ≥ 0.3 — sonst
   nur einen Eintrag zurückgeben.
8. "advice" ist ein einzelner deutscher Satz (max. 25 Wörter) mit der
   wichtigsten Sofortmaßnahme oder Pflegeempfehlung. Keine Werbung,
   keine Markennamen.
9. Antworte AUSSCHLIESSLICH mit dem JSON. Kein Fließtext, kein Markdown.
        """.trimIndent()
    }
}
