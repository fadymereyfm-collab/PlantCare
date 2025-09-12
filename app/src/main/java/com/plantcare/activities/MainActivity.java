package com.plantcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.plantcare.R;
import com.plantcare.fragments.PlantListFragment;
import com.plantcare.fragments.CareScheduleFragment;
import com.plantcare.fragments.PlantGuideFragment;

/**
 * Main activity for the PlantCare application
 * النشاط الرئيسي لتطبيق العناية بالنباتات
 */
public class MainActivity extends AppCompatActivity {
    
    private BottomNavigationView bottomNavigation;
    private PlantListFragment plantListFragment;
    private CareScheduleFragment careScheduleFragment;
    private PlantGuideFragment plantGuideFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initializeViews();
        setupBottomNavigation();
        
        // Show plant list fragment by default
        if (savedInstanceState == null) {
            showFragment(getPlantListFragment());
        }
    }
    
    private void initializeViews() {
        bottomNavigation = findViewById(R.id.bottom_navigation);
        setupActionBar();
    }
    
    private void setupActionBar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.app_name));
            getSupportActionBar().setElevation(0);
        }
    }
    
    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            
            if (itemId == R.id.nav_plants) {
                showFragment(getPlantListFragment());
                setTitle(getString(R.string.my_plants));
                return true;
            } else if (itemId == R.id.nav_schedule) {
                showFragment(getCareScheduleFragment());
                setTitle(getString(R.string.care_schedule));
                return true;
            } else if (itemId == R.id.nav_guide) {
                showFragment(getPlantGuideFragment());
                setTitle(getString(R.string.plant_guide));
                return true;
            }
            
            return false;
        });
    }
    
    private void showFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }
    
    private PlantListFragment getPlantListFragment() {
        if (plantListFragment == null) {
            plantListFragment = new PlantListFragment();
        }
        return plantListFragment;
    }
    
    private CareScheduleFragment getCareScheduleFragment() {
        if (careScheduleFragment == null) {
            careScheduleFragment = new CareScheduleFragment();
        }
        return careScheduleFragment;
    }
    
    private PlantGuideFragment getPlantGuideFragment() {
        if (plantGuideFragment == null) {
            plantGuideFragment = new PlantGuideFragment();
        }
        return plantGuideFragment;
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        
        if (itemId == R.id.action_add_plant) {
            startActivity(new Intent(this, AddPlantActivity.class));
            return true;
        } else if (itemId == R.id.action_settings) {
            // TODO: Implement settings
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
}