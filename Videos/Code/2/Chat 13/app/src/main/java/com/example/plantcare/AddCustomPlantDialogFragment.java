package com.example.plantcare;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
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
                    imagePath = saveBitmap(bitmap);
                    showPreview(bitmap);
                }
            });

    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    try {
                        Bitmap bmp = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), uri);
                        imagePath = saveBitmap(bmp);
                        showPreview(bmp);
                    } catch (Exception e) {
                        toast("Failed to load image: " + e.getMessage());
                    }
                }
            });

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

        btnPhoto.setOnClickListener(view -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermission.launch(Manifest.permission.CAMERA);
            } else {
                takePicturePreview.launch(null);
            }
        });

        btnGallery.setOnClickListener(view -> pickImage.launch("image/*"));

        btnAdd.setOnClickListener(view -> {
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
        });

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

    private String saveBitmap(Bitmap bmp) {
        try {
            File dir = new File(requireContext().getFilesDir(), "plant_photos");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "custom_" + System.currentTimeMillis() + ".jpg");
            // Modern phone cameras emit 4000×3000 frames — at JPEG 90 that's
            // ~12 MB per plant. Downscale the long edge to 1280 px and recompress
            // at 80 so we stay under ~400 KB and a stuffed catalog doesn't blow
            // out the user's app storage / Firestore quota.
            Bitmap scaled = downscaleToMaxEdge(bmp, 1280);
            try (FileOutputStream os = new FileOutputStream(f)) {
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, os);
                os.flush();
            }
            if (scaled != bmp) {
                try { scaled.recycle(); } catch (Throwable ignored) { /* expected */ }
            }
            return f.getAbsolutePath();
        } catch (Exception e) {
            toast("Failed to save photo");
            return null;
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