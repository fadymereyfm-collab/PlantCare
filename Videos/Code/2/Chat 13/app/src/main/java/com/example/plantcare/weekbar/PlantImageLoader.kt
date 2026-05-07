package com.example.plantcare.weekbar

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.example.plantcare.R
import com.example.plantcare.WikiImageHelper
import com.example.plantcare.media.PhotoStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object PlantImageLoader {

    @JvmStatic
    fun loadInto(
        context: Context,
        imageView: ImageView,
        plantId: Long,
        plantName: String?,
        userEmail: String?
    ) {
        // Variante je nach Pflanzenname wählen, damit der Katalog nicht einheitlich grün aussieht,
        // bevor ein echtes Bild geladen ist. (Siehe DefaultPlantIcon / Core-Structure-Report.)
        val placeholder = DefaultPlantIcon.forPlant(plantName, plantId)

        try { Glide.with(context).clear(imageView); android.util.Log.d("PlantImageLoader", "Cleared image view for plantId: $plantId") } catch (_: Throwable) {}
        imageView.setImageResource(placeholder)

        CoroutineScope(Dispatchers.Main).launch {
            val source = withContext(Dispatchers.IO) { resolveBestImage(context, plantId, plantName, userEmail) }
            android.util.Log.d("PlantImageLoader", "Resolved image source for plantId $plantId: ${source.first?.javaClass?.simpleName ?: "null"} (drawable: ${source.second ?: "null"})")
            try {
                when {
                    source.first != null -> {
                        val model = source.first!!
                        if (model is File) {
                            val sig = ObjectKey("${model.absolutePath}#${model.lastModified()}")
                            Glide.with(context)
                                .load(model)
                                .placeholder(placeholder)
                                .error(placeholder)
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                .signature(sig)
                                .circleCrop()
                                .into(imageView)
                        } else if (model is String) {
                            // HTTP URL as String — most reliable for Glide HTTP loading
                            Glide.with(context)
                                .load(model)
                                .placeholder(placeholder)
                                .error(placeholder)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .circleCrop()
                                .into(imageView)
                        } else {
                            val uri = model as Uri
                            val isHttpUri = uri.scheme?.lowercase(Locale.getDefault())?.startsWith("http") == true
                            if (isHttpUri) {
                                // HTTP URI — convert to String for more reliable Glide loading
                                Glide.with(context)
                                    .load(uri.toString())
                                    .placeholder(placeholder)
                                    .error(placeholder)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .circleCrop()
                                    .into(imageView)
                            } else {
                                val sig = ObjectKey("${uri}#${safeLastModified(context, uri)}")
                                Glide.with(context)
                                    .load(uri)
                                    .placeholder(placeholder)
                                    .error(placeholder)
                                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)                                    .signature(sig)
                                    .circleCrop()
                                    .into(imageView)
                            }
                        }
                    }
                    source.second != null -> {
                        Glide.with(context)
                            .load(source.second)
                            .placeholder(placeholder)
                            .error(placeholder)
                            .circleCrop()
                            .into(imageView)
                    }
                    else -> imageView.setImageResource(placeholder)
                }
            } catch (e: Throwable) {
                android.util.Log.e("PlantImageLoader", "Error loading image for plantId $plantId", e)
                imageView.setImageResource(placeholder)
            }
        }
    }

    /**
     * Returns Pair(Any?, Int?) where first is a File, String (HTTP URL), or Uri;
     * second is a drawable resId.
     */
    @JvmStatic
    suspend fun resolveBestImage(
        context: Context,
        plantId: Long,
        plantName: String?,
        userEmail: String?
    ): Pair<Any?, Int?> {

        // 1) Check actual cover FILE on disk (most reliable)
        if (plantId > 0) {
            try {
                val coverFile = PhotoStorage.coverFile(context, plantId)
                if (coverFile.exists() && coverFile.length() > 0) {
                    return Pair(coverFile, null)
                }
            } catch (e: Throwable) {
                android.util.Log.e("PlantImageLoader", "Error: ${e.message}", e)
            }
        }

        // 2) Check plant.imageUri from DB (by plantId), also get original name for catalog fallback
        var originalPlantName: String? = null
        var isCatalogPlant = false
        if (plantId > 0) {
            val result = withContext(Dispatchers.IO) {
                kotlin.runCatching {
                    val p = com.example.plantcare.data.repository.PlantRepository
                        .getInstance(context).findPlantById(plantId.toInt())
                    if (p != null) {
                        originalPlantName = p.name
                        isCatalogPlant = !p.isUserPlant
                        val uriStr = p.imageUri
                        if (!uriStr.isNullOrBlank()) {
                            // For HTTP URLs, return as String directly (more reliable with Glide)
                            if (uriStr.startsWith("http://") || uriStr.startsWith("https://")) {
                                return@runCatching uriStr
                            }
                            val uri = Uri.parse(uriStr)
                            if (uri.scheme == "file") {
                                val f = File(uri.path ?: "")
                                if (f.exists() && f.length() > 0) f else null
                            } else uri
                        } else null
                    } else null
                }.getOrNull()
            }
            if (result != null) return Pair(result, null)
        }

        // 3) Fallback: resolve plant by name/nickname and get its cover file or imageUri
        if (!plantName.isNullOrBlank()) {
            val resolved = withContext(Dispatchers.IO) {
                resolveByNameOrNickname(context, plantName, userEmail)
            }
            if (resolved.first != null) return Pair(resolved.first, null)
            // Keep the original plant name for catalog lookup
            if (resolved.second != null) originalPlantName = resolved.second
        }

        // 4) ArchiveStore cover (content:// URI, may be stale - last resort for URIs)
        if (!userEmail.isNullOrBlank() && plantId > 0) {
            ArchiveStore.getCoverUri(context, userEmail, plantId)?.let {
                if (isUriAccessible(context, it)) return Pair(it, null)
            }
        }

        // 5) Catalog drawable: try original plant name first, then passed plantName
        val namesToTry = mutableListOf<String>()
        if (!originalPlantName.isNullOrBlank()) namesToTry.add(originalPlantName!!)
        if (!plantName.isNullOrBlank() && plantName != originalPlantName) namesToTry.add(plantName)

        for (name in namesToTry) {
            val resId = findCatalogDrawable(context, name)
            if (resId != 0) return Pair(null, resId)
        }

        // 6) ON-DEMAND: For catalog plants without any image, try fetching from Wikipedia now.
        //    This runs on Dispatchers.IO so network calls are fine.
        if (isCatalogPlant && plantId > 0 && !originalPlantName.isNullOrBlank()) {
            val imageUrl = withContext(Dispatchers.IO) {
                try {
                    WikiImageHelper.fetchImageUrl(originalPlantName)
                } catch (_: Throwable) {
                    null
                }
            }
            if (!imageUrl.isNullOrBlank()) {
                // Store in DB for future use
                withContext(Dispatchers.IO) {
                    try {
                        com.example.plantcare.data.repository.PlantRepository
                            .getInstance(context)
                            .updateProfileImage(plantId.toInt(), imageUrl)
                    } catch (e: Throwable) {
                        android.util.Log.e("PlantImageLoader", "Error: ${e.message}", e)
                    }
                }
                return Pair(imageUrl, null) // Return as String for Glide HTTP loading
            }
        }

        return Pair(null, null)
    }

    /**
     * Returns Pair(imageSource, originalPlantName).
     */
    private suspend fun resolveByNameOrNickname(context: Context, plantName: String, userEmail: String?): Pair<Any?, String?> {
        val plantRepo = com.example.plantcare.data.repository.PlantRepository.getInstance(context)
        return kotlin.runCatching {
            var found: com.example.plantcare.Plant? = null

            if (!userEmail.isNullOrBlank()) {
                val copies = plantRepo.findUserPlantsByName(plantName, userEmail)
                if (copies.isNotEmpty()) found = plantRepo.findPlantById(copies[0].id)
                if (found == null) {
                    val byNick = plantRepo.findUserPlantsByNickname(plantName, userEmail)
                    if (byNick.isNotEmpty()) found = plantRepo.findPlantById(byNick[0].id)
                }
            }
            if (found == null) found = plantRepo.findAnyByNickname(plantName)
            if (found == null) found = plantRepo.findAnyByName(plantName)

            if (found != null) {
                // First check cover file on disk for this plant
                try {
                    val coverFile = PhotoStorage.coverFile(context, found.id.toLong())
                    if (coverFile.exists() && coverFile.length() > 0) {
                        return@runCatching Pair<Any?, String?>(coverFile, found.name)
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("PlantImageLoader", "Error: ${e.message}", e)
                }

                // Then check imageUri
                val uriStr = found.imageUri
                if (!uriStr.isNullOrBlank()) {
                    // For HTTP URLs, return as String directly
                    if (uriStr.startsWith("http://") || uriStr.startsWith("https://")) {
                        return@runCatching Pair<Any?, String?>(uriStr, found.name)
                    }
                    val uri = Uri.parse(uriStr)
                    if (uri.scheme == "file") {
                        val f = File(uri.path ?: "")
                        if (f.exists() && f.length() > 0) return@runCatching Pair<Any?, String?>(f, found.name)
                    } else {
                        return@runCatching Pair<Any?, String?>(uri, found.name)
                    }
                }

                // No image found, but return original name for catalog lookup
                return@runCatching Pair<Any?, String?>(null, found.name)
            }
            Pair<Any?, String?>(null, null)
        }.getOrNull() ?: Pair(null, null)
    }

    /**
     * Try multiple name formats to find a catalog drawable.
     * Tries: exact normalized name, then alpha-only (no digits/underscores).
     */
    private fun findCatalogDrawable(context: Context, name: String): Int {
        val base = name
            .lowercase(Locale.getDefault())
            .replace("\u00e4", "ae").replace("\u00f6", "oe")
            .replace("\u00fc", "ue").replace("\u00df", "ss")

        // Format 1: underscores between words, keep digits (e.g. "gummibaum_1")
        val withUnderscores = base
            .replace("[^a-z0-9]".toRegex(), "_")
            .replace("_+".toRegex(), "_")
            .trim('_')

        var id = context.resources.getIdentifier(withUnderscores, "drawable", context.packageName)
        if (id != 0) return id

        // Format 2: alpha only, no digits, no underscores (e.g. "gummibaum")
        val alphaOnly = base.replace("[^a-z]".toRegex(), "")
        if (alphaOnly != withUnderscores) {
            id = context.resources.getIdentifier(alphaOnly, "drawable", context.packageName)
            if (id != 0) return id
        }

        // Format 3: alpha + underscores, no digits (e.g. "einblatt_woh" → "einblatt")
        val alphaUnderscores = base
            .replace("[^a-z_\\s]".toRegex(), "")
            .replace("[\\s_]+".toRegex(), "_")
            .trim('_')
        if (alphaUnderscores != withUnderscores && alphaUnderscores != alphaOnly) {
            id = context.resources.getIdentifier(alphaUnderscores, "drawable", context.packageName)
            if (id != 0) return id
        }

        return 0
    }

    /**
     * Quick check whether a content:// URI is still accessible.
     */
    private fun isUriAccessible(context: Context, uri: Uri): Boolean {
        return try {
            if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                f.exists() && f.length() > 0
            } else if (uri.scheme == "content") {
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            } else false
        } catch (_: Throwable) {
            false
        }
    }

    private fun safeLastModified(context: Context, uri: Uri): Long {
        return try {
            when (uri.scheme?.lowercase(Locale.getDefault())) {
                "file" -> File(uri.path ?: "").lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
                "content" -> queryLastModified(context.contentResolver, uri)
                else -> System.currentTimeMillis()
            }
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    }

    private fun queryLastModified(cr: ContentResolver, uri: Uri): Long {
        val projections = arrayOf(
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_ADDED
        )
        var last: Long = 0
        var c: Cursor? = null
        try {
            c = cr.query(uri, projections, null, null, null)
            if (c != null && c.moveToFirst()) {
                val idx0 = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (idx0 >= 0) {
                    val v = c.getLong(idx0)
                    if (v > 0) return v
                }
                val idx1 = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                if (idx1 >= 0) {
                    val v = c.getLong(idx1)
                    if (v > 0) return v * 1000
                }
                val idx2 = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                if (idx2 >= 0) {
                    val v = c.getLong(idx2)
                    if (v > 0) last = v * 1000
                }
            }
        } catch (_: Throwable) {
        } finally {
            try { c?.close() } catch (_: Throwable) {}
        }
        return if (last > 0) last else System.currentTimeMillis()
    }
}
