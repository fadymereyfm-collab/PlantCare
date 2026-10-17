package com.example.plantcare.ui.disease

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.plantcare.R
import com.example.plantcare.data.disease.DiseaseDiagnosis
import com.example.plantcare.data.repository.DiseaseDiagnosisRepository
import com.example.plantcare.data.repository.PlantRepository
import com.google.android.material.textfield.TextInputEditText
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

        // 2026-05-20 Feature 4 (Enriched history): new badges + treatment fields
        val badgesRow = view.findViewById<LinearLayout>(R.id.badgesRow)
        val badgeSeverity = view.findViewById<TextView>(R.id.badgeSeverity)
        val badgeAffectedPart = view.findViewById<TextView>(R.id.badgeAffectedPart)
        val labelSuggestedTreatment = view.findViewById<TextView>(R.id.labelSuggestedTreatment)
        val txtSuggestedTreatment = view.findViewById<TextView>(R.id.txtSuggestedTreatment)
        val editAppliedTreatment = view.findViewById<TextInputEditText>(R.id.editAppliedTreatment)

        name.text = diagnosis.displayName
        confidence.text = context.getString(
            R.string.disease_confidence_percent,
            diagnosis.confidence * 100f
        )
        // Bug fix 2026-05-20: the diagnosis save flow never sets `note`
        // (it's a reserved free-text user field); the Gemini care hint lives
        // in `suggestedTreatment`. Falling back to it stops the "Hinweis"
        // line from showing a permanent "Keine Hinweise" placeholder.
        val noteText = diagnosis.note?.takeIf { it.isNotBlank() }
        val suggestedHint = diagnosis.suggestedTreatment?.takeIf { it.isNotBlank() }
        advice.text = noteText ?: suggestedHint ?: context.getString(R.string.disease_no_advice)

        // ─── Feature 4: severity + affected-part badges ───
        val severityLabel = DiagnosisBadgeUtil.severityLabel(context, diagnosis.severity)
        val severityColor = DiagnosisBadgeUtil.severityColor(context, diagnosis.severity)
        if (severityLabel != null) {
            badgeSeverity.visibility = View.VISIBLE
            badgeSeverity.text = severityLabel
            badgeSeverity.backgroundTintList = ColorStateList.valueOf(severityColor)
            badgeSeverity.setTextColor(
                ContextCompat.getColor(context, R.color.pc_onPrimary)
            )
        } else {
            badgeSeverity.visibility = View.GONE
        }

        val affectedLabel = DiagnosisBadgeUtil.affectedPartLabel(context, diagnosis.affectedPart)
        if (affectedLabel != null) {
            badgeAffectedPart.visibility = View.VISIBLE
            badgeAffectedPart.text = affectedLabel
        } else {
            badgeAffectedPart.visibility = View.GONE
        }

        badgesRow.visibility =
            if (severityLabel != null || affectedLabel != null) View.VISIBLE else View.GONE

        // ─── Feature 4: suggested treatment (read-only) ───
        // Only show this as a separate row when the "Hinweis" line above is
        // already occupied by a user note — otherwise the advice line is
        // ALREADY showing this same hint (see noteText fallback above) and a
        // second identical row would just be duplication.
        if (suggestedHint != null && noteText != null) {
            labelSuggestedTreatment.visibility = View.VISIBLE
            txtSuggestedTreatment.visibility = View.VISIBLE
            txtSuggestedTreatment.text = suggestedHint
        } else {
            labelSuggestedTreatment.visibility = View.GONE
            txtSuggestedTreatment.visibility = View.GONE
        }

        // ─── Feature 4: applied treatment (user-editable) ───
        editAppliedTreatment.setText(diagnosis.appliedTreatment.orEmpty())

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

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle(R.string.disease_history_detail_title)
            .setView(view)
            .setPositiveButton(R.string.close, null)
            .setNegativeButton(R.string.disease_history_detail_share) { _, _ ->
                shareDiagnosis(context, diagnosis)
            }
            .create()

        // 2026-05-20 Feature 4 (Enriched history): auto-save the edited
        // applied-treatment field on dismiss. No explicit "Save" button —
        // the user just types and closes, the change is persisted in the
        // background via the repository. Edits to plain-text user fields
        // are idempotent and small, so a missing save action is fine.
        dialog.setOnDismissListener {
            val edited = editAppliedTreatment.text?.toString()?.trim().orEmpty()
            val original = diagnosis.appliedTreatment.orEmpty().trim()
            if (edited != original) {
                owner.lifecycleScope.launch {
                    runCatching {
                        DiseaseDiagnosisRepository.getInstance(context)
                            .updateUserFields(
                                id = diagnosis.id,
                                note = diagnosis.note,
                                appliedTreatment = edited.ifBlank { null }
                            )
                    }.onFailure { com.example.plantcare.CrashReporter.log(it) }
                }
            }
        }
        dialog.show()
    }

    private fun shareDiagnosis(context: Context, diagnosis: DiseaseDiagnosis) {
        val body = context.getString(
            R.string.disease_share_body,
            diagnosis.displayName,
            diagnosis.confidence * 100f,
            // Same fallback as the detail view: `note` is empty for diagnoses,
            // so share the Gemini hint (`suggestedTreatment`) instead of a
            // generic "no advice" line.
            diagnosis.note?.takeIf { it.isNotBlank() }
                ?: diagnosis.suggestedTreatment?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.disease_no_advice)
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.disease_share_subject))
            putExtra(Intent.EXTRA_TEXT, body)
        }
        // Some stripped-down emulators / OEM ROMs have no app that handles
        // ACTION_SEND text/plain — createChooser is supposed to surface
        // "No apps can perform this action" itself but on certain firmware
        // it still throws ActivityNotFoundException up to the caller and
        // crashes the activity. Wrap defensively.
        try {
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.disease_history_detail_share))
            )
        } catch (t: Throwable) {
            com.example.plantcare.CrashReporter.log(t)
            android.widget.Toast.makeText(
                context,
                R.string.settings_help_open_failed,
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}
