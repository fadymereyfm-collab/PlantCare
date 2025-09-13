package com.plantcare;

public class TodayTask {
    private String title;
    private String description;
    private String type;
    
    public TodayTask(String title, String description, String type) {
        this.title = title;
        this.description = description;
        this.type = type;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getType() {
        return type;
    }
}