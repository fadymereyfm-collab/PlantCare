package com.example.plantcare;

import android.content.Context;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ReminderUtils {

    public static List<WateringReminder> generateReminders(Plant plant) {
        List<WateringReminder> reminders = new ArrayList<>();

        if (plant.getStartDate() == null || plant.getWateringInterval() <= 0) return reminders;

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(plant.getStartDate());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String todayStr = sdf.format(new Date());

        int interval = plant.getWateringInterval();
        for (int i = 0; i < 60; i += interval) {
            Date reminderDate = calendar.getTime();
            String dateStr = sdf.format(reminderDate);

            if (dateStr.compareTo(todayStr) >= 0) {
                WateringReminder reminder = new WateringReminder();
                reminder.plantId = plant.getId();
                reminder.plantName = plant.getNickname();
                reminder.date = dateStr;
                reminder.done = false;
                reminder.repeat = String.valueOf(interval); // كل التذكيرات تلقائية (repeat > 0)
                reminder.description = ""; // لا يوجد وصف (أي ليس يدوي)
                reminder.userEmail = plant.getUserEmail();
                reminders.add(reminder);
            }

            calendar.add(Calendar.DAY_OF_YEAR, interval);
        }

        return reminders;
    }

    public static void rescheduleFromToday(WateringReminder reminder, Context context) {
        try {
            AppDatabase db = DatabaseClient.getInstance(context).getAppDatabase();
            ReminderDao reminderDao = db.reminderDao();
            PlantDao plantDao = db.plantDao();

            int repeatDays;
            try {
                repeatDays = Integer.parseInt(reminder.repeat);
            } catch (NumberFormatException e) {
                repeatDays = 0;
            }

            if (repeatDays <= 0) {
                Plant plant = plantDao.findByNickname(reminder.plantName);
                if (plant == null) {
                    plant = plantDao.findUserPlantByNameAndUser(reminder.plantName, reminder.userEmail);
                }
                if (plant != null) {
                    repeatDays = plant.getWateringInterval();
                }
            }

            if (repeatDays <= 0) return;

            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String newDate = sdf.format(calendar.getTime());

            reminderDao.deleteFutureRemindersForPlant(reminder.plantId, newDate);

            reminder.date = newDate;
            reminder.done = false;
            reminder.completedDate = null;
            reminder.repeat = String.valueOf(repeatDays);
            reminderDao.update(reminder);

            Plant plant = plantDao.findByNickname(reminder.plantName);
            if (plant == null) {
                plant = plantDao.findUserPlantByNameAndUser(reminder.plantName, reminder.userEmail);
            }
            if (plant != null) {
                Date dateObj = sdf.parse(newDate);
                plant.setStartDate(dateObj);
                plant.setWateringInterval(repeatDays);
                plantDao.update(plant);

                List<WateringReminder> newReminders = ReminderUtils.generateReminders(plant);
                if (!newReminders.isEmpty()) {
                    newReminders.remove(0);
                    reminderDao.insertAll(newReminders);
                }
            }

            DataChangeNotifier.notifyChange();
            if (context != null) {
                DataChangeNotifier.notifyCalendar(context);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int parseWateringInterval(String watering) {
        if (watering == null || watering.isEmpty()) return 0;
        try {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(watering);
            int interval = 0;
            while (matcher.find()) {
                interval = Integer.parseInt(matcher.group(1));
            }
            return interval;
        } catch (Exception e) {
            // expected: parseInt may overflow on malformed input — fall through to 0
            return 0;
        }
    }

    public static Date parseDate(String dateStr) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr);
        } catch (ParseException e) {
            return null;
        }
    }
}