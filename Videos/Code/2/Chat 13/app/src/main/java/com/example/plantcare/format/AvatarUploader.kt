package com.example.plantcare.format

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.plantcare.CrashReporter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Wave 2 — Avatar handling.
 *
 * Local cache: a single `avatar.jpg` per user under
 * `getCacheDir()/avatars/{email-hash}.jpg`. Cleared on logout via
 * `clearLocalAvatar`.
 *
 * Cloud: best-effort upload to users/{uid}/avatar.jpg in Storage. Failures
 * route through CrashReporter — never crash the user. Storage rules already
 * lock down the per-user subtree to the owning user (see storage.rules from
 * Session 11).
 */
object AvatarUploader {

    private const val TAG = "AvatarUploader"
    private const val MAX_DIMENSION = 512  // px — keeps avatar under ~80 KB

    /** Reads the picked image, downscales to [MAX_DIMENSION], saves to local
     *  cache, then fires-and-forgets the cloud upload. Returns the local file
     *  on success; null if the source URI was unreadable. */
    fun saveLocalAndUpload(context: Context, source: Uri, email: String): File? {
        val downscaled = decodeAndDownscale(context, source) ?: return null
        val out = localFile(context, email)
        try {
            out.parentFile?.mkdirs()
            FileOutputStream(out).use { fos ->
                downscaled.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }
        } catch (t: Throwable) {
            CrashReporter.log(t)
            return null
        }

        // Fire-and-forget cloud upload. We do NOT block the UI on Firebase —
        // local file is already saved + readable, so the user sees their
        // avatar immediately. Cloud sync catches up when the device has
        // network.
        uploadToCloud(downscaled)

        downscaled.recycle()
        return out
    }

    fun localFile(context: Context, email: String): File {
        val dir = File(context.cacheDir, "avatars")
        // hash to keep filenames bounded + URL-safe — emails have @ and dots.
        val hash = email.lowercase().hashCode().toString().replace("-", "n")
        return File(dir, "$hash.jpg")
    }

    fun clearLocalAvatar(context: Context, email: String) {
        try { localFile(context, email).delete() } catch (t: Throwable) { CrashReporter.log(t) }
    }

    private fun uploadToCloud(bitmap: Bitmap) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseStorage.getInstance()
            .reference
            .child("users/$uid/avatar.jpg")
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        ref.putBytes(baos.toByteArray())
            .addOnFailureListener { CrashReporter.log(it) }
            .addOnSuccessListener { Log.d(TAG, "Avatar uploaded for uid=$uid") }
    }

    private fun decodeAndDownscale(context: Context, uri: Uri): Bitmap? {
        return try {
            // First pass: read bounds only so we don't blow the heap on a
            // 12-megapixel input.
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            var sampleSize = 1
            val maxSide = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            while (maxSide / sampleSize > MAX_DIMENSION * 2) sampleSize *= 2

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val raw = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            // Second pass: scale exactly so the longest side equals MAX_DIMENSION.
            val scale = MAX_DIMENSION.toFloat() / maxOf(raw.width, raw.height)
            if (scale >= 1f) return raw
            val targetW = (raw.width * scale).toInt().coerceAtLeast(1)
            val targetH = (raw.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(raw, targetW, targetH, true)
            if (scaled !== raw) raw.recycle()
            scaled
        } catch (t: Throwable) {
            CrashReporter.log(t)
            null
        }
    }
}
