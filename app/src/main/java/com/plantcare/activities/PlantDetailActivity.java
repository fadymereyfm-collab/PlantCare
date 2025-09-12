package com.plantcare.activities;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import com.plantcare.R;

/**
 * Activity for displaying plant details
 * نشاط عرض تفاصيل النبتة
 */
public class PlantDetailActivity extends AppCompatActivity {
    
    public static final String EXTRA_PLANT_ID = "plant_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plant_detail);
        
        setupActionBar();
        
        // Get plant ID from intent
        long plantId = getIntent().getLongExtra(EXTRA_PLANT_ID, -1);
        if (plantId == -1) {
            finish(); // Invalid plant ID
            return;
        }
        
        // TODO: Load plant details
    }
    
    private void setupActionBar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.plant_details));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}