package com.plantcare;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class DailyWateringAdapter extends RecyclerView.Adapter<DailyWateringAdapter.ViewHolder> {
    
    private List<WateringTask> wateringTasks;
    
    public DailyWateringAdapter(List<WateringTask> wateringTasks) {
        this.wateringTasks = wateringTasks;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_daily_watering, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WateringTask task = wateringTasks.get(position);
        holder.plantName.setText(task.getPlantName());
        holder.wateringTime.setText(task.getWateringTime());
        holder.typeIcon.setImageResource(R.drawable.ic_water);
    }
    
    @Override
    public int getItemCount() {
        return wateringTasks.size();
    }
    
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView plantName;
        public TextView wateringTime;
        public ImageView typeIcon;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            plantName = itemView.findViewById(R.id.plant_name);
            wateringTime = itemView.findViewById(R.id.watering_time);
            typeIcon = itemView.findViewById(R.id.type_icon);
        }
    }
}