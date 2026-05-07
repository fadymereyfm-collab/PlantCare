package com.example.plantcare.ui.onboarding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.plantcare.Plant
import com.example.plantcare.R

class PlantSelectionAdapter(
    private val onPlantClick: (Int) -> Unit
) : ListAdapter<Plant, PlantSelectionAdapter.PlantViewHolder>(PlantDiffCallback()) {

    private var selectedIds: Set<Int> = emptySet()

    fun updateSelectedIds(newSelectedIds: Set<Int>) {
        selectedIds = newSelectedIds
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlantViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plant_selection, parent, false)
        return PlantViewHolder(view, onPlantClick)
    }

    override fun onBindViewHolder(holder: PlantViewHolder, position: Int) {
        val plant = getItem(position)
        holder.bind(plant, selectedIds.contains(plant.id))
    }

    inner class PlantViewHolder(
        itemView: View,
        private val onPlantClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvPlantName: TextView = itemView.findViewById(R.id.tvPlantName)
        private val ivPlantImage: ImageView = itemView.findViewById(R.id.ivPlantImage)
        private val checkboxSelect: CheckBox = itemView.findViewById(R.id.checkboxSelect)
        private val selectionOverlay: View = itemView.findViewById(R.id.selectionOverlay)
        private val ivCheckmark: ImageView = itemView.findViewById(R.id.ivCheckmark)

        fun bind(plant: Plant, isSelected: Boolean) {
            tvPlantName.text = plant.name

            // Load plant image using Glide
            if (!plant.imageUri.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(plant.imageUri)
                    .centerCrop()
                    .into(ivPlantImage)
            }

            // Update selection state
            checkboxSelect.isChecked = isSelected
            if (isSelected) {
                selectionOverlay.visibility = View.VISIBLE
                ivCheckmark.visibility = View.VISIBLE
            } else {
                selectionOverlay.visibility = View.GONE
                ivCheckmark.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onPlantClick(plant.id)
            }

            checkboxSelect.setOnClickListener {
                onPlantClick(plant.id)
            }
        }
    }

    private class PlantDiffCallback : DiffUtil.ItemCallback<Plant>() {
        override fun areItemsTheSame(oldItem: Plant, newItem: Plant): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Plant, newItem: Plant): Boolean {
            // 2026-05-05: Plant is a Java entity without an equals() override —
            // the previous `oldItem == newItem` reduced to identity comparison
            // and DiffUtil never detected updates. Compare the visible-in-row
            // fields explicitly so the onboarding list refreshes when an image
            // or name comes in late.
            return oldItem.id == newItem.id &&
                    oldItem.name == newItem.name &&
                    oldItem.imageUri == newItem.imageUri
        }
    }
}
