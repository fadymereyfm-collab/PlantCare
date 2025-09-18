package com.plantcare;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages cloud data synchronization for PlantCare app.
 * Simulates cloud storage operations with local persistence for demonstration.
 * In a real implementation, this would connect to Firebase, AWS, or another cloud service.
 */
public class CloudDataManager {
    private static final String TAG = "CloudDataManager";
    private static final String CLOUD_PREFS = "CloudDataPrefs";
    private static final String KEY_CLOUD_DATA_PREFIX = "cloud_data_";
    
    private Context context;
    private ExecutorService executor;
    private Handler mainHandler;
    
    public interface DataImportCallback {
        void onSuccess(List<TodayTask> importedTasks);
        void onError(String error);
    }
    
    public interface DataExportCallback {
        void onSuccess();
        void onError(String error);
    }
    
    public CloudDataManager(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Import user data from cloud storage
     */
    public void importData(String userId, DataImportCallback callback) {
        Log.d(TAG, "Starting data import for user: " + userId);
        
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // Simulate network delay
                    Thread.sleep(1000);
                    
                    // Get cloud data from simulated cloud storage (SharedPreferences)
                    SharedPreferences cloudPrefs = context.getSharedPreferences(CLOUD_PREFS, Context.MODE_PRIVATE);
                    String cloudDataJson = cloudPrefs.getString(KEY_CLOUD_DATA_PREFIX + userId, null);
                    
                    if (cloudDataJson == null || cloudDataJson.isEmpty()) {
                        Log.d(TAG, "No cloud data found for user: " + userId + " - providing default data");
                        // For new users or users without cloud data, provide default tasks
                        List<TodayTask> defaultTasks = createDefaultTasks();
                        
                        // Save the default data to cloud immediately so it's available next time
                        String defaultDataJson = tasksToJson(defaultTasks);
                        SharedPreferences.Editor editor = cloudPrefs.edit();
                        editor.putString(KEY_CLOUD_DATA_PREFIX + userId, defaultDataJson);
                        editor.putLong(KEY_CLOUD_DATA_PREFIX + userId + "_timestamp", System.currentTimeMillis());
                        editor.commit();
                        
                        mainHandler.post(() -> callback.onSuccess(defaultTasks));
                        return;
                    }
                    
                    Log.d(TAG, "Found cloud data for user: " + userId);
                    List<TodayTask> importedTasks = parseCloudData(cloudDataJson);
                    
                    if (importedTasks.isEmpty()) {
                        Log.w(TAG, "Cloud data exists but no tasks found - providing default data");
                        List<TodayTask> defaultTasks = createDefaultTasks();
                        mainHandler.post(() -> callback.onSuccess(defaultTasks));
                    } else {
                        Log.d(TAG, "Successfully parsed " + importedTasks.size() + " tasks from cloud");
                        mainHandler.post(() -> callback.onSuccess(importedTasks));
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error importing data for user: " + userId, e);
                    mainHandler.post(() -> callback.onError("Import failed: " + e.getMessage()));
                }
            }
        });
    }
    
    /**
     * Export user data to cloud storage
     */
    public void exportData(String userId, List<TodayTask> tasks, DataExportCallback callback) {
        Log.d(TAG, "Starting data export for user: " + userId + " with " + tasks.size() + " tasks");
        
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // Simulate network delay
                    Thread.sleep(500);
                    
                    // Convert tasks to JSON
                    String cloudDataJson = tasksToJson(tasks);
                    
                    // Save to simulated cloud storage (SharedPreferences)
                    SharedPreferences cloudPrefs = context.getSharedPreferences(CLOUD_PREFS, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = cloudPrefs.edit();
                    editor.putString(KEY_CLOUD_DATA_PREFIX + userId, cloudDataJson);
                    editor.putLong(KEY_CLOUD_DATA_PREFIX + userId + "_timestamp", System.currentTimeMillis());
                    boolean success = editor.commit();
                    
                    if (success) {
                        Log.d(TAG, "Data export successful for user: " + userId);
                        mainHandler.post(() -> callback.onSuccess());
                    } else {
                        Log.e(TAG, "Failed to save data to cloud storage");
                        mainHandler.post(() -> callback.onError("Failed to save to cloud storage"));
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error exporting data for user: " + userId, e);
                    mainHandler.post(() -> callback.onError("Export failed: " + e.getMessage()));
                }
            }
        });
    }
    
    /**
     * Check if cloud data exists for a user
     */
    public boolean hasCloudData(String userId) {
        SharedPreferences cloudPrefs = context.getSharedPreferences(CLOUD_PREFS, Context.MODE_PRIVATE);
        return cloudPrefs.contains(KEY_CLOUD_DATA_PREFIX + userId);
    }
    
    /**
     * Get the timestamp of last cloud data update
     */
    public long getCloudDataTimestamp(String userId) {
        SharedPreferences cloudPrefs = context.getSharedPreferences(CLOUD_PREFS, Context.MODE_PRIVATE);
        return cloudPrefs.getLong(KEY_CLOUD_DATA_PREFIX + userId + "_timestamp", 0);
    }
    
    /**
     * Parse JSON data from cloud storage into TodayTask objects
     */
    private List<TodayTask> parseCloudData(String cloudDataJson) throws JSONException {
        List<TodayTask> tasks = new ArrayList<>();
        
        JSONObject rootObject = new JSONObject(cloudDataJson);
        JSONArray tasksArray = rootObject.getJSONArray("tasks");
        
        for (int i = 0; i < tasksArray.length(); i++) {
            JSONObject taskObj = tasksArray.getJSONObject(i);
            
            String title = taskObj.getString("title");
            String description = taskObj.getString("description");
            String type = taskObj.getString("type");
            
            TodayTask task = new TodayTask(title, description, type);
            tasks.add(task);
        }
        
        Log.d(TAG, "Parsed " + tasks.size() + " tasks from cloud data");
        return tasks;
    }
    
    /**
     * Convert TodayTask list to JSON for cloud storage
     */
    private String tasksToJson(List<TodayTask> tasks) throws JSONException {
        JSONObject rootObject = new JSONObject();
        JSONArray tasksArray = new JSONArray();
        
        for (TodayTask task : tasks) {
            JSONObject taskObj = new JSONObject();
            taskObj.put("title", task.getTitle());
            taskObj.put("description", task.getDescription());
            taskObj.put("type", task.getType());
            tasksArray.put(taskObj);
        }
        
        rootObject.put("tasks", tasksArray);
        rootObject.put("timestamp", System.currentTimeMillis());
        rootObject.put("version", "1.0");
        
        return rootObject.toString();
    }
    
    /**
     * Create default tasks for new users or when no cloud data exists
     */
    private List<TodayTask> createDefaultTasks() {
        List<TodayTask> defaultTasks = new ArrayList<>();
        
        defaultTasks.add(new TodayTask(
            "Water the Spider Plant", 
            "Check soil moisture and water if needed", 
            "watering"
        ));
        
        defaultTasks.add(new TodayTask(
            "Fertilize the Fiddle Leaf Fig", 
            "Apply liquid fertilizer diluted to half strength", 
            "fertilizing"
        ));
        
        defaultTasks.add(new TodayTask(
            "Water the Peace Lily", 
            "Water thoroughly until water drains from bottom", 
            "watering"
        ));
        
        Log.d(TAG, "Created " + defaultTasks.size() + " default tasks");
        return defaultTasks;
    }
    
    /**
     * Clear all cloud data for testing purposes
     */
    public void clearAllCloudData() {
        SharedPreferences cloudPrefs = context.getSharedPreferences(CLOUD_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = cloudPrefs.edit();
        editor.clear();
        editor.apply();
        Log.d(TAG, "Cleared all cloud data");
    }
}