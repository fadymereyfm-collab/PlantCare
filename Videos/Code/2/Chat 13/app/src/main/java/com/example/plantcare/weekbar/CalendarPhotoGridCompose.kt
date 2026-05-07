package com.example.plantcare.weekbar

import android.content.Context
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.example.plantcare.R
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarPhotoGrid(
    photos: List<CalendarPhotoItem>,
    modifier: Modifier = Modifier,
    onPhotoClick: ((CalendarPhotoItem) -> Unit)? = null,
    onPhotoLongClick: ((CalendarPhotoItem) -> Unit)? = null
) {
    if (photos.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            color = Color.Transparent
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(photos) { p ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onPhotoClick?.invoke(p) },
                                onLongClick = { onPhotoLongClick?.invoke(p) }
                            )
                    ) {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp)),
                            factory = { ctx -> ImageView(ctx) },
                            update = { iv -> loadCalendarPhotoInto(iv, p) }
                        )
                        if (!p.plantName.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = p.plantName,
                                style = MaterialTheme.typography.body2,
                                color = colorResource(R.color.pc_onSurface),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun loadCalendarPhotoInto(iv: ImageView, photo: CalendarPhotoItem) {
    val ctx = iv.context
    val placeholder = R.drawable.ic_default_plant
    val raw = photo.imagePath

    val model: Any? = when {
        raw.isNullOrBlank() -> null
        raw.startsWith("PENDING_DOC:") -> null
        raw.startsWith("http://") || raw.startsWith("https://") -> raw
        raw.startsWith("content://") ->
            resolveOwnFileProviderFile(ctx, raw)
                ?: photo.uri.takeIf { it != Uri.EMPTY }
        raw.startsWith("file://") ->
            File(Uri.parse(raw).path ?: "").takeIf { it.exists() && it.length() > 0 }
        else -> File(raw).takeIf { it.exists() && it.length() > 0 }
    }

    val request = Glide.with(ctx)
    if (model == null) {
        request.load(placeholder)
            .centerCrop()
            .into(iv)
        return
    }

    val builder = request.load(model)
        .placeholder(placeholder)
        .error(placeholder)
        .centerCrop()
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
    if (model is File) {
        builder.signature(ObjectKey("${model.absolutePath}#${model.lastModified()}"))
    }
    builder.into(iv)
}

/**
 * content:// URIs from this app's own FileProvider can lose their grant once the
 * capturing activity is destroyed (Functional Report §1.1). Mapping back to the
 * underlying File restores reliable loading via Glide.
 */
private fun resolveOwnFileProviderFile(context: Context, contentUriStr: String): File? {
    return try {
        val uri = Uri.parse(contentUriStr)
        if (uri.authority != context.packageName + ".provider") return null
        val segments = uri.pathSegments
        if (segments.size < 2) return null
        // provider_paths.xml ("my_images") and file_paths.xml ("all_external_files")
        // both map external-files-path to ".", so the rest of the path is relative to
        // getExternalFilesDir(null).
        val base = when (segments[0]) {
            "my_images", "all_external_files" -> context.getExternalFilesDir(null)
            else -> null
        } ?: return null
        val rel = segments.drop(1).joinToString("/")
        File(base, rel).takeIf { it.exists() && it.length() > 0 }
    } catch (_: Throwable) {
        // expected: malformed URI / missing file → caller falls back to Uri then placeholder
        null
    }
}
