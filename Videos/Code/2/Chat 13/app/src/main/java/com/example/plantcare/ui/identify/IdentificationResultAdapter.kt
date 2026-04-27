package com.example.plantcare.ui.identify

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.plantcare.R
import com.example.plantcare.data.plantnet.IdentificationResult
import com.google.android.material.button.MaterialButton

/**
 * Adapter for displaying plant identification results in a RecyclerView.
 *
 * @param onAddClick  Callback when "Hinzufügen" button is clicked — triggers enrich + add flow.
 * @param onItemClick Callback when the card itself (or thumbnail) is clicked — opens the
 *                    split-screen comparison dialog so the user can visually compare the
 *                    candidate reference photo against their own captured image.
 */
class IdentificationResultAdapter(
    private val onAddClick: (result: IdentificationResult, rank: Int) -> Unit,
    private val onItemClick: (result: IdentificationResult, rank: Int) -> Unit = { _, _ -> }
) : ListAdapter<IdentificationResult, IdentificationResultAdapter.ResultViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_identification_result, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    inner class ResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgSuggestion: ImageView = itemView.findViewById(R.id.imgSuggestion)
        private val txtCommonName: TextView = itemView.findViewById(R.id.txtCommonName)
        private val txtScientificName: TextView = itemView.findViewById(R.id.txtScientificName)
        private val txtConfidence: TextView = itemView.findViewById(R.id.txtConfidence)
        private val txtFamily: TextView = itemView.findViewById(R.id.txtFamily)
        private val confidenceBar: ProgressBar = itemView.findViewById(R.id.confidenceBar)
        private val btnAddPlant: MaterialButton = itemView.findViewById(R.id.btnAddPlant)

        fun bind(result: IdentificationResult, rank: Int) {
            // Display name: prefer common name, fall back to scientific name
            val displayName = result.commonName ?: result.scientificName
            txtCommonName.text = displayName
            txtScientificName.text = result.scientificName

            // Reference image from PlantNet. We always show something in the
            // thumbnail slot — a real reference photo if the API returned one,
            // otherwise the generic plant placeholder — so the cards stay
            // visually consistent and the user can quickly scan.
            val imageUrl = result.imageUrl
            if (!imageUrl.isNullOrBlank()) {
                Glide.with(itemView.context)
                    .load(imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_plant_placeholder)
                    .error(R.drawable.ic_plant_placeholder)
                    .into(imgSuggestion)
            } else {
                Glide.with(itemView.context).clear(imgSuggestion)
                imgSuggestion.setImageResource(R.drawable.ic_plant_placeholder)
            }

            // Confidence
            val pct = result.confidencePercent
            txtConfidence.text = "$pct%"
            confidenceBar.progress = pct

            // Family
            if (result.family != null) {
                txtFamily.text = itemView.context.getString(R.string.identify_family_format, result.family)
                txtFamily.visibility = View.VISIBLE
            } else {
                txtFamily.visibility = View.GONE
            }

            // Tap anywhere on the card → open split-screen comparison dialog.
            // The "Hinzufügen" button consumes its own click and does NOT propagate
            // to itemView, so both listeners are independent.
            itemView.setOnClickListener { onItemClick(result, rank) }

            // Add button → skip comparison, go straight to enrich + add flow.
            btnAddPlant.setOnClickListener { onAddClick(result, rank) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<IdentificationResult>() {
            override fun areItemsTheSame(
                oldItem: IdentificationResult,
                newItem: IdentificationResult
            ): Boolean = oldItem.scientificName == newItem.scientificName

            override fun areContentsTheSame(
                oldItem: IdentificationResult,
                newItem: IdentificationResult
            ): Boolean = oldItem == newItem
        }
    }
}
