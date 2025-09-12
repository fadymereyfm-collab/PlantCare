package com.plantcare.models;

/**
 * Enum representing different plant status states
 * تعداد يمثل حالات مختلفة لوضع النباتات
 */
public enum PlantStatus {
    HEALTHY("Healthy", "صحية"),           // النبتة بحالة جيدة
    NEEDS_CARE("Needs Care", "تحتاج رعاية"),     // تحتاج لعناية
    SICK("Sick", "مريضة"),                // مريضة
    DORMANT("Dormant", "خامدة"),          // فترة سكون
    GROWING("Growing", "نامية"),          // في طور النمو
    FLOWERING("Flowering", "مزهرة"),      // في طور الإزهار
    DYING("Dying", "ذابلة");             // ذابلة

    private final String englishName;
    private final String arabicName;

    PlantStatus(String englishName, String arabicName) {
        this.englishName = englishName;
        this.arabicName = arabicName;
    }

    public String getEnglishName() {
        return englishName;
    }

    public String getArabicName() {
        return arabicName;
    }

    public String getLocalizedName(boolean isArabic) {
        return isArabic ? arabicName : englishName;
    }
}