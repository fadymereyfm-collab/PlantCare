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
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

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

        if (editPassword != null) {
            editPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        if (editConfirm != null) {
            editConfirm.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
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
        } else {
            layoutConfirm.setVisibility(View.GONE);
            textTitle.setText(R.string.auth_sign_in);
            buttonPrimary.setText(R.string.auth_sign_in);
            if (textModeToggle != null) textModeToggle.setText(R.string.login_new_user_register);
        }
        clearError();
        if (editPassword != null) editPassword.setText(null);
        if (editConfirm != null) editConfirm.setText(null);
    }

    private boolean validateInputs() {
        String email = editEmail.getText() != null ? editEmail.getText().toString().trim() : "";
        String pass = editPassword.getText() != null ? editPassword.getText().toString() : "";
        String pass2 = editConfirm.getText() != null ? editConfirm.getText().toString() : "";

        if (TextUtils.isEmpty(email)) {
            showError("Bitte E‑Mail eingeben");
            return false;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("E‑Mail Adresse ungültig");
            return false;
        }
        if (TextUtils.isEmpty(pass) || pass.length() < 6) {
            showError("Passwort mindestens 6 Zeichen");
            return false;
        }
        if (registerMode) {
            if (TextUtils.isEmpty(pass2)) {
                showError("Bitte Bestätigung eingeben");
                return false;
            }
            if (!pass.equals(pass2)) {
                showError("Passwörter stimmen nicht überein");
                return false;
            }
        }
        return true;
    }

    private void performAuth() {
        setLoading(true);

        String email = editEmail.getText() != null ? editEmail.getText().toString().trim() : "";
        String pass = editPassword.getText() != null ? editPassword.getText().toString() : "";

        if (registerMode) {
            FirebaseAuth.getInstance()
                    .createUserWithEmailAndPassword(email, pass)
                    .addOnCompleteListener(task -> {
                        setLoading(false);
                        if (task.isSuccessful()) {
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
                            onAuthSuccess(email, /*name*/"");
                        } else {
                            handleAuthError(task.getException());
                        }
                    });
        }
    }

    private void handleAuthError(Exception e) {
        String msg = "Anmeldung fehlgeschlagen";
        if (e instanceof FirebaseAuthUserCollisionException) {
            msg = "E‑Mail bereits registriert";
        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
            msg = "E‑Mail oder Passwort ungültig";
        } else if (e instanceof FirebaseAuthInvalidUserException) {
            msg = "Benutzer nicht gefunden";
        } else if (e != null && e.getMessage() != null) {
            msg = e.getMessage();
        }
        showError(msg);
    }

    private void onAuthSuccess(String email, String name) {
        // حفظ في SharedPreferences
        SharedPreferences prefs = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("current_user_email", email)
                .putString("current_user_name", name != null ? name : "")
                .apply();

        // تأكيد وجود المستخدم محليًا في Room
        FragmentBg.runIO(this, () -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            UserDao userDao = db.userDao();
            User existing = userDao.getUserByEmail(email);
            if (existing == null) {
                userDao.insert(new User(email, name != null ? name : "", ""));
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
        try { DataChangeNotifier.notifyChange(); } catch (Exception ignored) {}

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