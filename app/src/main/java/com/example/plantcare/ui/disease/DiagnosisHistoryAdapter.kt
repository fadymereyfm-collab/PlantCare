package com.example.plantcare.ui.disease

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.plantcare.R
import com.example.plantcare.data.disease.DiseaseDiagnosis
import java.io.File
import java.text.DateFormat
import java.util.Date

class DiagnosisHistoryAdapter(
    private val onDelete: (DiseaseDiagnosis) -> Unit,
    private val onItemClick: (DiseaseDiagnosis) -> Unit = {}
) : ListAdapter<DiseaseDiagnosis, DiagnosisHistoryAdapter.VH>(DIFF) {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumb: ImageView = itemView.findViewById(R.id.imgThumb)
        val name: TextView = itemView.findViewById(R.id.txtName)
        val date: TextView = itemView.findViewById(R.id.txtDate)
        val confidence: TextView = itemView.findViewById(R.id.txtConfidence)
        val delete: ImageButton = itemView.findViewById(R.id.btnDelete)
        val badgesRow: LinearLayout = itemView.findViewById(R.id.badgesRow)
        val badgeSeverity: TextView = itemView.findViewById(R.id.badgeSeverity)
        val badgeAffectedPart: TextView = itemView.findViewById(R.id.badgeAffectedPart)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_diagnosis_history, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.name.text = item.displayName
        holder.date.text = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM, DateFormat.SHORT
        ).format(Date(item.createdAt))
        holder.confidence.text = holder.itemView.context.getString(
            R.string.disease_confidence_percent,
            item.confidence * 100f
        )

        // 2026-05-20 (#1): severity + affected-part badges (same labels/colours
        // as the detail dialog, via DiagnosisBadgeUtil). Hidden when both null.
        val ctx = holder.itemView.context
        val severityLabel = DiagnosisBadgeUtil.severityLabel(ctx, item.severity)
        if (severityLabel != null) {
            holder.badgeSeverity.visibility = View.VISIBLE
            holder.badgeSeverity.text = severityLabel
            holder.badgeSeverity.backgroundTintList =
                ColorStateList.valueOf(DiagnosisBadgeUtil.severityColor(ctx, item.severity))
            holder.badgeSeverity.setTextColor(ContextCompat.getColor(ctx, R.color.pc_onPrimary))
        } else {
            holder.badgeSeverity.visibility = View.GONE
        }
        val affectedLabel = DiagnosisBadgeUtil.affectedPartLabel(ctx, item.affectedPart)
        if (affectedLabel != null) {
            holder.badgeAffectedPart.visibility = View.VISIBLE
            holder.badgeAffectedPart.text = affectedLabel
        } else {
            holder.badgeAffectedPart.visibility = View.GONE
        }
        holder.badgesRow.visibility =
            if (severityLabel != null || affectedLabel != null) View.VISIBLE else View.GONE

        val file = File(item.imagePath)
        if (file.exists()) {
            Glide.with(holder.itemView.context)
                .load(file)
                .centerCrop()
                .placeholder(R.drawable.ic_plant_placeholder)
                .into(holder.thumb)
        } else {
            holder.thumb.setImageResource(R.drawable.ic_plant_placeholder)
        }

        holder.delete.setOnClickListener { onDelete(item) }
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DiseaseDiagnosis>() {
            override fun areItemsTheSame(oldItem: DiseaseDiagnosis, newItem: DiseaseDiagnosis): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: DiseaseDiagnosis, newItem: DiseaseDiagnosis): Boolean =
                oldItem == newItem
        }
    }
}
