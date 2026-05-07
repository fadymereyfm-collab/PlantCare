package com.example.plantcare;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.IntentSender;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.plantcare.ui.util.FragmentBg;
import com.google.android.gms.auth.api.identity.BeginSignInRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.auth.api.identity.SignInCredential;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.*;


/**
 * حوار اختيار أسلوب المصادقة: Google أو Email.
 * - عند اختيار Email يفتح LoginDialogFragment ويُغلق نفسه فوراً.
 * - يحتوي دعم Google One Tap مع Fallback للـ GoogleSignInClient التقليدي.
 * - يُخزّن معلومات المستخدم بنجاح في SharedPreferences + قاعدة البيانات المحلية (UserDao).
 */
public class AuthStartDialogFragment extends DialogFragment {

    // Google Identity (One Tap) + fallback GoogleSignInClient
    private SignInClient oneTapClient;
    private BeginSignInRequest signInRequest;
    private BeginSignInRequest signUpRequest;
    private ActivityResultLauncher<IntentSenderRequest> oneTapLauncher;

    private GoogleSignInClient legacyGoogleClient; // Fallback
    private ActivityResultLauncher<android.content.Intent> legacyLauncher;

    private boolean canUseGoogleIdToken = false;
    private String webClientId = null;

    // Phase 1.1 + 1.2: in-dialog progress + error TextView so the user can
    // see what's happening during a Google Sign-In round-trip.
    private android.widget.ProgressBar progress;
    private android.widget.TextView errorView;
    private MaterialButton btnGoogle;
    private MaterialButton btnEmail;

    public static AuthStartDialogFragment newInstance() {
        return new AuthStartDialogFragment();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_auth_start, null);

        // تهيئة Google
        resolveWebClientId();
        setupGoogleIdentity();
        setupGoogleLegacy();

        // الربط بالعناصر
        btnGoogle = v.findViewById(R.id.btnGoogle);
        btnEmail  = v.findViewById(R.id.btnEmail);
        android.widget.TextView textHaveAcc = v.findViewById(R.id.textHaveAccount);
        View closeBtn = v.findViewById(R.id.buttonClose);
        progress = v.findViewById(R.id.authStartProgress);
        errorView = v.findViewById(R.id.authStartError);

        if (closeBtn != null) {
            closeBtn.setOnClickListener(x -> dismissAllowingStateLoss());
        }

        if (btnGoogle != null) {
            btnGoogle.setAlpha(canUseGoogleIdToken ? 1f : 0.6f);
            btnGoogle.setOnClickListener(view -> {
                if (!canUseGoogleIdToken) {
                    showInlineError(getString(R.string.auth_google_not_configured));
                    return;
                }
                setLoading(true);
                startOneTap(signUpRequest, true);
            });
        }

        if (btnEmail != null) {
            btnEmail.setOnClickListener(view -> {
                openLoginDialog(true); // فتح وضع التسجيل مبدئياً
            });
        }

        if (textHaveAcc != null) {
            textHaveAcc.setOnClickListener(view -> openLoginDialog(false)); // وضع تسجيل الدخول
        }

        // Phase 6.1: Apple Sign-In button.
        MaterialButton btnApple = v.findViewById(R.id.btnApple);
        if (btnApple != null) {
            btnApple.setOnClickListener(view -> {
                if (getActivity() == null) return;
                setLoading(true);
                AuthAppleSignIn.start(requireActivity(),
                        (email, name) -> {
                            setLoading(false);
                            onSignedIn(email, name);
                        },
                        msg -> {
                            setLoading(false);
                            showInlineError(getString(R.string.auth_apple_failed, msg));
                        });
            });
        }

        // Phase 6.2: Magic Link button — passwordless email sign-in.
        MaterialButton btnMagicLink = v.findViewById(R.id.btnMagicLink);
        if (btnMagicLink != null) {
            btnMagicLink.setOnClickListener(view -> showMagicLinkDialog());
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), getTheme())
                .setView(v)
                .setCancelable(true)
                .create();

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
                w.setBackgroundDrawableResource(android.R.color.transparent);
            }
        }
    }

    private void openLoginDialog(boolean register) {
        LoginDialogFragment dialog = LoginDialogFragment.newInstance(
                register ? LoginDialogFragment.MODE_REGISTER : LoginDialogFragment.MODE_LOGIN
        );
        dialog.show(getParentFragmentManager(), "login_dialog_email");
        // أغلق حوار البداية فور الفتح
        dismissAllowingStateLoss();
    }

    // ---------------- Google One Tap ----------------
    private void resolveWebClientId() {
        try {
            int id = getResources().getIdentifier("default_web_client_id", "string", requireContext().getPackageName());
            if (id != 0) {
                webClientId = getString(id);
                canUseGoogleIdToken = webClientId != null && !webClientId.trim().isEmpty();
            } else {
                canUseGoogleIdToken = false;
            }
        } catch (Exception e) {
            canUseGoogleIdToken = false;
        }
    }

    private void setupGoogleIdentity() {
        oneTapClient = Identity.getSignInClient(requireContext());
        if (!canUseGoogleIdToken) return;

        signInRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(
                        BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                                .setSupported(true)
                                .setServerClientId(webClientId)
                                .setFilterByAuthorizedAccounts(true)
                                .build()
                )
                .build();

        signUpRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(
                        BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                                .setSupported(true)
                                .setServerClientId(webClientId)
                                .setFilterByAuthorizedAccounts(false)
                                .build()
                )
                .build();

        oneTapLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                        try {
                            SignInCredential credential = oneTapClient.getSignInCredentialFromIntent(result.getData());
                            String idToken = credential.getGoogleIdToken();
                            if (idToken == null || idToken.isEmpty()) {
                                setLoading(false);
                                showInlineError(getString(R.string.auth_google_one_tap_empty_token));
                                return;
                            }
                            signInFirebaseWithIdToken(idToken, credential.getDisplayName(), credential.getId());
                        } catch (ApiException e) {
                            setLoading(false);
                            showInlineError(getString(R.string.auth_google_one_tap_error, e.getMessage()));
                        }
                    } else {
                        // user cancelled the chooser
                        setLoading(false);
                    }
                }
        );
    }

    private void startOneTap(BeginSignInRequest request, boolean fallbackToLegacyChooser) {
        oneTapClient.beginSignIn(request)
                .addOnSuccessListener(result -> {
                    try {
                        IntentSenderRequest isr =
                                new IntentSenderRequest.Builder(result.getPendingIntent().getIntentSender()).build();
                        oneTapLauncher.launch(isr);
                    } catch (Exception e) {
                        setLoading(false);
                        showInlineError(getString(R.string.auth_google_one_tap_launch_error, e.getMessage()));
                    }
                })
                .addOnFailureListener(e -> {
                    if (fallbackToLegacyChooser && legacyGoogleClient != null) {
                        startLegacyGoogleChooser();
                    } else {
                        setLoading(false);
                        showInlineError(getString(R.string.auth_google_unavailable, e.getMessage()));
                    }
                });
    }

    // ---------------- Google Legacy (Chooser) ----------------
    private void setupGoogleLegacy() {
        if (!canUseGoogleIdToken) {
            legacyGoogleClient = null;
            return;
        }
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(webClientId)
                .build();
        legacyGoogleClient = GoogleSignIn.getClient(requireContext(), gso);
        legacyLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        try {
                            GoogleSignInAccount acc = task.getResult(ApiException.class);
                            if (acc == null) {
                                setLoading(false);
                                showInlineError(getString(R.string.auth_google_account_null));
                                return;
                            }
                            String idToken = acc.getIdToken();
                            if (idToken == null || idToken.isEmpty()) {
                                setLoading(false);
                                showInlineError(getString(R.string.auth_google_no_id_token));
                                return;
                            }
                            signInFirebaseWithIdToken(idToken, acc.getDisplayName(), acc.getEmail());
                        } catch (ApiException e) {
                            setLoading(false);
                            showInlineError(getString(R.string.auth_google_chooser_error, e.getMessage()));
                        }
                    } else {
                        setLoading(false);
                    }
                }
        );
    }

    private void startLegacyGoogleChooser() {
        if (legacyGoogleClient == null) return;
        legacyLauncher.launch(legacyGoogleClient.getSignInIntent());
    }

    private void signInFirebaseWithIdToken(String idToken, String displayName, String emailFromCred) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener(t -> {
                    setLoading(false);
                    if (t.isSuccessful()) {
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        String email = (user != null && user.getEmail() != null) ? user.getEmail() : emailFromCred;
                        onSignedIn(email, displayName);
                    } else {
                        showInlineError(getString(R.string.auth_firebase_signin_failed));
                    }
                });
    }

    private void onSignedIn(String email, String name) {
        final String emailFinal = (email != null) ? email : "nouser@unknown";
        // Phase 0.3: fall back to email prefix if Google didn't supply a display name.
        final String nameFinal  = AuthValidation.nameFromEmail(name, emailFinal);

        // Audit fix #7 (2026-05-06): any successful sign-in clears the local
        // rate limiter — otherwise a user who failed email/password 4 times
        // then signed in via Google/Apple/Magic Link would still see the
        // 30 s lockout when they next try email.
        AuthRateLimiter.onSuccess(requireContext());

        EmailContext.setCurrent(requireContext(), emailFinal);
        requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
                .edit().putString("current_user_name", nameFinal).apply();

        // إدخال المستخدم محلياً (إن لم يكن موجوداً)
        FragmentBg.runIO(this, () -> {
            com.example.plantcare.data.repository.AuthRepository authRepo =
                    com.example.plantcare.data.repository.AuthRepository
                            .getInstance(requireContext());
            User existing = authRepo.getUserByEmailBlocking(emailFinal);
            if (existing == null) {
                authRepo.insertUserBlocking(new User(emailFinal, nameFinal, ""));
            }
        });

        // NEW: أرسل نتيجة للمستمع في MainActivity لتحديث الواجهات فورًا
        Bundle result = new Bundle();
        result.putString("email", emailFinal);
        getParentFragmentManager().setFragmentResult("auth_result", result);

        Analytics.INSTANCE.logLogin(requireContext(), "google");
        try { DataChangeNotifier.notifyChange(); } catch (Exception e) { CrashReporter.INSTANCE.log(e); }

        dismissAllowingStateLoss();
    }

    /** Phase 6.2: ask for an email and send a Firebase magic link. */
    private void showMagicLinkDialog() {
        final android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setHint(R.string.email);

        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.auth_magic_link_label)
                .setView(container)
                .setPositiveButton(R.string.auth_magic_link_send, (d, w) -> {
                    String email = input.getText() != null ? input.getText().toString().trim() : "";
                    AuthMagicLink.start(requireContext(), email,
                            () -> android.widget.Toast.makeText(requireContext(),
                                    R.string.auth_magic_link_sent,
                                    android.widget.Toast.LENGTH_LONG).show(),
                            msg -> showInlineError(getString(R.string.auth_magic_link_failed, msg)));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void toast(String msg) {
        if (getContext() != null) {
            android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show();
        }
    }

    /** Phase 1.1: spinner + disable buttons during Google round-trip. */
    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnGoogle != null) btnGoogle.setEnabled(!loading);
        if (btnEmail != null) btnEmail.setEnabled(!loading);
        if (loading && errorView != null) {
            errorView.setVisibility(View.GONE);
        }
    }

    /** Phase 1.2: persistent inline error in the dialog instead of a Toast
     *  that disappears in 3 s. */
    private void showInlineError(String msg) {
        if (errorView == null) {
            // Fallback if the layout was loaded from a stale cache without the
            // new fields (shouldn't happen on a clean build).
            toast(msg);
            return;
        }
        errorView.setText(msg);
        errorView.setVisibility(View.VISIBLE);
    }

    @Override
    public int getTheme() {
        // يمكن استبدال الثيم هنا بثيم مخصص للحوار لو رغبت
        return super.getTheme();
    }
}