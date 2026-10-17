package com.example.plantcare;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * PlantPhoto entity matching the expectations of existing Kotlin & Java code:
 * - dateTaken is a String (yyyy-MM-dd) as set throughout the code (not a long).
 * - userEmail used for multi‑user segregation.
 * - isCover distinguishes cover/title image vs archive photo.
 *
 * Keep table name "plant_photo" to align with existing DAO queries.
 */
@Entity(
    tableName = "plant_photo",
    foreignKeys = @ForeignKey(
        entity = Plant.class,
        parentColumns = "id",
        childColumns = "plantId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {
        @Index("plantId"),
        @Index("userEmail")
    }
)
public class PlantPhoto {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public int plantId;

    @Nullable
    public String imagePath;      // URI / path
    @Nullable
    public String dateTaken;      // Stored as ISO-YYYY-MM-DD string in code
    public boolean isCover;       // true if chosen as cover/title
    @Nullable
    public String userEmail;      // owner
    // Optional flag some code referenced (harmless if unused)
    public boolean isProfile;     // may remain false for archive photos

    /**
     * Plant Journal (v11): unterscheidet "regular" (Kalenderfotos), "inspection"
     * (für Disease Diagnosis aufgenommen) und "cover" (Titelbild). Default ist
     * "regular", damit alle vor v11 angelegten Fotos automatisch korrekt klassifiziert
     * sind. Cover-Fotos behalten zusätzlich {@link #isCover}=true für Rückwärtskompatibilität.
     */
    @ColumnInfo(defaultValue = "regular")
    @Nullable
    public String photoType;

    /**
     * Plant Journal (v11): wenn das Foto Teil eines Krankheits-Checks war, verweist
     * dieses Feld auf {@code disease_diagnosis.id}. Kein DB-FK gesetzt, weil das Foto
     * unabhängig vom Diagnose-Record bestehen bleibt (z. B. wenn die Diagnose vom
     * Nutzer manuell gelöscht wird).
     */
    @Nullable
    public Integer diagnosisId;
}