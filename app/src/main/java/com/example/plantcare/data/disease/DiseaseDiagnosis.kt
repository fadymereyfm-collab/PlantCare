package com.example.plantcare.data.disease

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ergebnis einer lokalen Krankheitsdiagnose (TensorFlow Lite).
 *
 * Ein Datensatz entspricht einem Bild, das der Benutzer analysiert hat.
 * Optional kann das Ergebnis mit einer bestimmten Pflanze verknüpft sein.
 * plantId=0 bedeutet "nicht verknüpft" — daher kein FK-Constraint.
 */
@Entity(
    tableName = "disease_diagnosis",
    indices = [Index("plantId"), Index("userEmail")]
)
data class DiseaseDiagnosis(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** Bild-Pfad (lokaler Datei-Pfad, z. B. aus getExternalFilesDir). */
    val imagePath: String,

    /** Rohschlüssel aus labels.txt (z. B. "Tomato___Late_blight"). */
    val diseaseKey: String,

    /** Deutscher Anzeigename (z. B. "Tomate — Kraut- und Braunfäule"). */
    val displayName: String,

    /** Konfidenz des Modells zwischen 0.0 und 1.0. */
    val confidence: Float,

    /** Zeitpunkt der Diagnose (epoch millis). */
    val createdAt: Long,

    /** Optional: ID der Pflanze aus `plant`-Tabelle (0 = nicht verknüpft). */
    val plantId: Int = 0,

    /** E-Mail des Benutzers (für Multi-User / Guest). */
    val userEmail: String? = null,

    /** Optionaler Benutzer-Hinweis. */
    val note: String? = null,

    // ─── 2026-05-20 Feature 4 (Enriched history) ───────────────────────
    // All four columns are nullable. Older rows have null; new rows
    // populated from Gemini at save time.

    /**
     * Where on the plant the disease shows up. Gemini-emitted value;
     * one of: "leaf", "root", "stem", "flower", "whole". Null for
     * legacy rows and for diagnoses where Gemini didn't commit.
     */
    val affectedPart: String? = null,

    /**
     * Severity of the infestation as judged by Gemini. One of:
     * "mild", "moderate", "severe". Drives the colour-coded chip in
     * the history detail dialog.
     */
    val severity: String? = null,

    /**
     * Gemini's recommended treatment, kept separate from [note] so the
     * note field stays a pure free-text user field. Populated by the
     * `advice` field of the Gemini response.
     */
    val suggestedTreatment: String? = null,

    /**
     * What the user actually did after the diagnosis (free text), e.g.
     * "Rüschtt Neemöl 2026-05-22". Editable in the history detail
     * dialog. Null until the user fills it in.
     */
    val appliedTreatment: String? = null
)
