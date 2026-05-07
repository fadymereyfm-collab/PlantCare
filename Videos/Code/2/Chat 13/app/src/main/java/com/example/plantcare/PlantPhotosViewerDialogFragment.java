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
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;
import com.example.plantcare.data.disease.DiseaseDiagnosis;
import com.example.plantcare.ui.util.FragmentBg;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shows archived photos for a plant and supports delete/date change.
 * Uses FirebaseSyncManager.get().deletePhotoSmart(...) so deletion works
 * even while upload is pending.
 */
public class PlantPhotosViewerDialogFragment extends DialogFragment {

    private static final String ARG_PLANT = "plant";

    private Plant plant;

    /**
     * Map von {@code disease_diagnosis.id} → DiseaseDiagnosis-Eintrag, befüllt
     * im Hintergrund-Lader. Foto-Items mit nicht-null {@code diagnosisId} blenden
     * darüber den Krankheits-Badge ein.
     */
    private final Map<Integer, DiseaseDiagnosis> diagnosesById = new HashMap<>();

    public static PlantPhotosViewerDialogFragment newInstance(Plant plant) {
        PlantPhotosViewerDialogFragment fragment = new PlantPhotosViewerDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_PLANT, plant);
        fragment.setArguments(args);
        // Mirror into the field so callers using the freshly constructed
        // instance still see plant before onCreateDialog runs. Restored
        // from arguments below for the rotation/process-recreation case.
        fragment.plant = plant;
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Restore from arguments after rotation / process death — without
        // this the static-field-only newInstance lost `plant` and every
        // subsequent DB access NPE'd.
        if (plant == null && getArguments() != null) {
            try {
                plant = (Plant) getArguments().getSerializable(ARG_PLANT);
            } catch (Throwable t) { CrashReporter.INSTANCE.log(t); }
        }
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

        // Load photos + linked diagnoses on background thread.
        FragmentBg.<List<PlantPhoto>>runWithResult(this,
                () -> {
                    List<PlantPhoto> list = com.example.plantcare.data.repository
                            .PlantPhotoRepository.getInstance(requireContext())
                            .getPhotosForPlantBlocking(plant.id);
                    // Linked disease diagnoses, indexed by id, so the row binding
                    // can show the disease label without a per-item DB hit.
                    diagnosesById.clear();
                    try {
                        List<DiseaseDiagnosis> diags = com.example.plantcare.data.repository
                                .DiseaseDiagnosisRepository.getInstance(requireContext())
                                .getForPlantBlocking(plant.id);
                        if (diags != null) {
                            for (DiseaseDiagnosis d : diags) {
                                diagnosesById.put((int) d.getId(), d);
                            }
                        }
                    } catch (Throwable t) {
                        CrashReporter.INSTANCE.log(t);
                    }
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
                        List<PlantPhoto> list = com.example.plantcare.data.repository
                                .PlantPhotoRepository.getInstance(requireContext())
                                .getPhotosForPlantBlocking(plant.id);
                        diagnosesById.clear();
                        try {
                            List<DiseaseDiagnosis> diags = com.example.plantcare.data.repository
                                    .DiseaseDiagnosisRepository.getInstance(requireContext())
                                    .getForPlantBlocking(plant.id);
                            if (diags != null) {
                                for (DiseaseDiagnosis d : diags) {
                                    diagnosesById.put((int) d.getId(), d);
                                }
                            }
                        } catch (Throwable t) {
                            CrashReporter.INSTANCE.log(t);
                        }
                        return list != null ? list : new ArrayList<>();
                    },
                    this::setPhotos);
        }
    }

    private class PlantPhotoViewHolder extends RecyclerView.ViewHolder {
        private final android.widget.ImageView imageView;
        private final android.widget.TextView dateView;
        private final android.widget.TextView inspectionLabelView;
        private final android.widget.TextView inspectionNoteView;
        private final Runnable onChanged;

        public PlantPhotoViewHolder(@NonNull View itemView, Runnable onChanged) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imagePlantPhoto);
            dateView = itemView.findViewById(R.id.textPhotoDate);
            inspectionLabelView = itemView.findViewById(R.id.textPhotoInspectionLabel);
            inspectionNoteView = itemView.findViewById(R.id.textPhotoInspectionNote);
            this.onChanged = onChanged;
        }

        public void bind(PlantPhoto photo) {
            loadPhotoInto(imageView, photo);

            // Date label
            if (dateView != null) {
                if (photo.dateTaken != null && !photo.dateTaken.isEmpty()) {
                    dateView.setText(itemView.getContext().getString(R.string.calendar_date_prefix, photo.dateTaken));
                    dateView.setVisibility(View.VISIBLE);
                } else {
                    dateView.setVisibility(View.GONE);
                }
            }

            // Inspection / disease badge: only when this photo was archived from
            // a disease diagnosis (photoType="inspection" + diagnosisId set).
            DiseaseDiagnosis diag = (photo.diagnosisId != null)
                    ? diagnosesById.get(photo.diagnosisId)
                    : null;
            if (diag != null) {
                inspectionLabelView.setText(itemView.getContext().getString(
                        R.string.disease_archive_inspection_label, diag.getDisplayName()));
                inspectionLabelView.setVisibility(View.VISIBLE);
                String note = diag.getNote();
                if (note != null && !note.isEmpty()) {
                    inspectionNoteView.setText(note);
                    inspectionNoteView.setVisibility(View.VISIBLE);
                } else {
                    inspectionNoteView.setVisibility(View.GONE);
                }
            } else {
                inspectionLabelView.setVisibility(View.GONE);
                inspectionNoteView.setVisibility(View.GONE);
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
                                        photo.dateTaken = newDate;
                                        com.example.plantcare.data.repository.PlantPhotoRepository
                                                .getInstance(contextView.getContext())
                                                .updateBlocking(photo);
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

    /**
     * Path-aware photo loader. Mirrors the F1/F2 routing so PENDING_DOC, http(s),
     * content:// (own FileProvider, with File fallback), file://, and raw file
     * paths each take the most reliable Glide model.
     * <p>
     * Functional Report §1.2: passing a stale FileProvider URI to Glide.load(Uri)
     * silently fails to a broken-image placeholder once the capturing activity is
     * gone. Resolving content:// back to the underlying File under
     * getExternalFilesDir(null) restores reliable loading.
     */
    private static void loadPhotoInto(android.widget.ImageView iv, PlantPhoto p) {
        android.content.Context ctx = iv.getContext();
        if (p == null || p.imagePath == null || p.imagePath.isEmpty()) {
            iv.setImageResource(android.R.drawable.ic_menu_report_image);
            return;
        }
        String path = p.imagePath;

        if (path.startsWith("PENDING_DOC:")) {
            iv.setImageResource(android.R.drawable.ic_menu_report_image);
            return;
        }

        Object model;
        if (path.startsWith("http://") || path.startsWith("https://")) {
            model = path;
        } else if (path.startsWith("content://")) {
            File f = resolveOwnFileProviderFile(ctx, path);
            model = (f != null) ? f : Uri.parse(path);
        } else if (path.startsWith("file://")) {
            String inner = Uri.parse(path).getPath();
            File f = new File(inner != null ? inner : "");
            if (!f.exists() || f.length() == 0) {
                iv.setImageResource(android.R.drawable.ic_menu_report_image);
                return;
            }
            model = f;
        } else {
            File f = new File(path);
            if (!f.exists() || f.length() == 0) {
                iv.setImageResource(android.R.drawable.ic_menu_report_image);
                return;
            }
            model = f;
        }

        com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> req =
                Glide.with(ctx).load(model)
                        .placeholder(android.R.drawable.ic_menu_report_image)
                        .error(R.drawable.ic_broken_image)
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE);
        if (model instanceof File) {
            File mf = (File) model;
            req = req.signature(new ObjectKey(mf.getAbsolutePath() + "#" + mf.lastModified()));
        }
        req.into(iv);
    }

    /**
     * content:// URIs from this app's own FileProvider mapped back to the underlying
     * File. Both provider_paths.xml ("my_images") and file_paths.xml
     * ("all_external_files") point external-files-path to ".", so the path under
     * that tag is relative to getExternalFilesDir(null).
     */
    private static File resolveOwnFileProviderFile(android.content.Context ctx, String contentUriStr) {
        try {
            Uri uri = Uri.parse(contentUriStr);
            String authority = ctx.getPackageName() + ".provider";
            if (!authority.equals(uri.getAuthority())) return null;
            List<String> segs = uri.getPathSegments();
            if (segs == null || segs.size() < 2) return null;
            String tag = segs.get(0);
            File base;
            if ("my_images".equals(tag) || "all_external_files".equals(tag)) {
                base = ctx.getExternalFilesDir(null);
            } else {
                return null;
            }
            if (base == null) return null;
            StringBuilder rel = new StringBuilder();
            for (int i = 1; i < segs.size(); i++) {
                if (i > 1) rel.append('/');
                rel.append(segs.get(i));
            }
            File f = new File(base, rel.toString());
            return (f.exists() && f.length() > 0) ? f : null;
        } catch (Throwable e) {
            // expected: malformed URI / missing file → caller falls back to Uri then placeholder
            return null;
        }
    }
}