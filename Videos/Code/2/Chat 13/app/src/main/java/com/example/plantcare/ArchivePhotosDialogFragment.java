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
                        loadPhotoInto(iv, p);
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
                .setTitle(getString(R.string.archive_title_format,
                        titleName != null ? titleName : ""))
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
                                        photo.dateTaken = newDate;
                                        com.example.plantcare.data.repository.PlantPhotoRepository
                                                .getInstance(requireContext())
                                                .updateBlocking(photo);
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
                            loadPhotoInto(iv, p);
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

    /**
     * Path-aware photo loader. Mirrors the Calendar grid loader (F1) so PENDING_DOC,
     * http(s), content:// (own FileProvider, with File fallback), file://, and raw
     * file paths each take the most reliable Glide model.
     * <p>
     * Functional Report §1.2: passing a stale FileProvider URI to Glide.load(Uri)
     * silently fails to a broken-image placeholder. Resolving content:// back to the
     * underlying File under getExternalFilesDir(null) restores reliable loading.
     */
    private void loadPhotoInto(ImageView iv, PlantPhoto p) {
        if (TextUtils.isEmpty(p.imagePath)) {
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
            File f = resolveOwnFileProviderFile(requireContext(), path);
            model = (f != null) ? f : Uri.parse(path);
        } else if (path.startsWith("file://")) {
            String p2 = Uri.parse(path).getPath();
            File f = new File(p2 != null ? p2 : "");
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

        Glide.with(requireContext())
                .load(model)
                .placeholder(android.R.drawable.ic_menu_report_image)
                .error(R.drawable.ic_broken_image)
                .centerCrop()
                .into(iv);
    }

    /**
     * content:// from this app's own FileProvider mapped back to the underlying File.
     * Both provider_paths.xml ("my_images") and file_paths.xml ("all_external_files")
     * point external-files-path to ".", so the path under that tag is relative to
     * getExternalFilesDir(null).
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

    private List<PlantPhoto> loadPhotosForPlant(int plantId) {
        // Sprint-3 cleanup: replaced 50+ lines of reflection scaffolding
        // (3 method-name candidates × 2 param types + 4 listAll fallbacks)
        // with a typed PlantPhotoRepository call. The Repository contract
        // makes the reflection guard obsolete.
        try {
            return com.example.plantcare.data.repository.PlantPhotoRepository
                    .getInstance(requireContext().getApplicationContext())
                    .getPhotosForPlantBlocking(plantId);
        } catch (Throwable __ce) {
            com.example.plantcare.CrashReporter.INSTANCE.log(__ce);
            return new ArrayList<>();
        }
    }
}