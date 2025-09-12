package com.plantcare.models;

/**
 * Enum representing different types of plant care tasks
 * تعداد يمثل أنواع مختلفة من مهام العناية بالنباتات
 */
public enum TaskType {
    WATERING("Watering", "الري", "💧"),
    FERTILIZING("Fertilizing", "التسميد", "🌱"),
    PRUNING("Pruning", "التقليم", "✂️"),
    REPOTTING("Repotting", "إعادة الزراعة", "🪴"),
    PEST_CONTROL("Pest Control", "مكافحة الآفات", "🐛"),
    MISTING("Misting", "الرش", "💨"),
    CHECKING("Health Check", "فحص الصحة", "🔍"),
    ROTATING("Rotating", "التدوير", "🔄"),
    CLEANING("Cleaning", "التنظيف", "🧽");

    private final String englishName;
    private final String arabicName;
    private final String emoji;

    TaskType(String englishName, String arabicName, String emoji) {
        this.englishName = englishName;
        this.arabicName = arabicName;
        this.emoji = emoji;
    }

    public String getEnglishName() {
        return englishName;
    }

    public String getArabicName() {
        return arabicName;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getLocalizedName(boolean isArabic) {
        return isArabic ? arabicName : englishName;
    }

    public String getDisplayName(boolean isArabic) {
        return emoji + " " + getLocalizedName(isArabic);
    }
}