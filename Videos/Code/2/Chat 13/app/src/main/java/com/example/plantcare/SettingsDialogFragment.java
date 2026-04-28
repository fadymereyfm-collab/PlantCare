package com.example.plantcare;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.DialogFragment;

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

    private RadioGroup themeRadioGroup;
    private RadioButton radioSystem, radioLight, radioDark;

    // Datenschutz & Daten
    private SwitchMaterial switchAnalytics;
    private Button buttonExportData;

    // Urlaubsmodus
    private TextView vacationStatusText;
    private Button buttonVacationStart, buttonVacationEnd, buttonVacationClear;
    @Nullable private LocalDate pendingVacationStart;
    @Nullable private LocalDate pendingVacationEnd;

    private SharedPreferences prefs;
    private UserDao userDao;
    private String email;
    private String displayName;
    private volatile boolean providerGoogle;
    private volatile boolean providerPassword;
    private volatile boolean hasLocalPassword;

    private static final String PREFS_NAME = "prefs";
    private static final String KEY_USER_NAME  = "current_user_name";
    private static final String KEY_THEME_MODE = "theme_mode";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_settings, null);

        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        email = EmailContext.current(requireContext());
        displayName = prefs.getString(KEY_USER_NAME, "");

        userDao = AppDatabase.getInstance(requireContext().getApplicationContext()).userDao();

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

        themeRadioGroup = view.findViewById(R.id.themeRadioGroup);
        radioSystem = view.findViewById(R.id.radioSystem);
        radioLight = view.findViewById(R.id.radioLight);
        radioDark = view.findViewById(R.id.radioDark);

        vacationStatusText = view.findViewById(R.id.vacationStatusText);
        buttonVacationStart = view.findViewById(R.id.buttonVacationStart);
        buttonVacationEnd = view.findViewById(R.id.buttonVacationEnd);
        buttonVacationClear = view.findViewById(R.id.buttonVacationClear);

        switchAnalytics = view.findViewById(R.id.switchAnalytics);
        buttonExportData = view.findViewById(R.id.buttonExportData);

        initThemeToggle();
        initVacationSection();
        initPrivacySection();
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

        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        if (pendingVacationStart != null && pendingVacationEnd != null) {
            vacationStatusText.setText(getString(
                    R.string.vacation_status_set,
                    pendingVacationStart.format(fmt),
                    pendingVacationEnd.format(fmt)
            ));
        } else if (pendingVacationStart != null) {
            vacationStatusText.setText(getString(
                    R.string.vacation_status_start_only,
                    pendingVacationStart.format(fmt)
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
                ConsentManager.INSTANCE.setConsent(requireContext(), checked));

        buttonExportData.setOnClickListener(v -> onExportData());
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
        buttonChangePassword.setOnClickListener(v -> { /* placeholder */ });
        buttonSetPassword.setOnClickListener(v -> { /* placeholder */ });
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
        textEmail.setText(email != null ? email : "-");

        FragmentBg.runIO(this,
                () -> {
                    User localUser = (email != null) ? userDao.getUserByEmail(email) : null;
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
            if (email != null) userDao.updateUserName(email, newName);
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
        Analytics.INSTANCE.logLogout(requireContext());
        try { FirebaseAuth.getInstance().signOut(); } catch (Exception e) { CrashReporter.INSTANCE.log(e); }
        prefs.edit().remove(KEY_USER_NAME).apply();
        UserRepository.get(requireContext()).logout();
        if (getActivity() != null) getActivity().recreate();
    }

    private void onDeleteAccount() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.settings_delete_account))
                .setMessage(getString(R.string.settings_delete_confirm))
                .setPositiveButton(getString(R.string.delete), (d, w) -> {
                    FragmentBg.runIO(this,
                            () -> {
                                if (email != null) {
                                    // Remote photo deletion first (to prevent orphaned cloud data)
                                    try { FirebaseSyncManager.get().deleteAllPhotosForUser(email); } catch (Throwable e) { CrashReporter.INSTANCE.log(e); }
                                    AppDatabase db = AppDatabase.getInstance(requireContext().getApplicationContext());
                                    db.reminderDao().deleteAllRemindersForUser(email);
                                    db.plantDao().deleteAllUserPlantsForUser(email);
                                    db.plantPhotoDao().deleteAllPhotosForUser(email);
                                    db.userDao().deleteUserByEmail(email);
                                }
                            },
                            () -> {
                                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                                if (user != null) {
                                    user.delete().addOnCompleteListener(t -> finishDelete());
                                } else {
                                    finishDelete();
                                }
                            });
                })
                .setNegativeButton(getString(R.string.close), null)
                .show();
    }

    private void finishDelete() {
        if (getContext() != null) Analytics.INSTANCE.logAccountDeleted(requireContext());
        prefs.edit().clear().apply();
        toast(getString(R.string.settings_account_deleted));
        if (getActivity() != null) getActivity().recreate();
    }

    private void toast(String msg) {
        if (getContext() != null)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }
}