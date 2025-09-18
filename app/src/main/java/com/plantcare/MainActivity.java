package com.plantcare;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "PlantCarePrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_ID = "userId";
    
    private CloudDataManager cloudDataManager;
    private DataManager dataManager;
    private TextView loginStatusText;
    private Button loginLogoutButton;
    private RecyclerView tasksRecyclerView;
    private TodayAdapter todayAdapter;
    private boolean isLoggedIn = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initializeComponents();
        setupUI();
        checkLoginStatus();
    }
    
    private void initializeComponents() {
        cloudDataManager = new CloudDataManager(this);
        dataManager = new DataManager(this);
        
        loginStatusText = findViewById(R.id.login_status_text);
        loginLogoutButton = findViewById(R.id.login_logout_button);
        tasksRecyclerView = findViewById(R.id.tasks_recycler_view);
        
        // Setup RecyclerView
        tasksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        todayAdapter = new TodayAdapter(new ArrayList<>());
        tasksRecyclerView.setAdapter(todayAdapter);
    }
    
    private void setupUI() {
        loginLogoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLoggedIn) {
                    logout();
                } else {
                    login();
                }
            }
        });
    }
    
    private void checkLoginStatus() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false);
        updateUI();
        
        if (isLoggedIn) {
            // Import data from cloud when app starts and user is logged in
            importDataFromCloud();
        }
    }
    
    private void login() {
        Log.d(TAG, "Starting login process");
        
        // Simulate login process (in real app, this would connect to authentication service)
        String userId = "user_" + System.currentTimeMillis();
        
        // Save login state
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_USER_ID, userId);
        editor.apply();
        
        isLoggedIn = true;
        updateUI();
        
        // Import data from cloud after successful login
        importDataFromCloud();
        
        Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show();
    }
    
    private void logout() {
        Log.d(TAG, "Starting logout process");
        
        // Export current data to cloud before logout
        exportDataToCloud();
        
        // Clear login state
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, false);
        editor.remove(KEY_USER_ID);
        editor.apply();
        
        isLoggedIn = false;
        updateUI();
        
        // Clear local data
        dataManager.clearLocalData();
        loadLocalData(); // Refresh UI
        
        Toast.makeText(this, "Logout successful", Toast.LENGTH_SHORT).show();
    }
    
    private void importDataFromCloud() {
        Log.d(TAG, "Importing data from cloud");
        
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String userId = prefs.getString(KEY_USER_ID, null);
        
        if (userId == null) {
            Log.e(TAG, "Cannot import data: User ID is null");
            return;
        }
        
        // Check if we have local data already
        boolean hasLocalData = dataManager.hasLocalData();
        boolean hasCloudData = cloudDataManager.hasCloudData(userId);
        
        Log.d(TAG, "Data status - Local: " + hasLocalData + ", Cloud: " + hasCloudData);
        
        // Always try to import from cloud, especially after fresh install
        cloudDataManager.importData(userId, new CloudDataManager.DataImportCallback() {
            @Override
            public void onSuccess(List<TodayTask> importedTasks) {
                Log.d(TAG, "Data import successful. Imported " + importedTasks.size() + " tasks");
                
                // Determine how to handle the imported data
                if (!hasLocalData) {
                    // Fresh install or no local data - use imported data directly
                    Log.d(TAG, "No local data found, using imported data directly");
                    dataManager.saveTasks(importedTasks);
                } else {
                    // Merge with existing local data
                    Log.d(TAG, "Merging imported data with existing local data");
                    dataManager.mergeTasks(importedTasks);
                }
                
                // Update UI
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadLocalData();
                        String message = importedTasks.size() > 0 ? 
                            "Data imported successfully: " + importedTasks.size() + " tasks" :
                            "Connected to cloud storage";
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Data import failed: " + error);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Show error but don't block user from using the app
                        Toast.makeText(MainActivity.this, 
                            "Could not sync with cloud: " + error, 
                            Toast.LENGTH_LONG).show();
                        
                        // Load any existing local data
                        loadLocalData();
                        
                        // If no local data exists, create some default tasks for the user
                        if (!dataManager.hasLocalData()) {
                            Log.d(TAG, "No local data found, creating default tasks");
                            createDefaultLocalTasks();
                        }
                    }
                });
            }
        });
    }
    
    private void exportDataToCloud() {
        Log.d(TAG, "Exporting data to cloud");
        
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String userId = prefs.getString(KEY_USER_ID, null);
        
        if (userId == null) {
            Log.e(TAG, "Cannot export data: User ID is null");
            return;
        }
        
        List<TodayTask> localTasks = dataManager.getAllTasks();
        
        cloudDataManager.exportData(userId, localTasks, new CloudDataManager.DataExportCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Data export successful");
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Data export failed: " + error);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, 
                            "Failed to export data: " + error, 
                            Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    
    private void loadLocalData() {
        List<TodayTask> tasks = dataManager.getAllTasks();
        todayAdapter = new TodayAdapter(tasks);
        tasksRecyclerView.setAdapter(todayAdapter);
        Log.d(TAG, "Loaded " + tasks.size() + " tasks from local storage");
    }
    
    private void updateUI() {
        if (isLoggedIn) {
            loginStatusText.setText("Status: Logged In");
            loginLogoutButton.setText("Logout");
            loadLocalData();
        } else {
            loginStatusText.setText("Status: Not Logged In");
            loginLogoutButton.setText("Login");
            // Clear the tasks list when not logged in
            todayAdapter = new TodayAdapter(new ArrayList<>());
            tasksRecyclerView.setAdapter(todayAdapter);
        }
    }
    
    private void createDefaultLocalTasks() {
        Log.d(TAG, "Creating default local tasks for new user");
        List<TodayTask> defaultTasks = new ArrayList<>();
        
        defaultTasks.add(new TodayTask(
            "Welcome to PlantCare!", 
            "Start by adding your plants and setting up watering schedules", 
            "watering"
        ));
        
        defaultTasks.add(new TodayTask(
            "Check plant health", 
            "Look for signs of pests, diseases, or nutrient deficiencies", 
            "fertilizing"
        ));
        
        dataManager.saveTasks(defaultTasks);
        loadLocalData();
        
        Toast.makeText(this, "Welcome! Your data will sync with the cloud.", Toast.LENGTH_LONG).show();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Export data when app goes to background (if logged in)
        if (isLoggedIn) {
            exportDataToCloud();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Check if login status changed while app was in background
        checkLoginStatus();
    }
}