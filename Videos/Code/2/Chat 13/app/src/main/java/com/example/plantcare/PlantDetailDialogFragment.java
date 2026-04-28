package com.example.plantcare;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;
import com.example.plantcare.feature.memoir.GrowthMemoirBuilder;
import com.example.plantcare.feature.share.FamilyShareManager;
import com.example.plantcare.media.PhotoStorage;
import com.example.plantcare.media.CoverCloudSync;
import com.example.plantcare.ui.util.FragmentBg;
import com.example.plantcare.ui.util.QuickAddHelper;
import com.example.plantcare.weekbar.ArchiveStore;
import com.example.plantcare.WikiImageHelper;

import java.io.File;

import kotlin.Unit;

public class PlantDetailDialogFragment extends DialogFragment {

    private static final String TAG = "PlantCover";

    private Plant plant;
    private boolean isUserPlant;
    private Runnable dismissListener;
    private Runnable onPlantAdded;
    private boolean readOnlyMode = false;

    private ImageView imageView;

    private Uri pendingCoverUri;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;

    private final Runnable dataChangeListener = new Runnable() {
        @Override
        public void run() {
            if (getContext() == null || plant == null) return;
            FragmentBg.runIO(PlantDetailDialogFragment.this, () -> {
                try {
                    AppDatabase db = AppDatabase.getInstance(requireContext().getApplicationContext());
                    Plant updated = db.plantDao().findById((int) plant.id);
                    if (updated != null) {
                        plant = updated;
                    }
                } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
            }, () -> refreshCoverImage());
        }
    };

    public static PlantDetailDialogFragment newInstance(Plant plant, boolean isUserPlant) {
        PlantDetailDialogFragment fragment = new PlantDetailDialogFragment();
        fragment.plant = plant;
        fragment.isUserPlant = isUserPlant;
        return fragment;
    }

    public void setOnDismissListener(Runnable listener) { this.dismissListener = listener; }
    public void setOnPlantAdded(Runnable listener) { this.onPlantAdded = listener; }
    public void setReadOnlyMode(boolean mode) { this.readOnlyMode = mode; }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    Log.d(TAG, "TakePicture result: " + success + " uri=" + pendingCoverUri);
                    if (plant == null || getContext() == null) {
                        pendingCoverUri = null;
                        return;
                    }

                    final Uri capturedUri = pendingCoverUri;
                    pendingCoverUri = null;

                    File f = PhotoStorage.coverFile(requireContext(), plant.id);
                    Log.d(TAG, "Cover file exists=" + f.exists() + " size=" + f.length() + " lastModified=" + f.lastModified());

                    if (success && capturedUri != null) {
                        plant.imageUri = capturedUri.toString();
                        showImmediateCover(capturedUri);

                        FragmentBg.runIO(this, () -> {
                            try {
                                AppDatabase db = AppDatabase.getInstance(requireContext().getApplicationContext());
                                db.plantDao().updateProfileImage((int) plant.id, capturedUri.toString());
                                Log.d(TAG, "DB updated imageUri for plant " + plant.id + " (local content uri)");
                            } catch (Throwable t) {
                                Log.e(TAG, "Failed to update DB imageUri", t);
                            }
                        }, () -> refreshCoverImage());

                        // Save Archive cover immediately
                        try {
                            String email = getCurrentUserEmailSafe();
                            if (email != null) {
                                ArchiveStore.INSTANCE.setCover(requireContext().getApplicationContext(), email, (long) plant.id, capturedUri);
                            }
                        } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

                        CoverCloudSync.uploadCover(
                                requireContext(),
                                plant.id,
                                url -> {
                                    Log.d(TAG, "Cloud cover uploaded. url=" + url);
                                    plant.imageUri = url;
                                    FragmentBg.runIO(this, () -> {
                                        try {
                                            AppDatabase db = AppDatabase.getInstance(requireContext().getApplicationContext());
                                            db.plantDao().updateProfileImage((int) plant.id, url);
                                            Log.d(TAG, "DB updated imageUri for plant " + plant.id + " (cloud url)");
                                        } catch (Throwable t) {
                                            Log.e(TAG, "Failed to persist cloud URL", t);
                                        }
                                    }, () -> refreshCoverImage());
                                    return Unit.INSTANCE;
                                },
                                err -> {
                                    Log.e(TAG, "Cloud upload failed", err);
                                    if (isAdded()) {
                                        requireActivity().runOnUiThread(() ->
                                                Toast.makeText(requireContext(), R.string.error_upload_failed, Toast.LENGTH_SHORT).show()
                                        );
                                    }
                                    return Unit.INSTANCE;
                                }
                        );
                    } else {
                        Toast.makeText(requireContext(), R.string.msg_camera_required, Toast.LENGTH_SHORT).show();
                    }
                }
        );

        requestCameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    Log.d(TAG, "CAMERA permission result: " + granted);
                    if (granted) {
                        launchCameraInternal();
                    } else {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), R.string.permission_camera_denied, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        int layoutId = isUserPlant ? R.layout.dialog_plant_detail_user : R.layout.dialog_plant_detail;
        View view = LayoutInflater.from(getActivity()).inflate(layoutId, null);

        TextView name         = findText(view, R.id.textPlantName, R.id.textDate);
        TextView lighting     = findText(view, R.id.textLighting);
        TextView soil         = findText(view, R.id.textSoil, R.id.textSoilType);
        TextView fertilizing  = findText(view, R.id.textFertilizing);
        TextView watering     = findText(view, R.id.textWatering);
        TextView personalNote = findText(view, R.id.textPersonalNote);
        // Label-IDs unterscheiden sich zwischen den beiden Layouts
        // (dialog_plant_detail: labelPersonalNote | dialog_plant_detail_user: textPersonalNoteLabel).
        TextView personalNoteLabel = findText(view, R.id.labelPersonalNote, R.id.textPersonalNoteLabel);
        imageView             = findImage(view, R.id.plantImageView, R.id.plantDetailImageView, R.id.imagePlant);

        if (plant != null) {
            setTextSafe(name, nonEmpty(plant.name));
            setTextSafe(lighting, nonEmpty(plant.lighting));
            setTextSafe(soil, nonEmpty(plant.soil));
            setTextSafe(fertilizing, nonEmpty(plant.fertilizing));
            setTextSafe(watering, nonEmpty(plant.watering));

            // Info‑Block: Label + Text gehören zusammen sichtbar/unsichtbar.
            // Vorher wurde das Label nie eingeblendet, weshalb der Text darunter ohne
            // Überschrift erschien (siehe Bug‑Screenshot).
            boolean hasNote = plant.personalNote != null && !plant.personalNote.trim().isEmpty();
            if (personalNote != null) {
                if (hasNote) {
                    personalNote.setVisibility(View.VISIBLE);
                    personalNote.setText(plant.personalNote.trim());
                } else {
                    personalNote.setVisibility(View.GONE);
                }
            }
            if (personalNoteLabel != null) {
                personalNoteLabel.setVisibility(hasNote ? View.VISIBLE : View.GONE);
            }
        }

        Button buttonAdd        = findButton(view, R.id.buttonAddToMyPlants);
        Button buttonQuickAdd   = findButton(view, R.id.buttonQuickAdd);
        Button buttonEdit       = findButton(view, R.id.buttonEdit);
        Button buttonClose      = findButton(view, R.id.buttonClose);
        Button buttonDelete     = findButton(view, R.id.buttonDeletePlant);
        Button buttonCamera     = findButton(view, R.id.buttonCamera);
        Button buttonViewPhotos = findButton(view, R.id.buttonViewPhotos);
        Button buttonMoveRoom   = findButton(view, R.id.buttonMoveToRoom);
        Button buttonMemoir     = findButton(view, R.id.buttonGrowthMemoir);
        Button buttonShare      = findButton(view, R.id.buttonFamilyShare);

        if (readOnlyMode) {
            hide(buttonAdd, buttonQuickAdd, buttonEdit, buttonDelete, buttonCamera, buttonViewPhotos, buttonMoveRoom, buttonMemoir, buttonShare);
            show(buttonClose);
        }

        if (!isUserPlant && buttonAdd != null && !readOnlyMode) {
            buttonAdd.setOnClickListener(v -> {
                AddToMyPlantsDialogFragment dialog = AddToMyPlantsDialogFragment.newInstance(plant);
                dialog.setOnPlantAdded(() -> {
                    if (onPlantAdded != null) onPlantAdded.run();
                    dismiss();
                });
                dialog.show(getParentFragmentManager(), "add_to_my_plants");
            });
        }

        // Quick-Add: nur sichtbar im Katalogmodus. Ein Tap fügt die Pflanze sofort
        // mit dem zuletzt benutzten Raum und heutigem Datum hinzu (Snackbar-Undo).
        if (buttonQuickAdd != null) {
            if (!isUserPlant && !readOnlyMode) {
                buttonQuickAdd.setVisibility(View.VISIBLE);
                buttonQuickAdd.setOnClickListener(v -> {
                    if (plant == null || getActivity() == null) return;
                    View anchor = getActivity().getWindow().getDecorView();
                    QuickAddHelper.quickAdd(PlantDetailDialogFragment.this, plant, anchor, () -> {
                        if (onPlantAdded != null) onPlantAdded.run();
                        dismissAllowingStateLoss();
                    });
                });
            } else {
                buttonQuickAdd.setVisibility(View.GONE);
            }
        }

        if (buttonEdit != null && !readOnlyMode) {
            buttonEdit.setOnClickListener(v -> {
                EditPlantDialogFragment editDialog = EditPlantDialogFragment.newInstance(plant, isUserPlant);
                editDialog.setOnPlantEdited(() -> {
                    if (onPlantAdded != null) onPlantAdded.run();
                    dismiss();
                });
                editDialog.show(getParentFragmentManager(), "edit_plant");
            });
        }

        if (buttonDelete != null && !readOnlyMode) {
            buttonDelete.setOnClickListener(v -> {
                if (getParentFragment() instanceof PlantActionHandler) {
                    ((PlantActionHandler) getParentFragment()).onRequestDeletePlant(plant);
                } else if (getActivity() instanceof PlantActionHandler) {
                    ((PlantActionHandler) getActivity()).onRequestDeletePlant(plant);
                }
                dismiss();
            });
        }

        // Only allow taking a cover photo for user plants
        if (buttonCamera != null) {
            if (!readOnlyMode && isUserPlant) {
                buttonCamera.setVisibility(View.VISIBLE);
                buttonCamera.setOnClickListener(v -> requestCameraAndLaunch());
            } else {
                buttonCamera.setVisibility(View.GONE);
            }
        }

        if (buttonViewPhotos != null && !readOnlyMode) {
            buttonViewPhotos.setOnClickListener(v -> {
                if (getActivity() instanceof PlantMediaHandler) {
                    ((PlantMediaHandler) getActivity()).onRequestViewPlantPhotos(plant);
                }
            });
        }

        if (buttonMoveRoom != null && !readOnlyMode) {
            buttonMoveRoom.setOnClickListener(v -> {
                if (getActivity() instanceof PlantRoomHandler) {
                    ((PlantRoomHandler) getActivity()).onRequestMovePlantToRoom(plant);
                }
            });
        }

        if (buttonMemoir != null && !readOnlyMode) {
            buttonMemoir.setOnClickListener(v -> generateAndShareGrowthMemoir());
        }

        if (buttonShare != null && !readOnlyMode) {
            buttonShare.setOnClickListener(v -> showFamilyShareDialog());
        }

        if (buttonClose != null) buttonClose.setOnClickListener(v -> dismiss());

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setView(view)
                .create();

        dialog.setOnDismissListener(d -> {
            try { DataChangeNotifier.removeListener(dataChangeListener); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
            if (dismissListener != null) dismissListener.run();
        });

        return dialog;
    }

    /**
     * Lokale Familien-Freigabe: zeigt die aktuelle Liste eingeladener E-Mails
     * und ein Eingabefeld zum Hinzufügen/Entfernen.  Keine Server-Einladung,
     * nur sichtbare Dokumentation, wer mit-giessen darf.
     *
     * Das ist absichtlich ein simpler AlertDialog ohne eigenes Fragment –
     * der Zustand lebt komplett in Plant.sharedWith und wir müssen nichts
     * über Rotationen hinweg re-assemblieren.
     */
    private void showFamilyShareDialog() {
        if (plant == null || getContext() == null || !isAdded()) return;
        final android.content.Context ctx = requireContext();
        final android.widget.LinearLayout container = new android.widget.LinearLayout(ctx);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * ctx.getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);

        final TextView listView = new TextView(ctx);
        listView.setTextSize(14f);
        container.addView(listView);

        final android.widget.EditText emailInput = new android.widget.EditText(ctx);
        emailInput.setHint(R.string.share_add_email_hint);
        emailInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        container.addView(emailInput);

        // Listen-Renderer als Runnable, damit Add/Remove es aufrufen kann.
        Runnable renderList = () -> {
            java.util.List<String> emails = FamilyShareManager.getSharedEmails(plant);
            if (emails.isEmpty()) {
                listView.setText(R.string.share_nobody_label);
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < emails.size(); i++) {
                    sb.append("\u2022 ").append(emails.get(i));
                    if (i < emails.size() - 1) sb.append('\n');
                }
                listView.setText(sb.toString());
            }
        };
        renderList.run();

        final AlertDialog dlg = new AlertDialog.Builder(ctx)
                .setTitle(R.string.share_dialog_title)
                .setView(container)
                .setPositiveButton("OK", null) // Überschrieben nach show()
                .setNegativeButton(R.string.share_remove, null)
                .setNeutralButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .create();
        dlg.show();

        // Positive = Hinzufügen. Negative = Entfernen des eingegebenen Eintrags.
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String input = emailInput.getText() != null ? emailInput.getText().toString() : "";
            if (!FamilyShareManager.isValidEmail(input.trim())) {
                Toast.makeText(ctx, R.string.share_invalid_email, Toast.LENGTH_SHORT).show();
                return;
            }
            final String email = input.trim().toLowerCase();
            com.example.plantcare.util.BgExecutor.io(() -> {
                boolean ok = FamilyShareManager.addEmail(ctx, plant, email);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (ok) {
                        Toast.makeText(ctx,
                                getString(R.string.share_added_toast, email),
                                Toast.LENGTH_SHORT).show();
                        emailInput.setText("");
                        renderList.run();
                    } else {
                        Toast.makeText(ctx, R.string.share_invalid_email, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });
        dlg.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
            String input = emailInput.getText() != null ? emailInput.getText().toString() : "";
            final String email = input.trim().toLowerCase();
            if (email.isEmpty()) {
                Toast.makeText(ctx, R.string.share_invalid_email, Toast.LENGTH_SHORT).show();
                return;
            }
            com.example.plantcare.util.BgExecutor.io(() -> {
                boolean ok = FamilyShareManager.removeEmail(ctx, plant, email);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (ok) {
                        Toast.makeText(ctx,
                                getString(R.string.share_removed_toast, email),
                                Toast.LENGTH_SHORT).show();
                        emailInput.setText("");
                        renderList.run();
                    } else {
                        Toast.makeText(ctx, R.string.share_invalid_email, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });
    }

    /**
     * Baut aus den Archivfotos dieser Pflanze eine PNG-Collage und öffnet
     * einen System-Share-Dialog. Läuft auf einem Hintergrund-Thread, weil
     * sowohl das Dekodieren (bis zu 12 Bilder) als auch das Zeichnen teuer
     * sind.  UI-Feedback per Toast: "wird erstellt…", dann Share / Fehler.
     */
    private void generateAndShareGrowthMemoir() {
        if (plant == null || getContext() == null || !isAdded()) return;
        final android.content.Context ctx = requireContext().getApplicationContext();
        final long plantId = plant.id;
        final String plantName = plant.getNickname() != null && !plant.getNickname().isEmpty()
                ? plant.getNickname() : plant.name;
        final String email = EmailContext.current(ctx) != null ? EmailContext.current(ctx) : "local";

        Toast.makeText(ctx, R.string.memoir_generating, Toast.LENGTH_SHORT).show();

        com.example.plantcare.util.BgExecutor.io(() -> {
            GrowthMemoirBuilder.Result result = null;
            Throwable err = null;
            try {
                result = GrowthMemoirBuilder.build(ctx, email, plantId, plantName);
            } catch (Throwable t) {
                err = t;
                Log.e(TAG, "GrowthMemoir build failed", t);
            }
            final GrowthMemoirBuilder.Result finalResult = result;
            final Throwable finalErr = err;
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                if (finalErr != null) {
                    Toast.makeText(ctx, R.string.memoir_build_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (finalResult == null) {
                    Toast.makeText(ctx, R.string.memoir_no_photos, Toast.LENGTH_LONG).show();
                    return;
                }
                Intent share = new Intent(Intent.ACTION_SEND)
                        .setType("image/png")
                        .putExtra(Intent.EXTRA_STREAM, finalResult.getUri())
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(Intent.createChooser(share,
                            getString(R.string.memoir_share_chooser)));
                } catch (Throwable t) {
                    Toast.makeText(ctx, R.string.memoir_build_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void requestCameraAndLaunch() {
        if (getContext() == null) return;
        int state = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA);
        Log.d(TAG, "requestCameraAndLaunch() - permission state=" + state);
        if (state == PackageManager.PERMISSION_GRANTED) {
            launchCameraInternal();
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCameraInternal() {
        if (plant == null || getContext() == null) return;
        try {
            pendingCoverUri = PhotoStorage.coverUri(requireContext(), plant.id);
            Log.d(TAG, "Launching camera with Uri=" + pendingCoverUri);
            takePictureLauncher.launch(pendingCoverUri);
        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException launching camera", se);
            Toast.makeText(requireContext(), R.string.permission_camera_denied, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Log.e(TAG, "Error launching camera", t);
            Toast.makeText(requireContext(), R.string.msg_camera_required, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        try { DataChangeNotifier.addListener(dataChangeListener); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

        Dialog d = getDialog();
        if (d != null) {
            Window w = d.getWindow();
            if (w != null) {
                w.setBackgroundDrawableResource(android.R.color.transparent);
                DisplayMetrics dm = new DisplayMetrics();
                requireActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
                int width = (int) (dm.widthPixels * 0.90f);
                int maxHeight = (int) (dm.heightPixels * 0.82f);
                w.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);

                View scroll = d.findViewById(isUserPlant ? R.id.contentScrollUser : R.id.contentScroll);
                if (scroll != null) {
                    scroll.post(() -> {
                        int totalHeight = scroll.getHeight() + getButtonsHeight(d) + dp(40);
                        if (totalHeight > maxHeight) {
                            int buttonsH = getButtonsHeight(d);
                            int allowed = maxHeight - buttonsH - dp(40);
                            if (allowed < dp(160)) allowed = dp(160);
                            scroll.getLayoutParams().height = allowed;
                            scroll.requestLayout();
                        }
                    });
                }
            }
        }
        refreshCoverImage();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshCoverImage();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try { DataChangeNotifier.removeListener(dataChangeListener); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
    }

    private void refreshCoverImage() {
        if (imageView == null || getContext() == null || plant == null) return;

        // Priority 0) ArchiveStore cover (fast and consistent with list UI)
        try {
            String email = getCurrentUserEmailSafe();
            if (email != null) {
                Uri archived = ArchiveStore.INSTANCE.getCoverUri(requireContext().getApplicationContext(), email, (long) plant.id);
                if (archived != null) {
                    Log.d(TAG, "refreshCoverImage() using ArchiveStore uri=" + archived);
                    loadCoverIntoImageView(archived);
                    return;
                }
            }
        } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

        // Priority 1) In-memory plant.imageUri
        Uri cover = null;
        try {
            if (plant.imageUri != null && !plant.imageUri.trim().isEmpty()) cover = Uri.parse(plant.imageUri);
        } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

        if (cover != null) {
            Log.d(TAG, "refreshCoverImage() using in-memory uri=" + cover);
            loadCoverIntoImageView(cover);
            return;
        }

        FragmentBg.runIO(this, () -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext().getApplicationContext());
                Plant p = db.plantDao().findById((int) plant.id);
                Uri dbUri = (p != null && p.imageUri != null && !p.imageUri.trim().isEmpty()) ? Uri.parse(p.imageUri) : null;
                Log.d(TAG, "refreshCoverImage() DB uri=" + dbUri);

                if (dbUri != null) {
                    plant.imageUri = dbUri.toString();
                    if (isAdded()) requireActivity().runOnUiThread(() -> loadCoverIntoImageView(dbUri));
                    return;
                }

                // On-demand remote fetch by localId for user plants
                if (isUserPlant) {
                    if (isAdded()) requireActivity().runOnUiThread(() -> imageView.setImageResource(R.drawable.ic_default_plant));
                    CoverCloudSync.fetchCoverForPlant(
                            requireContext(),
                            plant.id,
                            url -> {
                                Log.d(TAG, "fetchCoverForPlant -> " + url);
                                try {
                                    plant.imageUri = url;
                                    Uri u = Uri.parse(url);
                                    if (isAdded()) requireActivity().runOnUiThread(() -> loadCoverIntoImageView(u));
                                } catch (Throwable t) {
                                    Log.e(TAG, "Failed to parse cover url", t);
                                }
                                return Unit.INSTANCE;
                            },
                            err -> {
                                Log.d(TAG, "No remote cover for plant " + plant.id, err);
                                // Fallback: try drawable resource by plant name
                                int resId = getDrawableResourceByPlantName();
                                if (resId != 0 && isAdded()) {
                                    requireActivity().runOnUiThread(() -> loadDrawableIntoImageView(resId));
                                }
                                return Unit.INSTANCE;
                            }
                    );
                    return;
                }

                File f = PhotoStorage.coverFile(requireContext(), plant.id);
                if (f.exists() && f.length() > 0) {
                    Uri fileProviderUri = PhotoStorage.coverUri(requireContext(), plant.id);
                    Log.d(TAG, "refreshCoverImage() fallback to fileProviderUri=" + fileProviderUri);
                    if (isAdded()) requireActivity().runOnUiThread(() -> loadCoverIntoImageView(fileProviderUri));
                } else {
                    // Fallback: try drawable resource by plant name (e.g. "einblatt" -> einblatt.jpg)
                    int resId = getDrawableResourceByPlantName();
                    if (resId != 0) {
                        Log.d(TAG, "refreshCoverImage() fallback to drawable resource for " + plant.name);
                        if (isAdded()) requireActivity().runOnUiThread(() -> loadDrawableIntoImageView(resId));
                    } else if (!plant.isUserPlant) {
                        // On-demand Wikipedia image fetch for catalog plants
                        Log.d(TAG, "refreshCoverImage() trying on-demand Wikipedia fetch for " + plant.name);
                        try {
                            String imageUrl = WikiImageHelper.fetchImageUrl(plant.name);
                            if (imageUrl != null && !imageUrl.isEmpty()) {
                                // Store in DB for future use
                                AppDatabase db2 = AppDatabase.getInstance(requireContext().getApplicationContext());
                                db2.plantDao().updateProfileImage(plant.id, imageUrl);
                                plant.imageUri = imageUrl;
                                Uri wikiUri = Uri.parse(imageUrl);
                                Log.d(TAG, "refreshCoverImage() Wikipedia image found: " + imageUrl);
                                if (isAdded()) requireActivity().runOnUiThread(() -> loadCoverIntoImageView(wikiUri));
                            } else {
                                if (isAdded()) requireActivity().runOnUiThread(() -> imageView.setImageResource(R.drawable.ic_default_plant));
                            }
                        } catch (Throwable wikiErr) {
                            Log.d(TAG, "refreshCoverImage() Wikipedia fetch failed for " + plant.name, wikiErr);
                            if (isAdded()) requireActivity().runOnUiThread(() -> imageView.setImageResource(R.drawable.ic_default_plant));
                        }
                    } else {
                        if (isAdded()) requireActivity().runOnUiThread(() -> imageView.setImageResource(R.drawable.ic_default_plant));
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "refreshCoverImage() DB read failed", t);
                if (isAdded()) requireActivity().runOnUiThread(() -> imageView.setImageResource(R.drawable.ic_default_plant));
            }
        });
    }

    private void showImmediateCover(@NonNull Uri uri) {
        Log.d(TAG, "showImmediateCover() uri=" + uri);
        loadCoverIntoImageView(uri);
    }

    private void loadCoverIntoImageView(@Nullable Uri uri) {
        if (getContext() == null) return;

        if (uri == null) {
            Log.d(TAG, "loadCoverIntoImageView() uri is null, showing placeholder");
            imageView.setImageResource(R.drawable.ic_default_plant);
            return;
        }

        long last = PhotoStorage.lastModified(requireContext(), plant.id);
        File f = PhotoStorage.coverFile(requireContext(), plant.id);
        Log.d(TAG, "loadCoverIntoImageView() uri=" + uri + " lastModified=" + last + " fileExists=" + f.exists() + " size=" + f.length());

        boolean isHttpUri = uri.getScheme() != null && uri.getScheme().toLowerCase().startsWith("http");
        if (isHttpUri) {
            // HTTP URLs (e.g. Wikipedia images): load as String for more reliable Glide HTTP loading
            Glide.with(requireContext())
                .load(uri.toString())
                .placeholder(R.drawable.ic_default_plant)
                .error(R.drawable.ic_default_plant)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .circleCrop()
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        Log.e(TAG, "Glide onLoadFailed (HTTP) model=" + model, e);
                        return false;
                    }
                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                        return false;
                    }
                })
                .into(imageView);
            return;
        }

        ObjectKey sig = new ObjectKey(String.valueOf(uri) + "#" + last);
        Glide.with(requireContext())
                .load(uri)
                .placeholder(R.drawable.ic_default_plant)
                .error(R.drawable.ic_default_plant)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .signature(sig)
                .circleCrop()
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        Log.e(TAG, "Glide onLoadFailed model=" + model, e);
                        return false;
                    }
                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                        Log.d(TAG, "Glide onResourceReady model=" + model + " dataSource=" + dataSource + " first=" + isFirstResource);
                        return false;
                    }
                })
                .into(imageView);
    }

    /**
     * Resolves a drawable resource ID from the plant name (e.g. "Einblatt" -> R.drawable.einblatt).
     * Returns 0 if no matching drawable exists.
     */
    private int getDrawableResourceByPlantName() {
        if (plant == null || getContext() == null) return 0;
        String name = plant.name;
        if (name == null || name.trim().isEmpty()) return 0;
        String key = name.trim().toLowerCase();
        key = key.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        key = key.replaceAll("[^a-z0-9]", "_").replaceAll("_+", "_");
        if (key.startsWith("_")) key = key.substring(1);
        if (key.endsWith("_")) key = key.substring(0, key.length() - 1);
        return requireContext().getResources().getIdentifier(key, "drawable", requireContext().getPackageName());
    }

    private void loadDrawableIntoImageView(int resId) {
        if (getContext() == null || imageView == null) return;
        Glide.with(requireContext())
                .load(resId)
                .placeholder(R.drawable.ic_default_plant)
                .error(R.drawable.ic_default_plant)
                .circleCrop()
                .into(imageView);
    }

    private String getCurrentUserEmailSafe() {
        try {
            if (plant != null && plant.userEmail != null && !plant.userEmail.trim().isEmpty()) return plant.userEmail;
        } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
        try {
            String e = EmailContext.current(requireContext());
            if (e != null && !e.trim().isEmpty()) return e;
        } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
        try {
            com.google.firebase.auth.FirebaseUser u = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (u != null && u.getEmail() != null && !u.getEmail().trim().isEmpty()) return u.getEmail();
        } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
        return null;
    }

    private int getButtonsHeight(Dialog d) {
        View bar = d.findViewById(isUserPlant ? R.id.buttonBarUser : R.id.buttonBar);
        if (bar == null) return 0;
        return bar.getHeight() == 0 ? dp(220) : bar.getHeight();
    }

    private int dp(int v) { float density = getResources().getDisplayMetrics().density; return (int) (v * density); }

    private TextView findText(View root, int... ids) {
        for (int id : ids) {
            View v = root.findViewById(id);
            if (v instanceof TextView) return (TextView) v;
        }
        return null;
    }
    private ImageView findImage(View root, int... ids) {
        for (int id : ids) {
            View v = root.findViewById(id);
            if (v instanceof ImageView) return (ImageView) v;
        }
        return null;
    }
    private Button findButton(View root, int id) {
        View v = root.findViewById(id);
        if (v instanceof Button) return (Button) v;
        return null;
    }

    private void setTextSafe(TextView tv, String value) { if (tv != null) tv.setText(value == null ? "" : value); }
    private void hide(View... views) { if (views != null) for (View v : views) if (v != null) v.setVisibility(View.GONE); }
    private void show(View v) { if (v != null) v.setVisibility(View.VISIBLE); }
    private String nonEmpty(String s) { return (s == null || s.trim().isEmpty()) ? "-" : s; }

    public interface PlantActionHandler { void onRequestDeletePlant(Plant plant); }
    public interface PlantMediaHandler {
        void onRequestCapturePlantPhoto(Plant plant);
        void onRequestViewPlantPhotos(Plant plant);
    }
    public interface PlantRoomHandler { void onRequestMovePlantToRoom(Plant plant); }
}