package com.example.plantcare;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileOutputStream;

public class AddCustomPlantDialogFragment extends DialogFragment {

    private TextInputEditText editName, editLighting, editSoil, editWatering, editFertilizing, editNote;
    private ImageView imagePreview;
    private String imagePath; // محلي داخل التطبيق

    private final ActivityResultLauncher<String> requestPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    toast("Camera permission denied");
                }
            });

    private final ActivityResultLauncher<Void> takePicturePreview =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
                if (bitmap != null) {
                    persistAndPreview(bitmap);
                }
            });

    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    try {
                        Bitmap bmp = decodeDownsampled(uri);
                        if (bmp == null) {
                            toast("Failed to load image");
                            return;
                        }
                        persistAndPreview(bmp);
                    } catch (OutOfMemoryError oom) {
                        // Modern gallery images can be 8000×6000 — full decode
                        // blows the heap. Reported to Crashlytics so we can
                        // size the downsampler if it still bites.
                        com.example.plantcare.CrashReporter.INSTANCE.log(oom);
                        toast("Image too large");
                    } catch (Exception e) {
                        toast("Failed to load image: " + e.getMessage());
                    }
                }
            });

    /**
     * Bounded-memory image decoder: reads the gallery URI twice — once for
     * dimensions only (`inJustDecodeBounds`), once with `inSampleSize` set so
     * the resulting bitmap fits within ~1280px on the long edge. Pre-fix the
     * pickImage callback used `MediaStore.Images.Media.getBitmap()`, which
     * decodes at full resolution and OOMs on stock camera photos (~25 MB
     * decoded for a 12 MP shot on a heap of ~96 MB).
     */
    @Nullable
    private Bitmap decodeDownsampled(@NonNull Uri uri) throws java.io.IOException {
        final int maxEdge = 1280;
        android.content.ContentResolver cr = requireContext().getContentResolver();
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (java.io.InputStream in = cr.openInputStream(uri)) {
            if (in == null) return null;
            android.graphics.BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        while (longest / sample > maxEdge) sample *= 2;

        android.graphics.BitmapFactory.Options decode = new android.graphics.BitmapFactory.Options();
        decode.inSampleSize = sample;
        try (java.io.InputStream in = cr.openInputStream(uri)) {
            if (in == null) return null;
            return android.graphics.BitmapFactory.decodeStream(in, null, decode);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = requireActivity().getLayoutInflater().inflate(R.layout.dialog_add_custom_plant, null);

        editName = v.findViewById(R.id.editName);
        editLighting = v.findViewById(R.id.editLighting);
        editSoil = v.findViewById(R.id.editSoil);
        editWatering = v.findViewById(R.id.editWatering);
        editFertilizing = v.findViewById(R.id.editFertilizing);
        editNote = v.findViewById(R.id.editNote);
        imagePreview = v.findViewById(R.id.imagePreview);

        MaterialButton btnPhoto = v.findViewById(R.id.buttonPhoto);
        MaterialButton btnGallery = v.findViewById(R.id.buttonGallery);
        MaterialButton btnAdd = v.findViewById(R.id.buttonAdd);

        // 2026-05-20 click-guard sweep: منع double-tap من فتح camera/permission dialog مرّتين
        btnPhoto.setOnClickListener(com.example.plantcare.ui.util.ClickGuard.wrap(view -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermission.launch(Manifest.permission.CAMERA);
            } else {
                takePicturePreview.launch(null);
            }
        }));

        // 2026-05-20 click-guard sweep: منع double-tap من فتح gallery picker مرّتين
        btnGallery.setOnClickListener(com.example.plantcare.ui.util.ClickGuard.wrap(view -> pickImage.launch("image/*")));

        // 2026-05-20 click-guard sweep: منع double-tap من إرسال fragment-result مرّتين
        btnAdd.setOnClickListener(com.example.plantcare.ui.util.ClickGuard.wrap(view -> {
            String name = textOf(editName);
            if (name.isEmpty()) {
                toast(getString(R.string.settings_enter_name));
                return;
            }
            Bundle b = new Bundle();
            b.putString("name", name);
            b.putString("lighting", textOf(editLighting));
            b.putString("soil", textOf(editSoil));
            b.putString("watering", textOf(editWatering));
            b.putString("fertilizing", textOf(editFertilizing));
            b.putString("note", textOf(editNote));
            b.putString("imagePath", imagePath);
            getParentFragmentManager().setFragmentResult("custom_plant_created", b);
            dismiss();
        }));

        Dialog d = new Dialog(requireContext());
        d.setContentView(v);
        d.setCancelable(true);
        return d;
    }

    @Override
    public void onStart() {
        super.onStart();
        // تثبيت عرض الحوار إلى ~92% لمنع الانكماش عند ظهور الصورة
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            DisplayMetrics dm = new DisplayMetrics();
            dialog.getWindow().getWindowManager().getDefaultDisplay().getMetrics(dm);
            int targetWidth = (int) (dm.widthPixels * 0.92f);
            dialog.getWindow().setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private String textOf(TextInputEditText e) {
        return e.getText() != null ? e.getText().toString().trim() : "";
    }

    private void showPreview(Bitmap bmp) {
        imagePreview.setVisibility(View.VISIBLE);
        applyStableImagePreviewSizing();
        imagePreview.setImageBitmap(bmp);
    }

    private void applyStableImagePreviewSizing() {
        ViewGroup.LayoutParams lp = imagePreview.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        int heightPx = (int) (220 * requireContext().getResources().getDisplayMetrics().density);
        lp.height = heightPx;
        imagePreview.setLayoutParams(lp);
        imagePreview.setAdjustViewBounds(false);
        imagePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
    }

    /**
     * DEF-21 (2026-05-18): persist the camera/gallery bitmap to disk AND
     * swap the ImageView to the downscaled copy, so the original 48 MB
     * frame (4000×3000 ARGB_8888 from modern phone cameras) is released.
     * Previously the original lingered in {@code imagePreview} until the
     * dialog closed — real RAM pressure on low-end devices.
     */
    private void persistAndPreview(Bitmap original) {
        Bitmap scaled = null;
        try {
            File dir = new File(requireContext().getFilesDir(), "plant_photos");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "custom_" + System.currentTimeMillis() + ".jpg");
            // Modern phone cameras emit 4000×3000 frames — at JPEG 90 that's
            // ~12 MB per plant. Downscale the long edge to 1280 px and recompress
            // at 80 so we stay under ~400 KB and a stuffed catalog doesn't blow
            // out the user's app storage / Firestore quota.
            scaled = downscaleToMaxEdge(original, 1280);
            try (FileOutputStream os = new FileOutputStream(f)) {
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, os);
                os.flush();
            }
            imagePath = f.getAbsolutePath();
            // Hand the ImageView the downscaled copy so the heavy original
            // can be GC'd. setImageBitmap dereferences the previous bitmap;
            // recycling the original guarantees its 48 MB pixel buffer is
            // freed even if Java still holds an unrelated reference.
            showPreview(scaled);
            if (scaled != original) {
                try { original.recycle(); } catch (Throwable t) { /* expected */ }
            }
        } catch (Exception e) {
            toast("Failed to save photo");
            // Persistence failed — best effort cleanup of whichever bitmap
            // we created. The ImageView never received it, so recycling is
            // safe.
            if (scaled != null && scaled != original) {
                try { scaled.recycle(); } catch (Throwable t) { /* expected */ }
            }
        }
    }

    private static Bitmap downscaleToMaxEdge(Bitmap src, int maxEdge) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longest = Math.max(w, h);
        if (longest <= maxEdge) return src;
        float ratio = maxEdge / (float) longest;
        int newW = Math.round(w * ratio);
        int newH = Math.round(h * ratio);
        return Bitmap.createScaledBitmap(src, newW, newH, true);
    }

    private void toast(String s) {
        Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show();
    }
}