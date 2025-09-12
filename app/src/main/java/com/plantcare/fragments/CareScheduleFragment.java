package com.plantcare.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.plantcare.R;

/**
 * Fragment displaying the care schedule and reminders
 * جزء عرض جدول العناية والتذكيرات
 */
public class CareScheduleFragment extends Fragment {
    
    private RecyclerView recyclerView;
    // TODO: Add adapter and view model

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_care_schedule, container, false);
        
        initializeViews(view);
        setupRecyclerView();
        
        return view;
    }
    
    private void initializeViews(View view) {
        recyclerView = view.findViewById(R.id.recycler_view_tasks);
    }
    
    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        // TODO: Set adapter for care tasks
    }
}