package com.example.plantcare;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.*;

public class RoomsWithPlantsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<RoomCategory> rooms = new ArrayList<>();
    private Map<Integer, List<Plant>> roomPlantsMap = new LinkedHashMap<>();

    public void setRoomsAndPlants(List<RoomCategory> rooms, Map<Integer, List<Plant>> roomPlantsMap) {
        this.rooms = rooms;
        this.roomPlantsMap = roomPlantsMap;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        int count = 0;
        for (RoomCategory room : rooms) {
            count++; // للغرفة نفسها (العنوان)
            List<Plant> plants = roomPlantsMap.get(room.id);
            if (plants != null && !plants.isEmpty()) {
                count += plants.size();
            } else {
                count++; // سطر "لا يوجد نباتات بعد" للغرف الفارغة
            }
        }
        return count;
    }

    @Override
    public int getItemViewType(int position) {
        int pos = 0;
        for (RoomCategory room : rooms) {
            if (pos == position) return 0; // غرفة
            pos++;
            List<Plant> plants = roomPlantsMap.get(room.id);
            if (plants != null && !plants.isEmpty()) {
                if (position < pos + plants.size()) return 1; // نبات
                pos += plants.size();
            } else {
                if (position == pos) return 2; // غرفة فارغة
                pos++;
            }
        }
        return 0;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 0) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_room_title, parent, false);
            return new RoomViewHolder(v);
        } else if (viewType == 1) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_plant_in_room, parent, false);
            return new PlantViewHolder(v);
        } else {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_empty_room, parent, false);
            return new EmptyRoomViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int pos = 0;
        for (RoomCategory room : rooms) {
            if (pos == position) {
                ((RoomViewHolder) holder).bind(room);
                return;
            }
            pos++;
            List<Plant> plants = roomPlantsMap.get(room.id);
            if (plants != null && !plants.isEmpty()) {
                if (position < pos + plants.size()) {
                    ((PlantViewHolder) holder).bind(plants.get(position - pos));
                    return;
                }
                pos += plants.size();
            } else {
                if (position == pos) {
                    ((EmptyRoomViewHolder) holder).bind();
                    return;
                }
                pos++;
            }
        }
    }

    static class RoomViewHolder extends RecyclerView.ViewHolder {
        TextView textRoom;
        RoomViewHolder(View v) {
            super(v);
            textRoom = v.findViewById(R.id.textRoomTitle);
        }
        void bind(RoomCategory room) {
            textRoom.setText(room.name);
        }
    }

    static class PlantViewHolder extends RecyclerView.ViewHolder {
        TextView textPlant;
        TextView personalNote;

        PlantViewHolder(View v) {
            super(v);
            textPlant = v.findViewById(R.id.textPlantInRoom);
            personalNote = v.findViewById(R.id.textPersonalNote);
            if (personalNote == null) {
                personalNote = new TextView(v.getContext());
                personalNote.setId(View.generateViewId());
                ((ViewGroup) v).addView(personalNote);
                personalNote.setVisibility(View.GONE);
            }
        }

        void bind(Plant plant) {
            textPlant.setText(plant.nickname != null && !plant.nickname.isEmpty() ? plant.nickname : plant.name);

            if (personalNote != null) {
                if (plant.getPersonalNote() != null && !plant.getPersonalNote().trim().isEmpty()) {
                    personalNote.setText(plant.getPersonalNote());
                    personalNote.setVisibility(View.VISIBLE);
                    personalNote.setTextColor(0xFF888888);
                    personalNote.setTextSize(13);
                } else {
                    personalNote.setVisibility(View.GONE);
                }
            }
        }
    }

    static class EmptyRoomViewHolder extends RecyclerView.ViewHolder {
        TextView emptyText;
        EmptyRoomViewHolder(View v) {
            super(v);
            emptyText = v.findViewById(R.id.textViewEmptyRoom);
        }
        void bind() {
            emptyText.setText(emptyText.getContext().getString(R.string.no_plants_in_room));
        }
    }
}