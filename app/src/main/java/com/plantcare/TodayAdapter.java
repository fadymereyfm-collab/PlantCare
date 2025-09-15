package com.plantcare;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class TodayAdapter extends RecyclerView.Adapter<TodayAdapter.ViewHolder> {
    
    private List<TodayTask> todayTasks;
    
    public TodayAdapter(List<TodayTask> todayTasks) {
        this.todayTasks = todayTasks;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_today_task, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TodayTask task = todayTasks.get(position);
        holder.taskTitle.setText(task.getTitle());
        holder.taskDescription.setText(task.getDescription());
        
        if (task.getType().equals("watering")) {
            holder.typeIcon.setImageResource(R.drawable.ic_water);
        } else {
            holder.typeIcon.setImageResource(R.drawable.ic_fertilizer);
        }
    }
    
    @Override
    public int getItemCount() {
        return todayTasks.size();
    }
    
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView taskTitle;
        public TextView taskDescription;
        public ImageView typeIcon;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            taskTitle = itemView.findViewById(R.id.task_title);
            taskDescription = itemView.findViewById(R.id.task_description);
            typeIcon = itemView.findViewById(R.id.type_icon);
        }
    }
}