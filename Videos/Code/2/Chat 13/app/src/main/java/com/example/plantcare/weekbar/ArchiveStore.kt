package com.example.plantcare.weekbar

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object ArchiveStore {
    private const val PREFS = "photo_store"
    private fun keyCover(email: String, plantId: Long) = "cover_${email}_${plantId}"
    private fun keyArchive(email: String, plantId: Long) = "archive_${email}_${plantId}"

    fun setCover(context: Context, email: String, plantId: Long, uri: Uri) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(keyCover(email, plantId), uri.toString()).apply()
    }

    fun getCoverUri(context: Context, email: String, plantId: Long): Uri? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val s = prefs.getString(keyCover(email, plantId), null) ?: return null
        return kotlin.runCatching { Uri.parse(s) }.getOrNull()
    }

    fun addCalendarPhoto(context: Context, email: String, plantId: Long, plantName: String?, uri: Uri, date: LocalDate) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = keyArchive(email, plantId)
        val arr = JSONArray(prefs.getString(key, "[]"))
        val obj = JSONObject().apply {
            put("uri", uri.toString())
            put("date", date.format(DateTimeFormatter.ISO_LOCAL_DATE))
            put("plantName", plantName ?: JSONObject.NULL)
        }
        arr.put(obj)
        prefs.edit().putString(key, arr.toString()).apply()
    }

    fun getPhotos(context: Context, email: String, plantId: Long): List<PhotoEntry> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = keyArchive(email, plantId)
        val arr = JSONArray(prefs.getString(key, "[]"))
        val list = mutableListOf<PhotoEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val uri = kotlin.runCatching { Uri.parse(o.getString("uri")) }.getOrNull() ?: continue
            val date = kotlin.runCatching { LocalDate.parse(o.getString("date")) }.getOrNull() ?: LocalDate.now()
            val name = if (o.isNull("plantName")) null else o.getString("plantName")
            list.add(PhotoEntry(uri, date, name))
        }
        return list
    }

    fun getLatestPhotoUri(context: Context, email: String, plantId: Long): Uri? {
        val list = getPhotos(context, email, plantId)
        return list.lastOrNull()?.uri
    }

    data class PhotoEntry(val uri: Uri, val date: LocalDate, val plantName: String?)
}