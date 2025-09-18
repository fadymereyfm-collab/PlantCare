package com.plantcare;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages local data storage for PlantCare app.
 * Handles saving and loading tasks from local SharedPreferences.
 */
public class DataManager {
    private static final String TAG = "DataManager";
    private static final String LOCAL_PREFS = "LocalDataPrefs";
    private static final String KEY_TASKS = "local_tasks";
    private static final String KEY_LAST_UPDATED = "last_updated";
    
    private Context context;
    private SharedPreferences localPrefs;
    
    public DataManager(Context context) {
        this.context = context;
        this.localPrefs = context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE);
    }
    
    /**
     * Save tasks to local storage
     */
    public boolean saveTasks(List<TodayTask> tasks) {
        try {
            String tasksJson = tasksToJson(tasks);
            
            SharedPreferences.Editor editor = localPrefs.edit();
            editor.putString(KEY_TASKS, tasksJson);
            editor.putLong(KEY_LAST_UPDATED, System.currentTimeMillis());
            boolean success = editor.commit();
            
            if (success) {
                Log.d(TAG, "Successfully saved " + tasks.size() + " tasks to local storage");
            } else {
                Log.e(TAG, "Failed to save tasks to local storage");
            }
            
            return success;
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving tasks to local storage", e);
            return false;
        }
    }
    
    /**
     * Load all tasks from local storage
     */
    public List<TodayTask> getAllTasks() {
        try {
            String tasksJson = localPrefs.getString(KEY_TASKS, null);
            
            if (tasksJson == null || tasksJson.isEmpty()) {
                Log.d(TAG, "No local tasks found");
                return new ArrayList<>();
            }
            
            List<TodayTask> tasks = parseTasksJson(tasksJson);
            Log.d(TAG, "Loaded " + tasks.size() + " tasks from local storage");
            return tasks;
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading tasks from local storage", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Add a single task to local storage
     */
    public boolean addTask(TodayTask task) {
        List<TodayTask> currentTasks = getAllTasks();
        currentTasks.add(task);
        return saveTasks(currentTasks);
    }
    
    /**
     * Remove a task from local storage by title
     */
    public boolean removeTask(String title) {
        List<TodayTask> currentTasks = getAllTasks();
        boolean removed = false;
        
        for (int i = currentTasks.size() - 1; i >= 0; i--) {
            if (currentTasks.get(i).getTitle().equals(title)) {
                currentTasks.remove(i);
                removed = true;
                break;
            }
        }
        
        if (removed) {
            return saveTasks(currentTasks);
        }
        
        return false;
    }
    
    /**
     * Clear all local data
     */
    public void clearLocalData() {
        SharedPreferences.Editor editor = localPrefs.edit();
        editor.clear();
        boolean success = editor.commit();
        
        if (success) {
            Log.d(TAG, "Successfully cleared all local data");
        } else {
            Log.e(TAG, "Failed to clear local data");
        }
    }
    
    /**
     * Get the timestamp of last local data update
     */
    public long getLastUpdatedTimestamp() {
        return localPrefs.getLong(KEY_LAST_UPDATED, 0);
    }
    
    /**
     * Check if local data exists
     */
    public boolean hasLocalData() {
        return localPrefs.contains(KEY_TASKS) && !getAllTasks().isEmpty();
    }
    
    /**
     * Get the number of tasks in local storage
     */
    public int getTaskCount() {
        return getAllTasks().size();
    }
    
    /**
     * Convert TodayTask list to JSON string
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
        
        return rootObject.toString();
    }
    
    /**
     * Parse JSON string to TodayTask list
     */
    private List<TodayTask> parseTasksJson(String tasksJson) throws JSONException {
        List<TodayTask> tasks = new ArrayList<>();
        
        JSONObject rootObject = new JSONObject(tasksJson);
        JSONArray tasksArray = rootObject.getJSONArray("tasks");
        
        for (int i = 0; i < tasksArray.length(); i++) {
            JSONObject taskObj = tasksArray.getJSONObject(i);
            
            String title = taskObj.getString("title");
            String description = taskObj.getString("description");
            String type = taskObj.getString("type");
            
            TodayTask task = new TodayTask(title, description, type);
            tasks.add(task);
        }
        
        return tasks;
    }
    
    /**
     * Update local data with new tasks, keeping existing ones if not replaced
     */
    public boolean mergeTasks(List<TodayTask> newTasks) {
        List<TodayTask> existingTasks = getAllTasks();
        List<TodayTask> mergedTasks = new ArrayList<>();
        
        // Add all new tasks
        mergedTasks.addAll(newTasks);
        
        // Add existing tasks that don't conflict with new ones
        for (TodayTask existingTask : existingTasks) {
            boolean alreadyExists = false;
            for (TodayTask newTask : newTasks) {
                if (existingTask.getTitle().equals(newTask.getTitle())) {
                    alreadyExists = true;
                    break;
                }
            }
            if (!alreadyExists) {
                mergedTasks.add(existingTask);
            }
        }
        
        Log.d(TAG, "Merged " + newTasks.size() + " new tasks with " + 
              existingTasks.size() + " existing tasks, result: " + mergedTasks.size() + " tasks");
        
        return saveTasks(mergedTasks);
    }
}