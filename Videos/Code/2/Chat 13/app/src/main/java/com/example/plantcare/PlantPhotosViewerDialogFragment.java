package com.example.plantcare;

import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.plantcare.ui.util.FragmentBg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows archived photos for a plant and supports delete/date change.
 * Uses FirebaseSyncManager.get().deletePhotoSmart(...) so deletion works
 * even while upload is pending.
 */
public class PlantPhotosViewerDialogFragment extends DialogFragment {

    private Plant plant;

    public static PlantPhotosViewerDialogFragment newInstance(Plant plant) {
        PlantPhotosViewerDialogFragment fragment = new PlantPhotosViewerDialogFragment();
        fragment.plant = plant;
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_plant_photos_viewer, null);

        TextView textPlantName = view.findViewById(R.id.textPlantName);
        TextView emptyMessage = view.findViewById(R.id.emptyMessage);
        RecyclerView recyclerView = view.findViewById(R.id.photosRecyclerView);

        if (plant != null && plant.name != null) {
            textPlantName.setText(plant.name);
        } else {
            textPlantName.setText("");
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        PlantPhotoAdapter adapter = new PlantPhotoAdapter();
        recyclerView.setAdapter(adapter);

        // Load photos on background thread
        FragmentBg.<List<PlantPhoto>>runWithResult(this,
                () -> {
                    AppDatabase db = AppDatabase.getInstance(requireContext());
                    List<PlantPhoto> list = db.plantPhotoDao().getPhotosForPlant(plant.id);
                    return list != null ? list : new ArrayList<>();
                },
                photos -> {
                    if (photos == null || photos.isEmpty()) {
                        emptyMessage.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    } else {
                        adapter.setPhotos(photos);
                        emptyMessage.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                    }
                });

        return new AlertDialog.Builder(requireActivity())
                .setTitle(getString(R.string.archive_photos_title_for, plant != null ? plant.name : ""))
                .setView(view)
                .setPositiveButton(R.string.action_close, (dialog, which) -> dismiss())
                .create();
    }

    private class PlantPhotoAdapter extends RecyclerView.Adapter<PlantPhotoViewHolder> {
        private List<PlantPhoto> photos;

        public void setPhotos(List<PlantPhoto> photos) {
            this.photos = photos;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PlantPhotoViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_plant_photo, parent, false);
            return new PlantPhotoViewHolder(itemView, this::refreshList);
        }

        @Override
        public void onBindViewHolder(@NonNull PlantPhotoViewHolder holder, int position) {
            PlantPhoto photo = photos.get(position);
            holder.bind(photo);
        }

        @Override
        public int getItemCount() {
            return photos != null ? photos.size() : 0;
        }

        private void refreshList() {
            FragmentBg.<List<PlantPhoto>>runWithResult(PlantPhotosViewerDialogFragment.this,
                    () -> {
                        AppDatabase db = AppDatabase.getInstance(requireContext());
                        List<PlantPhoto> list = db.plantPhotoDao().getPhotosForPlant(plant.id);
                        return list != null ? list : new ArrayList<>();
                    },
                    this::setPhotos);
        }
    }

    private class PlantPhotoViewHolder extends RecyclerView.ViewHolder {
        private final android.widget.ImageView imageView;
        private final android.widget.TextView dateView;
        private final Runnable onChanged;

        public PlantPhotoViewHolder(@NonNull View itemView, Runnable onChanged) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imagePlantPhoto);
            dateView = itemView.findViewById(R.id.textPhotoDate);
            this.onChanged = onChanged;
        }

        public void bind(PlantPhoto photo) {
            // Thumbnail rendering
            if (photo.imagePath != null) {
                String path = photo.imagePath;
                if (path.startsWith("http")) {
                    Glide.with(itemView.getContext())
                            .load(path)
                            .placeholder(android.R.drawable.ic_menu_report_image)
                            .error(R.drawable.ic_broken_image)
                            .into(imageView);
                } else if (path.startsWith("content://")) {
                    Glide.with(itemView.getContext())
                            .load(Uri.parse(path))
                            .placeholder(android.R.drawable.ic_menu_report_image)
                            .error(R.drawable.ic_broken_image)
                            .into(imageView);
                } else if (path.startsWith("file://")) {
                    Glide.with(itemView.getContext())
                            .load(Uri.parse(path))
                            .placeholder(android.R.drawable.ic_menu_report_image)
                            .error(R.drawable.ic_broken_image)
                            .into(imageView);
                } else if (path.startsWith("PENDING_DOC:")) {
                    // Show local placeholder (upload in progress)
                    imageView.setImageResource(android.R.drawable.ic_menu_report_image);
                } else {
                    // Assume absolute file path
                    File file = new File(path);
                    Glide.with(itemView.getContext())
                            .load(file)
                            .placeholder(android.R.drawable.ic_menu_report_image)
                            .error(R.drawable.ic_broken_image)
                            .into(imageView);
                }
            } else {
                imageView.setImageResource(android.R.drawable.ic_menu_report_image);
            }

            // Date label
            if (dateView != null) {
                if (photo.dateTaken != null && !photo.dateTaken.isEmpty()) {
                    dateView.setText(itemView.getContext().getString(R.string.calendar_date_prefix, photo.dateTaken));
                    dateView.setVisibility(View.VISIBLE);
                } else {
                    dateView.setVisibility(View.GONE);
                }
            }

            // Fullscreen open
            imageView.setOnClickListener(v -> {
                String path = photo.imagePath;
                if (path == null || path.startsWith("PENDING_DOC:")) {
                    Toast.makeText(v.getContext(), R.string.photo_still_uploading, Toast.LENGTH_SHORT).show();
                    return;
                }
                // Always open our FullScreenImageDialogFragment; it will handle http/content/file/absolute paths.
                FullScreenImageDialogFragment dialog = FullScreenImageDialogFragment.newInstance(path);
                androidx.fragment.app.FragmentActivity activity = (androidx.fragment.app.FragmentActivity) v.getContext();
                dialog.show(activity.getSupportFragmentManager(), "image_fullscreen");
            });

            // Long-press options (delete / change date)
            imageView.setOnLongClickListener(v -> {
                showPhotoOptionsDialogWithDelete(itemView, photo, onChanged);
                return true;
            });
        }
    }

    void showPhotoOptionsDialogWithDelete(View contextView, PlantPhoto photo, Runnable onChanged) {
        String[] options = {
                contextView.getContext().getString(R.string.action_delete),
                contextView.getContext().getString(R.string.action_change_date)
        };
        new AlertDialog.Builder(contextView.getContext())
                .setTitle(R.string.photo_options_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        new AlertDialog.Builder(contextView.getContext())
                                .setMessage(R.string.confirm_delete_photo_message)
                                .setPositiveButton(R.string.action_yes, (d, w) -> {
                                    FragmentBg.runIO(PlantPhotosViewerDialogFragment.this,
                                            () -> FirebaseSyncManager.get().deletePhotoSmart(photo, contextView.getContext()),
                                            () -> {
                                                Toast.makeText(contextView.getContext(), R.string.msg_photo_deleted, Toast.LENGTH_SHORT).show();
                                                if (onChanged != null) onChanged.run();
                                            });
                                })
                                .setNegativeButton(R.string.action_no, null)
                                .show();
                    } else if (which == 1) {
                        java.util.Calendar cal = java.util.Calendar.getInstance();
                        try {
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                            java.util.Date d = sdf.parse(photo.dateTaken);
                            if (d != null) cal.setTime(d);
                        } catch (Exception __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
                        android.app.DatePickerDialog dateDialog = new android.app.DatePickerDialog(contextView.getContext(), (v, y, m, d) -> {
                            String newDate = String.format(java.util.Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d);
                            FragmentBg.runIO(PlantPhotosViewerDialogFragment.this,
                                    () -> {
                                        AppDatabase db = AppDatabase.getInstance(contextView.getContext());
                                        photo.dateTaken = newDate;
                                        db.plantPhotoDao().update(photo);
                                    },
                                    () -> {
                                        Toast.makeText(contextView.getContext(), R.string.msg_date_changed, Toast.LENGTH_SHORT).show();
                                        if (onChanged != null) onChanged.run();
                                    });
                        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH));
                        dateDialog.show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}