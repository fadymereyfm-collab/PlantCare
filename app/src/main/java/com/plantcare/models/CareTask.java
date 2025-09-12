package com.plantcare.models;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;
import java.util.Date;

/**
 * Care task model representing scheduled care activities
 * نموذج مهام العناية يمثل أنشطة العناية المجدولة
 */
@Entity(tableName = "care_tasks",
        foreignKeys = @ForeignKey(
                entity = Plant.class,
                parentColumns = "id",
                childColumns = "plantId",
                onDelete = ForeignKey.CASCADE
        ))
public class CareTask {
    
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private long plantId;               // معرف النبتة
    private TaskType type;              // نوع المهمة
    private Date scheduledDate;         // التاريخ المجدول
    private Date completedDate;         // تاريخ الإنجاز
    private boolean completed;          // مكتملة أم لا
    private String notes;               // ملاحظات
    private boolean reminderSent;       // تم إرسال التذكير
    private int priority;               // الأولوية (1-5)
    
    // Constructors
    public CareTask() {}
    
    public CareTask(long plantId, TaskType type, Date scheduledDate) {
        this.plantId = plantId;
        this.type = type;
        this.scheduledDate = scheduledDate;
        this.completed = false;
        this.reminderSent = false;
        this.priority = 3; // Default medium priority
    }
    
    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public long getPlantId() { return plantId; }
    public void setPlantId(long plantId) { this.plantId = plantId; }
    
    public TaskType getType() { return type; }
    public void setType(TaskType type) { this.type = type; }
    
    public Date getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(Date scheduledDate) { this.scheduledDate = scheduledDate; }
    
    public Date getCompletedDate() { return completedDate; }
    public void setCompletedDate(Date completedDate) { this.completedDate = completedDate; }
    
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public boolean isReminderSent() { return reminderSent; }
    public void setReminderSent(boolean reminderSent) { this.reminderSent = reminderSent; }
    
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    
    /**
     * Check if task is overdue
     * فحص ما إذا كانت المهمة متأخرة
     */
    public boolean isOverdue() {
        if (completed || scheduledDate == null) return false;
        return new Date().after(scheduledDate);
    }
    
    /**
     * Get days until due date
     * الحصول على الأيام حتى تاريخ الاستحقاق
     */
    public int getDaysUntilDue() {
        if (scheduledDate == null) return Integer.MAX_VALUE;
        long diff = scheduledDate.getTime() - new Date().getTime();
        return (int) (diff / (24 * 60 * 60 * 1000));
    }
    
    /**
     * Mark task as completed
     * تمييز المهمة كمكتملة
     */
    public void markCompleted() {
        this.completed = true;
        this.completedDate = new Date();
    }
}