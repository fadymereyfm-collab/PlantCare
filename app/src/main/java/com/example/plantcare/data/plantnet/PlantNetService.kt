package com.example.plantcare.data.plantnet

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Service class for PlantNet plant identification API.
 *
 * PlantNet API v2 uses multipart/form-data POST requests.
 * Free tier: 500 requests/day.
 *
 * @see <a href="https://my-api.plantnet.org/">PlantNet API Docs</a>
 */
class PlantNetService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Identify a plant from an image file.
     *
     * Liefert ein [PlantNetOutcome] zurück, damit die aufrufende Schicht
     * zwischen echten Fehlerarten (Schlüssel, Quote, Netz, Timeout) unterscheiden
     * und dem Nutzer eine klare Meldung zeigen kann.
     *
     * @param imageFile The image file to send for identification
     * @param organ The plant organ visible in the image (leaf, flower, fruit, bark, auto)
     * @param apiKey PlantNet API key
     * @param lang Language for common names (default "de" for German)
     */
    suspend fun identify(
        imageFile: File,
        organ: String = "auto",
        apiKey: String,
        lang: String = "de"
    ): PlantNetOutcome = withContext(Dispatchers.IO) {
        try {
            // include-related-images=true — so each suggestion carries a few
            // representative PlantNet photos that we can show next to the name,
            // helping the user visually confirm which suggestion matches their shot.
            //
            // nb-results=3 — matches what `PlantIdentificationRepository` already
            // takes via `take(3)`. Asking the API for 5 then dropping the bottom
            // 2 was wasted bandwidth and a slightly larger response payload.
            val url = "$BASE_URL/v2/identify/all" +
                    "?include-related-images=true" +
                    "&no-reject=false" +
                    "&nb-results=3" +
                    "&lang=$lang" +
                    "&type=kt" +
                    "&api-key=$apiKey"

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "images",
                    imageFile.name,
                    imageFile.asRequestBody("image/jpeg".toMediaType())
                )
                .addFormDataPart("organs", organ)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                when {
                    response.isSuccessful && body != null -> {
                        val parsed = runCatching {
                            gson.fromJson(body, PlantNetResponse::class.java)
                        }.getOrNull()
                        if (parsed == null) {
                            PlantNetOutcome.Failure(PlantNetError.UNKNOWN, "Antwort nicht lesbar")
                        } else {
                            PlantNetOutcome.Success(parsed)
                        }
                    }
                    response.code == 401 || response.code == 403 ->
                        PlantNetOutcome.Failure(PlantNetError.INVALID_API_KEY, "HTTP ${response.code}")
                    response.code == 429 ->
                        PlantNetOutcome.Failure(PlantNetError.QUOTA_EXCEEDED, "HTTP 429")
                    response.code in 500..599 ->
                        PlantNetOutcome.Failure(PlantNetError.SERVER_ERROR, "HTTP ${response.code}")
                    else ->
                        PlantNetOutcome.Failure(PlantNetError.UNKNOWN, "HTTP ${response.code}")
                }
            }
        } catch (e: SocketTimeoutException) {
            PlantNetOutcome.Failure(PlantNetError.TIMEOUT, e.message)
        } catch (e: InterruptedIOException) {
            PlantNetOutcome.Failure(PlantNetError.TIMEOUT, e.message)
        } catch (e: UnknownHostException) {
            PlantNetOutcome.Failure(PlantNetError.NO_INTERNET, e.message)
        } catch (e: ConnectException) {
            PlantNetOutcome.Failure(PlantNetError.NO_INTERNET, e.message)
        } catch (e: IOException) {
            // Allgemeine Netz-/IO-Fehler (z. B. SSL, plötzlicher Abbruch).
            PlantNetOutcome.Failure(PlantNetError.NO_INTERNET, e.message)
        } catch (e: Exception) {
            com.example.plantcare.CrashReporter.log(e)
            PlantNetOutcome.Failure(PlantNetError.UNKNOWN, e.message)
        }
    }

    companion object {
        private const val BASE_URL = "https://my-api.plantnet.org"
    }
}
