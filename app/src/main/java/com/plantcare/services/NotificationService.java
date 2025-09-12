package com.plantcare.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import com.plantcare.R;
import com.plantcare.activities.MainActivity;

/**
 * Service for handling plant care notifications
 * خدمة التعامل مع إشعارات العناية بالنباتات
 */
public class NotificationService extends Service {
    
    private static final String CHANNEL_ID = "PLANT_CARE_CHANNEL";
    private static final String CHANNEL_NAME = "Plant Care Reminders";
    private static final String CHANNEL_DESCRIPTION = "Notifications for plant care reminders";
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle notification creation logic here
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    /**
     * Create notification channel for Android O and above
     * إنشاء قناة الإشعارات لأندرويد O وما بعده
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    /**
     * Send a plant care reminder notification
     * إرسال إشعار تذكير بالعناية بالنباتات
     */
    public static void sendCareReminder(Context context, String plantName, String careType) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String title = context.getString(R.string.app_name);
        String message = String.format("Time to %s your %s! / حان وقت %s %s!", 
                careType.toLowerCase(), plantName, careType, plantName);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_plants)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        
        NotificationManager notificationManager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
    
    /**
     * Schedule periodic care reminders
     * جدولة تذكيرات دورية للعناية
     */
    public static void scheduleCareTasks(Context context) {
        // TODO: Implement WorkManager for scheduled notifications
        // This would typically use Android WorkManager to schedule periodic tasks
    }
}