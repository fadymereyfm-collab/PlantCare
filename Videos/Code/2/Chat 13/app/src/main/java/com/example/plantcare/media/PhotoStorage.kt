package com.example.plantcare.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object PhotoStorage {
    private const val PROVIDER_SUFFIX = ".provider"

    @JvmStatic
    fun coverFile(context: Context, plantId: Long): File {
        val base = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        val dir = File(base, "plants/$plantId")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "cover.jpg")
    }

    @JvmStatic
    fun coverUri(context: Context, plantId: Long): Uri {
        val file = coverFile(context, plantId)
        if (!file.exists()) file.createNewFile()
        val authority = context.packageName + PROVIDER_SUFFIX
        return FileProvider.getUriForFile(context, authority, file)
    }

    @JvmStatic
    fun lastModified(context: Context, plantId: Long): Long {
        val file = coverFile(context, plantId)
        return if (file.exists()) file.lastModified() else 0L
    }

}