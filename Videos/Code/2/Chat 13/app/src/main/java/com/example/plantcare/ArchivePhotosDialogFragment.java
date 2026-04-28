package com.example.plantcare;

import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.example.plantcare.ui.util.FragmentBg;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * يعرض صور الأرشيف لنبتة معيّنة مع التاريخ أسفل كل صورة.
 * يعتمد على PlantPhotoDao إن وجد، ويحتوي على مسارات fallback بالـ reflection ثم التصفية.
 */
public class ArchivePhotosDialogFragment extends DialogFragment {

    public static ArchivePhotosDialogFragment newInstance(int plantId, String plantName) {
        Bundle args = new Bundle();
        args.putInt("plant_id", plantId);
        args.putString("plant_name", plantName);
        ArchivePhotosDialogFragment f = new ArchivePhotosDialogFragment();
        f.setArguments(args);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_archive_photos, null, false);
        GridLayout grid = view.findViewById(R.id.gridArchive);

        int plantId = getArguments() != null ? getArguments().getInt("plant_id") : 0;
        String titleName = getArguments() != null ? getArguments().getString("plant_name") : "Archiv";

        FragmentBg.<List<PlantPhoto>>runWithResult(this,
                () -> loadPhotosForPlant(plantId),
                photos -> {
                    grid.removeAllViews();
                    final int columnCount = 3;
                    grid.setColumnCount(columnCount);
                    LayoutInflater li = LayoutInflater.from(requireContext());
                    for (PlantPhoto p : photos) {
                    View cell = li.inflate(R.layout.item_archive_photo, grid, false);
                    ImageView iv = cell.findViewById(R.id.img);
                    TextView tv = cell.findViewById(R.id.date);
                    try {
                        if (!TextUtils.isEmpty(p.imagePath)) {
                            String path = p.imagePath;
                            if (path.startsWith("PENDING_DOC:")) {
                                iv.setImageResource(android.R.drawable.ic_menu_report_image);
                            } else if (path.startsWith("http")) {
                                Glide.with(requireContext())
                                        .load(path)
                                        .placeholder(android.R.drawable.ic_menu_report_image)
                                        .error(R.drawable.ic_broken_image)
                                        .centerCrop()
                                        .into(iv);
                            } else if (path.startsWith("content://") || path.startsWith("file://")) {
                                Glide.with(requireContext())
                                        .load(Uri.parse(path))
                                        .placeholder(android.R.drawable.ic_menu_report_image)
                                        .error(R.drawable.ic_broken_image)
                                        .centerCrop()
                                        .into(iv);
                            } else {
                                Glide.with(requireContext())
                                        .load(new File(path))
                                        .placeholder(android.R.drawable.ic_menu_report_image)
                                        .error(R.drawable.ic_broken_image)
                                        .centerCrop()
                                        .into(iv);
                            }
                        }
                    } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
                    tv.setText(!TextUtils.isEmpty(p.dateTaken) ? p.dateTaken : "");

                    // Click → fullscreen
                    iv.setOnClickListener(v -> {
                        String imgPath = p.imagePath;
                        if (imgPath != null && !imgPath.startsWith("PENDING_DOC:")) {
                            FullScreenImageDialogFragment dialog =
                                    FullScreenImageDialogFragment.newInstance(imgPath);
                            dialog.show(getParentFragmentManager(), "image_fullscreen");
                        }
                    });

                    // Long-press → options (delete / change date)
                    iv.setOnLongClickListener(v -> {
                        showPhotoOptions(v, p, grid, plantId);
                        return true;
                    });

                    grid.addView(cell);
                }
            });

        return new AlertDialog.Builder(requireContext())
                .setTitle(titleName + " • Archiv")
                .setView(view)
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private void showPhotoOptions(View contextView, PlantPhoto photo, GridLayout grid, int plantId) {
        String[] options = {
                getString(R.string.action_delete),
                getString(R.string.action_change_date)
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.photo_options_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        new AlertDialog.Builder(requireContext())
                                .setMessage(R.string.confirm_delete_photo_message)
                                .setPositiveButton(R.string.action_yes, (d, w) -> {
                                    FragmentBg.runIO(this,
                                            () -> FirebaseSyncManager.get().deletePhotoSmart(photo, requireContext()),
                                            () -> reloadGrid(grid, plantId));
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
                        android.app.DatePickerDialog dateDialog = new android.app.DatePickerDialog(requireContext(), (v, y, m, d) -> {
                            String newDate = String.format(java.util.Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d);
                            FragmentBg.runIO(this,
                                    () -> {
                                        AppDatabase db = AppDatabase.getInstance(requireContext());
                                        photo.dateTaken = newDate;
                                        db.plantPhotoDao().update(photo);
                                    },
                                    () -> reloadGrid(grid, plantId));
                        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH));
                        dateDialog.show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void reloadGrid(GridLayout grid, int plantId) {
        FragmentBg.<List<PlantPhoto>>runWithResult(this,
                () -> loadPhotosForPlant(plantId),
                photos -> {
                    grid.removeAllViews();
                    final int columnCount = 3;
                    grid.setColumnCount(columnCount);
                    LayoutInflater li = LayoutInflater.from(requireContext());
                    for (PlantPhoto p : photos) {
                        View cell = li.inflate(R.layout.item_archive_photo, grid, false);
                        ImageView iv = cell.findViewById(R.id.img);
                        TextView tv = cell.findViewById(R.id.date);
                        try {
                            if (!TextUtils.isEmpty(p.imagePath)) {
                                String path = p.imagePath;
                                if (path.startsWith("PENDING_DOC:")) {
                                    iv.setImageResource(android.R.drawable.ic_menu_report_image);
                                } else if (path.startsWith("http")) {
                                    Glide.with(requireContext())
                                            .load(path)
                                            .placeholder(android.R.drawable.ic_menu_report_image)
                                            .error(R.drawable.ic_broken_image)
                                            .centerCrop()
                                            .into(iv);
                                } else if (path.startsWith("content://") || path.startsWith("file://")) {
                                    Glide.with(requireContext())
                                            .load(Uri.parse(path))
                                            .placeholder(android.R.drawable.ic_menu_report_image)
                                            .error(R.drawable.ic_broken_image)
                                            .centerCrop()
                                            .into(iv);
                                } else {
                                    Glide.with(requireContext())
                                            .load(new java.io.File(path))
                                            .placeholder(android.R.drawable.ic_menu_report_image)
                                            .error(R.drawable.ic_broken_image)
                                            .centerCrop()
                                            .into(iv);
                                }
                            }
                        } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
                        tv.setText(!TextUtils.isEmpty(p.dateTaken) ? p.dateTaken : "");

                        iv.setOnClickListener(v -> {
                            String imgPath = p.imagePath;
                            if (imgPath != null && !imgPath.startsWith("PENDING_DOC:")) {
                                FullScreenImageDialogFragment dialog =
                                        FullScreenImageDialogFragment.newInstance(imgPath);
                                dialog.show(getParentFragmentManager(), "image_fullscreen");
                            }
                        });

                        iv.setOnLongClickListener(v -> {
                            showPhotoOptions(v, p, grid, plantId);
                            return true;
                        });

                        grid.addView(cell);
                    }
                });
    }

    private List<PlantPhoto> loadPhotosForPlant(int plantId) {
        try {
            AppDatabase db = DatabaseClient.getInstance(requireContext().getApplicationContext()).getAppDatabase();
            Object dao = db.plantPhotoDao();

            // 1) Try common DAO methods with reflection
            String[] methodNames = new String[] {
                    "getPhotosForPlant", "getPhotosForPlantId", "getAllForPlant"
            };
            for (String mName : methodNames) {
                try {
                    Method m = dao.getClass().getMethod(mName, int.class);
                    Object res = m.invoke(dao, plantId);
                    if (res instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<PlantPhoto> list = (List<PlantPhoto>) res;
                        return list != null ? list : new ArrayList<>();
                    }
                } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
                try {
                    Method m = dao.getClass().getMethod(mName, long.class);
                    Object res = m.invoke(dao, (long) plantId);
                    if (res instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<PlantPhoto> list = (List<PlantPhoto>) res;
                        return list != null ? list : new ArrayList<>();
                    }
                } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
            }

            // 2) Fallback: getAll / listAll then filter
            String[] listAllNames = new String[] { "getAll", "getAllPhotos", "listAll", "findAll" };
            for (String mName : listAllNames) {
                try {
                    Method m = dao.getClass().getMethod(mName);
                    Object res = m.invoke(dao);
                    if (res instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<PlantPhoto> list = (List<PlantPhoto>) res;
                        List<PlantPhoto> filtered = new ArrayList<>();
                        if (list != null) {
                            for (PlantPhoto p : list) {
                                if (p != null && p.plantId == plantId && !p.isCover) {
                                    filtered.add(p);
                                }
                            }
                        }
                        return filtered;
                    }
                } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
            }
        } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

        return new ArrayList<>();
    }
}