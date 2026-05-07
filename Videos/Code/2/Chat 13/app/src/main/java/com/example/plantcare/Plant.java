package com.example.plantcare;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;
import java.util.Date;

/**
 * Full Plant entity with all original fields plus Date (requires a Date converter in Room setup).
 * imageUri is exclusively the profile image (not overwritten by archive photos).
 */
@Entity(tableName = "plant")
public class Plant implements Serializable {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @Nullable
    public String name;
    @Nullable
    public String nickname;
    public Date startDate;
    public int wateringInterval;

    @ColumnInfo(name = "isUserPlant")
    public boolean isUserPlant;

    @Nullable
    public String lighting;
    @Nullable
    public String soil;
    @Nullable
    public String fertilizing;
    @Nullable
    public String watering;

    // Profile image only
    @Nullable
    public String imageUri;

    public boolean isFavorite;

    // Personal note per plant
    @Nullable
    public String personalNote;

    // Owner's email
    @ColumnInfo(name = "userEmail")
    @Nullable
    public String userEmail;

    // Associated room/category
    @ColumnInfo(name = "roomId")
    public int roomId;

    /**
     * Pflanzen-Kategorie für die Katalog-Filterung.
     * Zulässige Werte: "indoor", "outdoor", "herbal", "cacti".
     * Darf null sein (unklassifiziert / ältere Datensätze).
     */
    @ColumnInfo(name = "category")
    @Nullable
    public String category;

    /**
     * Familien-Freigabe: komma-getrennte Liste von E-Mails, die Zugriff auf
     * diese Pflanze haben (zusätzlich zum Eigentümer in {@link #userEmail}).
     * Beispiel: "mama@example.com,papa@example.com".
     * Darf null oder leer sein (Standard: nicht geteilt).
     */
    @ColumnInfo(name = "sharedWith")
    @Nullable
    public String sharedWith;

    @Override
    public String toString() {
        if (nickname != null && !nickname.isEmpty()) {
            return nickname;
        }
        return (name != null) ? name : super.toString();
    }

    // Getters & Setters

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }

    public int getWateringInterval() { return wateringInterval; }
    public void setWateringInterval(int wateringInterval) { this.wateringInterval = wateringInterval; }

    public boolean isUserPlant() { return isUserPlant; }
    public void setUserPlant(boolean userPlant) { isUserPlant = userPlant; }

    public String getLighting() { return lighting; }
    public void setLighting(String lighting) { this.lighting = lighting; }

    public String getSoil() { return soil; }
    public void setSoil(String soil) { this.soil = soil; }

    public String getFertilizing() { return fertilizing; }
    public void setFertilizing(String fertilizing) { this.fertilizing = fertilizing; }

    public String getWatering() { return watering; }
    public void setWatering(String watering) { this.watering = watering; }

    public String getImageUri() { return imageUri; }
    public void setImageUri(String imageUri) { this.imageUri = imageUri; }

    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    public String getPersonalNote() { return personalNote; }
    public void setPersonalNote(String personalNote) { this.personalNote = personalNote; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public int getRoomId() { return roomId; }
    public void setRoomId(int roomId) { this.roomId = roomId; }

    @Nullable public String getCategory() { return category; }
    public void setCategory(@Nullable String category) { this.category = category; }

    @Nullable public String getSharedWith() { return sharedWith; }
    public void setSharedWith(@Nullable String sharedWith) { this.sharedWith = sharedWith; }
}