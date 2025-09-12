package com.plantcare.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.plantcare.R;

/**
 * Fragment displaying plant care guide and tips
 * جزء عرض دليل العناية بالنباتات والنصائح
 */
public class PlantGuideFragment extends Fragment {
    
    private RecyclerView recyclerView;
    // TODO: Add adapter for plant care guides

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_plant_guide, container, false);
        
        initializeViews(view);
        setupRecyclerView();
        
        return view;
    }
    
    private void initializeViews(View view) {
        recyclerView = view.findViewById(R.id.recycler_view_guide);
    }
    
    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        // TODO: Set adapter for plant care guide items
    }
}