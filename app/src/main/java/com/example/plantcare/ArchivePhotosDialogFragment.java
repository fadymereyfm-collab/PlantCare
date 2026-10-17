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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.datepicker.MaterialDatePicker;
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
                    // 2026-05-20 click-guard sweep: منع double-tap من فتح FullScreenImageDialog مرّتين
                    iv.setOnClickListener(com.example.plantcare.ui.util.ClickGuard.wrap(v -> {
                        String effective = resolveLocalImagePath(p.imagePath);
                        if (effective != null) {
                            FullScreenImageDialogFragment dialog =
                                    FullScreenImageDialogFragment.newInstance(effective);
                            dialog.show(getParentFragmentManager(), "image_fullscreen");
                        }
                    }));

                    // Long-press → options (delete / change date)
                    iv.setOnLongClickListener(v -> {
                        showPhotoOptions(v, p, grid, plantId);
                        return true;
                    });

                    grid.addView(cell);
                }
            });

        // 2026-05-19 fix #6: استخدام Dialog مع layout مخصّص بدلاً من
        // AlertDialog افتراضي. الـ layout الجديد يحوي title + close X +
        // scroll + filled close button بنفس هوية باقي التطبيق.
        TextView titleView = view.findViewById(R.id.archiveTitle);
        if (titleView != null) {
            titleView.setText(getString(R.string.archive_title_format,
                    titleName != null ? titleName : ""));
        }
        android.widget.ImageButton closeIcon = view.findViewById(R.id.archiveClose);
        if (closeIcon != null) closeIcon.setOnClickListener(v -> dismiss());
        com.google.android.material.button.MaterialButton bottomClose =
                view.findViewById(R.id.buttonArchiveClose);
        if (bottomClose != null) bottomClose.setOnClickListener(v -> dismiss());

        android.app.Dialog dialog = new android.app.Dialog(requireContext());
        dialog.setContentView(view);
        dialog.setCancelable(true);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        android.app.Dialog dlg = getDialog();
        if (dlg == null || dlg.getWindow() == null) return;
        android.view.Window window = dlg.getWindow();
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        window.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int target = (int) (dm.widthPixels * 0.92f);
        window.setLayout(target, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setBackgroundDrawableResource(android.R.color.transparent);

        // Bottom inset padding ديناميكي ليبقى زرّ "Schliessen" بعيداً عن nav bar.
        android.view.View root = dlg.findViewById(R.id.rootArchivePhotos);
        if (root != null) {
            final int basePadPx = (int) (24 * getResources().getDisplayMetrics().density);
            final int extraPadPx = (int) (16 * getResources().getDisplayMetrics().density);
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                int bottomInset = insets.getInsets(
                        androidx.core.view.WindowInsetsCompat.Type.systemBars()
                                | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
                ).bottom;
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                        basePadPx + bottomInset + extraPadPx);
                return insets;
            });
            androidx.core.view.ViewCompat.requestApplyInsets(root);
        }
    }

    private void showPhotoOptions(View contextView, PlantPhoto photo, GridLayout grid, int plantId) {
        java.util.List<com.example.plantcare.ui.util.ActionListDialogFragment.Item> items =
                new java.util.ArrayList<>();
        items.add(new com.example.plantcare.ui.util.ActionListDialogFragment.Item(
                getString(R.string.action_delete),
                true,
                () -> {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setMessage(R.string.confirm_delete_photo_message)
                            .setPositiveButton(R.string.action_yes, (d, w) -> {
                                FragmentBg.runIO(this,
                                        () -> FirebaseSyncManager.get().deletePhotoSmart(photo, requireContext()),
                                        () -> reloadGrid(grid, plantId));
                            })
                            .setNegativeButton(R.string.action_no, null)
                            .show();
                    return kotlin.Unit.INSTANCE;
                }));
        items.add(new com.example.plantcare.ui.util.ActionListDialogFragment.Item(
                getString(R.string.action_change_date),
                false,
                () -> {
                    long initialSelection = java.util.Calendar.getInstance().getTimeInMillis();
                    try {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
                        java.util.Date d = sdf.parse(photo.dateTaken);
                        if (d != null) initialSelection = d.getTime();
                    } catch (Exception __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

                    MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                            .setTitleText(R.string.action_change_date)
                            .setSelection(initialSelection)
                            .build();

                    datePicker.addOnPositiveButtonClickListener(selection -> {
                        java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
                        cal.setTimeInMillis(selection);
                        int y = cal.get(java.util.Calendar.YEAR);
                        int m = cal.get(java.util.Calendar.MONTH);
                        int d = cal.get(java.util.Calendar.DAY_OF_MONTH);

                        String newDate = String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                        FragmentBg.runIO(this,
                                () -> {
                                    photo.dateTaken = newDate;
                                    com.example.plantcare.data.repository.PlantPhotoRepository
                                            .getInstance(requireContext())
                                            .updateBlocking(photo);
                                },
                                () -> reloadGrid(grid, plantId));
                    });
                    datePicker.show(getParentFragmentManager(), "date_picker");
                    return kotlin.Unit.INSTANCE;
                }));
        new com.example.plantcare.ui.util.ActionListDialogFragment()
                .configure(getString(R.string.photo_options_title), items)
                .show(getParentFragmentManager(),
                        com.example.plantcare.ui.util.ActionListDialogFragment.TAG);
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

                        // 2026-05-20 click-guard sweep: منع double-tap من فتح FullScreenImageDialog مرّتين
                        iv.setOnClickListener(com.example.plantcare.ui.util.ClickGuard.wrap(v -> {
                            String effective = resolveLocalImagePath(p.imagePath);
                            if (effective != null) {
                                FullScreenImageDialogFragment dialog =
                                        FullScreenImageDialogFragment.newInstance(effective);
                                dialog.show(getParentFragmentManager(), "image_fullscreen");
                            }
                        }));

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

        // PENDING_DOC:<docId>|<localUri> — extract the local URI after the
        // separator so the upload-in-progress window doesn't blank the
        // archive thumbnails. Pre-fix this branch returned the broken-image
        // placeholder for every photo whose Firebase upload was still in
        // flight (or had failed silently).
        if (path.startsWith("PENDING_DOC:")) {
            int sep = path.indexOf('|');
            if (sep > 0 && sep + 1 < path.length()) {
                path = path.substring(sep + 1);
            } else {
                iv.setImageResource(android.R.drawable.ic_menu_report_image);
                return;
            }
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

    /**
     * Resolve a `PlantPhoto.imagePath` to a path the FullScreenImageDialog can load.
     * <p>
     * The Firebase sync writes `PENDING_DOC:&lt;docId&gt;|&lt;localUri&gt;` while the
     * upload is in flight so the local file stays loadable in the UI before the
     * Firestore doc id is known. Pre-fix this fragment treated the PENDING_DOC
     * prefix as "image not ready yet" and silently dropped the click. The calendar
     * tab's MainScreenCompose extracts the suffix and loads it, so the two flows
     * had diverged: same photo, opens in calendar, dead-clicks in Rooms.
     * <p>
     * Returns null when nothing usable is encoded (PENDING_DOC: with no `|<uri>`
     * suffix, or null/empty input).
     */
    @Nullable
    private static String resolveLocalImagePath(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) return null;
        if (!raw.startsWith("PENDING_DOC:")) return raw;
        int sep = raw.indexOf('|');
        if (sep > 0 && sep + 1 < raw.length()) {
            return raw.substring(sep + 1);
        }
        return null;
    }
}