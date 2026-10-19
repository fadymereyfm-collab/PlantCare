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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    /** Wave 2: wrap baseContext with the user's font-scale pref so every
     *  text in this Activity respects the AppearancePrefs choice. Recreated
     *  by SettingsDialogFragment after a font-scale change. */
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(com.example.plantcare.format.FontScaleHelper.wrap(newBase));
    }

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
    /** Phase G1.5: was `calendarFragment` (CalendarFragment) — replaced
     *  by Diagnose. Calendar is now reached via a bottom-sheet opened
     *  from the Home week-strip (built in G4). */
    private Fragment diagnoseFragment;
    private Fragment todayFragment;
    /** F2.2: Einstellungen now lives as a Fragment in the Bottom Nav. */
    private Fragment settingsFragment;

    /**
     * Canonical Bottom Navigation tab indices.
     *
     * Order matches `res/menu/bottom_nav_menu.xml` and the values used by
     * the `launchTab` int extra (sent from PlantsInRoomActivity, the
     * widget, and any deep-link callers).
     *
     * F2.2 mapping was: 0=Today, 1=Calendar, 2=Garden, 3=Settings.
     * G1.5 mapping is:  0=Today (Home), 1=Garden, 2=Diagnose, 3=Settings.
     *
     * Calendar was dropped as a top-level tab in G1.5 — its content is
     * folded into Home (week-strip + "Full calendar" bottom-sheet in G4).
     * Diagnose was promoted from a buried sub-screen reachable only via
     * the UnifiedCamera mode slider to a first-class tab matching the
     * reference Plant Identifier app.
     *
     * Migration of launchTab callers (audited 2026-05-15):
     *   - PlantsInRoomActivity: was sending 2 (Garden) → now sends 1.
     *   - Widget: sends 0 (Today) → unchanged.
     *   - No other senders known.
     */
    private static final int NAV_INDEX_TODAY    = 0;
    private static final int NAV_INDEX_GARDEN   = 1;
    private static final int NAV_INDEX_DIAGNOSE = 2;
    private static final int NAV_INDEX_SETTINGS = 3;

    /** Saved-instance key for the currently-selected tab (F2.2). */
    private static final String KEY_SELECTED_TAB = "selected_tab";

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
        // Wave 2: Family Share — if launched from a share-invite push,
        // surface the accept/decline dialog as soon as the Activity is up.
        handleSharePushIntent(getIntent());

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
            // Pass the requested auth mode through from OnboardingActivity
            // ("Konto erstellen" → register, "Anmelden" → login). When the
            // user opens the app directly (no Onboarding extra), default to
            // register — the email button stays "Mit E-Mail registrieren"
            // exactly as before this change.
            String requestedMode = AuthStartDialogFragment.MODE_REGISTER;
            try {
                if (getIntent() != null) {
                    String m = getIntent().getStringExtra("auth_mode");
                    if (AuthStartDialogFragment.MODE_LOGIN.equalsIgnoreCase(m)) {
                        requestedMode = AuthStartDialogFragment.MODE_LOGIN;
                    }
                }
            } catch (Throwable t) {
                // fall through with register default
                CrashReporter.INSTANCE.log(t);
            }
            AuthStartDialogFragment auth = AuthStartDialogFragment.newInstance(requestedMode);
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
                        // AUTH-A fix (2026-05-08): pre-fix `importCloudDataForUser`
                        // ran ONLY from the `ensurePlantsCollectionHasImageUri`
                        // success callback. When that callback never fired
                        // (Firestore offline burst, intermittent permission
                        // denied on the legacy global `plants` collection,
                        // or any thrown-then-caught exception inside the
                        // continuation), the user landed on an empty
                        // account because the import chain was gated behind
                        // a step that's only relevant for one-time legacy
                        // schema repair. Now: kick off the cloud import
                        // immediately, and run the schema-repair as an
                        // independent best-effort task — they don't depend
                        // on each other for correctness.
                        importCloudDataForUser(email);
                        try {
                            CoverCloudSync.ensurePlantsCollectionHasImageUri(
                                    getApplicationContext(), null);
                        } catch (Throwable __ce) {
                            com.example.plantcare.CrashReporter.INSTANCE.log(__ce);
                        }
                    }
                }
        );

        seedDatabaseIfEmpty();
        setupActivityResultLaunchers();

        // Permission ordering — most user-visible feature first.
        //
        // Pre-fix order (notification → location) meant the system queued
        // the location dialog BEHIND the notification one. On a fresh
        // install where the user takes a moment to read the notification
        // rationale, the location dialog only surfaced 30+ seconds later,
        // and the weather card stayed empty until then. Calendar tab is
        // typically the first place users look after onboarding, so
        // weather has higher perceived urgency than push notifications.
        //
        // New order:
        //   1. Location FIRST — drives the weather card on the calendar tab.
        //      If already granted, fire a one-shot fetch immediately so the
        //      user doesn't wait up to 12 h for the periodic worker.
        //   2. Notifications second — needed for reminder pushes but the
        //      app is fully usable without them on Android 13+.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissionLauncher.launch(new String[] {
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            });
        } else {
            triggerWeatherFetchNow();
        }

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                   != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        allPlantsFragment = new AllPlantsFragment();
        myPlantsFragment = new MyPlantsFragment();
        diagnoseFragment = new com.example.plantcare.ui.diagnose.DiagnoseFragment();
        todayFragment = new TodayFragment();
        settingsFragment = new com.example.plantcare.ui.settings.SettingsFragment();

        // F2.2 — Bottom Navigation wiring. Replaces the legacy 4 top tabs
        // (tabAllPlants/tabMyPlants/tabCalendar/tabToday) and the 3 toolbar
        // ImageButtons (identify/disease/settings). Identify + Disease
        // become modes of the new center camera FAB in F2.3.
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav =
                findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                int tabIndex;
                if (id == R.id.nav_today)         tabIndex = NAV_INDEX_TODAY;
                else if (id == R.id.nav_garden)   tabIndex = NAV_INDEX_GARDEN;
                else if (id == R.id.nav_diagnose) tabIndex = NAV_INDEX_DIAGNOSE;
                else if (id == R.id.nav_settings) tabIndex = NAV_INDEX_SETTINGS;
                else return false;
                renderTab(tabIndex);
                return true;
            });
        }

        // F2.3 — Center camera FAB. Launches UnifiedCameraActivity, which
        // hosts the CameraX preview + mode slider (Identify | Diagnose) and
        // routes the captured image to PlantIdentifyActivity or
        // DiseaseDiagnosisActivity via EXTRA_PRELOADED_IMAGE_PATH.
        com.google.android.material.floatingactionbutton.FloatingActionButton fabCamera =
                findViewById(R.id.fabCamera);
        if (fabCamera != null) {
            // 2026-05-19 click-guard: منع double-tap من فتح UnifiedCameraActivity مرّتين
            fabCamera.setOnClickListener(
                com.example.plantcare.ui.util.ClickGuard.wrap(v -> startActivity(
                    new Intent(this,
                            com.example.plantcare.ui.camera.UnifiedCameraActivity.class))));
        }

        // Initial tab selection — priority: savedInstanceState > intent extra > default.
        int initialTab;
        if (savedInstanceState != null) {
            initialTab = savedInstanceState.getInt(KEY_SELECTED_TAB, NAV_INDEX_TODAY);
        } else {
            initialTab = getIntent() != null
                    ? getIntent().getIntExtra("launchTab", NAV_INDEX_TODAY)
                    : NAV_INDEX_TODAY;
        }
        // setSelectedItemId fires the listener → renderTab runs. If the
        // requested item happens to be the default-selected one, the
        // listener may not fire — the explicit findFragmentById guard
        // ensures the fragmentContainer is never left empty.
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(navIdForIndex(initialTab));
        }
        if (getSupportFragmentManager().findFragmentById(R.id.fragmentContainer) == null) {
            renderTab(initialTab);
        }

        // F2.4 — cold-start mirror of the onNewIntent openCatalog handler.
        // PlantsInRoomActivity's "Add from catalog" path normally hits
        // onNewIntent (MainActivity is in-stack), but a cleared-task /
        // process-death revival routes through onCreate instead.
        Intent createIntent = getIntent();
        if (savedInstanceState == null
                && createIntent != null
                && createIntent.getBooleanExtra("openCatalog", false)) {
            createIntent.removeExtra("openCatalog");
            startActivity(new Intent(this,
                    com.example.plantcare.ui.catalog.CatalogActivity.class));
        }

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
        // Pre-fix this rendered as a vanilla Material AlertDialog with three
        // bottom buttons (positive/negative/neutral), which the user flagged
        // as "non-matching" against the rest of the app's polished modals.
        // Now it uses the same ActionListDialogFragment + subtitle pattern
        // as the post-capture chooser — Outlined rows for the two real
        // choices, the title's subtitle carries the original message body,
        // and the dialog's built-in Abbrechen replaces the neutral button.
        java.util.List<com.example.plantcare.ui.util.ActionListDialogFragment.Item> items =
                new java.util.ArrayList<>();
        items.add(new com.example.plantcare.ui.util.ActionListDialogFragment.Item(
                getString(R.string.disease_chooser_specific),
                false,
                () -> { pickPlantThenLaunchDisease(); return kotlin.Unit.INSTANCE; }));
        items.add(new com.example.plantcare.ui.util.ActionListDialogFragment.Item(
                getString(R.string.disease_chooser_general),
                false,
                () -> { launchDiseaseActivity(0); return kotlin.Unit.INSTANCE; }));
        new com.example.plantcare.ui.util.ActionListDialogFragment()
                .configure(getString(R.string.disease_chooser_title), items)
                .subtitle(getString(R.string.disease_chooser_message))
                .show(getSupportFragmentManager(),
                        com.example.plantcare.ui.util.ActionListDialogFragment.TAG);
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
                java.util.List<com.example.plantcare.ui.util.ActionListDialogFragment.Item> items =
                        new java.util.ArrayList<>();
                for (Plant p : finalPlants) {
                    final Plant chosen = p;
                    String nick = (p.nickname != null && !p.nickname.trim().isEmpty()) ? p.nickname : null;
                    String label = nick != null ? nick
                            : (p.name != null ? p.name : "Pflanze #" + p.id);
                    items.add(new com.example.plantcare.ui.util.ActionListDialogFragment.Item(
                            label,
                            false,
                            () -> { launchDiseaseActivity(chosen.id); return kotlin.Unit.INSTANCE; }));
                }
                new com.example.plantcare.ui.util.ActionListDialogFragment()
                        .configure(getString(R.string.disease_chooser_pick_plant_title), items)
                        .show(getSupportFragmentManager(),
                                com.example.plantcare.ui.util.ActionListDialogFragment.TAG);
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

    /**
     * Programmatic tab selection — used by `onNewIntent` (launchTab extra)
     * and any other external/internal caller that wants to switch tabs.
     *
     * F2.2 — Routes through the BottomNavigationView so its selection
     * chip stays in sync. The listener handles the fragment swap and
     * title update via {@link #renderTab}. If the requested item is
     * already selected (listener won't fire), falls back to direct
     * render.
     */
    private void selectTab(int tabIndex) {
        com.google.android.material.bottomnavigation.BottomNavigationView nav =
                findViewById(R.id.bottomNav);
        int id = navIdForIndex(tabIndex);
        if (nav != null && nav.getSelectedItemId() != id) {
            nav.setSelectedItemId(id);
        } else {
            renderTab(tabIndex);
        }
    }

    /**
     * Internal — swaps the visible Fragment + updates the toolbar title.
     * Called from the BottomNavigationView listener and from
     * {@link #selectTab} when no selection change happens.
     */
    private void renderTab(int tabIndex) {
        final Fragment fragmentToShow;
        final int titleRes;
        switch (tabIndex) {
            case NAV_INDEX_GARDEN:
                fragmentToShow = myPlantsFragment;
                titleRes = R.string.nav_garden;
                break;
            case NAV_INDEX_DIAGNOSE:
                fragmentToShow = diagnoseFragment;
                titleRes = R.string.nav_diagnose;
                break;
            case NAV_INDEX_SETTINGS:
                fragmentToShow = settingsFragment;
                titleRes = R.string.nav_settings;
                break;
            case NAV_INDEX_TODAY:
            default:
                fragmentToShow = todayFragment;
                titleRes = R.string.nav_home;
        }
        TextView title = findViewById(R.id.toolbarTitle);
        if (title != null) {
            title.setText(titleRes);
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragmentToShow)
                .commit();
    }

    /** Maps a NAV_INDEX_* to its menu item resource ID (G1.5 mapping). */
    private static int navIdForIndex(int index) {
        switch (index) {
            case NAV_INDEX_GARDEN:   return R.id.nav_garden;
            case NAV_INDEX_DIAGNOSE: return R.id.nav_diagnose;
            case NAV_INDEX_SETTINGS: return R.id.nav_settings;
            case NAV_INDEX_TODAY:
            default:                 return R.id.nav_today;
        }
    }

    /** Inverse of {@link #navIdForIndex}. */
    private static int indexForNavId(int id) {
        if (id == R.id.nav_garden)   return NAV_INDEX_GARDEN;
        if (id == R.id.nav_diagnose) return NAV_INDEX_DIAGNOSE;
        if (id == R.id.nav_settings) return NAV_INDEX_SETTINGS;
        return NAV_INDEX_TODAY;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        com.google.android.material.bottomnavigation.BottomNavigationView nav =
                findViewById(R.id.bottomNav);
        int tab = (nav != null)
                ? indexForNavId(nav.getSelectedItemId())
                : NAV_INDEX_TODAY;
        outState.putInt(KEY_SELECTED_TAB, tab);
    }

    /**
     * F2.7 — Hardware Back behaviour for the new Bottom Navigation.
     *
     * Standard pattern: if the user is on any tab other than Heute, Back
     * pulls them home (to Heute) first. A second Back from Heute exits
     * the app as usual. This avoids the surprising "Back from Garten
     * exits the app immediately" experience and gives a single, learnable
     * exit point.
     */
    @Override
    public void onBackPressed() {
        com.google.android.material.bottomnavigation.BottomNavigationView nav =
                findViewById(R.id.bottomNav);
        if (nav != null && nav.getSelectedItemId() != R.id.nav_today) {
            selectTab(NAV_INDEX_TODAY);
            return;
        }
        super.onBackPressed();
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

    /**
     * Defensive re-seed: the primary seed runs in App.onCreate() before any
     * Activity opens. Kept here as a backup for upgrade scenarios where
     * App.onCreate ran before this version of CatalogSeeder existed.
     * Idempotent — guarded internally by `countAll == 0`.
     */
    private void seedDatabaseIfEmpty() {
        com.example.plantcare.data.CatalogSeeder.seedIfEmptyAsync(getApplicationContext());
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
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
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
            // Bug #3 FK ordering barrier — declared outside the try so the
            // outer catch (Throwable t) can also release it. See the block
            // comment further down for why the latch exists.
            final java.util.concurrent.CountDownLatch plantsLatch =
                    new java.util.concurrent.CountDownLatch(1);
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

                // Bug #4 fix (2026-05-14): promote any existing guest rows to
                // the newly-signed-in user BEFORE the cloud import fans out.
                //
                // Pre-fix scenario: a guest had Plant id=3 locally. The user
                // signed in to a cloud account that ALSO had a (different)
                // Plant id=3. Cloud restore tried to insert that plant with
                // id=3 → PlantDao @Insert defaults to OnConflictStrategy.ABORT
                // → SQLiteConstraintException → swallowed by the surrounding
                // `try { ... } catch (Throwable __ce) { ... }` per-row
                // wrapper → cloud row silently dropped. Same loss for
                // ReminderDao + JournalMemoDao (both ABORT). PlantPhotoDao
                // is REPLACE — there the cloud photo overwrites the guest
                // photo, so cloud wins but the guest photo is still lost.
                //
                // Fix: re-key guest plants AND their children to fresh
                // autogen IDs that can't collide with anything cloud sends
                // back. We bump SQLite's AUTOINCREMENT high-water mark to
                // 1,000,000 on all four tables (plant + reminder +
                // plant_photo + journal_memo) by inserting a throwaway
                // marker row at that id in each and immediately deleting
                // it (sqlite_sequence persists the mark even on delete
                // because every table uses INTEGER PRIMARY KEY
                // AUTOINCREMENT). Subsequent autogen inserts then start at
                // 1,000,001+ — safely above any realistic cloud id. Each
                // guest plant is then re-inserted with id=0 (autogen),
                // userEmail rewritten to the new account; its children
                // (Reminder / Photo / Memo) are likewise re-inserted with
                // id=0 so their OWN primary keys also move above the cloud
                // id range — updating plantId alone would leave the child
                // PK colliding with the cloud account's children (the
                // exact "Same loss for ReminderDao + JournalMemoDao"
                // failure described above). The original guest plant is
                // finally deleted; FK CASCADE sweeps the stale original
                // child rows (still pointing at the old plant id).
                //
                // Wrapped in `runInTransaction` so observers see only the
                // post-promote state (no marker flicker) and a mid-loop
                // failure rolls back cleanly.
                promoteGuestRowsToUser(appCtx, email);

                FirebaseSyncManager fsm = FirebaseSyncManager.get();
                // Now nine import streams: plants, reminders, photos,
                // rooms, memos, vacation, streak, challenges, proStatus.
                AtomicInteger remaining = new AtomicInteger(9);

                // Bug #3 fix (2026-05-14): FK ordering barrier.
                //
                // Pre-fix all 9 streams ran in parallel on BgExecutor.io.
                // The three child entities — WateringReminder, PlantPhoto,
                // JournalMemo — each carry an `@ForeignKey(parentColumns="id",
                // childColumns="plantId", onDelete=CASCADE)` to Plant. If a
                // child row arrived before its parent Plant was inserted,
                // Room rejected the insert with SQLITE_CONSTRAINT and the
                // surrounding `try { ... } catch (Throwable __ce) { ... }`
                // silently swallowed it. Worst case: a user reinstalling on
                // a fresh device saw 90% of their data restored with random
                // gaps in reminders/photos/memos.
                //
                // Fix: the three child callbacks (onRemindersImported,
                // onPhotosImported, importMemosForCurrentUser) await
                // `plantsLatch` before their inserts. The latch counts
                // down from `onPlantsImported.finally` so even an exception
                // during plant insert still releases the children (they'll
                // FK-fail the same way they would today, but only for
                // plants that genuinely failed — not for plants that
                // simply hadn't been written yet). 30-second timeout
                // protects against deadlock if onPlantsImported never
                // fires (network drop, Firestore SDK glitch); on timeout
                // we log to Crashlytics and proceed (children FK-fail as
                // pre-fix — no worse than today's behavior). Rooms,
                // ProStatus, Streak, Challenges, Vacation are independent
                // and stay parallel.
                // (plantsLatch is declared just above the enclosing try so
                // the outer catch can release it on the failure path.)

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
                                // Cloud rooms imported from a device that
                                // pre-dates the canonical-position seed
                                // arrive with position=0, which collapses
                                // back to alphabetical (Bad first instead
                                // of Wohnzimmer). Stamp the canonical
                                // index for any room name that matches a
                                // default — preserves user re-orderings
                                // (those have non-zero positions already)
                                // while fixing the typical "I just signed
                                // in and everything is alphabetical" case.
                                String[] canonical = appCtx.getResources()
                                        .getStringArray(R.array.default_rooms);
                                for (RoomCategory r : rooms) {
                                    if (r == null || r.name == null || r.name.isEmpty()) continue;
                                    if (r.userEmail == null || r.userEmail.isEmpty()) r.userEmail = email;
                                    if (r.position == 0) {
                                        for (int i = 0; i < canonical.length; i++) {
                                            if (canonical[i].equals(r.name)) {
                                                r.position = i;
                                                break;
                                            }
                                        }
                                    }
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
                            // Bug #3: wait for plants to finish before inserting
                            // memos — JournalMemo has FK CASCADE on plantId.
                            awaitPlantsLatch(plantsLatch);
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
                                // Bug #3 fix: ALWAYS release the latch — even if
                                // plants imported with an empty list or threw
                                // mid-loop. Children waiting on this latch will
                                // proceed; if a specific plant is missing they'll
                                // FK-fail individually (same as today's behavior),
                                // but they won't deadlock. Order matters: count
                                // down BEFORE the AtomicInteger so child callbacks
                                // unblocked here see the full plant set on the
                                // next IO thread pickup.
                                plantsLatch.countDown();
                                if (remaining.decrementAndGet() == 0) onCloudImportFinished();
                            }
                        });
                    }

                    @Override
                    public void onRemindersImported(List<WateringReminder> reminders) {
                        com.example.plantcare.util.BgExecutor.io(() -> {
                            try {
                                // Bug #3: wait for plants — WateringReminder has
                                // FK CASCADE on plantId.
                                awaitPlantsLatch(plantsLatch);
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
                                // Bug #3: wait for plants — PlantPhoto has FK
                                // CASCADE on plantId.
                                awaitPlantsLatch(plantsLatch);
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
                // Bug #3 fix: also release the plants latch so any child
                // callback that managed to register before the throw
                // doesn't sit on its 30s await — they'll proceed (and
                // FK-fail individually if their parent plant never
                // arrived), which is the same behavior pre-fix.
                plantsLatch.countDown();
                runOnUiThread(() -> Toast.makeText(this, R.string.cloud_import_failed, Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * Bug #3 helper (2026-05-14): block the current background thread until
     * `onPlantsImported.finally` releases `plantsLatch`, with a 30-second
     * safety timeout to prevent deadlock if Plants never arrive (Firestore
     * SDK glitch, network drop mid-import, etc.). On timeout we log and
     * proceed — child inserts FK-fail individually as they would have
     * pre-fix, which is the same outcome the old "no barrier at all" code
     * produced. Restores the interrupt flag if the wait was interrupted so
     * the caller can react if it cares.
     */
    private static void awaitPlantsLatch(java.util.concurrent.CountDownLatch latch) {
        try {
            if (!latch.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                com.example.plantcare.CrashReporter.INSTANCE.log(new RuntimeException(
                        "Cloud import: plants stream didn't finish within 30s — child rows may FK-fail"));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** Bug #4: known guest-mode email constant (mirrors `AuthRepository.GUEST_EMAIL`). */
    private static final String GUEST_EMAIL_VALUE = "guest@local";

    /**
     * Bug #4: SQLite `AUTOINCREMENT` high-water mark we bump to before
     * re-keying guest plants AND their children. Cloud rows come back
     * with their own (cloud-side) autogen ids — typically well below
     * 10,000 — so any value comfortably above realistic cloud usage
     * works. 1,000,000 is the conservative pick.
     */
    private static final int REKEY_AUTOGEN_OFFSET = 1_000_000;

    /**
     * Bug #4 helper (2026-05-14, extended same day): re-key any guest-mode
     * plants (and their Reminder / Photo / Memo children) so they can
     * coexist with cloud rows after sign-in without colliding on PK,
     * THEN push the re-keyed rows to Firestore so they survive a
     * subsequent sign-out / sign-in cycle (the wipe step at the start
     * of the next importCloudDataForUser would otherwise delete them
     * — they'd be `userEmail = newEmail` rows with no cloud
     * counterpart to restore). See the inline comment at the call site
     * for the full root-cause / strategy write-up.
     *
     * Runs synchronously on the BgExecutor.io thread that called
     * importCloudDataForUser, AFTER the wipe of `WHERE userEmail = email`
     * rows and BEFORE the 9 cloud-import streams kick off. The local
     * re-key is wrapped in `runInTransaction` so observers see only the
     * final state and a mid-loop failure rolls back cleanly. The
     * Firebase sync calls intentionally run AFTER the transaction
     * commits — if Room rolls back, no orphan cloud writes hit
     * Firestore.
     *
     * Trade-offs intentionally accepted:
     *   - Photos are NOT cloud-synced here. Storage upload requires
     *     resolving the local Uri and the upload API
     *     (`uploadPlantPhotoWithPending`) handles a different state
     *     machine than the simple syncPlant/syncReminder calls. A
     *     follow-up task can sync them via the existing photo upload
     *     pipeline. Until then: a guest's photos survive the first
     *     sign-in (they're still on disk + in the local DB), but a
     *     subsequent sign-out / sign-in cycle wipes the entities and
     *     the on-disk files become orphans.
     *   - Firebase syncs are fire-and-forget. Firebase's offline queue
     *     retries until the device is online, but if the user signs
     *     out within milliseconds of sign-in (before the queue
     *     flushes), the writes are lost.
     *   - Rooms aren't promoted (Plant.roomId has no FK constraint, so
     *     a duplicated default room name is the worst that happens —
     *     UX wart, not data loss).
     *
     * No-op when:
     *   - newEmail is null / empty (defensive — should never happen here).
     *   - newEmail equals the guest-mode constant (the user is "signing
     *     in" to guest, not promoting away from it).
     *   - there are no guest plants to migrate (the common case for
     *     users who never used guest mode).
     */
    private static void promoteGuestRowsToUser(Context ctx, String newEmail) {
        if (newEmail == null || newEmail.isEmpty() || GUEST_EMAIL_VALUE.equals(newEmail)) return;

        try {
            AppDatabase db = AppDatabase.getInstance(ctx);
            PlantDao plantDao = db.plantDao();
            ReminderDao reminderDao = db.reminderDao();
            PlantPhotoDao photoDao = db.plantPhotoDao();
            com.example.plantcare.data.journal.JournalMemoDao memoDao = db.journalMemoDao();

            java.util.List<Plant> guestPlants = plantDao.getAllUserPlantsForUser(GUEST_EMAIL_VALUE);
            if (guestPlants == null || guestPlants.isEmpty()) return;

            // Capture the promoted rows so we can sync them to Firestore
            // AFTER the local transaction commits. Effective-final list
            // references; their contents are mutated inside the lambda.
            final java.util.List<Plant> promotedPlants = new java.util.ArrayList<>();
            final java.util.List<WateringReminder> promotedReminders = new java.util.ArrayList<>();
            final java.util.List<com.example.plantcare.data.journal.JournalMemo> promotedMemos = new java.util.ArrayList<>();

            db.runInTransaction(() -> {
                // Step 1: bump the AUTOINCREMENT high-water mark for ALL
                // FOUR tables that take re-keyed rows — `plant` and its
                // three child tables (`reminder`, `plant_photo`,
                // `journal_memo`).
                //
                // Why all four, not just `plant`: the children carry their
                // OWN autogen primary keys. A guest's reminder id=5 would,
                // post-promote, still collide with the cloud account's
                // reminder id=5 on import → ABORT (reminder/memo) or
                // REPLACE (photo) → silent data loss. Re-keying only the
                // plant fixes the headline collision but leaves the child
                // PKs colliding. So we bump every child table's sequence
                // too, then re-INSERT each child with id=0 (Step 2) so it
                // autogens an id >= OFFSET+1, safely above any cloud child
                // id.
                //
                // Each marker is inserted at REKEY_AUTOGEN_OFFSET then
                // deleted; sqlite_sequence retains the high-water mark on
                // delete. The child markers need a valid plantId (FK to
                // Plant), so the plant marker is inserted first and the
                // markers are deleted child-first, plant-marker-last.
                Plant pMarker = new Plant();
                pMarker.id = REKEY_AUTOGEN_OFFSET;
                pMarker.name = "__guest_rekey_marker__";
                pMarker.userEmail = "__guest_rekey_marker__";
                pMarker.isUserPlant = false;
                plantDao.insert(pMarker);

                WateringReminder rMarker = new WateringReminder();
                rMarker.id = REKEY_AUTOGEN_OFFSET;
                rMarker.plantId = REKEY_AUTOGEN_OFFSET;
                rMarker.userEmail = "__guest_rekey_marker__";
                reminderDao.insert(rMarker);

                PlantPhoto phMarker = new PlantPhoto();
                phMarker.id = REKEY_AUTOGEN_OFFSET;
                phMarker.plantId = REKEY_AUTOGEN_OFFSET;
                phMarker.userEmail = "__guest_rekey_marker__";
                photoDao.insert(phMarker);

                com.example.plantcare.data.journal.JournalMemo mMarker =
                        new com.example.plantcare.data.journal.JournalMemo();
                mMarker.setId(REKEY_AUTOGEN_OFFSET);
                mMarker.setPlantId(REKEY_AUTOGEN_OFFSET);
                mMarker.setUserEmail("__guest_rekey_marker__");
                memoDao.insert(mMarker);

                WateringReminder rGhost = new WateringReminder();
                rGhost.id = REKEY_AUTOGEN_OFFSET;
                reminderDao.delete(rGhost);
                PlantPhoto phGhost = new PlantPhoto();
                phGhost.id = REKEY_AUTOGEN_OFFSET;
                photoDao.delete(phGhost);
                com.example.plantcare.data.journal.JournalMemo mGhost =
                        new com.example.plantcare.data.journal.JournalMemo();
                mGhost.setId(REKEY_AUTOGEN_OFFSET);
                memoDao.delete(mGhost);
                Plant pGhost = new Plant();
                pGhost.id = REKEY_AUTOGEN_OFFSET;
                plantDao.delete(pGhost);

                // Step 2: re-key each guest plant AND its children.
                for (Plant gp : guestPlants) {
                    int oldId = gp.id;

                    // Snapshot children BEFORE we touch the parent — we need
                    // their pre-promote plantId to find them.
                    java.util.List<WateringReminder> reminders = reminderDao.getRemindersForPlant(oldId);
                    java.util.List<PlantPhoto> photos = photoDao.getPhotosForPlant(oldId);
                    java.util.List<com.example.plantcare.data.journal.JournalMemo> memos = memoDao.getForPlant(oldId);

                    // Insert the plant copy with id=0 → autogen picks an id
                    // >= REKEY_AUTOGEN_OFFSET + 1. Reattribute to the new
                    // user in the same call.
                    gp.id = 0;
                    gp.userEmail = newEmail;
                    long newIdLong = plantDao.insert(gp);
                    int newId = (int) newIdLong;
                    // Sync the Java field with the persisted state so
                    // post-transaction Firebase calls reference the
                    // correct id. Room doesn't auto-write the autogen
                    // result back to the entity for `int` PKs.
                    gp.id = newId;
                    promotedPlants.add(gp);

                    // Children: re-INSERT with id=0 (fresh autogen PK,
                    // >= OFFSET+1) pointing at the new plant id + new email.
                    // We deliberately do NOT update-in-place — the child's
                    // OWN PK must change to escape the cloud-child id range.
                    // The original child rows still reference oldId and are
                    // swept by the FK CASCADE when the old plant is deleted
                    // below.
                    if (reminders != null) {
                        for (WateringReminder r : reminders) {
                            r.id = 0;
                            r.plantId = newId;
                            r.userEmail = newEmail;
                            // ReminderDao.insert is void — the object keeps
                            // id=0, which is harmless: syncReminder keys the
                            // Firestore doc on plantId+"_"+date (not on
                            // reminder.id), and a future restore re-autogens
                            // the local row from id=0.
                            reminderDao.insert(r);
                            promotedReminders.add(r);
                        }
                    }
                    if (photos != null) {
                        for (PlantPhoto ph : photos) {
                            ph.id = 0;
                            ph.plantId = newId;
                            ph.userEmail = newEmail;
                            photoDao.insert(ph);
                            // Photos are intentionally NOT added to a
                            // promoted list — see the trade-off note in
                            // the helper Javadoc. Local re-key only.
                        }
                    }
                    if (memos != null) {
                        for (com.example.plantcare.data.journal.JournalMemo m : memos) {
                            m.setId(0);
                            m.setPlantId(newId);
                            m.setUserEmail(newEmail);
                            // JournalMemoDao.insert returns the new rowid —
                            // write it back, because syncJournalMemo keys
                            // the Firestore doc on memo.getId() and skips
                            // any memo with id <= 0.
                            long newMemoId = memoDao.insert(m);
                            m.setId((int) newMemoId);
                            promotedMemos.add(m);
                        }
                    }

                    // Delete the original guest plant. Its OLD children
                    // rows still reference oldId, so the FK CASCADE sweeps
                    // them here; the re-inserted copies reference newId and
                    // survive.
                    Plant oldGhost = new Plant();
                    oldGhost.id = oldId;
                    plantDao.delete(oldGhost);
                }
            });

            // After the local transaction commits successfully, push every
            // promoted row to Firestore. Fire-and-forget — Firebase's
            // offline queue handles retries when the device is online.
            // Wrapped per-row in try/catch so a single Firebase error
            // doesn't abort the rest of the sync (mirrors the per-row
            // pattern used elsewhere in this class).
            FirebaseSyncManager fsm = FirebaseSyncManager.get();
            for (Plant gp : promotedPlants) {
                try { fsm.syncPlant(gp); } catch (Throwable t) { com.example.plantcare.CrashReporter.INSTANCE.log(t); }
            }
            for (WateringReminder r : promotedReminders) {
                try { fsm.syncReminder(r); } catch (Throwable t) { com.example.plantcare.CrashReporter.INSTANCE.log(t); }
            }
            for (com.example.plantcare.data.journal.JournalMemo m : promotedMemos) {
                try { fsm.syncJournalMemo(m); } catch (Throwable t) { com.example.plantcare.CrashReporter.INSTANCE.log(t); }
            }
        } catch (Throwable t) {
            com.example.plantcare.CrashReporter.INSTANCE.log(t);
        }
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

    /**
     * Wave 2 — Family Share — also handle invite-accept pushes when the
     * Activity is already in the back stack. Without this, a tap on the
     * push notification would silently re-foreground MainActivity without
     * surfacing the accept dialog.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSharePushIntent(intent);
        // v16 close-out: PlantsInRoomActivity bounces back here with a
        // launchTab extra when the user picks "Add from catalog" inside
        // an empty-room view. The Activity is usually still in the stack
        // (FLAG_ACTIVITY_REORDER_TO_FRONT), so onCreate doesn't re-run
        // — handle the tab switch here too.
        //
        // F2.2: `launchTab` numerics changed — see NAV_INDEX_* constants.
        // PlantsInRoomActivity now sends launchTab=NAV_INDEX_GARDEN (2)
        // plus an `openCatalog=true` hint. F2.4 consumes the hint: after
        // selecting the Garden tab, we immediately launch CatalogActivity
        // as a sub-screen so the user lands exactly where they were
        // heading (catalog with the room context already remembered by
        // QuickAddHelper).
        if (intent != null && intent.hasExtra("launchTab")) {
            int tab = intent.getIntExtra("launchTab", NAV_INDEX_TODAY);
            selectTab(tab);
            intent.removeExtra("launchTab");
        }
        if (intent != null && intent.getBooleanExtra("openCatalog", false)) {
            intent.removeExtra("openCatalog");
            startActivity(new Intent(this,
                    com.example.plantcare.ui.catalog.CatalogActivity.class));
        }
    }

    private void handleSharePushIntent(Intent intent) {
        if (intent == null) return;
        String type = intent.getStringExtra("share_push_type");
        if (type == null) return;
        if ("share_invite".equals(type)) {
            String inviteId = intent.getStringExtra("share_invite_id");
            String fromEmail = intent.getStringExtra("share_from_email");
            String plantName = intent.getStringExtra("share_plant_name");
            if (inviteId == null) return;
            // Strip the extras so we don't re-fire on rotation.
            intent.removeExtra("share_push_type");
            intent.removeExtra("share_invite_id");
            String body;
            if (plantName != null && fromEmail != null) {
                body = getString(R.string.share_invite_dialog_body, fromEmail, plantName);
            } else if (fromEmail != null) {
                body = getString(R.string.share_invite_dialog_body_short, fromEmail);
            } else {
                body = getString(R.string.share_invite_dialog_body_generic);
            }
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.share_invite_dialog_title)
                    .setMessage(body)
                    .setPositiveButton(R.string.share_invite_dialog_accept, (d, w) ->
                            com.example.plantcare.feature.share.ShareInviteManager.INSTANCE
                                    .acceptInvite(inviteId, ok -> {
                                        runOnUiThread(() -> Toast.makeText(this,
                                                ok ? R.string.share_invite_accepted_toast
                                                   : R.string.share_invite_accept_failed_toast,
                                                Toast.LENGTH_SHORT).show());
                                        return kotlin.Unit.INSTANCE;
                                    }))
                    .setNegativeButton(R.string.share_invite_dialog_decline, (d, w) ->
                            com.example.plantcare.feature.share.ShareInviteManager.INSTANCE
                                    .declineInvite(inviteId, ok -> kotlin.Unit.INSTANCE))
                    .show();
        } else if ("share_invite_accepted".equals(type)) {
            String fromEmail = intent.getStringExtra("share_from_email");
            intent.removeExtra("share_push_type");
            String body = (fromEmail != null)
                    ? getString(R.string.share_invite_accepted_owner_body, fromEmail)
                    : getString(R.string.share_invite_accepted_owner_generic);
            Toast.makeText(this, body, Toast.LENGTH_LONG).show();
        } else if ("share_invite_revoked".equals(type)) {
            // Phase 7 (Wave 2 Family Share, 2026-05-14): silent consumption.
            // The Cloud Function `onShareInviteRevoked` already deleted the
            // recipient's pointer doc; the SharedPlantsRepository listener
            // (if running, i.e. SharedPlantsActivity is open) will tick and
            // remove the plant card automatically. No dialog is appropriate
            // here — the recipient didn't initiate this action and a
            // surprise toast would be jarring. Just clear the extras so
            // we don't re-fire on rotation.
            intent.removeExtra("share_push_type");
            intent.removeExtra("share_invite_id");
            intent.removeExtra("share_plant_id");
        }
    }
}