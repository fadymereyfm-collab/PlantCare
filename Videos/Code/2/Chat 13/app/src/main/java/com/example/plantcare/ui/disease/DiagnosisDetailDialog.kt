package com.example.plantcare.ui.disease

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.plantcare.R
import com.example.plantcare.data.disease.DiseaseDiagnosis
import com.example.plantcare.data.repository.PlantRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shared "diagnosis detail" presentation, used from both
 * [DiagnosisHistoryActivity] and the per-plant journal. Centralising it here
 * avoids two copies of the same plant-name-lookup + share-intent code path.
 *
 * The dialog itself is purely read-only and dismisses on the close action; the
 * Share button forwards a plain-text summary through the system share sheet
 * (no image — the user only consented to ship the photo to Gemini, not to
 * arbitrary third-party apps).
 */
object DiagnosisDetailDialog {

    fun show(
        context: Context,
        owner: LifecycleOwner,
        diagnosis: DiseaseDiagnosis
    ) {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_diagnosis_detail, null, false)
        val img = view.findViewById<ImageView>(R.id.imgDetail)
        val name = view.findViewById<TextView>(R.id.txtDetailName)
        val confidence = view.findViewById<TextView>(R.id.txtDetailConfidence)
        val plantLabel = view.findViewById<TextView>(R.id.txtDetailPlant)
        val advice = view.findViewById<TextView>(R.id.txtDetailAdvice)

        name.text = diagnosis.displayName
        confidence.text = context.getString(
            R.string.disease_confidence_percent,
            diagnosis.confidence * 100f
        )
        advice.text = diagnosis.note
            ?: context.getString(R.string.disease_no_advice)

        val file = File(diagnosis.imagePath)
        if (file.exists()) {
            Glide.with(context).load(file).centerCrop().into(img)
        } else {
            img.setImageResource(R.drawable.ic_plant_placeholder)
        }

        if (diagnosis.plantId > 0) {
            owner.lifecycleScope.launch {
                val plant = withContext(Dispatchers.IO) {
                    runCatching {
                        PlantRepository.getInstance(context).findByIdBlocking(diagnosis.plantId)
                    }.getOrNull()
                }
                val pName = plant?.nickname?.takeIf { it.isNotBlank() } ?: plant?.name
                plantLabel.text = pName
                    ?: context.getString(R.string.disease_history_detail_no_plant)
            }
        } else {
            plantLabel.text = context.getString(R.string.disease_history_detail_no_plant)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.disease_history_detail_title)
            .setView(view)
            .setPositiveButton(R.string.close, null)
            .setNegativeButton(R.string.disease_history_detail_share) { _, _ ->
                shareDiagnosis(context, diagnosis)
            }
            .show()
    }

    private fun shareDiagnosis(context: Context, diagnosis: DiseaseDiagnosis) {
        val body = context.getString(
            R.string.disease_share_body,
            diagnosis.displayName,
            diagnosis.confidence * 100f,
            diagnosis.note ?: context.getString(R.string.disease_no_advice)
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.disease_share_subject))
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.disease_history_detail_share))
        )
    }
}
