package com.example.plantcare.ui.journal

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.example.plantcare.R
import com.example.plantcare.data.journal.JournalEntry
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * RecyclerView adapter for the per-plant timeline. Three view types:
 * Watering, Photo, Diagnosis. Shares the same path-aware photo loader as F1/F2
 * so cached FileProvider URIs don't degrade to broken-image placeholders.
 *
 * Long-press on a watering entry surfaces [onWateringNoteRequested] so the
 * hosting fragment can present an inline note editor — the only write surface
 * the journal exposes (deliberately scoped: capture-time photoType selectors
 * would add friction to the high-frequency capture path, and diagnosis linkage
 * waits for v1.1's cloud Health API).
 */
class PlantJournalAdapter(
    private val onWateringNoteRequested: ((JournalEntry.WateringEvent) -> Unit)? = null,
    private val onDiagnosisClick: ((JournalEntry.DiagnosisEntry) -> Unit)? = null,
    private val onMemoActionsRequested: ((JournalEntry.MemoEntry) -> Unit)? = null
) : ListAdapter<JournalEntry, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is JournalEntry.WateringEvent -> TYPE_WATERING
        is JournalEntry.PhotoEntry -> TYPE_PHOTO
        is JournalEntry.DiagnosisEntry -> TYPE_DIAGNOSIS
        is JournalEntry.MemoEntry -> TYPE_MEMO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_WATERING -> WateringVH(
                inflater.inflate(R.layout.item_journal_watering, parent, false),
                onWateringNoteRequested
            )
            TYPE_PHOTO -> PhotoVH(inflater.inflate(R.layout.item_journal_photo, parent, false))
            TYPE_DIAGNOSIS -> DiagnosisVH(
                inflater.inflate(R.layout.item_journal_diagnosis, parent, false),
                onDiagnosisClick
            )
            TYPE_MEMO -> MemoVH(
                inflater.inflate(R.layout.item_journal_memo, parent, false),
                onMemoActionsRequested
            )
            else -> error("Unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is JournalEntry.WateringEvent -> (holder as WateringVH).bind(item)
            is JournalEntry.PhotoEntry -> (holder as PhotoVH).bind(item)
            is JournalEntry.DiagnosisEntry -> (holder as DiagnosisVH).bind(item)
            is JournalEntry.MemoEntry -> (holder as MemoVH).bind(item)
        }
    }

    private class WateringVH(
        view: View,
        private val onNoteRequested: ((JournalEntry.WateringEvent) -> Unit)?
    ) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.journalWateringTitle)
        private val sub: TextView = view.findViewById(R.id.journalWateringSubline)
        private val note: TextView = view.findViewById(R.id.journalWateringNote)

        fun bind(item: JournalEntry.WateringEvent) {
            val ctx = itemView.context
            title.text = item.dateString
            val by = item.reminder.wateredBy?.takeIf { it.isNotBlank() }
            sub.text = if (by != null) {
                ctx.getString(R.string.journal_watering_done_by_format, by)
            } else {
                ctx.getString(R.string.journal_watering_done)
            }
            val n = item.reminder.notes?.takeIf { it.isNotBlank() }
            if (n != null) {
                note.text = n
                note.visibility = View.VISIBLE
            } else {
                note.visibility = View.GONE
            }

            itemView.setOnLongClickListener {
                onNoteRequested?.invoke(item)
                onNoteRequested != null
            }
        }
    }

    private class PhotoVH(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.journalPhotoTitle)
        private val thumb: ImageView = view.findViewById(R.id.journalPhotoThumb)

        fun bind(item: JournalEntry.PhotoEntry) {
            title.text = item.dateString
            loadIntoThumb(thumb, item.photo.imagePath)
        }
    }

    private class DiagnosisVH(
        view: View,
        private val onClick: ((JournalEntry.DiagnosisEntry) -> Unit)?
    ) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.journalDiagnosisTitle)
        private val thumb: ImageView = view.findViewById(R.id.journalDiagnosisThumb)
        private val result: TextView = view.findViewById(R.id.journalDiagnosisResult)
        private val note: TextView = view.findViewById(R.id.journalDiagnosisNote)

        fun bind(item: JournalEntry.DiagnosisEntry) {
            val ctx = itemView.context
            title.text = item.dateString
            loadIntoThumb(thumb, item.diagnosis.imagePath)
            val pct = (item.diagnosis.confidence * 100f).roundToInt().coerceIn(0, 100)
            result.text = ctx.getString(R.string.journal_diagnosis_result_format, item.diagnosis.displayName, pct)
            val n = item.diagnosis.note?.takeIf { it.isNotBlank() }
            if (n != null) {
                note.text = n
                note.visibility = View.VISIBLE
            } else {
                note.visibility = View.GONE
            }
            // Tap → host fragment opens the shared detail dialog (same UI as
            // DiagnosisHistoryActivity). Card stays inert if no callback wired.
            itemView.setOnClickListener { onClick?.invoke(item) }
            itemView.isClickable = onClick != null
        }
    }

    private class MemoVH(
        view: View,
        private val onActionsRequested: ((JournalEntry.MemoEntry) -> Unit)?
    ) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.journalMemoTitle)
        private val body: TextView = view.findViewById(R.id.journalMemoBody)

        fun bind(item: JournalEntry.MemoEntry) {
            title.text = item.dateString
            body.text = item.memo.text
            itemView.setOnLongClickListener {
                onActionsRequested?.invoke(item)
                onActionsRequested != null
            }
        }
    }

    companion object {
        private const val TYPE_WATERING = 1
        private const val TYPE_PHOTO = 2
        private const val TYPE_DIAGNOSIS = 3
        private const val TYPE_MEMO = 4

        private val DIFF = object : DiffUtil.ItemCallback<JournalEntry>() {
            override fun areItemsTheSame(a: JournalEntry, b: JournalEntry): Boolean = a.stableId == b.stableId
            override fun areContentsTheSame(a: JournalEntry, b: JournalEntry): Boolean = a == b
        }

        /**
         * Same routing as F1/F2: branch on raw path so PENDING_DOC, http(s), and
         * own-FileProvider content:// each take the most reliable Glide model.
         */
        private fun loadIntoThumb(iv: ImageView, raw: String?) {
            val ctx = iv.context
            val placeholder = R.drawable.ic_default_plant
            val builder = Glide.with(ctx)

            val model: Any? = when {
                raw.isNullOrBlank() -> null
                raw.startsWith("PENDING_DOC:") -> null
                raw.startsWith("http://") || raw.startsWith("https://") -> raw
                raw.startsWith("content://") ->
                    resolveOwnFileProviderFile(ctx, raw) ?: Uri.parse(raw)
                raw.startsWith("file://") ->
                    File(Uri.parse(raw).path ?: "").takeIf { it.exists() && it.length() > 0 }
                else -> File(raw).takeIf { it.exists() && it.length() > 0 }
            }

            if (model == null) {
                builder.load(placeholder).centerCrop().into(iv)
                return
            }
            val req = builder.load(model)
                .placeholder(placeholder)
                .error(placeholder)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            if (model is File) {
                req.signature(ObjectKey("${model.absolutePath}#${model.lastModified()}"))
            }
            req.into(iv)
        }

        private fun resolveOwnFileProviderFile(ctx: Context, raw: String): File? {
            return try {
                val uri = Uri.parse(raw)
                if (uri.authority != ctx.packageName + ".provider") return null
                val segs = uri.pathSegments
                if (segs.size < 2) return null
                val base = when (segs[0].lowercase(Locale.ROOT)) {
                    "my_images", "all_external_files" -> ctx.getExternalFilesDir(null)
                    else -> null
                } ?: return null
                val rel = segs.drop(1).joinToString("/")
                File(base, rel).takeIf { it.exists() && it.length() > 0 }
            } catch (_: Throwable) {
                // expected: malformed URI / missing file → fall back to placeholder
                null
            }
        }
    }
}
