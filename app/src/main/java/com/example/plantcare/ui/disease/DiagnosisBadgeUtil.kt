package com.example.plantcare.ui.disease

import android.content.Context
import androidx.core.content.ContextCompat
import com.example.plantcare.R

/**
 * Shared formatting for the severity + affected-part badges of a
 * [com.example.plantcare.data.disease.DiseaseDiagnosis]. Used by both the
 * detail dialog and the history list rows so the labels and colours stay
 * identical across the two surfaces.
 */
object DiagnosisBadgeUtil {

    /** German label for a Gemini severity value, or null when unknown. */
    fun severityLabel(context: Context, severity: String?): String? =
        when (severity?.lowercase()) {
            "mild" -> context.getString(R.string.disease_severity_mild)
            "moderate" -> context.getString(R.string.disease_severity_moderate)
            "severe" -> context.getString(R.string.disease_severity_severe)
            else -> null
        }

    /** Background colour for the severity chip: green / amber / red. */
    fun severityColor(context: Context, severity: String?): Int =
        when (severity?.lowercase()) {
            "mild" -> ContextCompat.getColor(context, R.color.pc_primary)
            "moderate" -> ContextCompat.getColor(context, R.color.pc_tertiary)
            "severe" -> ContextCompat.getColor(context, R.color.pc_error)
            else -> ContextCompat.getColor(context, R.color.pc_surfaceContainerHigh)
        }

    /** German label for a Gemini affected-part value, or null when unknown. */
    fun affectedPartLabel(context: Context, part: String?): String? =
        when (part?.lowercase()) {
            "leaf" -> context.getString(R.string.disease_affected_leaf)
            "root" -> context.getString(R.string.disease_affected_root)
            "stem" -> context.getString(R.string.disease_affected_stem)
            "flower" -> context.getString(R.string.disease_affected_flower)
            "whole" -> context.getString(R.string.disease_affected_whole)
            else -> null
        }
}
