package com.example.plantcare;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Date;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class WateringEventStore {

    private final com.example.plantcare.data.repository.ReminderRepository reminderRepo;
    private final Context context;

    public WateringEventStore(Context context) {
        this.context = context;
        reminderRepo = com.example.plantcare.data.repository.ReminderRepository.getInstance(context);
    }

    public List<WateringReminder> getActiveReminders() {
        String todayString = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String userEmail = EmailContext.current(context);
        return reminderRepo.getTodayAndOverdueRemindersForUserBlocking(todayString, userEmail);
    }

    public void completeReminder(WateringReminder reminder) {
        reminder.done = true;
        reminder.completedDate = new Date();
        reminderRepo.updateBlocking(reminder);
    }

    public void resetReminder(WateringReminder reminder) {
        reminder.done = false;
        reminder.completedDate = null;
        reminderRepo.updateBlocking(reminder);
    }
}