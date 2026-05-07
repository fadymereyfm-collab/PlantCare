package com.example.plantcare.ui.disease

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.plantcare.R
import com.example.plantcare.data.disease.DiseaseReferenceImage
import com.example.plantcare.data.disease.DiseaseResult
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * Vertical adapter for the differential-diagnosis candidate cards. Each card
 * shows: rank badge, disease name, confidence, advice line, a horizontal
 * carousel of reference images (or "Bilder werden geladen..." spinner / empty
 * state), an attribution credit and a "Passt zu meinem Foto" button.
 *
 * Reference-image state for each card comes from a [Map] keyed by disease
 * key — the activity rebinds whenever the VM's `referenceImages` LiveData
 * pushes a new snapshot, and DiffUtil keeps the adapter from blowing away
 * already-bound carousels.
 */
class DiseaseCandidateAdapter(
    private val onMatch: (DiseaseResult) -> Unit
) : ListAdapter<DiseaseResult, DiseaseCandidateAdapter.VH>(DIFF) {

    /** Keyed by [DiseaseResult.diseaseKey]. */
    private var imagesByKey: Map<String, List<DiseaseReferenceImage>> = emptyMap()

    /** Disease key the user has visually confirmed (or null). */
    private var selectedKey: String? = null

    /**
     * Drives the per-card carousel state without resetting the parent list.
     * The diff is by reference equality on the inner list, so a partial
     * update (one card's images arrived) only refreshes that card.
     */
    fun setReferenceImages(map: Map<String, List<DiseaseReferenceImage>>) {
        if (imagesByKey === map) return
        imagesByKey = map
        // We can't use DiffUtil here cheaply (carousel state isn't part of
        // the DiseaseResult shape), so fall back to per-card payload notifies.
        for (i in 0 until itemCount) {
            notifyItemChanged(i, PAYLOAD_REFS)
        }
    }

    /**
     * Update which card is shown as user-selected. Triggers a per-card
     * payload notify so only the previously- and newly-selected cards
     * re-bind (no global re-layout).
     */
    fun setSelectedKey(key: String?) {
        if (selectedKey == key) return
        val previous = selectedKey
        selectedKey = key
        for (i in 0 until itemCount) {
            val item = getItem(i)
            if (item.diseaseKey == previous || item.diseaseKey == key) {
                notifyItemChanged(i, PAYLOAD_SELECTION)
            }
        }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView as MaterialCardView
        val rank: TextView = itemView.findViewById(R.id.txtRank)
        val name: TextView = itemView.findViewById(R.id.txtDiseaseName)
        val confidence: TextView = itemView.findViewById(R.id.txtConfidence)
        val advice: TextView = itemView.findViewById(R.id.txtAdvice)
        val refsLabel: TextView = itemView.findViewById(R.id.txtReferenceLabel)
        val refsProgress: ProgressBar = itemView.findViewById(R.id.refImagesProgress)
        val refsEmpty: TextView = itemView.findViewById(R.id.txtRefImagesEmpty)
        val refsRecycler: RecyclerView = itemView.findViewById(R.id.refImagesRecycler)
        val attribution: TextView = itemView.findViewById(R.id.txtAttribution)
        val btnMatch: MaterialButton = itemView.findViewById(R.id.btnMatch)
        val refAdapter = ReferenceImageAdapter { ref ->
            // Tap on a thumbnail → open the source page in the user's
            // browser (Wikipedia article, iNaturalist observation,
            // PlantVillage repo). Fail silently if no page URL.
            val url = ref.pageUrl ?: return@ReferenceImageAdapter
            try {
                val ctx = itemView.context
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (t: Throwable) {
                com.example.plantcare.CrashReporter.log(t)
            }
        }

        init {
            refsRecycler.layoutManager = LinearLayoutManager(
                itemView.context,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            refsRecycler.adapter = refAdapter
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_disease_candidate, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        bindCore(holder, getItem(position), position)
        bindReferences(holder, getItem(position))
        bindSelection(holder, getItem(position))
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        when {
            payloads.isEmpty() -> super.onBindViewHolder(holder, position, payloads)
            payloads.contains(PAYLOAD_REFS) -> bindReferences(holder, getItem(position))
            payloads.contains(PAYLOAD_SELECTION) -> bindSelection(holder, getItem(position))
            else -> super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun bindCore(holder: VH, item: DiseaseResult, position: Int) {
        val ctx = holder.itemView.context
        holder.rank.text = "${position + 1}"
        holder.name.text = item.displayName
        holder.confidence.text = ctx.getString(
            R.string.disease_confidence_percent,
            item.confidence * 100f
        )
        holder.advice.text = item.advice ?: ctx.getString(R.string.disease_no_advice)

        // For keys that aren't real pathologies (healthy, unclear, generic
        // physiological stress) the "Passt zu meinem Foto" affordance is
        // misleading — selecting "Healthy" as a diagnosis to save makes no
        // sense, and we can't show reference images for these either.
        // Hide the button on those rows so the user only acts on actionable
        // candidates.
        val keyLower = item.diseaseKey.lowercase()
        val canMatch = !item.isHealthy && keyLower !in NO_REFERENCE_KEYS
        holder.btnMatch.visibility = if (canMatch) View.VISIBLE else View.GONE
        holder.btnMatch.setOnClickListener { onMatch(item) }
    }

    /**
     * Visual state for the user's selection: highlight the chosen card
     * with a thicker primary-coloured border and swap the button label
     * from "Passt zu meinem Foto" to "✓ Ausgewählt" so the tap leaves an
     * obvious trace even if the user doesn't scroll down to see the
     * "Selected" indicator and Save button.
     */
    private fun bindSelection(holder: VH, item: DiseaseResult) {
        val ctx = holder.itemView.context
        val isSelected = item.diseaseKey == selectedKey
        if (isSelected) {
            holder.card.strokeWidth = (ctx.resources.displayMetrics.density * 2.5f).toInt()
            holder.card.setStrokeColor(ContextCompat.getColorStateList(ctx, R.color.pc_primary))
            holder.btnMatch.text = ctx.getString(R.string.disease_candidate_selected)
            holder.btnMatch.setIconResource(R.drawable.ic_check)
        } else {
            holder.card.strokeWidth = ctx.resources.displayMetrics.density.toInt()
            holder.card.setStrokeColor(ContextCompat.getColorStateList(ctx, R.color.pc_outline))
            holder.btnMatch.text = ctx.getString(R.string.disease_candidate_match)
        }
    }

    private fun bindReferences(holder: VH, item: DiseaseResult) {
        val ctx = holder.itemView.context
        // Hide the entire reference section for keys with no botanical
        // analog (healthy plants, unclear cases, generic physiological
        // states). These will never produce useful reference photos — the
        // repository short-circuits the same set, and showing the empty
        // state for them would just be noise.
        val keyLower = item.diseaseKey.lowercase()
        if (item.isHealthy || keyLower in NO_REFERENCE_KEYS) {
            holder.refsLabel.visibility = View.GONE
            holder.refsProgress.visibility = View.GONE
            holder.refsEmpty.visibility = View.GONE
            holder.refsRecycler.visibility = View.GONE
            holder.attribution.visibility = View.GONE
            return
        }
        holder.refsLabel.visibility = View.VISIBLE
        val images = imagesByKey[item.diseaseKey]
        when {
            images == null -> {
                // Still loading.
                holder.refsProgress.visibility = View.VISIBLE
                holder.refsEmpty.visibility = View.GONE
                holder.refsRecycler.visibility = View.GONE
                holder.attribution.visibility = View.GONE
            }
            images.isEmpty() -> {
                holder.refsProgress.visibility = View.GONE
                holder.refsEmpty.visibility = View.VISIBLE
                holder.refsRecycler.visibility = View.GONE
                holder.attribution.visibility = View.GONE
            }
            else -> {
                holder.refsProgress.visibility = View.GONE
                holder.refsEmpty.visibility = View.GONE
                holder.refsRecycler.visibility = View.VISIBLE
                holder.refAdapter.submitList(images)
                val attr = images.mapNotNull { it.attribution }.distinct()
                if (attr.isNotEmpty()) {
                    holder.attribution.visibility = View.VISIBLE
                    holder.attribution.text = ctx.getString(
                        R.string.disease_reference_attribution,
                        attr.joinToString(separator = " · ")
                    )
                } else {
                    holder.attribution.visibility = View.GONE
                }
            }
        }
    }

    companion object {
        private const val PAYLOAD_REFS = "refs"
        private const val PAYLOAD_SELECTION = "selection"

        /**
         * Keys that have no useful reference image and no actionable
         * "match this" semantics. Mirrored on the repository side
         * (DiseaseReferenceImageRepository.NO_REFERENCE_KEYS) — keep both
         * lists in sync.
         */
        private val NO_REFERENCE_KEYS = setOf(
            "healthy",
            "unclear",
            "physiological_stress"
        )

        private val DIFF = object : DiffUtil.ItemCallback<DiseaseResult>() {
            override fun areItemsTheSame(
                oldItem: DiseaseResult,
                newItem: DiseaseResult
            ): Boolean = oldItem.diseaseKey == newItem.diseaseKey

            override fun areContentsTheSame(
                oldItem: DiseaseResult,
                newItem: DiseaseResult
            ): Boolean = oldItem == newItem
        }
    }
}
