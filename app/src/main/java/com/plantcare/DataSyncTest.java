package com.plantcare;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Test class to demonstrate the cloud data import/export functionality
 * This simulates the scenario described in the issue where data is not imported after app reinstall
 */
public class DataSyncTest {
    private static final String TAG = "DataSyncTest";
    
    private Context context;
    private CloudDataManager cloudDataManager;
    private DataManager dataManager;
    
    public DataSyncTest(Context context) {
        this.context = context;
        this.cloudDataManager = new CloudDataManager(context);
        this.dataManager = new DataManager(context);
    }
    
    /**
     * Test the complete scenario: login, add data, export, logout, clear local data, 
     * login again, and verify import works
     */
    public void testCloudDataImportAfterReinstall() {
        Log.d(TAG, "=== Starting Cloud Data Import Test ===");
        
        // Step 1: Simulate initial login and data creation
        String userId = "test_user_123";
        Log.d(TAG, "Step 1: Simulating initial login for user: " + userId);
        
        // Create some test tasks
        List<TodayTask> originalTasks = createTestTasks();
        Log.d(TAG, "Created " + originalTasks.size() + " test tasks");
        
        // Save tasks locally
        boolean localSaveSuccess = dataManager.saveTasks(originalTasks);
        Log.d(TAG, "Local save result: " + localSaveSuccess);
        
        // Step 2: Export data to cloud
        Log.d(TAG, "Step 2: Exporting data to cloud...");
        cloudDataManager.exportData(userId, originalTasks, new CloudDataManager.DataExportCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "✓ Export successful");
                continueTest_Step3(userId);
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "✗ Export failed: " + error);
            }
        });
    }
    
    private void continueTest_Step3(String userId) {
        // Step 3: Simulate logout and app reinstall (clear local data)
        Log.d(TAG, "Step 3: Simulating logout and app reinstall...");
        dataManager.clearLocalData();
        
        // Verify local data is cleared
        List<TodayTask> localTasks = dataManager.getAllTasks();
        Log.d(TAG, "Local tasks after clear: " + localTasks.size() + " (should be 0)");
        
        if (localTasks.size() == 0) {
            Log.d(TAG, "✓ Local data successfully cleared");
        } else {
            Log.e(TAG, "✗ Local data not properly cleared");
        }
        
        // Step 4: Simulate login again and import data
        Log.d(TAG, "Step 4: Simulating login and data import...");
        cloudDataManager.importData(userId, new CloudDataManager.DataImportCallback() {
            @Override
            public void onSuccess(List<TodayTask> importedTasks) {
                Log.d(TAG, "✓ Import successful - imported " + importedTasks.size() + " tasks");
                
                // Save imported data locally
                boolean saveSuccess = dataManager.saveTasks(importedTasks);
                Log.d(TAG, "Local save after import result: " + saveSuccess);
                
                // Verify data integrity
                verifyDataIntegrity(importedTasks);
                
                Log.d(TAG, "=== Test Completed Successfully ===");
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "✗ Import failed: " + error);
                Log.d(TAG, "=== Test Failed ===");
            }
        });
    }
    
    private void verifyDataIntegrity(List<TodayTask> importedTasks) {
        Log.d(TAG, "Verifying data integrity...");
        
        List<TodayTask> originalTasks = createTestTasks();
        
        if (importedTasks.size() != originalTasks.size()) {
            Log.e(TAG, "✗ Task count mismatch. Original: " + originalTasks.size() + 
                  ", Imported: " + importedTasks.size());
            return;
        }
        
        // Check if all original tasks are present in imported tasks
        boolean allTasksFound = true;
        for (TodayTask originalTask : originalTasks) {
            boolean found = false;
            for (TodayTask importedTask : importedTasks) {
                if (originalTask.getTitle().equals(importedTask.getTitle()) &&
                    originalTask.getDescription().equals(importedTask.getDescription()) &&
                    originalTask.getType().equals(importedTask.getType())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                Log.e(TAG, "✗ Task not found in import: " + originalTask.getTitle());
                allTasksFound = false;
            }
        }
        
        if (allTasksFound) {
            Log.d(TAG, "✓ All tasks verified successfully");
        } else {
            Log.e(TAG, "✗ Data integrity check failed");
        }
    }
    
    private List<TodayTask> createTestTasks() {
        List<TodayTask> tasks = new ArrayList<>();
        
        tasks.add(new TodayTask(
            "Water Spider Plant", 
            "Check soil moisture and water if dry", 
            "watering"
        ));
        
        tasks.add(new TodayTask(
            "Fertilize Monstera", 
            "Apply diluted liquid fertilizer", 
            "fertilizing"
        ));
        
        tasks.add(new TodayTask(
            "Water Peace Lily", 
            "Water thoroughly until drainage", 
            "watering"
        ));
        
        return tasks;
    }
    
    /**
     * Test method to verify cloud data manager handles edge cases
     */
    public void testEdgeCases() {
        Log.d(TAG, "=== Testing Edge Cases ===");
        
        // Test 1: Import for user with no cloud data
        cloudDataManager.importData("nonexistent_user", new CloudDataManager.DataImportCallback() {
            @Override
            public void onSuccess(List<TodayTask> importedTasks) {
                Log.d(TAG, "✓ Import for new user successful - got " + importedTasks.size() + " default tasks");
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "✗ Import for new user failed: " + error);
            }
        });
        
        // Test 2: Export empty task list
        cloudDataManager.exportData("test_user_empty", new ArrayList<>(), new CloudDataManager.DataExportCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "✓ Export of empty task list successful");
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "✗ Export of empty task list failed: " + error);
            }
        });
    }
}