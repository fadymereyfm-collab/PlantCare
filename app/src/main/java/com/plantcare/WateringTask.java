package com.plantcare;

public class WateringTask {
    private String plantName;
    private String wateringTime;
    
    public WateringTask(String plantName, String wateringTime) {
        this.plantName = plantName;
        this.wateringTime = wateringTime;
    }
    
    public String getPlantName() {
        return plantName;
    }
    
    public String getWateringTime() {
        return wateringTime;
    }
}