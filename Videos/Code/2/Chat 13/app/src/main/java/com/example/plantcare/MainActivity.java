package com.example.plantcare;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.MediaStore;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.plantcare.DataChangeNotifier;
import com.example.plantcare.ads.AdManager;
import com.example.plantcare.weekbar.ArchiveStore;
import com.example.plantcare.media.CoverCloudSync; // Cloud sync for cover/profile picture
import com.example.plantcare.media.PhotoStorage;
import com.google.android.gms.ads.AdView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixes for reinstall:
 * - For cover captures, save into PhotoStorage.coverFile so CoverCloudSync can upload it.
 * - On upload, CoverCloudSync mirrors to plants collection (imageUri).
 * - After login/reinstall, run a repair pass to mirror missing imageUri in plants from users/{uid}/plants,
 *   then import, then pull covers into Room.
 */
public class MainActivity extends AppCompatActivity {

    private AdManager adManager;

    private Plant currentPlantForPhoto;
    private PlantDao dao;
    private Uri photoURI;
    private boolean isCoverPhoto = false;
    private String pendingPhotoDate = null;

    private String currentUserEmail;
    private String pendingGalleryPhotoDate = null;
    private Integer pendingGalleryPlantId = null;

    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;
    private ActivityResultLauncher<Void> cameraPreviewLauncher;
    private ActivityResultLauncher<String> requestNotificationPermissionLauncher;
    private ActivityResultLauncher<String[]> requestLocationPermissionLauncher;

    private Fragment allPlantsFragment;
    private Fragment myPlantsFragment;
    private Fragment calendarFragment;
    private Fragment todayFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        dao = DatabaseClient.getInstance(getApplicationContext()).plantDao();
        currentUserEmail = EmailContext.current(this);

        // Guest mode: allow usage without Firebase auth
        boolean isGuest = EmailContext.isGuest(this);

        if (!isGuest && com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) {
            AuthStartDialogFragment auth = new AuthStartDialogFragment();
            auth.show(getSupportFragmentManager(), "auth_start");
        } else if (isGuest && currentUserEmail == null) {
            // Set guest email if not already set
            currentUserEmail = "guest@local";
            EmailContext.setCurrent(this, "guest@local", true);
        }

        if (!isGuest && com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) {
            // Reinstall-safe sequence without Java lambdas -> Kotlin Unit mismatch
            try { CoverCloudSync.ensurePlantsCollectionHasImageUri(getApplicationContext(), null); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
            try { CoverCloudSync.pullCoversToRoom(getApplicationContext(), null, null); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
        }

        getSupportFragmentManager().setFragmentResultListener(
                "auth_result", this, (requestKey, result) -> {
                    String email = result.getString("email");
                    if (email != null) {
                        currentUserEmail = email;
                        refreshFragments();
                        // After auth on a clean install, repair then import then pull
                        try {
                            CoverCloudSync.ensurePlantsCollectionHasImageUri(
                                    getApplicationContext(),
                                    new kotlin.jvm.functions.Function0<kotlin.Unit>() {
                                        @Override
                                        public kotlin.Unit invoke() {
                                            importCloudDataForUser(email);
                                            return kotlin.Unit.INSTANCE;
                                        }
                                    }
                            );
                        } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
                    }
                }
        );

        seedDatabaseIfEmpty();
        setupActivityResultLaunchers();

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                   != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        // Request location permission for weather-based reminder adjustments.
        // If already granted, kick off a one-shot weather fetch immediately
        // so the user doesn't wait 12 hours for the periodic worker.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissionLauncher.launch(new String[] {
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            });
        } else {
            triggerWeatherFetchNow();
        }

        allPlantsFragment = new AllPlantsFragment();
        myPlantsFragment = new MyPlantsFragment();
        calendarFragment = new CalendarFragment();
        todayFragment = new TodayFragment();

        TextView tabAllPlants = findViewById(R.id.tabAllPlants);
        TextView tabMyPlants = findViewById(R.id.tabMyPlants);
        TextView tabCalendar = findViewById(R.id.tabCalendar);
        TextView tabToday = findViewById(R.id.tabToday);

        tabAllPlants.setOnClickListener(v -> selectTab(0));
        tabMyPlants.setOnClickListener(v -> selectTab(1));
        tabCalendar.setOnClickListener(v -> selectTab(2));
        tabToday.setOnClickListener(v -> selectTab(3));

        ImageButton identifyButton = findViewById(R.id.identifyButton);
        if (identifyButton != null) {
            identifyButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, com.example.plantcare.ui.identify.PlantIdentifyActivity.class);
                // Kein Raumkontext in der Hauptleiste — 0 = "nicht zugeordnet".
                // Andere Aufrufer (z. B. aus einem Raum heraus) sollen EXTRA_ROOM_ID setzen.
                intent.putExtra(com.example.plantcare.ui.identify.PlantIdentifyActivity.EXTRA_ROOM_ID, 0);
                startActivity(intent);
            });
        }

        ImageButton diseaseButton = findViewById(R.id.diseaseButton);
        if (diseaseButton != null) {
            diseaseButton.setOnClickListener(v -> {
                // TFLite-Modell (plant_disease_model.tflite) fehlt in assets/.
                // Statt eine kaputte Activity zu öffnen: Hinweis zeigen.
                // Sobald die Datei geliefert ist, diesen Block durch den Intent-Start ersetzen.
                Toast.makeText(this,
                        "Krankheitserkennung: bald verfügbar",
                        Toast.LENGTH_SHORT).show();
            });
        }

        ImageButton settingsButton = findViewById(R.id.settingsButton);
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> openSettingsDialog());
        }

        selectTab(0);

        adManager = new AdManager(this, (AdView) findViewById(R.id.adBanner));
        adManager.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adManager != null) adManager.resume();
        com.example.plantcare.billing.BillingManager
                .getInstance(this)
                .restorePurchasesAsync();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (adManager != null) adManager.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adManager != null) adManager.destroy();
    }

    private void selectTab(int tabIndex) {
        TextView[] tabs = {
                findViewById(R.id.tabAllPlants),
                findViewById(R.id.tabMyPlants),
                findViewById(R.id.tabCalendar),
                findViewById(R.id.tabToday)
        };
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setBackgroundResource(i == tabIndex ? R.drawable.tab_selected_background : R.drawable.tab_unselected_background);
            tabs[i].setTextColor(getResources().getColor(i == tabIndex ? R.color.tab_active_text : R.color.tab_inactive_text));
            tabs[i].setTypeface(null, i == tabIndex ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }

        Fragment fragmentToShow;
        switch (tabIndex) {
            case 0: fragmentToShow = allPlantsFragment; break;
            case 1: fragmentToShow = myPlantsFragment; break;
            case 2: fragmentToShow = calendarFragment; break;
            case 3: fragmentToShow = todayFragment; break;
            default: fragmentToShow = allPlantsFragment;
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragmentToShow)
                .commit();
    }

    private void setupActivityResultLaunchers() {
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> { if (result.getResultCode() == RESULT_OK) handleCameraResult(); }
        );
        cameraPreviewLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bitmap -> {
                    if (bitmap != null) {
                        Uri saved = saveBitmapToFile(bitmap);
                        if (saved != null) {
                            photoURI = saved;
                            handleCameraResult();
                        } else {
                            Toast.makeText(this, getString(R.string.camera_file_create_error), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleGalleryResult(result.getData().getData());
                    }
                });
        requestCameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) launchCameraIntent();
                    else Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show();
                });
        // Notification permission launcher (Android 13+)
        requestNotificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> { /* User chose — we respect their decision */ }
        );

        // Location permission launcher — on grant, fire a one-shot weather fetch.
        requestLocationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                grants -> {
                    Boolean coarse = grants.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                    if (Boolean.TRUE.equals(coarse)) triggerWeatherFetchNow();
                }
        );
    }

    /**
     * Enqueues a one-time WeatherAdjustmentWorker so the weather card appears
     * within seconds rather than waiting up to 12h for the periodic worker.
     * Observes the worker so the UI re-reads cached weather once it's written.
     */
    private void triggerWeatherFetchNow() {
        androidx.work.OneTimeWorkRequest req =
                new androidx.work.OneTimeWorkRequest.Builder(WeatherAdjustmentWorker.class)
                        .build();
        androidx.work.WorkManager wm = androidx.work.WorkManager.getInstance(this);
        wm.enqueueUniqueWork(
                "weather_one_shot",
                androidx.work.ExistingWorkPolicy.REPLACE,
                req
        );
        wm.getWorkInfoByIdLiveData(req.getId()).observe(this, info -> {
            if (info != null && info.getState().isFinished()) {
                DataChangeNotifier.notifyChange();
            }
        });
    }

    private void seedDatabaseIfEmpty() {
        com.example.plantcare.util.BgExecutor.io(() -> {
            if (dao.countAll() == 0) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(getAssets().open("plants.csv")))) {
                    String line;
                    boolean isFirstLine = true;
                    while ((line = reader.readLine()) != null) {
                        if (isFirstLine) { isFirstLine = false; continue; }
                        String[] parts = line.split(",");
                        if (parts.length >= 5) {
                            Plant p = new Plant();
                            p.name = parts[0].trim();
                            p.lighting = parts[1].trim();
                            p.soil = parts[2].trim();
                            p.fertilizing = parts[3].trim();
                            p.watering = parts[4].trim();
                            p.imageUri = parts.length > 5 ? parts[5].trim() : null;
                            p.isUserPlant = false;
                            p.userEmail = null;
                            // Auto-Klassifizierung beim Seed (indoor/outdoor/herbal/cacti)
                            p.category = com.example.plantcare.ui.util.PlantCategoryUtil
                                    .classify(p.name, p.lighting, p.watering);
                            dao.insert(p);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // Einmaliger Nachlauf nach MIGRATION_6_7: klassifiziere ältere
            // Katalog-Einträge, die noch keine Kategorie haben.
            try {
                com.example.plantcare.ui.util.PlantCategoryUtil
                        .classifyAllUnclassified(AppDatabase.getInstance(getApplicationContext()));
            } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

            // Catalog images are fetched on-demand per plant via
            // PlantImageLoader.resolveBestImage(...) (step 6, Wikipedia fallback).
            // Bulk startup fetch was removed to keep app launch fast and
            // to avoid unnecessary network traffic for plants never viewed.
        });
    }

    private void openSettingsDialog() {
        SettingsDialogFragment dialog = new SettingsDialogFragment();
        dialog.show(getSupportFragmentManager(), "settings_dialog");
    }

    public void takePhotoForPlant(Plant plant, boolean isCover, String dateOverride) {
        currentPlantForPhoto = plant;
        isCoverPhoto = isCover;
        pendingPhotoDate = dateOverride;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }
        launchCameraIntent();
    }

    public void takePhotoForCalendar(java.time.LocalDate date) {
        pendingGalleryPlantId = 0;
        pendingPhotoDate = date.toString();
        currentPlantForPhoto = null;
        isCoverPhoto = false;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }
        launchCameraIntent();
    }

    public void pickImageFromGalleryForCalendar(java.time.LocalDate date) {
        pendingGalleryPhotoDate = date.toString();
        pendingGalleryPlantId = 0;
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private void launchCameraIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        List<ResolveInfo> handlers = getPackageManager().queryIntentActivities(takePictureIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (handlers == null || handlers.isEmpty()) {
            Toast.makeText(this, getString(R.string.camera_app_not_found), Toast.LENGTH_SHORT).show();
            cameraPreviewLauncher.launch(null);
            return;
        }
        File photoFile;
        try {
            photoFile = createImageFile();
            if (photoFile != null) {
                photoURI = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".provider",
                        photoFile
                );
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                PackageManager pm = getPackageManager();
                List<ResolveInfo> resInfoList = pm.queryIntentActivities(takePictureIntent, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo resolveInfo : resInfoList) {
                    grantUriPermission(resolveInfo.activityInfo.packageName, photoURI,
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                cameraLauncher.launch(takePictureIntent);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            Toast.makeText(this, getString(R.string.camera_file_create_error), Toast.LENGTH_SHORT).show();
        }
    }

    private String getCurrentUserEmail() {
        if (currentUserEmail != null) return currentUserEmail;
        return EmailContext.current(this);
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(null);
        return File.createTempFile(fileName, ".jpg", storageDir);
    }

    private Uri saveBitmapToFile(Bitmap bitmap) {
        try {
            File out = createImageFile();
            try (FileOutputStream os = new FileOutputStream(out)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, os);
                os.flush();
            }
            return androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    out
            );
        } catch (Exception e) {
            return null;
        }
    }

    private void handleCameraResult() {
        String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String dateToUse = (pendingPhotoDate != null && !pendingPhotoDate.isEmpty()) ? pendingPhotoDate : todayStr;

        com.example.plantcare.util.BgExecutor.io(() -> {
            if (currentPlantForPhoto != null) {
                if (isCoverPhoto) {
                    Uri coverUri = copyToCoverAndGetUri(currentPlantForPhoto.id, photoURI);

                    if (coverUri != null) {
                        currentPlantForPhoto.imageUri = coverUri.toString();
                    } else if (photoURI != null) {
                        currentPlantForPhoto.imageUri = photoURI.toString();
                    }
                    dao.update(currentPlantForPhoto);
                    FirebaseSyncManager.get().syncPlant(currentPlantForPhoto);

                    try {
                        String email = getCurrentUserEmail();
                        if (email != null) {
                            Uri toStore = (coverUri != null) ? coverUri : photoURI;
                            if (toStore != null) {
                                ArchiveStore.INSTANCE.setCover(getApplicationContext(), email, (long) currentPlantForPhoto.id, toStore);
                            }
                        }
                    } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

                    Uri photoForUpload = (coverUri != null) ? coverUri : photoURI;
                    insertAndUploadPhoto(currentPlantForPhoto.id, dateToUse, photoForUpload, true);

                    try {
                        CoverCloudSync.uploadCover(getApplicationContext(), (long) currentPlantForPhoto.id, null, null);
                    } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
                } else {
                    insertAndUploadPhoto(currentPlantForPhoto.id, dateToUse, photoURI, false);
                }
            } else {
                insertAndUploadPhoto((pendingGalleryPlantId != null) ? pendingGalleryPlantId : 0, dateToUse, photoURI, false);
            }
            runOnUiThread(this::refreshFragments);
            com.example.plantcare.DataChangeNotifier.notifyChange();
        });

        pendingPhotoDate = null;
        pendingGalleryPlantId = null;
    }

    private Uri copyToCoverAndGetUri(int plantId, Uri source) {
        if (source == null) return null;
        try {
            File dest = PhotoStorage.coverFile(getApplicationContext(), (long) plantId);
            try (InputStream in = getContentResolver().openInputStream(source);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.flush();
            }
            return PhotoStorage.coverUri(this, (long) plantId);
        } catch (Throwable t) {
            return null;
        }
    }

    private void handleGalleryResult(Uri imageUri) {
        String dateToUse = (pendingGalleryPhotoDate != null && !pendingGalleryPhotoDate.isEmpty())
                ? pendingGalleryPhotoDate
                : new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        int plantId = (pendingGalleryPlantId != null) ? pendingGalleryPlantId : 0;

        com.example.plantcare.util.BgExecutor.io(() -> {
            insertAndUploadPhoto(plantId, dateToUse, imageUri, false);
            runOnUiThread(this::refreshFragments);
            com.example.plantcare.DataChangeNotifier.notifyChange();
        });

        pendingGalleryPhotoDate = null;
        pendingGalleryPlantId = null;
    }

    private void insertAndUploadPhoto(int plantId, String dateStr, Uri uri, boolean isCover) {
        try {
            PlantPhotoDao photoDao = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .plantPhotoDao();
            PlantPhoto photo = new PlantPhoto();
            photo.plantId = plantId;
            photo.imagePath = (uri != null) ? uri.toString() : null;
            photo.dateTaken = dateStr;
            photo.isCover = isCover;
            photo.userEmail = getCurrentUserEmail();
            long newId = photoDao.insert(photo);
            photo.id = (int) newId;

            if (uri != null) {
                FirebaseSyncManager.get().uploadPlantPhotoWithPending(getApplicationContext(), photo, uri);
            }
        } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
    }

    public void refreshFragments() {
        if (todayFragment instanceof TodayFragment) {
            ((TodayFragment) todayFragment).refresh();
        }
    }

    public void startPhotoForDate(Plant selectedPlant, String dateToUse) {
        takePhotoForPlant(selectedPlant, false, dateToUse);
    }

    public void capturePhotoForPlant(Plant plant, boolean isCover) {
        takePhotoForPlant(plant, isCover, null);
    }

    private void importCloudDataForUser(@NonNull String email) {
        com.example.plantcare.util.BgExecutor.io(() -> {
            try {
                AppDatabase db = DatabaseClient.getInstance(getApplicationContext()).getAppDatabase();
                db.reminderDao().deleteAllRemindersForUser(email);
                db.plantDao().deleteAllUserPlantsForUser(email);
                db.plantPhotoDao().deleteAllPhotosForUser(email);

                FirebaseSyncManager fsm = FirebaseSyncManager.get();
                AtomicInteger remaining = new AtomicInteger(3);

                fsm.importUserData(email, new FirebaseSyncManager.DataImportCallback() {
                    @Override
                    public void onPlantsImported(List<Plant> plants) {
                        com.example.plantcare.util.BgExecutor.io(() -> {
                            try {
                                if (plants != null) {
                                    for (Plant p : plants) {
                                        if (p == null) continue;
                                        if (p.userEmail == null || p.userEmail.isEmpty()) p.userEmail = email;
                                        p.isUserPlant = true;
                                        try { db.plantDao().insert(p); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
                                    }
                                }
                            } finally {
                                if (remaining.decrementAndGet() == 0) onCloudImportFinished();
                            }
                        });
                    }

                    @Override
                    public void onRemindersImported(List<WateringReminder> reminders) {
                        com.example.plantcare.util.BgExecutor.io(() -> {
                            try {
                                if (reminders != null) {
                                    for (WateringReminder r : reminders) {
                                        if (r == null) continue;
                                        if (r.userEmail == null || r.userEmail.isEmpty()) r.userEmail = email;
                                        try { db.reminderDao().insert(r); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
                                    }
                                }
                            } finally {
                                if (remaining.decrementAndGet() == 0) onCloudImportFinished();
                            }
                        });
                    }

                    @Override
                    public void onPhotosImported(List<PlantPhoto> photos) {
                        com.example.plantcare.util.BgExecutor.io(() -> {
                            try {
                                if (photos != null) {
                                    for (PlantPhoto ph : photos) {
                                        if (ph == null) continue;
                                        if (ph.userEmail == null || ph.userEmail.isEmpty()) ph.userEmail = email;
                                        try { db.plantPhotoDao().insert(ph); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
                                    }
                                }
                            } finally {
                                if (remaining.decrementAndGet() == 0) onCloudImportFinished();
                            }
                        });
                    }
                });
            } catch (Throwable t) {
                runOnUiThread(() -> Toast.makeText(this, R.string.cloud_import_failed, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void onCloudImportFinished() {
        try { CoverCloudSync.pullCoversToRoom(getApplicationContext(), null, null); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

        runOnUiThread(() -> {
            try { Toast.makeText(this, R.string.cloud_import_success, Toast.LENGTH_SHORT).show(); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
            refreshFragments();
        });
        try { com.example.plantcare.DataChangeNotifier.notifyChange(); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
    }
}