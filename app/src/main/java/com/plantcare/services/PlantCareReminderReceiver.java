package com.plantcare.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Broadcast receiver for plant care reminders
 * مستقبل البث لتذكيرات العناية بالنباتات
 */
public class PlantCareReminderReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String plantName = intent.getStringExtra("plant_name");
        String careType = intent.getStringExtra("care_type");
        
        if (plantName != null && careType != null) {
            NotificationService.sendCareReminder(context, plantName, careType);
        }
    }
}