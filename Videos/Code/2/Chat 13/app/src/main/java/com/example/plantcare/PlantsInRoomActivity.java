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
                AppDatabase db = DatabaseClient.getInstance(getApplicationContext()).getAppDatabase();
                db.plantDao().update(currentPlantForPhoto);

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
                    PlantPhotoDao photoDao = db.plantPhotoDao();
                    PlantPhoto photo = new PlantPhoto();
                    photo.plantId = currentPlantForPhoto.id;
                    photo.imagePath = (coverUri != null) ? coverUri.toString() : (photoURI != null ? photoURI.toString() : null);
                    photo.dateTaken = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
                    photo.isCover = true;
                    photo.userEmail = userEmail;
                    long newId = photoDao.insert(photo);
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
            AppDatabase db = DatabaseClient.getInstance(this).getAppDatabase();
            List<Plant> plants = db.plantDao().getAllUserPlantsInRoom(finalRoomId, userEmail);
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
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    com.example.plantcare.util.BgExecutor.io(() -> {
                        FirebaseSyncManager fsm = FirebaseSyncManager.get();
                        try {
                            AppDatabase db = DatabaseClient.getInstance(getApplicationContext()).getAppDatabase();
                            List<PlantPhoto> photos = db.plantPhotoDao().getPhotosForPlant(plant.id);
                            if (photos != null) {
                                for (PlantPhoto p : photos) {
                                    try { fsm.deletePhotoSmart(p, getApplicationContext()); } catch (Throwable ignore) {}
                                }
                            }
                            db.reminderDao().deleteRemindersForPlantAndUser(plant.id, plant.name, plant.userEmail);
                            fsm.deletePlant(plant);
                            db.plantDao().delete(plant);
                            runOnUiThread(() -> {
                                Toast.makeText(this, getString(R.string.msg_deleted), Toast.LENGTH_SHORT).show();
                                refreshList();
                            });
                            DataChangeNotifier.notifyChange();
                        } catch (Throwable t) {
                            runOnUiThread(() -> Toast.makeText(this, R.string.delete_failed, Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public void onRequestMovePlantToRoom(Plant plant) {
        com.example.plantcare.util.BgExecutor.io(() -> {
            AppDatabase db = DatabaseClient.getInstance(getApplicationContext()).getAppDatabase();
            List<RoomCategory> rooms = db.roomCategoryDao().getAllRoomsForUser(plant.userEmail);
            runOnUiThread(() -> {
                if (rooms == null || rooms.isEmpty()) {
                    Toast.makeText(this, R.string.no_rooms_available, Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] names = new String[rooms.size()];
                for (int i = 0; i < rooms.size(); i++) names[i] = rooms.get(i).name;
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("In Raum verschieben")
                        .setItems(names, (d, which) -> {
                            RoomCategory target = rooms.get(which);
                            com.example.plantcare.util.BgExecutor.io(() -> {
                                plant.roomId = target.id;
                                DatabaseClient.getInstance(getApplicationContext()).getAppDatabase().plantDao().update(plant);
                                runOnUiThread(() -> {
                                    Toast.makeText(this, getString(R.string.msg_moved), Toast.LENGTH_SHORT).show();
                                    refreshList();
                                });
                                DataChangeNotifier.notifyChange();
                            });
                        })
                        .setNegativeButton("Abbrechen", null)
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