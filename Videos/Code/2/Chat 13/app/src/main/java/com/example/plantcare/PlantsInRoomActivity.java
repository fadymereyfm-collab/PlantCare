package com.example.plantcare;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.plantcare.media.CoverCloudSync;
import com.example.plantcare.media.PhotoStorage;
import com.example.plantcare.weekbar.ArchiveStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class PlantsInRoomActivity extends AppCompatActivity
        implements PlantDetailDialogFragment.PlantMediaHandler,
        PlantDetailDialogFragment.PlantActionHandler,
        PlantDetailDialogFragment.PlantRoomHandler {

    private PlantAdapter adapter;
    private int roomId;
    private String roomName;
    private String userEmail;

    /**
     * Reload the toolbar title + plant list when the room is renamed or
     * deleted from another screen (e.g. MyPlants long-press menu). Without
     * this, the title stays on the stale name until the user backs out and
     * re-enters the room.
     */
    private final Runnable dataChangeListener = this::refreshFromRoomState;

    private Uri photoURI;
    private File capturedPhotoFile; // absolute file of the captured image
    private Plant currentPlantForPhoto;

    private ActivityResultLauncher<android.content.Intent> cameraLauncher;
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;
    private ActivityResultLauncher<Void> cameraPreviewLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plants_in_room);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        roomId = getIntent().getIntExtra("room_id", getIntent().getIntExtra("roomId", -1));
        roomName = getIntent().getStringExtra("room_name");
        if (roomName == null) roomName = getIntent().getStringExtra("roomName");

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            if (roomName != null) getSupportActionBar().setTitle(roomName);
        }

        RecyclerView recyclerView = findViewById(R.id.recyclerViewPlants);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new PlantAdapter(this, plant -> {
            PlantDetailDialogFragment dialog = PlantDetailDialogFragment.newInstance(plant, true);
            dialog.show(getSupportFragmentManager(), "plant_detail");
        }, true);
        recyclerView.setAdapter(adapter);

        userEmail = EmailContext.current(this);

        setupActivityResultLaunchers();
        refreshList();
    }

    @Override
    protected void onStart() {
        super.onStart();
        DataChangeNotifier.addListener(dataChangeListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        DataChangeNotifier.removeListener(dataChangeListener);
    }

    /**
     * Re-resolve the room from the DB so a rename / delete from elsewhere
     * propagates here. If the room is gone (deleted with no plants), back
     * out — the activity has nothing meaningful to show.
     */
    private void refreshFromRoomState() {
        final int rid = roomId;
        com.example.plantcare.util.BgExecutor.io(() -> {
            RoomCategory rc = com.example.plantcare.data.repository
                    .RoomCategoryRepository.getInstance(getApplicationContext())
                    .findByIdBlocking(rid);
            runOnUiThread(() -> {
                if (rc == null) { finish(); return; }
                if (!rc.name.equals(roomName)) {
                    roomName = rc.name;
                    if (getSupportActionBar() != null) getSupportActionBar().setTitle(rc.name);
                }
                refreshList();
            });
        });
    }

    private void setupActivityResultLaunchers() {
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        handleCameraResult();
                    }
                });

        cameraPreviewLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bitmap -> {
                    if (bitmap != null) {
                        try {
                            File out = createImageFile();
                            try (FileOutputStream os = new FileOutputStream(out)) {
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, os);
                                os.flush();
                            }
                            capturedPhotoFile = out;
                            photoURI = androidx.core.content.FileProvider.getUriForFile(
                                    this,
                                    getPackageName() + ".provider",
                                    out
                            );
                            handleCameraResult();
                        } catch (Exception e) {
                            Toast.makeText(this, getString(R.string.camera_file_create_error), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        requestCameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        launchCameraIntent();
                    } else {
                        Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void launchCameraIntent() {
        android.content.Intent takePictureIntent = new android.content.Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        List<ResolveInfo> handlers = getPackageManager().queryIntentActivities(takePictureIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (handlers == null || handlers.isEmpty()) {
            Toast.makeText(this, getString(R.string.camera_app_not_found), Toast.LENGTH_SHORT).show();
            cameraPreviewLauncher.launch(null);
            return;
        }
        try {
            File photoFile = createImageFile();
            if (photoFile != null) {
                capturedPhotoFile = photoFile;
                photoURI = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".provider",
                        photoFile
                );
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                takePictureIntent.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION | android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                for (ResolveInfo info : handlers) {
                    grantUriPermission(info.activityInfo.packageName, photoURI,
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION | android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                cameraLauncher.launch(takePictureIntent);
            }
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.camera_file_create_error), Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        return ImageUtils.createImageFile(this);
    }

    private void handleCameraResult() {
        if (currentPlantForPhoto == null || (photoURI == null && capturedPhotoFile == null)) return;
        com.example.plantcare.util.BgExecutor.io(() -> {
            try {
                // Ensure the captured photo becomes the canonical cover.jpg for this plant
                Uri coverUri = null;
                if (capturedPhotoFile != null) {
                    // Copy file bytes to PhotoStorage.coverFile
                    File dest = PhotoStorage.coverFile(getApplicationContext(), (long) currentPlantForPhoto.id);
                    try (FileInputStream in = new FileInputStream(capturedPhotoFile);
                         FileOutputStream out = new FileOutputStream(dest)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        out.flush();
                    } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
                    coverUri = PhotoStorage.coverUri(this, (long) currentPlantForPhoto.id);
                    try {
                        MediaScannerConnection.scanFile(
                                getApplicationContext(),
                                new String[]{dest.getAbsolutePath()},
                                null,
                                null
                        );
                    } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
                } else if (photoURI != null) {
                    // Copy from content uri to cover file
                    try {
                        File dest = PhotoStorage.coverFile(getApplicationContext(), (long) currentPlantForPhoto.id);
                        try (java.io.InputStream in = getContentResolver().openInputStream(photoURI);
                             FileOutputStream out = new FileOutputStream(dest)) {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                            out.flush();
                        }
                        coverUri = PhotoStorage.coverUri(this, (long) currentPlantForPhoto.id);
                    } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
                }

                // 1) Update profile image in Plant row
                if (coverUri != null) {
                    currentPlantForPhoto.imageUri = coverUri.toString();
                } else if (photoURI != null) {
                    currentPlantForPhoto.imageUri = photoURI.toString();
                }
                com.example.plantcare.data.repository.PlantRepository
                        .getInstance(getApplicationContext())
                        .updateBlocking(currentPlantForPhoto);

                // 2) Set as cover in ArchiveStore so the loader picks it with highest priority
                try {
                    if (userEmail == null) {
                        userEmail = EmailContext.current(getApplicationContext());
                    }
                    Uri toArchive = (coverUri != null) ? coverUri : photoURI;
                    if (userEmail != null && toArchive != null) {
                        ArchiveStore.INSTANCE.setCover(getApplicationContext(), userEmail, currentPlantForPhoto.id, toArchive);
                    }
                } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

                // 3) Persist a PlantPhoto marked as cover and start pending-aware upload
                try {
                    com.example.plantcare.data.repository.PlantPhotoRepository photoRepo =
                            com.example.plantcare.data.repository.PlantPhotoRepository
                                    .getInstance(getApplicationContext());
                    PlantPhoto photo = new PlantPhoto();
                    photo.plantId = currentPlantForPhoto.id;
                    photo.imagePath = (coverUri != null) ? coverUri.toString() : (photoURI != null ? photoURI.toString() : null);
                    photo.dateTaken = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
                    photo.isCover = true;
                    photo.userEmail = userEmail;
                    long newId = photoRepo.insertBlocking(photo);
                    photo.id = (int) newId;

                    Uri uploadUri = (coverUri != null) ? coverUri : photoURI;
                    if (uploadUri != null) {
                        FirebaseSyncManager.get().uploadPlantPhotoWithPending(getApplicationContext(), photo, uploadUri);
                    }
                } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

                // 4) Sync plant metadata (imageUri) to Firebase (best-effort)
                try { FirebaseSyncManager.get().syncPlant(currentPlantForPhoto); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

                // 5) Upload cover to Storage and persist download URL to Firestore + Room + plants collection
                try { CoverCloudSync.uploadCover(getApplicationContext(), (long) currentPlantForPhoto.id, null, null); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.msg_saved), Toast.LENGTH_SHORT).show();
                    refreshList(); // refresh list item image
                });
                DataChangeNotifier.notifyChange(); // refresh any open detail dialog image

            } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
        });
        currentPlantForPhoto = null;
        photoURI = null;
        capturedPhotoFile = null;
    }

    private void refreshList() {
        final int finalRoomId = roomId;
        com.example.plantcare.util.BgExecutor.io(() -> {
            List<Plant> plants = com.example.plantcare.data.repository.PlantRepository
                    .getInstance(this)
                    .getAllUserPlantsInRoomBlocking(finalRoomId, userEmail);
            runOnUiThread(() -> adapter.setPlantList(plants));
        });
    }

    @Override
    public void onRequestCapturePlantPhoto(Plant plant) {
        currentPlantForPhoto = plant;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            launchCameraIntent();
        }
    }

    @Override
    public void onRequestViewPlantPhotos(Plant plant) {
        PlantPhotosViewerDialogFragment dlg = PlantPhotosViewerDialogFragment.newInstance(plant);
        dlg.show(getSupportFragmentManager(), "plant_photos_viewer");
    }

    @Override
    public void onRequestDeletePlant(Plant plant) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_plant_title)
                .setMessage(R.string.confirm_delete_plant_message)
                .setPositiveButton(R.string.action_delete, (d, w) -> startDeleteWithUndo(plant))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /**
     * Soft-confirm pattern: hide the row immediately so the list feels
     * responsive, show a Snackbar with UNDO, and only commit the actual
     * deletion after the Snackbar dismisses without action. Tapping UNDO
     * restores ONLY this plant — concurrent deletes on other plants
     * remain pending. DB and Firebase are untouched until the timeout,
     * so undo is free.
     */
    private void startDeleteWithUndo(Plant plant) {
        final int plantId = plant.id;
        adapter.hidePlantById(plantId);
        final boolean[] undone = { false };
        com.google.android.material.snackbar.Snackbar
                .make(findViewById(R.id.recyclerViewPlants),
                        getString(R.string.msg_deleted),
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction(R.string.action_undo, v -> {
                    undone[0] = true;
                    adapter.restorePendingDelete(plantId);
                })
                .addCallback(new com.google.android.material.snackbar.Snackbar.Callback() {
                    @Override
                    public void onDismissed(com.google.android.material.snackbar.Snackbar sb, int event) {
                        if (undone[0]) return;
                        commitPlantDeletion(plant);
                    }
                })
                .show();
    }

    private void commitPlantDeletion(Plant plant) {
        com.example.plantcare.util.BgExecutor.io(() -> {
            FirebaseSyncManager fsm = FirebaseSyncManager.get();
            try {
                List<PlantPhoto> photos = com.example.plantcare.data.repository
                        .PlantPhotoRepository.getInstance(getApplicationContext())
                        .getPhotosForPlantBlocking(plant.id);
                if (photos != null) {
                    for (PlantPhoto p : photos) {
                        try { fsm.deletePhotoSmart(p, getApplicationContext()); } catch (Throwable ignore) { /* best-effort */ }
                    }
                }
                com.example.plantcare.data.repository.ReminderRepository
                        .getInstance(getApplicationContext())
                        .deleteRemindersForPlantAndUserBlocking(plant.id, plant.name, plant.userEmail);
                fsm.deletePlant(plant);
                com.example.plantcare.data.repository.PlantRepository
                        .getInstance(getApplicationContext()).deleteBlocking(plant);
                runOnUiThread(() -> {
                    adapter.clearPendingDelete(plant.id);
                    refreshList();
                });
                DataChangeNotifier.notifyChange();
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    // Failure path: surface the row again so the user can retry.
                    adapter.restorePendingDelete(plant.id);
                    Toast.makeText(this, R.string.delete_failed, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    public void onRequestMovePlantToRoom(Plant plant) {
        com.example.plantcare.util.BgExecutor.io(() -> {
            List<RoomCategory> all = com.example.plantcare.data.repository
                    .RoomCategoryRepository.getInstance(getApplicationContext())
                    .getAllRoomsForUserBlocking(plant.userEmail);
            // Filter out the plant's current room — moving in place is just
            // a no-op, no point showing it.
            final List<RoomCategory> targets = new java.util.ArrayList<>();
            if (all != null) {
                for (RoomCategory r : all) if (r.id != plant.roomId) targets.add(r);
            }
            runOnUiThread(() -> {
                if (targets.isEmpty()) {
                    Toast.makeText(this, R.string.no_rooms_available, Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] names = new String[targets.size()];
                for (int i = 0; i < targets.size(); i++) names[i] = targets.get(i).name;
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.move_to_room_title)
                        .setItems(names, (d, which) -> {
                            RoomCategory target = targets.get(which);
                            com.example.plantcare.util.BgExecutor.io(() -> {
                                plant.roomId = target.id;
                                com.example.plantcare.data.repository.PlantRepository
                                        .getInstance(getApplicationContext()).updateBlocking(plant);
                                try {
                                    FirebaseSyncManager.get().syncPlant(plant);
                                } catch (Throwable t) { CrashReporter.INSTANCE.log(t); }
                                runOnUiThread(() -> {
                                    Toast.makeText(this, getString(R.string.msg_moved), Toast.LENGTH_SHORT).show();
                                    refreshList();
                                });
                                DataChangeNotifier.notifyChange();
                            });
                        })
                        .setNegativeButton(R.string.action_cancel, null)
                        .show();
            });
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}