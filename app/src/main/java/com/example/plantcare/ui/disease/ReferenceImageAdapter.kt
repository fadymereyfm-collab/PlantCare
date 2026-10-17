package com.example.plantcare.ui.disease

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.plantcare.R
import com.example.plantcare.data.disease.DiseaseReferenceImage

/**
 * Horizontal carousel adapter for the reference images attached to a single
 * disease candidate. Glide handles disk-cache + decode; the RecyclerView is
 * given a fixed item width (120dp) so the layout doesn't reflow as images
 * stream in.
 */
class ReferenceImageAdapter(
    private val onImageTap: ((DiseaseReferenceImage) -> Unit)? = null
) : ListAdapter<DiseaseReferenceImage, ReferenceImageAdapter.VH>(DIFF) {

    class VH(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val img: ImageView = itemView.findViewById(R.id.imgRefPhoto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_disease_reference_image, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        Glide.with(holder.img)
            .load(item.thumbnailUrl ?: item.imageUrl)
            .placeholder(R.color.pc_surfaceVariant)
            .error(R.drawable.ic_disease)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .centerCrop()
            .into(holder.img)
        holder.itemView.setOnClickListener {
            onImageTap?.invoke(item)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DiseaseReferenceImage>() {
            override fun areItemsTheSame(
                oldItem: DiseaseReferenceImage,
                newItem: DiseaseReferenceImage
            ): Boolean = oldItem.imageUrl == newItem.imageUrl

            override fun areContentsTheSame(
                oldItem: DiseaseReferenceImage,
                newItem: DiseaseReferenceImage
            ): Boolean = oldItem == newItem
        }
    }
}
