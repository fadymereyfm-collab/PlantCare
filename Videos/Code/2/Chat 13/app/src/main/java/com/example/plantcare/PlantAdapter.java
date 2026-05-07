package com.example.plantcare;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.plantcare.weekbar.PlantImageLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class PlantAdapter extends RecyclerView.Adapter<PlantAdapter.PlantViewHolder> implements Filterable {

    private final List<Plant> originalList = new ArrayList<>();
    private final List<Plant> filteredList = new ArrayList<>();
    /**
     * IDs the host activity has soft-deleted via the Snackbar-undo flow but
     * not yet committed. They're hidden from filteredList AND skipped when
     * setPlantList is called with fresh DB rows that still contain them
     * (DB delete hasn't fired yet). This avoids the flicker where a plant
     * the user thinks is gone briefly reappears, and ensures multi-delete
     * undo only restores the still-pending IDs.
     */
    private final Set<Integer> pendingDeleteIds = new HashSet<>();
    private final Context context;
    private final OnPlantClickListener listener;
    private final boolean isUserList;

    public interface OnPlantClickListener {
        void onPlantClick(Plant plant);
    }
    public interface OnPlantLongClickListener {
        void onPlantLongClick(Plant plant);
    }

    private OnPlantLongClickListener longClickListener;

    public void setOnPlantLongClickListener(OnPlantLongClickListener listener) {
        this.longClickListener = listener;
    }

    public PlantAdapter(Context context, OnPlantClickListener listener, boolean isUserList) {
        this.context = context;
        this.listener = listener;
        this.isUserList = isUserList;
    }

    public void setPlantList(List<Plant> list) {
        originalList.clear();
        if (list != null) originalList.addAll(list);
        rebuildFilteredFromOriginal();
        notifyDataSetChanged();
    }

    /**
     * Optimistic-delete helper: mark the plant as pending-delete and drop it
     * from the visible list. Concurrent setPlantList calls (e.g. from
     * DataChangeNotifier broadcasts firing during the Snackbar timeout)
     * also skip this id so the row doesn't reappear before the DB delete
     * commits.
     */
    public void hidePlantById(int plantId) {
        pendingDeleteIds.add(plantId);
        for (int i = filteredList.size() - 1; i >= 0; i--) {
            if (filteredList.get(i).id == plantId) {
                filteredList.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    /**
     * Restore a single plant from pending-delete (UNDO tap). Other plants
     * still pending stay hidden — important for the case where the user
     * deletes A, then B, then taps UNDO on B's snackbar: only B comes back.
     */
    public void restorePendingDelete(int plantId) {
        if (!pendingDeleteIds.remove(plantId)) return;
        rebuildFilteredFromOriginal();
        notifyDataSetChanged();
    }

    /** Forget about a delete that has now been committed to the DB. */
    public void clearPendingDelete(int plantId) {
        pendingDeleteIds.remove(plantId);
    }

    private void rebuildFilteredFromOriginal() {
        filteredList.clear();
        for (Plant p : originalList) {
            if (!pendingDeleteIds.contains(p.id)) filteredList.add(p);
        }
        sortFilteredList();
    }

    @NonNull
    @Override
    public PlantViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_plant, parent, false);
        return new PlantViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlantViewHolder holder, int position) {
        Plant plant = filteredList.get(position);

        // الاسم (Nickname أولاً إن وُجد)
        if (plant.isUserPlant && plant.nickname != null && !plant.nickname.isEmpty()) {
            holder.name.setText(plant.nickname);
        } else {
            holder.name.setText(plant.name);
        }

        // السطر الثاني: معلومات السقاية بدل تكرار الاسم
        if (holder.type != null) {
            if (plant.watering != null && !plant.watering.trim().isEmpty()) {
                holder.type.setText(context.getString(R.string.watering_format, plant.watering.trim()));
            } else {
                holder.type.setText("");
            }
        }

        // الصورة: استخدام PlantImageLoader الموحّد (cover → archive → DB → catalog)
        if (holder.image != null) {
            String userEmail = EmailContext.current(context);
            String displayName = (plant.isUserPlant && plant.nickname != null && !plant.nickname.isEmpty())
                    ? plant.nickname : plant.name;
            PlantImageLoader.loadInto(context, holder.image, (long) plant.id, displayName, userEmail);
        }

        // Favourites: works for both catalog (Pin to top of "Alle Pflanzen")
        // and user plants (pin within their room). Previously the star was
        // hidden for user plants, so the `isFavorite` flag was effectively
        // dead for the most-used view.
        if (holder.star != null) {
            holder.star.setVisibility(View.VISIBLE);
            holder.star.setImageResource(
                    plant.isFavorite ? R.drawable.ic_star_filled : R.drawable.ic_star_outline
            );
            holder.star.setOnClickListener(v -> {
                plant.isFavorite = !plant.isFavorite;
                com.example.plantcare.util.BgExecutor.io(() -> {
                    com.example.plantcare.data.repository.PlantRepository
                            .getInstance(context).updateBlocking(plant);
                    if (plant.isUserPlant) {
                        try {
                            FirebaseSyncManager.get().syncPlant(plant);
                        } catch (Throwable t) { CrashReporter.INSTANCE.log(t); }
                    }
                    DataChangeNotifier.notifyChange();
                });
                sortFilteredList();
                notifyDataSetChanged();
            });
        }

        // الملاحظة الشخصية
        if (holder.personalNote != null) {
            if (plant.getPersonalNote() != null && !plant.getPersonalNote().trim().isEmpty()) {
                holder.personalNote.setVisibility(View.VISIBLE);
                holder.personalNote.setText(plant.getPersonalNote());
            } else {
                holder.personalNote.setVisibility(View.GONE);
            }
        }

        holder.itemView.setOnClickListener(v -> listener.onPlantClick(plant));
        holder.itemView.setOnLongClickListener(v -> {
            if (isUserList && longClickListener != null) {
                longClickListener.onPlantLongClick(plant);
                return true;
            }
            return false;
        });
    }

    /**
     * تحويل اسم/لقب النبتة إلى اسم resource صالح (أحرف فقط).
     */
    private String getDrawableResourceName(Plant plant) {
        String name;
        if (plant.isUserPlant && plant.nickname != null && !plant.nickname.isEmpty()) {
            name = plant.nickname;
        } else {
            name = plant.name;
        }
        if (name == null) return "";
        String n = name.trim().toLowerCase();
        n = n.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        n = n.replaceAll("[^a-z]", "");
        return n;
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence query) {
                List<Plant> result = new ArrayList<>();
                if (query == null || query.length() == 0) {
                    result.addAll(originalList);
                } else {
                    String filterPattern = query.toString().toLowerCase().trim();
                    for (Plant plant : originalList) {
                        boolean matchName = plant.name != null && plant.name.toLowerCase().contains(filterPattern);
                        boolean matchNick = plant.nickname != null && plant.nickname.toLowerCase().contains(filterPattern);
                        if (matchName || matchNick) result.add(plant);
                    }
                }
                FilterResults results = new FilterResults();
                results.values = result;
                return results;
            }
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                filteredList.clear();
                List<Plant> matched = (List<Plant>) results.values;
                if (matched != null) {
                    for (Plant p : matched) {
                        if (!pendingDeleteIds.contains(p.id)) filteredList.add(p);
                    }
                }
                sortFilteredList();
                notifyDataSetChanged();
            }
        };
    }

    private void sortFilteredList() {
        Collections.sort(filteredList, (p1, p2) -> Boolean.compare(!p1.isFavorite, !p2.isFavorite));
    }

    static class PlantViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView type;
        ImageView image;
        ImageView star;
        TextView personalNote;
        public PlantViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.textPlantName);
            type = itemView.findViewById(R.id.textPlantType);
            image = itemView.findViewById(R.id.imagePlant);
            star = itemView.findViewById(R.id.imageFavorite);
            personalNote = itemView.findViewById(R.id.textPersonalNote);
        }
    }
}