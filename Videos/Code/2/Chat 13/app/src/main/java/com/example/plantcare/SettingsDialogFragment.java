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
import android.widget.FrameLayout;
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

    // Phase 4.1 + 6.4: biometric toggle + sign-out-all button
    private SwitchMaterial switchBiometric;
    private Button buttonSignOutAll;

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
    private com.example.plantcare.data.repository.AuthRepository authRepo;
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

    private void toast(String msg) {
        if (getContext() != null)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }
}