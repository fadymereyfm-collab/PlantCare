package com.example.plantcare;

import android.app.Dialog;
import android.os.Bundle;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.*;

import com.example.plantcare.ui.util.FragmentBg;
import com.bumptech.glide.Glide;

import java.io.File;
import java.util.*;

public class CalendarPhotosDialogFragment extends DialogFragment {

    private String selectedDate;
    private PhotoAdapter adapter;

    public static CalendarPhotosDialogFragment newInstance(String date) {
        CalendarPhotosDialogFragment fragment = new CalendarPhotosDialogFragment();
        Bundle args = new Bundle();
        args.putString("selectedDate", date);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        selectedDate = getArguments().getString("selectedDate");
        View view = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_plant_photos, null);

        TextView dateTitle = view.findViewById(R.id.textPlantName);
        dateTitle.setText(getString(R.string.photos_from_date, selectedDate));

        RecyclerView recyclerView = view.findViewById(R.id.recyclerPhotos);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));

        adapter = new PhotoAdapter();
        recyclerView.setAdapter(adapter);

        loadPhotos();

        return new AlertDialog.Builder(requireContext())
                .setView(view)
                .setPositiveButton("Schließen", null)
                .create();
    }

    private void loadPhotos() {
        FragmentBg.<List<PlantPhoto>>runWithResult(this,
                () -> AppDatabase.getInstance(requireContext())
                        .plantPhotoDao().getPhotosByDate(selectedDate),
                photos -> adapter.setPhotos(photos));
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private static class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {

        private List<PlantPhoto> photoList = new ArrayList<>();

        public void setPhotos(List<PlantPhoto> list) {
            this.photoList = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo_grid, parent, false);
            return new PhotoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
            PlantPhoto photo = photoList.get(position);
            File file = new File(photo.imagePath);
            Glide.with(holder.imageView.getContext()).load(file).into(holder.imageView);
        }

        @Override
        public int getItemCount() {
            return photoList.size();
        }

        static class PhotoViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;

            public PhotoViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.imagePhoto);
            }
        }
    }
}
