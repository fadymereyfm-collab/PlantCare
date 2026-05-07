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
    val note: String? = null
)
