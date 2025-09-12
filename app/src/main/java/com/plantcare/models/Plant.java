package com.plantcare.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.Date;

/**
 * Plant model representing a plant in the user's care
 * نموذج النبتة يمثل نبتة في رعاية المستخدم
 */
@Entity(tableName = "plants")
public class Plant {
    
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private String name;                    // اسم النبتة
    private String type;                    // نوع النبتة
    private String scientificName;          // الاسم العلمي
    private Date plantingDate;              // تاريخ الزراعة
    private String location;                // الموقع (داخلي/خارجي)
    private int wateringFrequency;          // تكرار الري بالأيام
    private int fertilizingFrequency;       // تكرار التسميد بالأيام
    private Date lastWatered;               // آخر موعد ري
    private Date lastFertilized;            // آخر موعد تسميد
    private PlantStatus status;             // حالة النبتة
    private String imageUrl;                // رابط صورة النبتة
    private String notes;                   // ملاحظات
    private boolean notificationsEnabled;   // التذكيرات مفعلة
    
    // Constructors
    public Plant() {}
    
    public Plant(String name, String type, String location) {
        this.name = name;
        this.type = type;
        this.location = location;
        this.plantingDate = new Date();
        this.status = PlantStatus.HEALTHY;
        this.wateringFrequency = 3; // Default: every 3 days
        this.fertilizingFrequency = 30; // Default: every 30 days
        this.notificationsEnabled = true;
    }
    
    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getScientificName() { return scientificName; }
    public void setScientificName(String scientificName) { this.scientificName = scientificName; }
    
    public Date getPlantingDate() { return plantingDate; }
    public void setPlantingDate(Date plantingDate) { this.plantingDate = plantingDate; }
    
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    
    public int getWateringFrequency() { return wateringFrequency; }
    public void setWateringFrequency(int wateringFrequency) { this.wateringFrequency = wateringFrequency; }
    
    public int getFertilizingFrequency() { return fertilizingFrequency; }
    public void setFertilizingFrequency(int fertilizingFrequency) { this.fertilizingFrequency = fertilizingFrequency; }
    
    public Date getLastWatered() { return lastWatered; }
    public void setLastWatered(Date lastWatered) { this.lastWatered = lastWatered; }
    
    public Date getLastFertilized() { return lastFertilized; }
    public void setLastFertilized(Date lastFertilized) { this.lastFertilized = lastFertilized; }
    
    public PlantStatus getStatus() { return status; }
    public void setStatus(PlantStatus status) { this.status = status; }
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }
    
    /**
     * Calculate days since last watering
     * حساب الأيام منذ آخر ري
     */
    public int getDaysSinceLastWatering() {
        if (lastWatered == null) return Integer.MAX_VALUE;
        long diff = new Date().getTime() - lastWatered.getTime();
        return (int) (diff / (24 * 60 * 60 * 1000));
    }
    
    /**
     * Calculate days since last fertilizing
     * حساب الأيام منذ آخر تسميد
     */
    public int getDaysSinceLastFertilizing() {
        if (lastFertilized == null) return Integer.MAX_VALUE;
        long diff = new Date().getTime() - lastFertilized.getTime();
        return (int) (diff / (24 * 60 * 60 * 1000));
    }
    
    /**
     * Check if plant needs watering
     * فحص ما إذا كانت النبتة تحتاج للري
     */
    public boolean needsWatering() {
        return getDaysSinceLastWatering() >= wateringFrequency;
    }
    
    /**
     * Check if plant needs fertilizing
     * فحص ما إذا كانت النبتة تحتاج للتسميد
     */
    public boolean needsFertilizing() {
        return getDaysSinceLastFertilizing() >= fertilizingFrequency;
    }
}