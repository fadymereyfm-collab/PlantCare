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
import com.example.plantcare.media.PhotoStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
            // U9 — pre-fix `GridCells.Adaptive(120.dp)` produced a single
            // huge thumbnail (~330dp wide) when only one photo existed for
            // the day, dwarfing the reminder cards above and pushing the
            // bottom action bar off-screen. Fixed at 3 columns so each
            // thumbnail is a predictable ~106dp on a 360dp-wide phone, and
            // height capped at 240.dp keeps the grid from monopolising the
            // screen even with 9+ photos.
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
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
    val placeholder = DefaultPlantIcon.forPlant(photo.plantName, photo.plantId)
    val original = photo.imagePath
    // Strip the "PENDING_DOC:<docId>|" prefix that FirebaseSyncManager adds
    // while an upload is in flight. The original local URI follows the `|`,
    // so the rest of the resolution logic can treat it like any other path.
    val raw: String? = when {
        original == null -> null
        original.startsWith("PENDING_DOC:") -> {
            val sep = original.indexOf('|')
            if (sep > 0 && sep + 1 < original.length) original.substring(sep + 1) else null
        }
        else -> original
    }

    // Resolve the photo's own imagePath first — covers the happy path:
    // freshly captured `content://` URIs and uploaded https URLs alike.
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

    if (model != null) {
        val builder = Glide.with(ctx).load(model)
            .placeholder(placeholder)
            .error(placeholder)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        if (model is File) {
            builder.signature(ObjectKey("${model.absolutePath}#${model.lastModified()}"))
        }
        builder.into(iv)
        return
    }

    // Functional Report §6.1 (B1/F1) deeper fix: when imagePath is blank
    // or PENDING_DOC: (Firebase upload window — the local content:// is
    // overwritten with "PENDING_DOC:<docId>" the moment upload starts),
    // we cascade through the same resolution chain the rest of the app
    // uses (cover file → DB imageUri → ArchiveStore → catalog drawable →
    // Wiki image). PlantImageLoader exposes that chain via
    // resolveBestImage. We can't call PlantImageLoader.loadInto directly
    // here because it forces circleCrop, while the calendar grid wants
    // RoundedCornerShape (centerCrop) — so we resolve, then load by
    // hand with centerCrop preserved.
    iv.setImageResource(placeholder)
    val userEmail = try {
        com.example.plantcare.EmailContext.current(ctx)
    } catch (_: Throwable) {
        // expected: very early app start before EmailContext is wired
        null
    }
    // Same job-tag pattern as PlantImageLoader — cancel any prior in-flight
    // resolution attached to this ImageView so RecyclerView reuse doesn't
    // race a stale Wiki/DB result onto the bound-since item.
    (iv.getTag(R.id.tag_plant_image_load_job) as? Job)?.cancel()
    val job = CALENDAR_PHOTO_SCOPE.launch {
        val resolved = try {
            PlantImageLoader.resolveBestImage(ctx, photo.plantId, photo.plantName, userEmail)
        } catch (_: Throwable) {
            // expected: storage / DB edge cases — fall back to placeholder.
            Pair<Any?, Int?>(null, null)
        }
        val finalModel: Any = resolved.first ?: resolved.second ?: placeholder
        try {
            val builder = Glide.with(ctx).load(finalModel)
                .placeholder(placeholder)
                .error(placeholder)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            if (finalModel is File) {
                builder.signature(ObjectKey("${finalModel.absolutePath}#${finalModel.lastModified()}"))
            }
            builder.into(iv)
        } catch (_: Throwable) {
            iv.setImageResource(placeholder)
        }
    }
    iv.setTag(R.id.tag_plant_image_load_job, job)
}

/**
 * Shared SupervisorJob scope so per-photo failures don't take down siblings.
 * One file scope keeps coroutine bookkeeping cheap; cancellation is per-Job
 * via the tag attached to each ImageView (see loadCalendarPhotoInto).
 */
private val CALENDAR_PHOTO_SCOPE: CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
