package com.example.plantcare.ui.disease

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.plantcare.R
import com.example.plantcare.ui.util.ClickGuard
import java.io.File

/**
 * 2026-05-20 Feature 2 (Multi-image) — adapter for the horizontal
 * photo strip in DiseaseDiagnosisActivity.
 *
 * Each tile is a 72dp rounded thumbnail with a "×" remove badge that
 * fires [onRemove] with the position. The list is owned externally
 * by [DiseaseDiagnosisViewModel.selectedImagePaths]; the activity
 * pushes new lists in via [submit].
 *
 * Note: This adapter does NOT add a trailing "+ add" cell. The
 * existing Camera/Gallery buttons keep that role — they just switch
 * from "replace" to "append" semantics once the strip is non-empty.
 */
class DiagnosisPhotosAdapter(
    private val onRemove: (position: Int) -> Unit
) : RecyclerView.Adapter<DiagnosisPhotosAdapter.VH>() {

    private val paths: MutableList<String> = mutableListOf()

    /** Replace the entire list and refresh the strip. */
    fun submit(newPaths: List<String>) {
        paths.clear()
        paths.addAll(newPaths)
        // notifyDataSetChanged is fine here — at most 4 items.
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_diagnosis_photo_thumb, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        // RecyclerView recycling fix (per ClickGuard sweep convention):
        // re-enable the row in case a previous bind left it disabled.
        holder.itemView.isClickable = true
        holder.itemView.isEnabled = true

        val path = paths[position]
        Glide.with(holder.thumbImage)
            .load(File(path))
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(false)
            .centerCrop()
            .into(holder.thumbImage)

        // ClickGuard on the × button — a rapid double-tap could remove
        // two photos in a row, which is confusing.
        holder.thumbRemove.setOnClickListener(
            ClickGuard.wrap {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) onRemove(pos)
            }
        )
    }

    override fun getItemCount(): Int = paths.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbImage: ImageView = itemView.findViewById(R.id.thumbImage)
        val thumbRemove: ImageView = itemView.findViewById(R.id.thumbRemove)
    }
}
