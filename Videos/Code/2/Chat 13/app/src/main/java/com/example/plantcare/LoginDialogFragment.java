package com.example.plantcare;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.plantcare.ui.util.FragmentBg;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;

/**
 * حوار تسجيل الدخول / إنشاء حساب بالبريد عبر FirebaseAuth.
 * - يحافظ على نفس الواجهة لديك (dialog_login.xml) ونفس مفاتيح العرض.
 * - يرسل "auth_result" بعد النجاح لتحديث MainActivity.
 */
public class LoginDialogFragment extends DialogFragment {

    private static final String ARG_MODE = "mode";
    public static final String MODE_LOGIN = "login";
    public static final String MODE_REGISTER = "register";

    private TextInputLayout layoutEmail;
    private TextInputLayout layoutPassword;
    private TextInputLayout layoutConfirm;
    private TextInputEditText editEmail;
    private TextInputEditText editPassword;
    private TextInputEditText editConfirm;
    private TextView textError;
    private TextView textTitle;
    private TextView textModeToggle;
    private MaterialButton buttonPrimary;
    private ProgressBar progress;

    // Phase 1.3: strength meter views
    private View strengthRow;
    private View strengthBar1, strengthBar2, strengthBar3;
    private TextView strengthLabel;
    // Phase 2.2: forgot password link (added below buttonPrimary at runtime)
    private TextView textForgotPassword;

    private boolean registerMode = false;

    public static LoginDialogFragment newInstance() {
        return new LoginDialogFragment();
    }

    public static LoginDialogFragment newInstance(String mode) {
        LoginDialogFragment f = new LoginDialogFragment();
        Bundle b = new Bundle();
        if (MODE_REGISTER.equalsIgnoreCase(mode)) {
            b.putString(ARG_MODE, MODE_REGISTER);
        } else {
            b.putString(ARG_MODE, MODE_LOGIN);
        }
        f.setArguments(b);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        if (getArguments() != null) {
            String mode = getArguments().getString(ARG_MODE, MODE_LOGIN);
            registerMode = MODE_REGISTER.equalsIgnoreCase(mode);
        }

        LayoutInflater inflater = LayoutInflater.from(getContext());
        View v = inflater.inflate(R.layout.dialog_login, null);

        bindViews(v);
        setupModeToggle();
        setupPrimaryButton();
        setupCloseButton(v);

        applyModeUI(registerMode);

        Dialog dialog = new Dialog(requireContext(), getTheme());
        dialog.setContentView(v);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog d = getDialog();
        if (d != null) {
            Window w = d.getWindow();
            if (w != null) {
                int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9f);
                w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }
    }

    private void bindViews(View v) {
        layoutEmail = v.findViewById(R.id.layoutEmail);
        layoutPassword = v.findViewById(R.id.layoutPassword);
        layoutConfirm = v.findViewById(R.id.layoutConfirm);
        editEmail = v.findViewById(R.id.editEmail);
        editPassword = v.findViewById(R.id.editPassword);
        editConfirm = v.findViewById(R.id.editConfirm);
        textError = v.findViewById(R.id.textError);
        textTitle = v.findViewById(R.id.textTitle);
        textModeToggle = v.findViewById(R.id.textModeToggle);
        buttonPrimary = v.findViewById(R.id.buttonPrimary);
        progress = v.findViewById(R.id.progress);
        textForgotPassword = v.findViewById(R.id.textForgotPassword);
        if (textForgotPassword != null) {
            textForgotPassword.setOnClickListener(view -> showForgotPasswordDialog());
        }

        if (editPassword != null) {
            editPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        if (editConfirm != null) {
            editConfirm.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }

        // Phase 1.3: strength meter — only shown in register mode.
        strengthRow = v.findViewById(R.id.strengthRow);
        strengthBar1 = v.findViewById(R.id.strengthBar1);
        strengthBar2 = v.findViewById(R.id.strengthBar2);
        strengthBar3 = v.findViewById(R.id.strengthBar3);
        strengthLabel = v.findViewById(R.id.strengthLabel);
        if (editPassword != null && strengthRow != null) {
            editPassword.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateStrengthMeter(s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }
    }

    /** Phase 1.3: 3-bar strength meter, only meaningful in register mode. */
    private void updateStrengthMeter(String password) {
        if (strengthRow == null) return;
        if (!registerMode || password.isEmpty()) {
            strengthRow.setVisibility(View.GONE);
            return;
        }
        strengthRow.setVisibility(View.VISIBLE);
        int level = AuthValidation.passwordStrength(password);
        int filled = level == 0 ? 1 : (level == 1 ? 2 : 3);
        int colorActive = level == 0 ? getResources().getColor(R.color.pc_error, null)
                          : level == 1 ? android.graphics.Color.parseColor("#E0A41A")
                          : getResources().getColor(R.color.pc_primary, null);
        int colorIdle = getResources().getColor(R.color.pc_outline, null);

        strengthBar1.setBackgroundColor(filled >= 1 ? colorActive : colorIdle);
        strengthBar2.setBackgroundColor(filled >= 2 ? colorActive : colorIdle);
        strengthBar3.setBackgroundColor(filled >= 3 ? colorActive : colorIdle);

        int labelRes = level == 0 ? R.string.auth_pw_strength_weak
                       : level == 1 ? R.string.auth_pw_strength_medium
                       : R.string.auth_pw_strength_strong;
        strengthLabel.setText(labelRes);
        strengthLabel.setTextColor(colorActive);
    }

    private void setupModeToggle() {
        if (textModeToggle != null) {
            textModeToggle.setOnClickListener(view -> {
                registerMode = !registerMode;
                applyModeUI(registerMode);
            });
        }
    }

    private void setupPrimaryButton() {
        buttonPrimary.setOnClickListener(v -> {
            clearError();
            if (!validateInputs()) return;
            performAuth();
        });
    }

    private void setupCloseButton(View root) {
        ImageButton close = root.findViewById(R.id.buttonClose);
        if (close != null) {
            close.setOnClickListener(v -> dismissAllowingStateLoss());
        }
    }

    private void applyModeUI(boolean isRegister) {
        if (isRegister) {
            layoutConfirm.setVisibility(View.VISIBLE);
            textTitle.setText(R.string.auth_sign_up);
            buttonPrimary.setText(R.string.onboarding_create_account);
            if (textModeToggle != null) textModeToggle.setText(R.string.login_already_have_account);
            if (textForgotPassword != null) textForgotPassword.setVisibility(View.GONE);
        } else {
            layoutConfirm.setVisibility(View.GONE);
            textTitle.setText(R.string.auth_sign_in);
            buttonPrimary.setText(R.string.auth_sign_in);
            if (textModeToggle != null) textModeToggle.setText(R.string.login_new_user_register);
            if (textForgotPassword != null) textForgotPassword.setVisibility(View.VISIBLE);
        }
        clearError();
        if (strengthRow != null) strengthRow.setVisibility(View.GONE);
        if (editPassword != null) editPassword.setText(null);
        if (editConfirm != null) editConfirm.setText(null);
    }

    /** Phase 2.2: Firebase password reset — pre-fills the email field if
     *  the user already typed it in. Friendly success toast, error in the
     *  inline error view so it doesn't disappear. */
    private void showForgotPasswordDialog() {
        final EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        if (editEmail != null && editEmail.getText() != null) {
            input.setText(editEmail.getText().toString().trim());
        }

        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.auth_forgot_password_title)
                .setMessage(R.string.auth_forgot_password_message)
                .setView(container)
                .setPositiveButton(R.string.auth_forgot_password_send, (d, w) -> {
                    String email = input.getText() != null ? input.getText().toString().trim() : "";
                    if (!AuthValidation.isEmailValid(email)) {
                        showError(getString(R.string.auth_error_email_invalid));
                        return;
                    }
                    com.google.firebase.auth.FirebaseAuth.getInstance()
                            .sendPasswordResetEmail(email)
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    Toast.makeText(requireContext(),
                                            R.string.auth_forgot_password_sent,
                                            Toast.LENGTH_LONG).show();
                                } else {
                                    String err = task.getException() != null
                                            ? task.getException().getMessage() : "?";
                                    showError(getString(R.string.auth_forgot_password_failed, err));
                                }
                            });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private boolean validateInputs() {
        String email = editEmail.getText() != null ? editEmail.getText().toString().trim() : "";
        String pass = editPassword.getText() != null ? editPassword.getText().toString() : "";
        String pass2 = editConfirm.getText() != null ? editConfirm.getText().toString() : "";

        if (TextUtils.isEmpty(email)) {
            showError(getString(R.string.auth_error_email_required));
            return false;
        }
        if (!AuthValidation.isEmailValid(email)) {
            showError(getString(R.string.auth_error_email_invalid));
            return false;
        }
        if (AuthValidation.passwordTooShort(pass)) {
            showError(AuthValidation.passwordTooShortMessage(requireContext()));
            return false;
        }
        if (registerMode) {
            if (TextUtils.isEmpty(pass2)) {
                showError(getString(R.string.auth_error_password_confirm_required));
                return false;
            }
            if (!pass.equals(pass2)) {
                showError(getString(R.string.auth_error_password_mismatch));
                return false;
            }
        }
        return true;
    }

    private void performAuth() {
        // Phase 2.3: client-side rate limit. Sign-up is exempt because
        // there's nothing to brute-force.
        if (!registerMode) {
            int waitSec = AuthRateLimiter.secondsUntilUnlocked(requireContext());
            if (waitSec > 0) {
                showError(getString(R.string.auth_rate_limited, waitSec));
                return;
            }
        }
        setLoading(true);

        String email = editEmail.getText() != null ? editEmail.getText().toString().trim() : "";
        String pass = editPassword.getText() != null ? editPassword.getText().toString() : "";

        if (registerMode) {
            FirebaseAuth.getInstance()
                    .createUserWithEmailAndPassword(email, pass)
                    .addOnCompleteListener(task -> {
                        setLoading(false);
                        if (task.isSuccessful()) {
                            // Phase 3.2: kick off a verification email so the
                            // signup is provable. We don't gate further use
                            // on it for v1.0 — the Settings badge does.
                            // Audit fix #3 (2026-05-06): sendEmailVerification
                            // is async — the previous try/catch never caught
                            // anything. Use addOnFailureListener instead so
                            // we actually log delivery failures.
                            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
                            if (u != null) {
                                u.sendEmailVerification()
                                        .addOnFailureListener(t ->
                                                CrashReporter.INSTANCE.log(t));
                            }
                            onAuthSuccess(email, /*name*/"");
                        } else {
                            handleAuthError(task.getException());
                        }
                    });
        } else {
            FirebaseAuth.getInstance()
                    .signInWithEmailAndPassword(email, pass)
                    .addOnCompleteListener(task -> {
                        setLoading(false);
                        if (task.isSuccessful()) {
                            AuthRateLimiter.onSuccess(requireContext());
                            onAuthSuccess(email, /*name*/"");
                        } else {
                            AuthRateLimiter.onFailure(requireContext());
                            handleAuthError(task.getException());
                        }
                    });
        }
    }

    private void handleAuthError(Exception e) {
        String msg = getString(R.string.auth_error_generic_failed);
        if (e instanceof FirebaseAuthUserCollisionException) {
            msg = getString(R.string.auth_error_email_collision);
        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
            msg = getString(R.string.auth_error_invalid_credentials);
        } else if (e instanceof FirebaseAuthInvalidUserException) {
            msg = getString(R.string.auth_error_user_not_found);
        } else if (e != null && e.getMessage() != null) {
            msg = e.getMessage();
        }
        showError(msg);
    }

    private void onAuthSuccess(String email, String name) {
        // Phase 0.3: fall back to email prefix if no name was supplied so the
        // settings screen always has something to show.
        String displayName = AuthValidation.nameFromEmail(name, email);

        // حفظ في SharedPreferences
        EmailContext.setCurrent(requireContext(), email);
        requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
                .edit().putString("current_user_name", displayName).apply();

        // تأكيد وجود المستخدم محليًا في Room
        final String finalDisplayName = displayName;
        FragmentBg.runIO(this, () -> {
            com.example.plantcare.data.repository.AuthRepository authRepo =
                    com.example.plantcare.data.repository.AuthRepository
                            .getInstance(requireContext());
            User existing = authRepo.getUserByEmailBlocking(email);
            if (existing == null) {
                authRepo.insertUserBlocking(new User(email, finalDisplayName, ""));
            }
        });

        // إرسال نتيجة لـ MainActivity للتحديث الفوري
        Bundle result = new Bundle();
        result.putString("email", email);
        getParentFragmentManager().setFragmentResult("auth_result", result);

        if (registerMode) {
            Analytics.INSTANCE.logSignUp(requireContext(), "email");
        } else {
            Analytics.INSTANCE.logLogin(requireContext(), "email");
        }
        try { DataChangeNotifier.notifyChange(); } catch (Exception __ce) { com.example.plantcare.CrashReporter.INSTANCE.log(__ce); }

        // إغلاق AuthStartDialogFragment إن كان ظاهرًا
        closeAuthStartIfVisible();

        dismissAllowingStateLoss();
    }

    private void closeAuthStartIfVisible() {
        for (androidx.fragment.app.Fragment f : getParentFragmentManager().getFragments()) {
            if (f instanceof AuthStartDialogFragment) {
                ((DialogFragment) f).dismissAllowingStateLoss();
            }
        }
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        buttonPrimary.setEnabled(!loading);
        editEmail.setEnabled(!loading);
        editPassword.setEnabled(!loading);
        if (editConfirm != null) editConfirm.setEnabled(!loading);
        if (textModeToggle != null) textModeToggle.setEnabled(!loading);
    }

    private void showError(String msg) {
        textError.setVisibility(View.VISIBLE);
        textError.setText(msg);
    }

    private void clearError() {
        textError.setVisibility(View.GONE);
        textError.setText("");
    }

    @Override
    public int getTheme() {
        return super.getTheme();
    }
}