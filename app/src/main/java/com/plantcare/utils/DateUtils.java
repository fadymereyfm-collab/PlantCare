package com.plantcare.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Utility class for date operations
 * فئة مساعدة لعمليات التاريخ
 */
public class DateUtils {
    
    public static final String DATE_FORMAT = "dd/MM/yyyy";
    public static final String DATE_TIME_FORMAT = "dd/MM/yyyy HH:mm";
    
    /**
     * Format date to string
     * تنسيق التاريخ إلى نص
     */
    public static String formatDate(Date date) {
        if (date == null) return "";
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
        return sdf.format(date);
    }
    
    /**
     * Format date and time to string
     * تنسيق التاريخ والوقت إلى نص
     */
    public static String formatDateTime(Date date) {
        if (date == null) return "";
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_TIME_FORMAT, Locale.getDefault());
        return sdf.format(date);
    }
    
    /**
     * Add days to a date
     * إضافة أيام إلى تاريخ
     */
    public static Date addDays(Date date, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }
    
    /**
     * Get the difference in days between two dates
     * الحصول على الفرق بالأيام بين تاريخين
     */
    public static int getDaysDifference(Date startDate, Date endDate) {
        long diffInMillis = endDate.getTime() - startDate.getTime();
        return (int) (diffInMillis / (24 * 60 * 60 * 1000));
    }
    
    /**
     * Check if a date is today
     * فحص ما إذا كان التاريخ هو اليوم
     */
    public static boolean isToday(Date date) {
        Calendar today = Calendar.getInstance();
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        
        return today.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
               today.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR);
    }
    
    /**
     * Check if a date is tomorrow
     * فحص ما إذا كان التاريخ هو غداً
     */
    public static boolean isTomorrow(Date date) {
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_MONTH, 1);
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        
        return tomorrow.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
               tomorrow.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR);
    }
}