package com.example.plantcare;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Sage Garden — Room list adapter.
 * Renders each room with a contextual icon (couch/bed/door/shower/...)
 * and a "X Pflanze(n)" pill badge showing the current plant count.
 */
public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.RoomViewHolder> {

    private List<RoomCategory> rooms;
    private final OnRoomClickListener listener;
    private static final ExecutorService COUNT_EXECUTOR = Executors.newSingleThreadExecutor();

    public interface OnRoomClickListener {
        void onRoomClick(RoomCategory room);
    }

    public interface OnRoomLongClickListener {
        void onRoomLongClick(RoomCategory room);
    }

    private OnRoomLongClickListener longClickListener;

    public void setOnRoomLongClickListener(OnRoomLongClickListener l) {
        this.longClickListener = l;
    }

    public RoomAdapter(List<RoomCategory> rooms, OnRoomClickListener listener) {
        this.rooms = rooms;
        this.listener = listener;
    }

    public void setRoomList(List<RoomCategory> newRooms) {
        this.rooms = newRooms;
        notifyDataSetChanged();
    }

    /** Reorder helper used by ItemTouchHelper drag-to-reorder. */
    public void moveItem(int from, int to) {
        if (rooms == null || from < 0 || to < 0
                || from >= rooms.size() || to >= rooms.size()) return;
        java.util.Collections.swap(rooms, from, to);
        notifyItemMoved(from, to);
    }

    /** Snapshot of the room ids in current display order — for persistence. */
    public List<Integer> currentOrderIds() {
        List<Integer> ids = new java.util.ArrayList<>();
        if (rooms != null) for (RoomCategory r : rooms) ids.add(r.id);
        return ids;
    }

    @NonNull
    @Override
    public RoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_room, parent, false);
        return new RoomViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RoomViewHolder holder, int position) {
        final RoomCategory room = rooms.get(position);
        final Context ctx = holder.itemView.getContext().getApplicationContext();

        holder.name.setText(room.name);
        holder.icon.setImageResource(iconForRoom(room.name));

        // Placeholder while we resolve the real count off the main thread.
        holder.count.setText(ctx.getString(R.string.plants_count_placeholder));

        // Cancel any in-flight count from the previous bind on this holder —
        // otherwise a slow query for the old room can land after the holder
        // was recycled to a new room and write the wrong number into the
        // badge. The Future returns false on cancel-after-finish (no harm),
        // so we don't need to track state separately.
        if (holder.pendingCountQuery != null) {
            holder.pendingCountQuery.cancel(true);
        }
        // Bind the holder to this specific room so the post() callback can
        // verify nothing has shifted under it before mutating the badge.
        holder.boundRoomId = room.id;

        final int boundRoomId = room.id;
        holder.pendingCountQuery = COUNT_EXECUTOR.submit(() -> {
            int count;
            try {
                count = com.example.plantcare.data.repository.PlantRepository
                        .getInstance(ctx)
                        .countPlantsByRoomBlocking(room.id, room.userEmail);
            } catch (Throwable t) {
                count = 0;
            }
            final int finalCount = count;
            holder.itemView.post(() -> {
                if (holder.boundRoomId != boundRoomId) return; // Recycled — skip.
                String text = finalCount == 1
                        ? ctx.getString(R.string.plants_count_one)
                        : ctx.getString(R.string.plants_count_other, finalCount);
                holder.count.setText(text);
            });
        });

        holder.itemView.setOnClickListener(v -> listener.onRoomClick(room));
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null && room.id > 0) {
                // Skip rooms with id == 0 — those are the synthetic defaults
                // shown to a brand-new user before the DB writes happen.
                longClickListener.onRoomLongClick(room);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return (rooms != null) ? rooms.size() : 0;
    }

    /**
     * Maps a German room name to one of 12 colored playful room icons.
     *
     * Order matters: {@code "toilette"} must be tested BEFORE
     * {@code "flur"} because both keywords historically used to share
     * one drawable (the door). Each icon is now a colored circle with a
     * white Material symbol — see drawable/sage_ic_room_*.xml and
     * res/values/colors.xml (room_*).
     */
    /**
     * Keyword groups checked top-to-bottom; first hit wins. Toilet runs
     * before bath so "Bathroom" doesn't get swallowed by `contains("bath")`
     * against the toilet "restroom" keyword.
     */
    private static final Object[][] ICON_RULES = {
            { R.drawable.sage_ic_room_toilet,   new String[] { "toilet", "klo", "wc", "restroom" } },
            { R.drawable.sage_ic_room_living,   new String[] { "wohn", "living", "lounge" } },
            { R.drawable.sage_ic_room_bedroom,  new String[] { "schlaf", "bedroom", "bed " } },
            { R.drawable.sage_ic_room_bathroom, new String[] { "bad", "dusch", "bath", "shower" } },
            { R.drawable.sage_ic_room_hallway,  new String[] { "flur", "eingang", "diele", "korridor",
                                                                "hall", "entry", "entrance" } },
            { R.drawable.sage_ic_room_kitchen,  new String[] { "küche", "kueche", "kitchen" } },
            { R.drawable.sage_ic_room_office,   new String[] { "büro", "buero", "arbeit", "office", "study" } },
            { R.drawable.sage_ic_room_balcony,  new String[] { "balkon", "terrasse", "loggia",
                                                                "balcony", "terrace", "patio" } },
            { R.drawable.sage_ic_room_garden,   new String[] { "garten", "hof", "garden", "yard" } },
            { R.drawable.sage_ic_room_kids,     new String[] { "kinder", "baby", "kids", "nursery", "child" } },
            { R.drawable.sage_ic_room_dining,   new String[] { "ess", "speise", "dining" } },
    };

    @DrawableRes
    private static int iconForRoom(String name) {
        if (name == null) return R.drawable.sage_ic_room_default;
        String n = name.toLowerCase(Locale.ROOT);
        for (Object[] rule : ICON_RULES) {
            String[] keywords = (String[]) rule[1];
            for (String k : keywords) if (n.contains(k)) return (Integer) rule[0];
        }
        return R.drawable.sage_ic_room_default;
    }

    static class RoomViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView count;
        // Tracks the in-flight plant-count query so we can cancel it on
        // recycle and suppress its post() callback if it lands too late.
        Future<?> pendingCountQuery;
        int boundRoomId = -1;

        RoomViewHolder(View view) {
            super(view);
            icon  = view.findViewById(R.id.roomIcon);
            name  = view.findViewById(R.id.textViewRoomName);
            count = view.findViewById(R.id.textViewPlantCount);
        }
    }
}
