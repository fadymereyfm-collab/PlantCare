package com.example.plantcare.data.gemini

import com.google.gson.annotations.SerializedName

/**
 * Request/response payloads for Google Gemini's `generateContent` REST endpoint.
 *
 * We only use the bits required for vision-in / text-out: a single user
 * message with two parts (inline image bytes + text prompt) and a JSON-mode
 * response.
 *
 * @see <a href="https://ai.google.dev/api/generate-content">Gemini generateContent docs</a>
 */
data class GeminiRequest(
    @SerializedName("contents")
    val contents: List<GeminiContent>,
    @SerializedName("generationConfig")
    val generationConfig: GeminiGenerationConfig? = null,
    @SerializedName("safetySettings")
    val safetySettings: List<GeminiSafetySetting>? = null
)

data class GeminiContent(
    @SerializedName("role")
    val role: String? = null,
    @SerializedName("parts")
    val parts: List<GeminiPart>
)

data class GeminiPart(
    @SerializedName("text")
    val text: String? = null,
    @SerializedName("inline_data")
    val inlineData: GeminiInlineData? = null
)

data class GeminiInlineData(
    @SerializedName("mime_type")
    val mimeType: String,
    /** Base64-encoded image bytes (without the `data:` prefix). */
    @SerializedName("data")
    val data: String
)

data class GeminiGenerationConfig(
    @SerializedName("temperature")
    val temperature: Double? = null,
    @SerializedName("maxOutputTokens")
    val maxOutputTokens: Int? = null,
    @SerializedName("responseMimeType")
    val responseMimeType: String? = null,
    /**
     * Gemini 2.5 Flash enables "thinking mode" by default — the model spends
     * 200–2000 hidden reasoning tokens before producing output, which can
     * exhaust `maxOutputTokens` and truncate the JSON response mid-stream.
     * Set `thinkingBudget = 0` to disable thinking and get fast, structured
     * output ([Gemini docs](https://ai.google.dev/gemini-api/docs/thinking)).
     */
    @SerializedName("thinkingConfig")
    val thinkingConfig: GeminiThinkingConfig? = null
)

data class GeminiThinkingConfig(
    @SerializedName("thinkingBudget")
    val thinkingBudget: Int
)

/**
 * Loosened safety thresholds — plant images include parts that some default
 * filters mis-classify as graphic (rotting tissue, insect close-ups). We
 * relax to BLOCK_ONLY_HIGH so honest disease photos aren't rejected.
 */
data class GeminiSafetySetting(
    @SerializedName("category")
    val category: String,
    @SerializedName("threshold")
    val threshold: String
)

data class GeminiResponse(
    @SerializedName("candidates")
    val candidates: List<GeminiCandidate>?,
    @SerializedName("promptFeedback")
    val promptFeedback: GeminiPromptFeedback?
)

data class GeminiCandidate(
    @SerializedName("content")
    val content: GeminiContent?,
    @SerializedName("finishReason")
    val finishReason: String?
)

data class GeminiPromptFeedback(
    @SerializedName("blockReason")
    val blockReason: String?
)

/**
 * Schema we ask Gemini to emit (via `responseMimeType = application/json`):
 *
 * ```json
 * {
 *   "plant_detected": true,
 *   "results": [
 *     { "disease_key": "spider_mites",
 *       "display_name": "Spinnmilben",
 *       "confidence": 0.78,
 *       "is_healthy": false,
 *       "advice": "Luftfeuchte erhöhen, Pflanze abduschen, ggf. Raubmilben einsetzen." }
 *   ]
 * }
 * ```
 */
data class GeminiDiseasePayload(
    @SerializedName("plant_detected")
    val plantDetected: Boolean = true,
    /**
     * Best-effort plant species identification (e.g. "Monstera deliciosa",
     * "Echte Aloe (Aloe vera)"). Null when Gemini sees a plant but can't
     * pin the species. Surfaced in the UI both as context for the user
     * AND as a sanity-check against the user's chosen target plant —
     * if the user assigns the diagnosis to "Pothos" but Gemini sees
     * "Monstera", we ask before saving.
     */
    @SerializedName("plant_species")
    val plantSpecies: String? = null,
    @SerializedName("results")
    val results: List<GeminiDiseaseEntry>?
)

data class GeminiDiseaseEntry(
    @SerializedName("disease_key")
    val diseaseKey: String?,
    @SerializedName("display_name")
    val displayName: String?,
    @SerializedName("confidence")
    val confidence: Double = 0.0,
    @SerializedName("is_healthy")
    val isHealthy: Boolean = false,
    @SerializedName("advice")
    val advice: String?
)

sealed class GeminiOutcome {
    /** Successfully decoded structured payload. */
    data class Success(
        val payload: GeminiDiseasePayload,
        val bodyPreview: String? = null
    ) : GeminiOutcome()

    /** Gemini decided the photo isn't a plant. */
    data object PlantNotDetected : GeminiOutcome()

    data class Failure(
        val error: GeminiError,
        val rawMessage: String? = null
    ) : GeminiOutcome()
}

enum class GeminiError {
    INVALID_API_KEY,   // 401/403 or AIzaSy key rejected
    QUOTA_EXCEEDED,    // 429 — free-tier 250/day hit, OR a per-minute burst
    NO_INTERNET,       // ConnectException / UnknownHostException / IOException
    TIMEOUT,           // SocketTimeoutException / InterruptedIOException
    SERVER_ERROR,      // 5xx
    SAFETY_BLOCKED,    // promptFeedback.blockReason is non-null
    UNKNOWN
}
