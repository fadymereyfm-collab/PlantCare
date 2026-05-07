package com.example.plantcare.ui.disease

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
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
