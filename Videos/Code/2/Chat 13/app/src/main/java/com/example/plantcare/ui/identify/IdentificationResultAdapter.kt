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
import kotlinx.coroutines.cancel
import com.bumptech.glide.Glide
import com.example.plantcare.R
import com.example.plantcare.WikiImageHelper
import com.example.plantcare.data.plantnet.IdentificationResult
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Adapter for displaying plant identification results in a RecyclerView.
 *
 * @param onAddClick  Callback when "Hinzufügen" button is clicked.
 * @param onItemClick Callback when the card itself is clicked.
 */
class IdentificationResultAdapter(
    private val onAddClick: (result: IdentificationResult, rank: Int) -> Unit,
    private val onItemClick: (result: IdentificationResult, rank: Int) -> Unit = { _, _ -> }
) : ListAdapter<IdentificationResult, IdentificationResultAdapter.ResultViewHolder>(DIFF_CALLBACK) {

    /**
     * v17: per-row catalog match state. Keyed by lowercased scientific name.
     */
    private val catalogMatches: MutableMap<String, com.example.plantcare.Plant?> =
        java.util.concurrent.ConcurrentHashMap()

    /**
     * Replace the catalog-match map and refresh visible cards.
     */
    fun setCatalogMatches(matches: Map<String, com.example.plantcare.Plant?>) {
        catalogMatches.clear()
        for ((k, v) in matches) catalogMatches[k.lowercase()] = v
        notifyDataSetChanged()
    }

    /** Public read-only view for callers that need to know which rows match. */
    fun catalogMatchFor(scientificName: String?): com.example.plantcare.Plant? =
        scientificName?.lowercase()?.let { catalogMatches[it] }

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
        private val txtCatalogBadge: TextView = itemView.findViewById(R.id.txtCatalogBadge)
        private val confidenceBar: ProgressBar = itemView.findViewById(R.id.confidenceBar)
        private val btnAddPlant: MaterialButton = itemView.findViewById(R.id.btnAddPlant)

        fun bind(result: IdentificationResult, rank: Int) {
            // v17: prefer catalog name, fall back to PlantNet common, then sci.
            val match = catalogMatchFor(result.scientificName)
            val displayName = match?.name?.takeIf { it.isNotBlank() }
                ?: result.commonName
                ?: result.scientificName
            txtCommonName.text = displayName
            txtScientificName.text = result.scientificName

            // Toggle "In unserem Katalog" badge.
            txtCatalogBadge.visibility = if (match != null) View.VISIBLE else View.GONE

            // Reference image from PlantNet, with Wikipedia fallback.
            val imageUrl = result.imageUrl
            val placeholderRes = R.drawable.ic_plant_placeholder
            if (!imageUrl.isNullOrBlank()) {
                Glide.with(itemView.context)
                    .load(imageUrl)
                    .centerCrop()
                    .placeholder(placeholderRes)
                    .error(placeholderRes)
                    .into(imgSuggestion)
            } else {
                Glide.with(itemView.context).clear(imgSuggestion)
                imgSuggestion.setImageResource(placeholderRes)
                val sci = result.scientificName
                if (sci.isNotBlank()) {
                    val targetView = imgSuggestion
                    val targetTag = sci
                    targetView.tag = targetTag
                    val cached = wikiUrlCache[sci]
                    if (cached != null) {
                        if (cached != NEGATIVE_CACHE) {
                            Glide.with(targetView.context)
                                .load(cached)
                                .centerCrop()
                                .placeholder(placeholderRes)
                                .error(placeholderRes)
                                .into(targetView)
                        }
                    } else {
                        wikiScope.launch {
                            val url = withContext(Dispatchers.IO) {
                                try { WikiImageHelper.fetchImageUrl(sci) } catch (_: Throwable) { null }
                            }
                            wikiUrlCache[sci] = url ?: NEGATIVE_CACHE
                            if (!url.isNullOrBlank() && targetView.tag == targetTag) {
                                Glide.with(targetView.context)
                                    .load(url)
                                    .centerCrop()
                                    .placeholder(placeholderRes)
                                    .error(placeholderRes)
                                    .into(targetView)
                            }
                        }
                    }
                }
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

            // Tap card -> compare dialog. Add button -> add flow.
            itemView.setOnClickListener { onItemClick(result, rank) }
            btnAddPlant.setOnClickListener { onAddClick(result, rank) }
        }
    }

    /**
     * Adapter-scoped coroutine scope for the Wikipedia thumbnail fallback.
     */
    private val wikiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        wikiScope.cancel()
    }

    companion object {
        private const val NEGATIVE_CACHE = "__NEG__"
        private val wikiUrlCache = java.util.concurrent.ConcurrentHashMap<String, String>()

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
