package com.example.plantcare;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
import java.util.concurrent.Executors;
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
    private com.example.plantcare.data.repository.PlantRepository plantRepo;
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
        plantRepo = com.example.plantcare.data.repository.PlantRepository
                .getInstance(getApplicationContext());
        currentUserEmail = EmailContext.current(this);

        // Phase 6.2: if launched from a Firebase magic link, finish sign-in.
        // Audit fix #1 (2026-05-06): the original handler only updated
        // EmailContext + recreated; it skipped the AuthRepository row that
        // every other sign-in path inserts. Without it, a magic-link user
        // would have a Firebase session but no User row in Room, breaking
        // every Settings query that joins on email.
        AuthMagicLink.finishFromIntent(this, getIntent(),
                email -> {
                    EmailContext.setCurrent(this, email);
                    final String displayName = AuthValidation.nameFromEmail(null, email);
                    com.example.plantcare.util.BgExecutor.io(() -> {
                        com.example.plantcare.data.repository.AuthRepository repo =
                                com.example.plantcare.data.repository.AuthRepository
                                        .getInstance(getApplicationContext());
                        User existing = repo.getUserByEmailBlocking(email);
                        if (existing == null) {
                            repo.insertUserBlocking(new User(email, displayName, ""));
                        }
                    });
                    Analytics.INSTANCE.logLogin(this, "magic_link");
                    recreate();
                },
                msg -> Toast.makeText(this, getString(R.string.auth_magic_link_failed, msg),
                        Toast.LENGTH_LONG).show());

        // Audit fix #2 (2026-05-06): resume Google-account deletion after
        // re-authentication. SettingsDialogFragment writes
        // "pending_delete_email" to SecurePrefs before signing the user
        // out; once Firebase confirms a fresh sign-in for the same email,
        // we delete the account here.
        try {
            android.content.SharedPreferences sp = SecurePrefsHelper.INSTANCE.get(this);
            String pending = sp.getString("pending_delete_email", null);
            com.google.firebase.auth.FirebaseUser fbU =
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (pending != null && fbU != null && pending.equals(fbU.getEmail())) {
                sp.edit().remove("pending_delete_email").apply();
                final String em = pending;
                com.example.plantcare.util.BgExecutor.io(() -> {
                    try { FirebaseSyncManager.get().deleteAllPhotosForUser(em); } catch (Throwable t) { CrashReporter.INSTANCE.log(t); }
                    Context appCtx = getApplicationContext();
                    com.example.plantcare.data.repository.ReminderRepository
                            .getInstance(appCtx).deleteAllRemindersForUserBlocking(em);
                    com.example.plantcare.data.repository.PlantRepository
                            .getInstance(appCtx).deleteAllUserPlantsForUserBlocking(em);
                    com.example.plantcare.data.repository.PlantPhotoRepository
                            .getInstance(appCtx).deleteAllPhotosForUserBlocking(em);
                    com.example.plantcare.data.repository.AuthRepository
                            .getInstance(appCtx).deleteUserByEmailBlocking(em);
                    runOnUiThread(() -> fbU.delete());
                });
            }
        } catch (Throwable t) { CrashReporter.INSTANCE.log(t); }

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

        // Phase 4.1 / 4.2: optional biometric unlock. Firebase already
        // restored the session above; this just keeps the visible content
        // hidden until the user authenticates locally. Only runs on cold
        // start (savedInstanceState == null) to avoid nagging on rotation.
        // Audit fix #5 (2026-05-06): the previous "negative button → signOut"
        // path was a UX trap. The label says "use password" but we were
        // logging the user out of Firebase entirely, which destroys their
        // session and then needs full re-auth. Now: just close the app on
        // negative button — the next launch goes through the same biometric
        // gate, and the user can disable the toggle from any other device
        // (or via "forgot password" flow).
        if (savedInstanceState == null
                && !isGuest
                && AuthBiometric.isEnabled(this)
                && AuthBiometric.isAvailable(this)
                && com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) {
            final View root = findViewById(android.R.id.content);
            if (root != null) root.setVisibility(View.INVISIBLE);
            AuthBiometric.prompt(this,
                    () -> { if (root != null) root.setVisibility(View.VISIBLE); },
                    this::finishAndRemoveTask);
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
                        // Raise the room sync barrier IMMEDIATELY — before
                        // refreshFragments triggers the new MyPlantsFragment
                        // to call ensureDefaultsForUserBlocking. Without this
                        // the fragment seeds local defaults AND syncs them
                        // to Firestore in the ~200ms window before the cloud
                        // import starts, silently overwriting any rename the
                        // user did on their other device. Cleared in
                        // onCloudImportFinished + by the safety timeout
                        // installed below in case the import chain breaks.
                        com.example.plantcare.data.repository.RoomCategoryRepository
                                .CLOUD_IMPORT_IN_PROGRESS.set(true);
                        // Safety net: if ensurePlantsCollectionHasImageUri's
                        // callback never fires (Firestore offline, user
                        // backgrounded the app), the flag would stay raised
                        // forever and the user would see no rooms ever.
                        // 30 s is generous — typical cloud import is < 2 s.
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .postDelayed(() -> {
                                    com.example.plantcare.data.repository.RoomCategoryRepository
                                            .CLOUD_IMPORT_IN_PROGRESS.set(false);
                                }, 30_000L);
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
                        } catch (Throwable __ce) {
                            // Lift the barrier if the repair step itself
                            // throws — without this the flag would stay
                            // raised until the safety timeout (30 s) fires.
                            com.example.plantcare.data.repository.RoomCategoryRepository
                                    .CLOUD_IMPORT_IN_PROGRESS.set(false);
                            com.example.plantcare.CrashReporter.INSTANCE.log(__ce);
                        }
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
                // PlantIdentifyActivity selects the target room itself
                // inside AddToMyPlantsDialogFragment, so we don't pass
                // a room context from here.
                Intent intent = new Intent(this, com.example.plantcare.ui.identify.PlantIdentifyActivity.class);
                startActivity(intent);
            });
        }

        ImageButton diseaseButton = findViewById(R.id.diseaseButton);
        if (diseaseButton != null) {
            diseaseButton.setOnClickListener(v -> openDiseaseDiagnosisFlow());
        }

        ImageButton settingsButton = findViewById(R.id.settingsButton);
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> openSettingsDialog());
        }

        selectTab(0);

        adManager = new AdManager(this, (AdView) findViewById(R.id.adBanner));
        adManager.start();
        // B1: live-react to a Pro purchase that completes while this
        // Activity is on screen (paywall → success → banner should
        // disappear without an Activity recreate).
        adManager.observeProState(this);
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

    /**
     * Pre-Dialog für den Krankheitsdiagnose-Knopf.
     *
     * Bietet zwei Wege:
     *  1. Allgemeine Diagnose — ohne Pflanzenbezug (Foto wird einfach analysiert).
     *  2. Eine bestimmte eigene Pflanze prüfen — danach wird die Pflanzenauswahl
     *     gezeigt und die ausgewählte plantId an die Diagnose-Activity übergeben.
     *     Nach erfolgreicher Diagnose fragt die Activity zusätzlich, ob das Foto
     *     + der Krankheitshinweis ins Foto-Archiv der Pflanze übernommen werden
     *     sollen.
     */
    private void openDiseaseDiagnosisFlow() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.disease_chooser_title)
                .setMessage(R.string.disease_chooser_message)
                .setPositiveButton(R.string.disease_chooser_specific, (dlg, which) -> {
                    dlg.dismiss();
                    pickPlantThenLaunchDisease();
                })
                .setNegativeButton(R.string.disease_chooser_general, (dlg, which) -> {
                    dlg.dismiss();
                    launchDiseaseActivity(0);
                })
                .setNeutralButton(R.string.disease_pick_plant_cancel, (dlg, which) -> dlg.dismiss())
                .show();
    }

    /**
     * Lädt die Pflanzen des aktuellen Nutzers im Hintergrund und zeigt einen
     * Auswahl­dialog. Bei leerer Sammlung wird ein Toast gezeigt und auf den
     * allgemeinen Flow zurückgefallen.
     */
    private void pickPlantThenLaunchDisease() {
        final String email = EmailContext.current(this);
        // M2: was Executors.newSingleThreadExecutor() and never shut
        // down — every disease-button tap leaked one Thread plus its
        // pool's queue. BgExecutor is a shared, bounded pool with the
        // same semantics for our one-shot DB read.
        com.example.plantcare.util.BgExecutor.io(() -> {
            List<Plant> plants;
            try {
                plants = (email == null || email.isEmpty())
                        ? plantRepo.getAllUserPlantsBlocking()
                        : plantRepo.getAllUserPlantsForUserBlocking(email);
            } catch (Throwable t) {
                CrashReporter.INSTANCE.log(t);
                plants = null;
            }
            final List<Plant> finalPlants = plants;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (finalPlants == null || finalPlants.isEmpty()) {
                    Toast.makeText(this, R.string.disease_chooser_no_plants, Toast.LENGTH_LONG).show();
                    launchDiseaseActivity(0);
                    return;
                }
                String[] labels = new String[finalPlants.size()];
                for (int i = 0; i < finalPlants.size(); i++) {
                    Plant p = finalPlants.get(i);
                    String nick = (p.nickname != null && !p.nickname.trim().isEmpty()) ? p.nickname : null;
                    labels[i] = nick != null ? nick
                            : (p.name != null ? p.name : "Pflanze #" + p.id);
                }
                new AlertDialog.Builder(this)
                        .setTitle(R.string.disease_chooser_pick_plant_title)
                        .setItems(labels, (dialog, which) -> {
                            Plant chosen = finalPlants.get(which);
                            launchDiseaseActivity(chosen.id);
                            dialog.dismiss();
                        })
                        .setNegativeButton(R.string.disease_pick_plant_cancel, null)
                        .show();
            });
        });
        // BgExecutor is a process-wide shared pool — no shutdown needed.
    }

    /**
     * Startet die {@link com.example.plantcare.ui.disease.DiseaseDiagnosisActivity}.
     * Wenn {@code plantId > 0}, wird die ID als Extra mitgegeben; die Activity
     * speichert die Diagnose dann ohne erneute Pflanzenauswahl und bietet
     * anschließend die Übernahme ins Foto-Archiv an.
     */
    private void launchDiseaseActivity(int plantId) {
        Intent intent = new Intent(this,
                com.example.plantcare.ui.disease.DiseaseDiagnosisActivity.class);
        if (plantId > 0) {
            intent.putExtra(
                    com.example.plantcare.ui.disease.DiseaseDiagnosisActivity.EXTRA_PLANT_ID,
                    plantId);
        }
        startActivity(intent);
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
            if (plantRepo.countAllBlocking() == 0) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(getAssets().open("plants.csv")))) {
                    String line;
                    boolean isFirstLine = true;
                    while ((line = reader.readLine()) != null) {
                        if (isFirstLine) { isFirstLine = false; continue; }
                        // Quoted-aware split: descriptions may contain commas
                        // wrapped in double quotes (RFC-4180-ish). The previous
                        // naive `split(",")` truncated such rows silently — a
                        // future catalog row like "Aloe Vera","Hell, sonnig",...
                        // would lose its lighting field entirely.
                        String[] parts = parseCsvLine(line);
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
                            plantRepo.insertBlocking(p);
                        }
                    }
                } catch (IOException e) {
                    com.example.plantcare.CrashReporter.INSTANCE.log(e);
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

    /**
     * Minimal RFC-4180-ish CSV line parser. Supports double-quoted fields
     * with embedded commas. Doesn't bother with escaped quotes inside
     * fields ("" → ") because plants.csv doesn't need them — but if a
     * future row does, the row will simply be one comma-split below the
     * 5-field threshold and the seeder skips it instead of corrupting
     * the catalog.
     */
    private static String[] parseCsvLine(String line) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
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
            com.example.plantcare.CrashReporter.INSTANCE.log(ex);
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
        // Locale.US for the wire format that lands in plant_photo.dateTaken.
        // On ar/fa devices Locale.getDefault() produces Eastern-Arabic
        // digits (٢٠٢٦-٠٥-٠٦) which never match Latin-digit rows in
        // SQL date comparisons — same A2 root cause as the worker fixes.
        String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
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
                    plantRepo.updateBlocking(currentPlantForPhoto);
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
                // #17 fix: openInputStream can return null (URI revoked,
                // ContentProvider gone). Pre-fix the next read() would
                // NPE and the catch (Throwable) silently returned null —
                // the user's cover-photo capture vanished with no
                // signal in Crashlytics. Explicit null check + log.
                if (in == null) {
                    com.example.plantcare.CrashReporter.INSTANCE.log(
                            new IllegalStateException(
                                    "openInputStream returned null for cover source uri"));
                    return null;
                }
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.flush();
            }
            return PhotoStorage.coverUri(this, (long) plantId);
        } catch (Throwable t) {
            com.example.plantcare.CrashReporter.INSTANCE.log(t);
            return null;
        }
    }

    private void handleGalleryResult(Uri imageUri) {
        // Locale.US — same wire-format reasoning as handleCameraResult.
        String dateToUse = (pendingGalleryPhotoDate != null && !pendingGalleryPhotoDate.isEmpty())
                ? pendingGalleryPhotoDate
                : new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
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
            com.example.plantcare.data.repository.PlantPhotoRepository photoRepo =
                    com.example.plantcare.data.repository.PlantPhotoRepository
                            .getInstance(getApplicationContext());
            PlantPhoto photo = new PlantPhoto();
            photo.plantId = plantId;
            photo.imagePath = (uri != null) ? uri.toString() : null;
            photo.dateTaken = dateStr;
            photo.isCover = isCover;
            photo.userEmail = getCurrentUserEmail();
            long newId = photoRepo.insertBlocking(photo);
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
        // Sync barrier: tell ensureDefaultsForUserBlocking to stand down
        // until the cloud import finishes. Otherwise UI-driven defaults
        // race with cloud-preserved IDs and collide on PK during insert.
        // Cleared in onCloudImportFinished (success or failure of the 4
        // streams).
        com.example.plantcare.data.repository.RoomCategoryRepository
                .CLOUD_IMPORT_IN_PROGRESS.set(true);
        com.example.plantcare.util.BgExecutor.io(() -> {
            try {
                Context appCtx = getApplicationContext();
                com.example.plantcare.data.repository.ReminderRepository
                        .getInstance(appCtx).deleteAllRemindersForUserBlocking(email);
                com.example.plantcare.data.repository.PlantRepository
                        .getInstance(appCtx).deleteAllUserPlantsForUserBlocking(email);
                com.example.plantcare.data.repository.PlantPhotoRepository
                        .getInstance(appCtx).deleteAllPhotosForUserBlocking(email);
                // Also wipe local rooms so the import doesn't merge cloud
                // rooms with whatever defaults this device created during
                // first launch — those would show up as duplicates.
                com.example.plantcare.data.repository.RoomCategoryRepository roomRepo =
                        com.example.plantcare.data.repository.RoomCategoryRepository.getInstance(appCtx);
                java.util.List<RoomCategory> existingRooms = roomRepo.getAllRoomsForUserBlocking(email);
                if (existingRooms != null) {
                    for (RoomCategory r : existingRooms) {
                        try { roomRepo.deleteBlocking(r); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
                    }
                }

                // F10.3: wipe local memos for this user before the cloud
                // restore overwrites them. Mirror of the rooms pattern above —
                // a signed-in user reinstalling on a new device should see
                // ONLY the cloud memos, not whatever leftover the local DB
                // still carries from a previous account on the same device.
                try {
                    AppDatabase.getInstance(appCtx).journalMemoDao().deleteAllForUser(email);
                } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

                // Vacation cloud restore (V2): wipe local vacation prefs
                // for this email before importing — otherwise a vacation
                // we cleared on Device A would zombie back from Device B's
                // stale local prefs after import. The cloud is the
                // source of truth here; if it's empty (no vacation set)
                // local should also be empty.
                try {
                    com.example.plantcare.feature.vacation.VacationPrefs
                            .clearLocalOnly(appCtx, email);
                } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

                // C3 gamification cloud restore — wipe local first so
                // we don't merge the previous user's streak into this
                // user's account on a shared device. Mirror the rooms /
                // memos pattern.
                try {
                    com.example.plantcare.feature.streak.StreakTracker.reset(appCtx, email);
                } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
                try {
                    com.example.plantcare.feature.streak.ChallengeRegistry.reset(appCtx, email);
                } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

                FirebaseSyncManager fsm = FirebaseSyncManager.get();
                // Now nine import streams: plants, reminders, photos,
                // rooms, memos, vacation, streak, challenges, proStatus.
                AtomicInteger remaining = new AtomicInteger(9);

                fsm.importProStatusForCurrentUser(proDoc -> {
                    com.example.plantcare.util.BgExecutor.io(() -> {
                        try {
                            if (proDoc != null) {
                                com.example.plantcare.billing.ProStatusManager
                                        .restoreFromCloud(appCtx, proDoc);
                                // ZZ1: push the restored value into
                                // BillingManager._isPro so the AdManager
                                // observer + paywall callers see it
                                // live. restoreFromCloud writes prefs
                                // directly (avoids a sync loop) so the
                                // StateFlow needs an explicit refresh.
                                com.example.plantcare.billing.BillingManager
                                        .getInstance(appCtx)
                                        .refreshFromLocal();
                            }
                        } finally {
                            if (remaining.decrementAndGet() == 0) onCloudImportFinished();
                        }
                    });
                });

                fsm.importStreakForCurrentUser(streakDoc -> {
                    com.example.plantcare.util.BgExecutor.io(() -> {
                        try {
                            if (streakDoc != null) {
                                com.example.plantcare.feature.streak.StreakTracker
                                        .restoreFromCloud(
                                                appCtx, email,
                                                streakDoc.getCurrentStreak(),
                                                streakDoc.getBestStreak(),
                                                streakDoc.getLastDay());
                            }
                        } finally {
                            if (remaining.decrementAndGet() == 0) onCloudImportFinished();
                        }
                    });
                });

                fsm.importChallengesForCurrentUser(challengesDoc -> {
                    com.example.plantcare.util.BgExecutor.io(() -> {
                        try {
                            if (challengesDoc != null) {
                                com.example.plantcare.feature.streak.ChallengeRegistry
                                        .restoreFromCloud(appCtx, email, challengesDoc);
                            }
                        } finally {
                            if (remaining.decrementAndGet() == 0) onCloudImportFinished();
                        }
                    });
                });

                fsm.importVacationForCurrentUser(doc -> {
                    com.example.plantcare.util.BgExecutor.io(() -> {
                        try {
                            if (doc != null && doc.getStart() != null && doc.getEnd() != null) {
                                com.example.plantcare.feature.vacation.VacationPrefs
                                        .restoreFromCloud(
                                                appCtx, email,
                                                doc.getStart(),
                                                doc.getEnd(),
                                                doc.getWelcomeFired());
                            }
                        } finally {
                            if (remaining.decrementAndGet() == 0) onCloudImportFinished();
                        }
                    });
                });

                fsm.importRoomsForCurrentUser(rooms -> {
                    com.example.plantcare.util.BgExecutor.io(() -> {
                        try {
                            if (rooms != null) {
                                for (RoomCategory r : rooms) {
                                    if (r == null || r.name == null || r.name.isEmpty()) continue;
                                    if (r.userEmail == null || r.userEmail.isEmpty()) r.userEmail = email;
                                    try { roomRepo.insertBlocking(r); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
                                }
                            }
                        } finally {
                            if (remaining.decrementAndGet() == 0) onCloudImportFinished();
                        }
                    });
                });

                fsm.importMemosForCurrentUser(memos -> {
                    com.example.plantcare.util.BgExecutor.io(() -> {
                        try {
                            if (memos != null) {
                                com.example.plantcare.data.journal.JournalMemoDao memoDao =
                                        AppDatabase.getInstance(getApplicationContext()).journalMemoDao();
                                for (com.example.plantcare.data.journal.JournalMemo m : memos) {
                                    if (m == null) continue;
                                    com.example.plantcare.data.journal.JournalMemo toInsert =
                                            (m.getUserEmail() == null || m.getUserEmail().isEmpty())
                                                    ? m.copy(m.getId(), m.getPlantId(), email, m.getText(), m.getCreatedAt(), m.getUpdatedAt())
                                                    : m;
                                    try { memoDao.insert(toInsert); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
                                }
                            }
                        } finally {
                            if (remaining.decrementAndGet() == 0) onCloudImportFinished();
                        }
                    });
                });

                fsm.importUserData(email, new FirebaseSyncManager.DataImportCallback() {
                    @Override
                    public void onPlantsImported(List<Plant> plants) {
                        com.example.plantcare.util.BgExecutor.io(() -> {
                            try {
                                if (plants != null) {
                                    com.example.plantcare.data.repository.PlantRepository repo =
                                            com.example.plantcare.data.repository.PlantRepository
                                                    .getInstance(getApplicationContext());
                                    for (Plant p : plants) {
                                        if (p == null) continue;
                                        if (p.userEmail == null || p.userEmail.isEmpty()) p.userEmail = email;
                                        p.isUserPlant = true;
                                        try { repo.insertBlocking(p); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
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
                                    com.example.plantcare.data.repository.ReminderRepository repo =
                                            com.example.plantcare.data.repository.ReminderRepository
                                                    .getInstance(getApplicationContext());
                                    for (WateringReminder r : reminders) {
                                        if (r == null) continue;
                                        if (r.userEmail == null || r.userEmail.isEmpty()) r.userEmail = email;
                                        try { repo.insertBlocking(r); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
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
                                    com.example.plantcare.data.repository.PlantPhotoRepository repo =
                                            com.example.plantcare.data.repository.PlantPhotoRepository
                                                    .getInstance(getApplicationContext());
                                    for (PlantPhoto ph : photos) {
                                        if (ph == null) continue;
                                        if (ph.userEmail == null || ph.userEmail.isEmpty()) ph.userEmail = email;
                                        try { repo.insertBlocking(ph); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
                                    }
                                }
                            } finally {
                                if (remaining.decrementAndGet() == 0) onCloudImportFinished();
                            }
                        });
                    }
                });
            } catch (Throwable t) {
                // Outer-failure path: lift the sync barrier so UI can
                // resume seeding defaults. Without this an exception
                // here would leave ensureDefaultsForUserBlocking gated
                // forever for the lifetime of the process.
                com.example.plantcare.data.repository.RoomCategoryRepository
                        .CLOUD_IMPORT_IN_PROGRESS.set(false);
                runOnUiThread(() -> Toast.makeText(this, R.string.cloud_import_failed, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void onCloudImportFinished() {
        // Lift the sync barrier — ensureDefaultsForUserBlocking can run
        // again. Any room collection that arrived empty from Firestore
        // (first sign-in ever for this account) will now seed the five
        // German defaults on the next UI load.
        com.example.plantcare.data.repository.RoomCategoryRepository
                .CLOUD_IMPORT_IN_PROGRESS.set(false);

        try { CoverCloudSync.pullCoversToRoom(getApplicationContext(), null, null); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

        runOnUiThread(() -> {
            try { Toast.makeText(this, R.string.cloud_import_success, Toast.LENGTH_SHORT).show(); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
            refreshFragments();
        });
        try { com.example.plantcare.DataChangeNotifier.notifyChange(); } catch (Throwable __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }
    }
}