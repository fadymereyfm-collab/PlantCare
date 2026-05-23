package com.example.plantcare;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.DialogFragment;

import com.example.plantcare.format.AppearancePrefs;
import com.example.plantcare.format.AvatarUploader;
import com.example.plantcare.format.LastSyncTracker;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.firestore.FirebaseFirestore;

import com.example.plantcare.feature.vacation.VacationPrefs;
import com.example.plantcare.ui.util.FragmentBg;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

/**
 * Updated:
 *  - On account deletion also deletes remote photos (Firestore + Storage).
 */
public class SettingsDialogFragment extends DialogFragment {

    private EditText editName;
    private TextView textEmail, textProviders;
    private Button buttonSaveName, buttonManageGoogle, buttonLogout, buttonDeleteAccount;

    private View groupChangePassword;
    private EditText editOldPassword, editNewPassword, editConfirmPassword;
    private Button buttonChangePassword;

    private View groupSetPassword;
    private EditText editNewPasswordOnly, editConfirmPasswordOnly;
    private Button buttonSetPassword;

    // Phase 4.1 + 6.4: biometric toggle + sign-out-all button
    private SwitchMaterial switchBiometric;
    private Button buttonSignOutAll;

    private RadioGroup themeRadioGroup;
    private RadioButton radioSystem, radioLight, radioDark;

    // Datenschutz & Daten
    private SwitchMaterial switchAnalytics;
    private SwitchMaterial switchCrashReports;
    private Button buttonExportData;

    // Wave 1: Notifications card
    private Button buttonNotifMorningTime;
    private Button buttonNotifEveningTime;

    // Wave 1: Help & About card
    private Button buttonHelpFaq, buttonHelpFeedback, buttonHelpReplayTutorial,
            buttonHelpRateApp, buttonHelpPrivacy, buttonHelpTerms, buttonHelpLicenses;
    private TextView textAppVersion;

    // Wave 2: search, avatar, bio, appearance extras, family, cloud, NPA, profile vis, per-type notifs
    private EditText editSettingsSearch;
    private TextView textSearchEmpty;
    private LinearLayout settingsRoot;

    private ShapeableImageView imageAvatar;
    private Button buttonAvatarChange, buttonAvatarRemove;
    private EditText editBio;

    private RadioGroup unitsRadioGroup, dateFormatRadioGroup, fontScaleRadioGroup;

    private SwitchMaterial switchProfilePublic, switchAdsPersonalized;
    private SwitchMaterial switchNotifWater, switchNotifFertilize, switchNotifMist,
            switchNotifRepot, switchNotifWeather;

    private TextView textFamilyCount;
    private Button buttonFamilyOpen;
    private TextView textLastSync;

    private ActivityResultLauncher<String> avatarPickerLauncher;

    // Wave 2 pref keys (kept here as constants — single source of truth for both
    // SettingsDialogFragment and any reader-side code like AdManager).
    public static final String KEY_BIO = "user_bio";
    public static final String KEY_PROFILE_PUBLIC = "profile_public";
    public static final String KEY_ADS_PERSONALIZED = "ads_personalized";
    public static final String KEY_NOTIF_TYPE_WATER = "notif_type_water";
    public static final String KEY_NOTIF_TYPE_FERTILIZE = "notif_type_fertilize";
    public static final String KEY_NOTIF_TYPE_MIST = "notif_type_mist";
    public static final String KEY_NOTIF_TYPE_REPOT = "notif_type_repot";
    public static final String KEY_NOTIF_TYPE_WEATHER = "notif_type_weather";

    // Urlaubsmodus
    private TextView vacationStatusText;
    private Button buttonVacationStart, buttonVacationEnd, buttonVacationClear;
    @Nullable private LocalDate pendingVacationStart;
    @Nullable private LocalDate pendingVacationEnd;

    private SharedPreferences prefs;
    private com.example.plantcare.data.repository.AuthRepository authRepo;
    private String email;
    private String displayName;
    private volatile boolean providerGoogle;
    private volatile boolean providerPassword;
    private volatile boolean hasLocalPassword;

    private static final String PREFS_NAME = "prefs";
    private static final String KEY_USER_NAME  = "current_user_name";
    private static final String KEY_THEME_MODE = "theme_mode";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Wave 2: register the gallery picker before STARTED so the launcher
        // is available the moment the user taps "Change photo".
        avatarPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::onAvatarPicked);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_settings, null);

        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        email = EmailContext.current(requireContext());
        displayName = prefs.getString(KEY_USER_NAME, "");

        authRepo = com.example.plantcare.data.repository.AuthRepository
                .getInstance(requireContext().getApplicationContext());

        bindViews(view);
        wireActions();
        loadAccountSectionAsync();
        if (BuildConfig.IS_DEV) {
            view.findViewById(R.id.cardDevOptions).setVisibility(View.VISIBLE);
            view.findViewById(R.id.buttonTestCrash).setOnClickListener(v ->
                    CrashReporter.INSTANCE.log(new RuntimeException("Test crash from dev settings")));
        }

        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(view);
        dialog.setCancelable(true);
        dialog.setOnShowListener(dlg -> {
            Window window = dialog.getWindow();
            if (window != null) {
                int width = (int)(requireContext().getResources().getDisplayMetrics().widthPixels * 0.90f);
                window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            }
        });

        return dialog;
    }

    private void bindViews(View view) {
        ImageButton buttonClose = view.findViewById(R.id.buttonClose);
        buttonClose.setOnClickListener(v -> dismiss());

        editName = view.findViewById(R.id.editName);
        textEmail = view.findViewById(R.id.textEmail);
        textProviders = view.findViewById(R.id.textProviders);
        buttonSaveName = view.findViewById(R.id.buttonSaveName);
        buttonManageGoogle = view.findViewById(R.id.buttonManageGoogle);
        buttonLogout = view.findViewById(R.id.buttonLogout);
        buttonDeleteAccount = view.findViewById(R.id.buttonDeleteAccount);

        groupChangePassword = view.findViewById(R.id.groupChangePassword);
        editOldPassword = view.findViewById(R.id.editOldPassword);
        editNewPassword = view.findViewById(R.id.editNewPassword);
        editConfirmPassword = view.findViewById(R.id.editConfirmPassword);
        buttonChangePassword = view.findViewById(R.id.buttonChangePassword);

        groupSetPassword = view.findViewById(R.id.groupSetPassword);
        editNewPasswordOnly = view.findViewById(R.id.editNewPasswordOnly);
        editConfirmPasswordOnly = view.findViewById(R.id.editConfirmPasswordOnly);
        buttonSetPassword = view.findViewById(R.id.buttonSetPassword);

        // Phase 4.1: biometric toggle.
        switchBiometric = view.findViewById(R.id.switchBiometric);
        if (switchBiometric != null) {
            boolean available = AuthBiometric.isAvailable(requireContext());
            switchBiometric.setEnabled(available);
            switchBiometric.setChecked(available && AuthBiometric.isEnabled(requireContext()));
            switchBiometric.setOnCheckedChangeListener((btn, checked) -> {
                if (checked && !available) {
                    btn.setChecked(false);
                    toast(getString(R.string.auth_biometric_unavailable));
                    return;
                }
                AuthBiometric.setEnabled(requireContext(), checked);
            });
        }

        // Phase 6.4: sign out from every device.
        buttonSignOutAll = view.findViewById(R.id.buttonSignOutAll);
        if (buttonSignOutAll != null) {
            buttonSignOutAll.setOnClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setMessage(R.string.auth_sessions_logout_all_confirm)
                        .setPositiveButton(R.string.auth_sessions_logout_all, (d, w) -> {
                            AuthSessions.signOutAllDevices(requireContext(),
                                    () -> {
                                        toast(getString(R.string.auth_sessions_logout_all_done));
                                        if (getActivity() != null) getActivity().recreate();
                                    },
                                    msg -> toast(msg));
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }

        themeRadioGroup = view.findViewById(R.id.themeRadioGroup);
        radioSystem = view.findViewById(R.id.radioSystem);
        radioLight = view.findViewById(R.id.radioLight);
        radioDark = view.findViewById(R.id.radioDark);

        vacationStatusText = view.findViewById(R.id.vacationStatusText);
        buttonVacationStart = view.findViewById(R.id.buttonVacationStart);
        buttonVacationEnd = view.findViewById(R.id.buttonVacationEnd);
        buttonVacationClear = view.findViewById(R.id.buttonVacationClear);

        switchAnalytics = view.findViewById(R.id.switchAnalytics);
        switchCrashReports = view.findViewById(R.id.switchCrashReports);
        buttonExportData = view.findViewById(R.id.buttonExportData);

        // Wave 1: notifications
        buttonNotifMorningTime = view.findViewById(R.id.buttonNotifMorningTime);
        buttonNotifEveningTime = view.findViewById(R.id.buttonNotifEveningTime);

        // Wave 1: help & about
        buttonHelpFaq = view.findViewById(R.id.buttonHelpFaq);
        buttonHelpFeedback = view.findViewById(R.id.buttonHelpFeedback);
        buttonHelpReplayTutorial = view.findViewById(R.id.buttonHelpReplayTutorial);
        buttonHelpRateApp = view.findViewById(R.id.buttonHelpRateApp);
        buttonHelpPrivacy = view.findViewById(R.id.buttonHelpPrivacy);
        buttonHelpTerms = view.findViewById(R.id.buttonHelpTerms);
        buttonHelpLicenses = view.findViewById(R.id.buttonHelpLicenses);
        textAppVersion = view.findViewById(R.id.textAppVersion);

        // Wave 2: search filter
        editSettingsSearch = view.findViewById(R.id.editSettingsSearch);
        textSearchEmpty = view.findViewById(R.id.textSearchEmpty);
        // The dialog root LinearLayout that holds all cards — used for visibility filtering.
        settingsRoot = (LinearLayout) ((ViewGroup) view.findViewById(R.id.settingsScroll)).getChildAt(0);

        // Wave 2: avatar + bio
        imageAvatar = view.findViewById(R.id.imageAvatar);
        buttonAvatarChange = view.findViewById(R.id.buttonAvatarChange);
        buttonAvatarRemove = view.findViewById(R.id.buttonAvatarRemove);
        editBio = view.findViewById(R.id.editBio);

        // Wave 2: appearance extras
        unitsRadioGroup = view.findViewById(R.id.unitsRadioGroup);
        dateFormatRadioGroup = view.findViewById(R.id.dateFormatRadioGroup);
        fontScaleRadioGroup = view.findViewById(R.id.fontScaleRadioGroup);

        // Wave 2: privacy extras
        switchProfilePublic = view.findViewById(R.id.switchProfilePublic);
        switchAdsPersonalized = view.findViewById(R.id.switchAdsPersonalized);

        // Wave 2: per-type notifs
        switchNotifWater = view.findViewById(R.id.switchNotifWater);
        switchNotifFertilize = view.findViewById(R.id.switchNotifFertilize);
        switchNotifMist = view.findViewById(R.id.switchNotifMist);
        switchNotifRepot = view.findViewById(R.id.switchNotifRepot);
        switchNotifWeather = view.findViewById(R.id.switchNotifWeather);

        // Wave 2: family + cloud
        textFamilyCount = view.findViewById(R.id.textFamilyCount);
        buttonFamilyOpen = view.findViewById(R.id.buttonFamilyOpen);
        textLastSync = view.findViewById(R.id.textLastSync);

        initThemeToggle();
        initVacationSection();
        initPrivacySection();
        initNotificationsSection();
        initHelpSection();
        // Wave 2 inits
        initAvatarAndBio();
        initAppearanceExtras();
        initPerTypeNotifications();
        initFamilyAndCloudSections();
        initSettingsSearch();
    }

    private void initVacationSection() {
        refreshVacationUi();
        buttonVacationStart.setOnClickListener(v -> pickDate(true));
        buttonVacationEnd.setOnClickListener(v -> pickDate(false));
        buttonVacationClear.setOnClickListener(v -> {
            if (email == null) return;
            VacationPrefs.clearVacation(requireContext(), email);
            pendingVacationStart = null;
            pendingVacationEnd = null;
            refreshVacationUi();
            Toast.makeText(getContext(), R.string.vacation_cleared_toast, Toast.LENGTH_SHORT).show();
        });
    }

    private void pickDate(boolean isStart) {
        LocalDate initial = LocalDate.now();
        Calendar cal = Calendar.getInstance();
        cal.set(initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth());
        android.app.DatePickerDialog dlg = new android.app.DatePickerDialog(
                requireContext(),
                (view, year, month, day) -> {
                    LocalDate picked = LocalDate.of(year, month + 1, day);
                    if (isStart) pendingVacationStart = picked;
                    else pendingVacationEnd = picked;
                    commitVacationIfReady();
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        // Vacation in the past is meaningless — the worker would never gate
        // any future reminder. Disable selecting yesterday-or-earlier so the
        // user can't accidentally save a no-op range. Calendar millis (not
        // LocalDate) — DatePicker only understands the legacy API.
        dlg.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dlg.show();
    }

    private void commitVacationIfReady() {
        if (email == null) return;
        // Teilweise Auswahl nur in UI anzeigen.
        if (pendingVacationStart != null && pendingVacationEnd != null) {
            LocalDate s = pendingVacationStart;
            LocalDate e = pendingVacationEnd;
            if (e.isBefore(s)) {
                Toast.makeText(getContext(), R.string.vacation_invalid_range, Toast.LENGTH_SHORT).show();
                pendingVacationEnd = null;
                refreshVacationUi();
                return;
            }
            VacationPrefs.setVacation(requireContext(), email, s, e);
            Toast.makeText(getContext(), R.string.vacation_saved_toast, Toast.LENGTH_SHORT).show();
        }
        refreshVacationUi();
    }

    private void refreshVacationUi() {
        if (email == null) {
            vacationStatusText.setText(R.string.vacation_requires_account);
            return;
        }
        LocalDate start = VacationPrefs.getStart(requireContext(), email);
        LocalDate end = VacationPrefs.getEnd(requireContext(), email);
        if (pendingVacationStart == null) pendingVacationStart = start;
        if (pendingVacationEnd == null) pendingVacationEnd = end;

        // Wave 2: respect the user's date-format choice.
        if (pendingVacationStart != null && pendingVacationEnd != null) {
            vacationStatusText.setText(getString(
                    R.string.vacation_status_set,
                    com.example.plantcare.format.DateFormatter.INSTANCE.format(requireContext(), pendingVacationStart),
                    com.example.plantcare.format.DateFormatter.INSTANCE.format(requireContext(), pendingVacationEnd)
            ));
        } else if (pendingVacationStart != null) {
            vacationStatusText.setText(getString(
                    R.string.vacation_status_start_only,
                    com.example.plantcare.format.DateFormatter.INSTANCE.format(requireContext(), pendingVacationStart)
            ));
        } else {
            vacationStatusText.setText(R.string.vacation_inactive_hint);
        }
    }

    private void initThemeToggle() {
        String savedMode = prefs.getString(KEY_THEME_MODE, "system");

        switch (savedMode) {
            case "light":
                radioLight.setChecked(true);
                break;
            case "dark":
                radioDark.setChecked(true);
                break;
            case "system":
            default:
                radioSystem.setChecked(true);
                break;
        }

        themeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String mode;
            if (checkedId == R.id.radioLight) {
                mode = "light";
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else if (checkedId == R.id.radioDark) {
                mode = "dark";
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                mode = "system";
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            }
            prefs.edit().putString(KEY_THEME_MODE, mode).apply();
        });
    }

    private void initPrivacySection() {
        switchAnalytics.setChecked(ConsentManager.INSTANCE.isAnalyticsEnabled(requireContext()));
        switchAnalytics.setOnCheckedChangeListener((btn, checked) ->
                ConsentManager.INSTANCE.setAnalyticsEnabled(requireContext(), checked));

        if (switchCrashReports != null) {
            switchCrashReports.setChecked(ConsentManager.INSTANCE.isCrashReportsEnabled(requireContext()));
            switchCrashReports.setOnCheckedChangeListener((btn, checked) ->
                    ConsentManager.INSTANCE.setCrashReportsEnabled(requireContext(), checked));
        }

        buttonExportData.setOnClickListener(v -> onExportData());
    }

    /** Wave 1: morning + evening reminder times — closes DEFERRED #6.
     *  Saved hours are read by PlantReminderWorker on every run, so the
     *  next periodic firing already respects the new window. */
    private void initNotificationsSection() {
        if (buttonNotifMorningTime == null || buttonNotifEveningTime == null) return;

        refreshNotifTimeLabels();

        buttonNotifMorningTime.setOnClickListener(v -> showNotifTimePicker(true));
        buttonNotifEveningTime.setOnClickListener(v -> showNotifTimePicker(false));
    }

    private void refreshNotifTimeLabels() {
        int morning = prefs.getInt(PlantReminderWorker.KEY_NOTIF_MORNING_HOUR,
                PlantReminderWorker.DEFAULT_MORNING_HOUR);
        int evening = prefs.getInt(PlantReminderWorker.KEY_NOTIF_EVENING_HOUR,
                PlantReminderWorker.DEFAULT_EVENING_HOUR);
        buttonNotifMorningTime.setText(getString(R.string.settings_notif_time_format, morning));
        buttonNotifEveningTime.setText(getString(R.string.settings_notif_time_format, evening));
    }

    private void showNotifTimePicker(boolean isMorning) {
        int currentHour = isMorning
                ? prefs.getInt(PlantReminderWorker.KEY_NOTIF_MORNING_HOUR,
                        PlantReminderWorker.DEFAULT_MORNING_HOUR)
                : prefs.getInt(PlantReminderWorker.KEY_NOTIF_EVENING_HOUR,
                        PlantReminderWorker.DEFAULT_EVENING_HOUR);

        // We use the standard system TimePickerDialog with 24-hour layout —
        // matches the German market convention. Minutes are forced to :00 on
        // save because the Worker windows are hour-granular.
        new android.app.TimePickerDialog(
                requireContext(),
                (view, hourOfDay, minute) -> {
                    int saved;
                    String prefKey;
                    if (isMorning) {
                        // Clamp to a sane morning band — outside this the
                        // window would overlap evening or never fire.
                        saved = clamp(hourOfDay, 5, 12);
                        prefKey = PlantReminderWorker.KEY_NOTIF_MORNING_HOUR;
                    } else {
                        saved = clamp(hourOfDay, 14, 22);
                        prefKey = PlantReminderWorker.KEY_NOTIF_EVENING_HOUR;
                    }
                    prefs.edit().putInt(prefKey, saved).apply();
                    refreshNotifTimeLabels();
                },
                currentHour,
                0,
                /* is24HourView */ true
        ).show();
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /** Wave 1: Help & About — pure UI + intents. */
    private void initHelpSection() {
        if (textAppVersion != null) {
            textAppVersion.setText(getString(R.string.settings_help_version_value,
                    BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        }

        wireUrlButton(buttonHelpFaq, R.string.support_faq_url);
        wireUrlButton(buttonHelpPrivacy, R.string.consent_privacy_url);
        wireUrlButton(buttonHelpTerms, R.string.consent_terms_url);

        if (buttonHelpFeedback != null) {
            buttonHelpFeedback.setOnClickListener(v -> openFeedbackEmail());
        }
        if (buttonHelpReplayTutorial != null) {
            buttonHelpReplayTutorial.setOnClickListener(v -> replayTutorial());
        }
        if (buttonHelpRateApp != null) {
            buttonHelpRateApp.setOnClickListener(v -> openPlayStoreListing());
        }
        if (buttonHelpLicenses != null) {
            buttonHelpLicenses.setOnClickListener(v -> showOssLicenses());
        }
    }

    private void wireUrlButton(@Nullable Button btn, int urlRes) {
        if (btn == null) return;
        btn.setOnClickListener(v -> {
            String url = getString(urlRes);
            if (url == null || url.isEmpty()) {
                toast(getString(R.string.settings_help_open_failed));
                return;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                CrashReporter.INSTANCE.log(e);
                toast(getString(R.string.settings_help_open_failed));
            }
        });
    }

    private void openFeedbackEmail() {
        String to = getString(R.string.support_feedback_email);
        Uri uri = Uri.parse("mailto:" + to)
                .buildUpon()
                .appendQueryParameter("subject", getString(R.string.settings_help_feedback_subject))
                .appendQueryParameter("body", "\n\n— Version "
                        + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
                .build();
        Intent i = new Intent(Intent.ACTION_SENDTO, uri);
        try {
            startActivity(i);
        } catch (android.content.ActivityNotFoundException e) {
            // Kein Email-Client installiert — Fallback zur Webseite, sonst Hinweis.
            toast(getString(R.string.settings_help_open_failed));
        }
    }

    private void replayTutorial() {
        try {
            Intent i = new Intent(requireContext(),
                    com.example.plantcare.ui.onboarding.OnboardingActivity.class);
            // Ensure a fresh task so the back stack stays clean (user came
            // from Settings — they don't want to be dropped back into it).
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            dismiss();
        } catch (Exception e) {
            CrashReporter.INSTANCE.log(e);
            toast(getString(R.string.settings_help_open_failed));
        }
    }

    private void openPlayStoreListing() {
        String pkg = requireContext().getPackageName();
        Uri marketUri = Uri.parse("market://details?id=" + pkg);
        Intent market = new Intent(Intent.ACTION_VIEW, marketUri);
        // Try the Play Store app first, fall back to web.
        try {
            startActivity(market);
        } catch (android.content.ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
            } catch (Exception ee) {
                CrashReporter.INSTANCE.log(ee);
                toast(getString(R.string.settings_help_open_failed));
            }
        }
    }

    private void showOssLicenses() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_help_licenses_title)
                .setMessage(R.string.settings_help_licenses_body)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void onExportData() {
        buttonExportData.setEnabled(false);
        DataExportManager.INSTANCE.exportAndShare(requireContext(), email, intent -> {
            if (!isAdded()) return null;
            buttonExportData.setEnabled(true);
            if (intent != null) {
                startActivity(Intent.createChooser(intent,
                        getString(R.string.settings_export_data)));
            } else {
                toast(getString(R.string.settings_export_error));
            }
            return null;
        });
    }

    private void wireActions() {
        buttonSaveName.setOnClickListener(v -> onSaveName());
        buttonManageGoogle.setOnClickListener(v -> openGoogleAccount());
        buttonChangePassword.setOnClickListener(v ->
                AuthPasswordDialogs.showChangePassword(requireContext()));
        buttonSetPassword.setOnClickListener(v ->
                AuthPasswordDialogs.showSetPassword(requireContext()));
        buttonLogout.setOnClickListener(v -> onLogout());
        buttonDeleteAccount.setOnClickListener(v -> onDeleteAccount());
        wireProSection();
        wireLanguagePicker();
    }

    private void wireLanguagePicker() {
        View view = getView();
        if (view == null) return;
        RadioGroup group = view.findViewById(R.id.languageRadioGroup);
        RadioButton rbSystem = view.findViewById(R.id.radioLangSystem);
        RadioButton rbDe = view.findViewById(R.id.radioLangDe);
        RadioButton rbEn = view.findViewById(R.id.radioLangEn);
        if (group == null || rbSystem == null || rbDe == null || rbEn == null) return;

        androidx.core.os.LocaleListCompat current =
                androidx.appcompat.app.AppCompatDelegate.getApplicationLocales();
        if (current.isEmpty()) {
            rbSystem.setChecked(true);
        } else {
            String tag = current.toLanguageTags();
            if (tag.startsWith("en")) rbEn.setChecked(true);
            else if (tag.startsWith("de")) rbDe.setChecked(true);
            else rbSystem.setChecked(true);
        }

        group.setOnCheckedChangeListener((g, checkedId) -> {
            androidx.core.os.LocaleListCompat target;
            if (checkedId == R.id.radioLangEn) {
                target = androidx.core.os.LocaleListCompat.forLanguageTags("en");
            } else if (checkedId == R.id.radioLangDe) {
                target = androidx.core.os.LocaleListCompat.forLanguageTags("de");
            } else {
                target = androidx.core.os.LocaleListCompat.getEmptyLocaleList();
            }
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(target);
        });
    }

    private void wireProSection() {
        View view = getView();
        if (view == null) return;
        TextView txtStatus = view.findViewById(R.id.textProStatus);
        Button btnOpen = view.findViewById(R.id.buttonOpenPaywall);
        Button btnRestore = view.findViewById(R.id.buttonRestorePro);
        if (txtStatus == null || btnOpen == null || btnRestore == null) return;

        boolean isPro = com.example.plantcare.billing.ProStatusManager.isPro(requireContext());
        txtStatus.setText(isPro ? R.string.settings_pro_status_active : R.string.settings_pro_status_free);
        btnOpen.setVisibility(isPro ? View.GONE : View.VISIBLE);

        btnOpen.setOnClickListener(v -> {
            new com.example.plantcare.billing.PaywallDialogFragment().show(
                    getParentFragmentManager(),
                    com.example.plantcare.billing.PaywallDialogFragment.TAG);
        });
        btnRestore.setOnClickListener(v -> {
            com.example.plantcare.billing.BillingManager
                    .getInstance(requireContext())
                    .restorePurchasesAsync();
            Toast.makeText(requireContext(), R.string.paywall_restore_done, Toast.LENGTH_SHORT).show();
        });
    }

    private void loadAccountSectionAsync() {
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        providerGoogle = false;
        providerPassword = false;
        if (fbUser != null) {
            for (UserInfo info : fbUser.getProviderData()) {
                if ("google.com".equals(info.getProviderId())) providerGoogle = true;
                if ("password".equals(info.getProviderId())) providerPassword = true;
            }
        }

        editName.setText(displayName);
        // Phase 3.2: append a verification badge after the email so the user
        // sees the state at a glance + can resend the verification mail.
        FirebaseUser fbUserCheck = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUserCheck != null && email != null) {
            String badge = fbUserCheck.isEmailVerified()
                    ? getString(R.string.auth_email_verified_badge)
                    : getString(R.string.auth_email_unverified_badge);
            textEmail.setText(email + "  (" + badge + ")");
            // Long-press email row to resend verification email if still unverified.
            if (!fbUserCheck.isEmailVerified()) {
                textEmail.setOnLongClickListener(v -> {
                    fbUserCheck.sendEmailVerification()
                            .addOnCompleteListener(t -> {
                                if (t.isSuccessful()) {
                                    toast(getString(R.string.auth_email_verify_sent));
                                } else {
                                    String m = t.getException() != null
                                            ? t.getException().getMessage() : "?";
                                    toast(getString(R.string.auth_email_verify_failed, m));
                                }
                            });
                    return true;
                });
            }
        } else {
            textEmail.setText(email != null ? email : "-");
        }

        FragmentBg.runIO(this,
                () -> {
                    User localUser = (email != null) ? authRepo.getUserByEmailBlocking(email) : null;
                    hasLocalPassword = (localUser != null && localUser.passwordHash != null && !localUser.passwordHash.trim().isEmpty());
                },
                () -> {
                    String providerLabel;
                    if (providerGoogle && (providerPassword || hasLocalPassword)) {
                        providerLabel = getString(R.string.settings_provider_google) + " + " + getString(R.string.settings_provider_email);
                    } else if (providerGoogle) {
                        providerLabel = getString(R.string.settings_provider_google);
                    } else if (providerPassword || hasLocalPassword) {
                        providerLabel = getString(R.string.settings_provider_email);
                    } else {
                        providerLabel = getString(R.string.settings_provider_unknown);
                    }
                    textProviders.setText(providerLabel);
                    updateSecurityVisibility();
                    buttonManageGoogle.setVisibility(providerGoogle ? View.VISIBLE : View.GONE);
                });
    }

    private void updateSecurityVisibility() {
        boolean hasPassword = providerPassword || hasLocalPassword;
        groupChangePassword.setVisibility(hasPassword ? View.VISIBLE : View.GONE);
        groupSetPassword.setVisibility(!hasPassword && providerGoogle ? View.VISIBLE : View.GONE);
    }

    private void onSaveName() {
        String newName = editName.getText().toString().trim();
        if (TextUtils.isEmpty(newName)) {
            toast(getString(R.string.settings_enter_name));
            return;
        }
        prefs.edit().putString(KEY_USER_NAME, newName).apply();
        FragmentBg.runIO(this, () -> {
            if (email != null) authRepo.updateUserNameBlocking(email, newName);
        });
        try {
            FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
            if (fbUser != null) {
                UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                        .setDisplayName(newName).build();
                fbUser.updateProfile(req);
            }
        } catch (Exception e) { CrashReporter.INSTANCE.log(e); }
        toast(getString(R.string.settings_name_updated));
    }

    private void openGoogleAccount() {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://myaccount.google.com"));
            startActivity(i);
        } catch (Exception e) {
            toast("Unable to open Google Account settings.");
        }
    }

    private void onLogout() {
        // Phase 1.4: confirm before sign-out — accidental taps used to dump
        // the user back to the auth dialog with no recovery.
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.auth_logout_confirm_title)
                .setMessage(R.string.auth_logout_confirm_message)
                .setPositiveButton(R.string.auth_logout_confirm_yes, (d, w) -> doLogout())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doLogout() {
        Analytics.INSTANCE.logLogout(requireContext());
        try { FirebaseAuth.getInstance().signOut(); } catch (Exception e) { CrashReporter.INSTANCE.log(e); }
        prefs.edit().remove(KEY_USER_NAME).apply();
        UserRepository.get(requireContext()).logout();
        // Wipe BOTH weather state files. weather_prefs holds the cached
        // tip/city/temp (UI-visible immediately); weather_cache holds the
        // raw API response keyed by rounded lat/lon (privacy-sensitive —
        // it's the previous user's home coordinates). Otherwise User A
        // signs out (in Berlin) and User B signs in (in Munich) and
        // sees Berlin's tip until the worker's next 12h cycle, plus
        // weather_cache exposes A's home location to B if they share the
        // device.
        try {
            requireContext()
                    .getSharedPreferences("weather_prefs", android.content.Context.MODE_PRIVATE)
                    .edit().clear().apply();
        } catch (Exception e) { CrashReporter.INSTANCE.log(e); }
        try {
            requireContext()
                    .getSharedPreferences("weather_cache", android.content.Context.MODE_PRIVATE)
                    .edit().clear().apply();
        } catch (Exception e) { CrashReporter.INSTANCE.log(e); }
        // C13: streak + challenge state is per-email, but a shared
        // device handed off to a different user keeps the prior
        // user's "30-day streak" and "5 plants added" trophy on
        // screen until they happen to add a 6th plant. Wipe the
        // signed-out user's entries explicitly so the next account
        // starts clean.
        if (email != null && !email.isEmpty()) {
            try {
                com.example.plantcare.feature.streak.StreakTracker
                        .reset(requireContext(), email);
            } catch (Exception e) { CrashReporter.INSTANCE.log(e); }
            try {
                com.example.plantcare.feature.streak.ChallengeRegistry
                        .reset(requireContext(), email);
            } catch (Exception e) { CrashReporter.INSTANCE.log(e); }
        }
        if (getActivity() != null) getActivity().recreate();
    }

    private void onDeleteAccount() {
        // Phase 2.1: Firebase requires a recent login to delete the user. If
        // the existing session is older than ~5 minutes the call will fail
        // with FirebaseAuthRecentLoginRequiredException and the user sees
        // nothing happen. We pre-empt that by re-authenticating *before*
        // the destructive cascade runs.
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.settings_delete_account))
                .setMessage(getString(R.string.settings_delete_confirm))
                .setPositiveButton(getString(R.string.delete), (d, w) -> requestReauthThenDelete())
                .setNegativeButton(getString(R.string.close), null)
                .show();
    }

    /**
     * Phase 2.1: gate the destructive flow behind a re-authentication step.
     * Three branches:
     *   1. No Firebase user (legacy local-only) → cascade local delete only.
     *   2. Email/password provider → prompt for current password.
     *   3. Google provider → re-launch a Google credential picker.
     */
    private void requestReauthThenDelete() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            performLocalDelete();
            return;
        }

        // Pick the strongest available provider. If the user has both,
        // prefer password since it's a single in-app dialog.
        boolean hasPassword = false;
        boolean hasGoogle = false;
        for (UserInfo info : user.getProviderData()) {
            if ("password".equals(info.getProviderId())) hasPassword = true;
            else if ("google.com".equals(info.getProviderId())) hasGoogle = true;
        }

        if (hasPassword) {
            promptPasswordReauth(user);
        } else if (hasGoogle) {
            // Audit fix #2 (2026-05-06): Google reauth previously fell straight
            // into performFirebaseDelete — which would throw
            // FirebaseAuthRecentLoginRequiredException on any session older
            // than ~5 min. Replaced with the standard Google credential
            // re-prompt: sign the user out, send them to the auth chooser
            // with a flag so we can resume the delete after they re-sign-in.
            //
            // Implementation: persist a "pending delete" marker in
            // SecurePrefs, sign out (which kicks them to AuthStartDialog
            // on next start), and the next time they sign in via Google we
            // resume the delete from MainActivity.onCreate.
            persistPendingDelete();
            toast(getString(R.string.auth_reauth_google_required));
            try { FirebaseAuth.getInstance().signOut(); } catch (Exception e) { CrashReporter.INSTANCE.log(e); }
            if (getActivity() != null) getActivity().recreate();
        } else {
            // No known provider (legacy local-only) — local cascade only.
            performLocalDelete();
        }
    }

    /** Audit fix #2: write a "delete pending re-auth" marker to SecurePrefs.
     *  MainActivity.onCreate checks it after every successful sign-in and
     *  resumes the delete if the same email signs in. */
    private void persistPendingDelete() {
        if (email == null) return;
        SecurePrefsHelper.INSTANCE.get(requireContext()).edit()
                .putString("pending_delete_email", email)
                .apply();
    }

    private void promptPasswordReauth(FirebaseUser user) {
        final EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.auth_reauth_password_hint);

        FrameLayout container = new FrameLayout(requireContext());
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.auth_reauth_required_title)
                .setMessage(R.string.auth_reauth_required_message)
                .setView(container)
                .setPositiveButton(R.string.auth_reauth_confirm, (d, w) -> {
                    String pwd = input.getText() != null ? input.getText().toString() : "";
                    if (pwd.isEmpty() || user.getEmail() == null) {
                        toast(getString(R.string.auth_reauth_failed));
                        return;
                    }
                    com.google.firebase.auth.AuthCredential cred =
                            com.google.firebase.auth.EmailAuthProvider
                                    .getCredential(user.getEmail(), pwd);
                    user.reauthenticate(cred).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            performFirebaseDelete(user);
                        } else {
                            toast(getString(R.string.auth_reauth_failed));
                        }
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void performFirebaseDelete(FirebaseUser user) {
        FragmentBg.runIO(this,
                () -> {
                    if (email != null) {
                        try { FirebaseSyncManager.get().deleteAllPhotosForUser(email); } catch (Throwable e) { CrashReporter.INSTANCE.log(e); }
                        Context appCtx = requireContext().getApplicationContext();
                        com.example.plantcare.data.repository.ReminderRepository
                                .getInstance(appCtx).deleteAllRemindersForUserBlocking(email);
                        com.example.plantcare.data.repository.PlantRepository
                                .getInstance(appCtx).deleteAllUserPlantsForUserBlocking(email);
                        com.example.plantcare.data.repository.PlantPhotoRepository
                                .getInstance(appCtx).deleteAllPhotosForUserBlocking(email);
                        authRepo.deleteUserByEmailBlocking(email);
                    }
                },
                () -> user.delete().addOnCompleteListener(t -> {
                    if (t.isSuccessful()) {
                        finishDelete();
                    } else {
                        toast(getString(R.string.auth_reauth_failed));
                    }
                }));
    }

    private void performLocalDelete() {
        FragmentBg.runIO(this,
                () -> {
                    if (email != null) {
                        Context appCtx = requireContext().getApplicationContext();
                        com.example.plantcare.data.repository.ReminderRepository
                                .getInstance(appCtx).deleteAllRemindersForUserBlocking(email);
                        com.example.plantcare.data.repository.PlantRepository
                                .getInstance(appCtx).deleteAllUserPlantsForUserBlocking(email);
                        com.example.plantcare.data.repository.PlantPhotoRepository
                                .getInstance(appCtx).deleteAllPhotosForUserBlocking(email);
                        authRepo.deleteUserByEmailBlocking(email);
                    }
                },
                this::finishDelete);
    }

    private void finishDelete() {
        if (getContext() != null) Analytics.INSTANCE.logAccountDeleted(requireContext());
        prefs.edit().clear().apply();
        toast(getString(R.string.settings_account_deleted));
        if (getActivity() != null) getActivity().recreate();
    }

    /* ──────────────────────────── Wave 2 ──────────────────────────── */

    /** Wave 2: Bio + Avatar wiring. Bio persists on every keystroke (debounced
     *  via TextWatcher → SharedPreferences); avatar uses a gallery picker. */
    private void initAvatarAndBio() {
        // Bio — read existing + persist on edit.
        if (editBio != null) {
            editBio.setText(prefs.getString(KEY_BIO, ""));
            editBio.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    prefs.edit().putString(KEY_BIO, s == null ? "" : s.toString()).apply();
                }
            });
        }

        // Avatar — load existing local file if present, otherwise leave the
        // placeholder drawable.
        refreshAvatarPreview();

        if (buttonAvatarChange != null) {
            buttonAvatarChange.setOnClickListener(v -> {
                try {
                    avatarPickerLauncher.launch("image/*");
                } catch (Exception e) {
                    CrashReporter.INSTANCE.log(e);
                    toast(getString(R.string.settings_avatar_pick_failed));
                }
            });
        }
        if (buttonAvatarRemove != null) {
            buttonAvatarRemove.setOnClickListener(v -> {
                if (email != null) AvatarUploader.INSTANCE.clearLocalAvatar(requireContext(), email);
                refreshAvatarPreview();
            });
        }
    }

    private void refreshAvatarPreview() {
        if (imageAvatar == null) return;
        if (email == null) return;
        java.io.File f = AvatarUploader.INSTANCE.localFile(requireContext(), email);
        if (f.exists() && f.length() > 0) {
            // Glide handles decoding off the main thread, applies the
            // ShapeableImageView's circle crop on top of the bitmap, and
            // caches the decoded result. Skip-memory-cache=false keeps
            // re-opens of the dialog snappy.
            imageAvatar.setPadding(0, 0, 0, 0);
            com.bumptech.glide.Glide.with(this)
                    .load(f)
                    .signature(new com.bumptech.glide.signature.ObjectKey(f.lastModified()))
                    .placeholder(R.drawable.ic_settings_black)
                    .error(R.drawable.ic_settings_black)
                    .centerCrop()
                    .into(imageAvatar);
            if (buttonAvatarRemove != null) buttonAvatarRemove.setVisibility(View.VISIBLE);
            return;
        }
        // Fallback to placeholder.
        com.bumptech.glide.Glide.with(this).clear(imageAvatar);
        imageAvatar.setImageResource(R.drawable.ic_settings_black);
        int padPx = (int) (14 * getResources().getDisplayMetrics().density);
        imageAvatar.setPadding(padPx, padPx, padPx, padPx);
        if (buttonAvatarRemove != null) buttonAvatarRemove.setVisibility(View.GONE);
    }

    private void onAvatarPicked(@Nullable Uri pickedUri) {
        if (pickedUri == null || email == null) return;
        FragmentBg.runIO(this,
                () -> AvatarUploader.INSTANCE.saveLocalAndUpload(requireContext(), pickedUri, email),
                this::refreshAvatarPreview);
    }

    /** Wave 2: Units / Date format / Font scale RadioGroups. Font scale recreates
     *  the host Activity so the new Configuration takes effect. */
    private void initAppearanceExtras() {
        if (unitsRadioGroup != null) {
            AppearancePrefs.Units u = AppearancePrefs.INSTANCE.getUnits(requireContext());
            (u == AppearancePrefs.Units.IMPERIAL
                    ? (RadioButton) unitsRadioGroup.findViewById(R.id.radioUnitsImperial)
                    : (RadioButton) unitsRadioGroup.findViewById(R.id.radioUnitsMetric))
                    .setChecked(true);
            unitsRadioGroup.setOnCheckedChangeListener((g, id) -> {
                AppearancePrefs.Units pick = (id == R.id.radioUnitsImperial)
                        ? AppearancePrefs.Units.IMPERIAL
                        : AppearancePrefs.Units.METRIC;
                AppearancePrefs.INSTANCE.setUnits(requireContext(), pick);
            });
        }

        if (dateFormatRadioGroup != null) {
            AppearancePrefs.DateFormat fmt = AppearancePrefs.INSTANCE.getDateFormat(requireContext());
            int rid;
            switch (fmt) {
                case DE: rid = R.id.radioDateDe; break;
                case US: rid = R.id.radioDateUs; break;
                default: rid = R.id.radioDateIso; break;
            }
            ((RadioButton) dateFormatRadioGroup.findViewById(rid)).setChecked(true);
            dateFormatRadioGroup.setOnCheckedChangeListener((g, id) -> {
                AppearancePrefs.DateFormat pick;
                if (id == R.id.radioDateDe) pick = AppearancePrefs.DateFormat.DE;
                else if (id == R.id.radioDateUs) pick = AppearancePrefs.DateFormat.US;
                else pick = AppearancePrefs.DateFormat.ISO;
                AppearancePrefs.INSTANCE.setDateFormat(requireContext(), pick);
            });
        }

        if (fontScaleRadioGroup != null) {
            AppearancePrefs.FontScale fs = AppearancePrefs.INSTANCE.getFontScale(requireContext());
            int rid;
            switch (fs) {
                case SMALL: rid = R.id.radioFontSmall; break;
                case LARGE: rid = R.id.radioFontLarge; break;
                case EXTRA_LARGE: rid = R.id.radioFontXLarge; break;
                default: rid = R.id.radioFontNormal; break;
            }
            ((RadioButton) fontScaleRadioGroup.findViewById(rid)).setChecked(true);
            fontScaleRadioGroup.setOnCheckedChangeListener((g, id) -> {
                AppearancePrefs.FontScale pick;
                if (id == R.id.radioFontSmall) pick = AppearancePrefs.FontScale.SMALL;
                else if (id == R.id.radioFontLarge) pick = AppearancePrefs.FontScale.LARGE;
                else if (id == R.id.radioFontXLarge) pick = AppearancePrefs.FontScale.EXTRA_LARGE;
                else pick = AppearancePrefs.FontScale.NORMAL;
                AppearancePrefs.INSTANCE.setFontScale(requireContext(), pick);
                // Activity recreate — base Context wraps fresh fontScale.
                if (getActivity() != null) getActivity().recreate();
            });
        }
    }

    /** Wave 2: per-type notifications + visibility + NPA. */
    private void initPerTypeNotifications() {
        bindNotifTypeSwitch(switchNotifWater, KEY_NOTIF_TYPE_WATER, true);
        bindNotifTypeSwitch(switchNotifFertilize, KEY_NOTIF_TYPE_FERTILIZE, true);
        bindNotifTypeSwitch(switchNotifMist, KEY_NOTIF_TYPE_MIST, true);
        bindNotifTypeSwitch(switchNotifRepot, KEY_NOTIF_TYPE_REPOT, true);
        bindNotifTypeSwitch(switchNotifWeather, KEY_NOTIF_TYPE_WEATHER, true);

        if (switchProfilePublic != null) {
            switchProfilePublic.setChecked(prefs.getBoolean(KEY_PROFILE_PUBLIC, false));
            switchProfilePublic.setOnCheckedChangeListener((btn, checked) -> {
                prefs.edit().putBoolean(KEY_PROFILE_PUBLIC, checked).apply();
                mirrorProfileVisibilityToFirestore(checked);
            });
        }

        if (switchAdsPersonalized != null) {
            // Default on — historical behaviour. Off = NPA flag set on AdRequest.
            switchAdsPersonalized.setChecked(prefs.getBoolean(KEY_ADS_PERSONALIZED, true));
            switchAdsPersonalized.setOnCheckedChangeListener((btn, checked) ->
                    prefs.edit().putBoolean(KEY_ADS_PERSONALIZED, checked).apply());
        }
    }

    private void bindNotifTypeSwitch(@Nullable SwitchMaterial sw, String key, boolean defaultOn) {
        if (sw == null) return;
        sw.setChecked(prefs.getBoolean(key, defaultOn));
        sw.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(key, checked).apply());
    }

    /** Best-effort mirror to /users/{uid}/profile/visibility — value used by
     *  the public-facing surfaces (none today; the field is here for the
     *  upcoming community/share epic). Failures are non-fatal. */
    private void mirrorProfileVisibilityToFirestore(boolean isPublic) {
        com.google.firebase.auth.FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("public", isPublic);
        data.put("updated_at", com.google.firebase.firestore.FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("profile").document("visibility")
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener(CrashReporter.INSTANCE::log);
    }

    /** Wave 2: Family Share entry + last-sync display. */
    private void initFamilyAndCloudSections() {
        if (textFamilyCount != null) {
            textFamilyCount.setText(getString(R.string.settings_family_count, 0));
            // Real count requires a per-user query against Plant.sharedWith;
            // running here would touch DB on the main thread. Defer to IO.
            FragmentBg.<Integer>runWithResult(this,
                    () -> {
                        if (email == null) return 0;
                        try {
                            java.util.List<Plant> plants = com.example.plantcare.data.repository
                                    .PlantRepository.getInstance(requireContext().getApplicationContext())
                                    .getAllUserPlantsForUserBlocking(email);
                            java.util.Set<String> uniqueShared = new java.util.HashSet<>();
                            if (plants != null) for (Plant p : plants) {
                                uniqueShared.addAll(com.example.plantcare.feature.share.FamilyShareManager
                                        .getSharedEmails(p));
                            }
                            return uniqueShared.size();
                        } catch (Throwable t) {
                            CrashReporter.INSTANCE.log(t);
                            return 0;
                        }
                    },
                    n -> {
                        if (textFamilyCount != null) {
                            textFamilyCount.setText(getString(R.string.settings_family_count, n == null ? 0 : n));
                        }
                    });
        }

        if (buttonFamilyOpen != null) {
            buttonFamilyOpen.setOnClickListener(v -> showFamilyShareOverview());
        }

        refreshLastSyncLabel();
    }

    /**
     * Wave 2: real Family Share overview — lists every co-waterer the user
     * has across all their plants. Tapping a row navigates to the per-plant
     * detail dialog where add/remove already lives. Cloud Functions
     * invitations are still on the roadmap (DEFERRED #13) but this surface
     * already exposes the full local share graph honestly.
     */
    private void showFamilyShareOverview() {
        if (email == null) {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(R.string.settings_section_family)
                    .setMessage(R.string.vacation_requires_account)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        FragmentBg.<java.util.List<Plant>>runWithResult(this,
                () -> com.example.plantcare.data.repository
                        .PlantRepository.getInstance(requireContext().getApplicationContext())
                        .getAllUserPlantsForUserBlocking(email),
                plants -> {
                    if (plants == null || plants.isEmpty()) {
                        toast(getString(R.string.settings_family_pending_body));
                        return;
                    }
                    // Build email → list of plant names map.
                    java.util.Map<String, java.util.List<String>> graph = new java.util.TreeMap<>();
                    for (Plant p : plants) {
                        for (String shared : com.example.plantcare.feature.share
                                .FamilyShareManager.getSharedEmails(p)) {
                            graph.computeIfAbsent(shared, k -> new java.util.ArrayList<>())
                                    .add(p.name == null ? "—" : p.name);
                        }
                    }
                    if (graph.isEmpty()) {
                        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle(R.string.settings_section_family)
                                .setMessage(R.string.settings_family_pending_body)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                        return;
                    }
                    // Render as "email\n  • plant1\n  • plant2" entries.
                    StringBuilder body = new StringBuilder();
                    for (java.util.Map.Entry<String, java.util.List<String>> e : graph.entrySet()) {
                        body.append(e.getKey()).append('\n');
                        for (String pn : e.getValue()) {
                            body.append("   • ").append(pn).append('\n');
                        }
                        body.append('\n');
                    }
                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle(R.string.settings_section_family)
                            .setMessage(body.toString().trim())
                            .setPositiveButton(android.R.string.ok, null)
                            .setNeutralButton(R.string.settings_family_pending_title, (d, w) ->
                                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                            .setTitle(R.string.settings_family_pending_title)
                                            .setMessage(R.string.settings_family_pending_body)
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show())
                            .show();
                });
    }

    private void refreshLastSyncLabel() {
        if (textLastSync == null) return;
        long ts = LastSyncTracker.lastSyncMillis(requireContext());
        if (ts <= 0L) {
            textLastSync.setText(R.string.settings_cloud_last_sync_never);
            return;
        }
        long deltaMs = System.currentTimeMillis() - ts;
        long mins = deltaMs / 60_000L;
        if (mins < 1) {
            textLastSync.setText(R.string.settings_cloud_last_sync_just_now);
        } else if (mins < 60) {
            textLastSync.setText(getString(R.string.settings_cloud_last_sync_relative_min, (int) mins));
        } else if (mins < 60 * 24) {
            textLastSync.setText(getString(R.string.settings_cloud_last_sync_relative_hour, (int) (mins / 60)));
        } else {
            textLastSync.setText(getString(R.string.settings_cloud_last_sync_relative_day, (int) (mins / (60 * 24))));
        }
    }

    /** Wave 2: settings search bar — filters the top-level MaterialCardView
     *  children by the section title's text contains-match. */
    private void initSettingsSearch() {
        if (editSettingsSearch == null || settingsRoot == null) return;
        editSettingsSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                applySearchFilter(s == null ? "" : s.toString().trim().toLowerCase());
            }
        });
    }

    private void applySearchFilter(String query) {
        if (settingsRoot == null) return;
        boolean anyMatch = query.isEmpty();
        for (int i = 0; i < settingsRoot.getChildCount(); i++) {
            View child = settingsRoot.getChildAt(i);
            // Skip non-card scaffold (close button, search field, empty state).
            if (!(child instanceof com.google.android.material.card.MaterialCardView)) continue;
            if (query.isEmpty()) {
                child.setVisibility(View.VISIBLE);
                continue;
            }
            // Walk the card looking for a TextView whose text contains the query.
            boolean match = cardMatchesQuery(child, query);
            child.setVisibility(match ? View.VISIBLE : View.GONE);
            if (match) anyMatch = true;
        }
        if (textSearchEmpty != null) {
            textSearchEmpty.setVisibility(anyMatch ? View.GONE : View.VISIBLE);
        }
    }

    private boolean cardMatchesQuery(View card, String query) {
        if (card instanceof TextView) {
            CharSequence t = ((TextView) card).getText();
            return t != null && t.toString().toLowerCase().contains(query);
        }
        if (card instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) card;
            for (int i = 0; i < vg.getChildCount(); i++) {
                if (cardMatchesQuery(vg.getChildAt(i), query)) return true;
            }
        }
        return false;
    }

    private void toast(String msg) {
        if (getContext() != null)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }
}