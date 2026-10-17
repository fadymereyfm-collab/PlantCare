package com.example.plantcare;

import android.app.Dialog;
import android.content.Context;
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
 *
 * <p>الوضع (Register / Login) يتمّ تمريره عبر {@link #newInstance(String)}
 * كي تعرض الواجهة أزراراً ونصوصاً متّسقة مع نيّة المستخدم القادم من
 * Onboarding ("Konto erstellen" → Register، "Anmelden" → Login).</p>
 *
 * <p>2026-05-13 — تنظيف الإصلاحات على هذا الملف:</p>
 * <ul>
 *   <li>إزالة أزرار Apple + Magic Link من واجهة هذا الـDialog (Apple غير
 *       مدعومة على Android، و Magic Link معتمد على Firebase Dynamic Links
 *       غير مكوّن في Firebase Console — يظلّ {@code AuthMagicLink} في الكود
 *       لمعالجة Deep Links قديمة في {@link MainActivity}).</li>
 *   <li>نقل {@code registerForActivityResult} من {@code onCreateDialog} إلى
 *       {@link #onCreate(Bundle)} لاحترام دورة حياة Fragment (وثيقة Google
 *       تشترط التسجيل قبل وصول الـLifecycle إلى STARTED).</li>
 *   <li>إظهار خطأ واضح عند {@code RESULT_CANCELED} في كلا مسار OneTap والـ
 *       Legacy chooser — المسار السابق كان يبتلع الإلغاء بصمت وكان المستخدم
 *       يرى الـchooser ثم لا يحصل على أي ردّ بعد اختيار الحساب.</li>
 *   <li>إضافة fallback إلى Legacy chooser عند إلغاء OneTap بدل مجرّد
 *       فشل {@code beginSignIn} — كي لا تتعطّل التجربة على الأجهزة التي
 *       يفشل OneTap عليها بصمت بسبب تعارض SHA / Play Services.</li>
 * </ul>
 */
public class AuthStartDialogFragment extends DialogFragment {

    public static final String MODE_REGISTER = "register";
    public static final String MODE_LOGIN    = "login";
    private static final String ARG_MODE = "mode";

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
    private MaterialButton buttonToggleMode;

    /** "register" → btnEmail opens LoginDialog in MODE_REGISTER. "login" → MODE_LOGIN. */
    private String authMode = MODE_REGISTER;

    public static AuthStartDialogFragment newInstance() {
        return newInstance(MODE_REGISTER);
    }

    /** @param mode {@link #MODE_REGISTER} or {@link #MODE_LOGIN}. */
    public static AuthStartDialogFragment newInstance(@Nullable String mode) {
        AuthStartDialogFragment f = new AuthStartDialogFragment();
        Bundle b = new Bundle();
        b.putString(ARG_MODE, MODE_LOGIN.equalsIgnoreCase(mode) ? MODE_LOGIN : MODE_REGISTER);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            String requested = getArguments().getString(ARG_MODE, MODE_REGISTER);
            authMode = MODE_LOGIN.equalsIgnoreCase(requested) ? MODE_LOGIN : MODE_REGISTER;
        }

        // Google docs: registerForActivityResult MUST be called before the
        // fragment reaches STARTED state. The previous implementation invoked
        // this from onCreateDialog (after onCreate has finished), which on
        // some devices causes the result callback to never fire — the user
        // sees the chooser, picks an account, then nothing happens. Moving
        // it here is the documented fix.
        resolveWebClientId();
        setupGoogleIdentityLauncher();
        setupGoogleLegacyLauncher();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_auth_start, null);

        // Configure Google clients (they're cheap to construct; we only need
        // the launchers registered in onCreate).
        configureGoogleSignInRequests();
        configureGoogleLegacyClient();

        // الربط بالعناصر
        btnGoogle = v.findViewById(R.id.btnGoogle);
        btnEmail  = v.findViewById(R.id.btnEmail);
        buttonToggleMode = v.findViewById(R.id.buttonToggleMode);
        View closeBtn = v.findViewById(R.id.buttonClose);
        progress = v.findViewById(R.id.authStartProgress);
        errorView = v.findViewById(R.id.authStartError);

        // 2026-05-14: title shifts per mode so the two Onboarding entry
        // points ("Konto erstellen" vs "Anmelden") visibly open different
        // dialogs even though they share the underlying chooser.
        android.widget.TextView title = v.findViewById(R.id.textAuthTitle);
        if (title != null) {
            title.setText(authMode.equals(MODE_LOGIN)
                    ? R.string.auth_start_title_login
                    : R.string.auth_start_title_register);
        }

        if (closeBtn != null) {
            closeBtn.setOnClickListener(x -> dismissAllowingStateLoss());
        }

        if (btnGoogle != null) {
            btnGoogle.setAlpha(canUseGoogleIdToken ? 1f : 0.6f);
            // 2026-05-20 click-guard sweep: منع double-tap من بدء Google sign-in flow مرّتين
            btnGoogle.setOnClickListener(com.example.plantcare.ui.util.ClickGuard.wrap(view -> {
                if (!canUseGoogleIdToken) {
                    showInlineError(getString(R.string.auth_google_not_configured));
                    return;
                }
                setLoading(true);
                // Sign-in path first (filterByAuthorizedAccounts=true) so
                // returning users skip the chooser. On failure the helper
                // falls back to signUpRequest, then to the legacy chooser.
                startOneTap(signInRequest, /*fallbackToLegacyChooser=*/ true);
            }));
        }

        // Email button: text + target dialog depend on the requested mode.
        // Konto erstellen flow → green "Mit E-Mail registrieren" → REGISTER.
        // Anmelden flow         → green "Mit E-Mail anmelden"    → LOGIN.
        if (btnEmail != null) {
            btnEmail.setText(authMode.equals(MODE_LOGIN)
                    ? R.string.auth_button_email_login
                    : R.string.auth_button_email_continue);
            // 2026-05-20 click-guard sweep: منع double-tap من فتح LoginDialog مرّتين
            btnEmail.setOnClickListener(com.example.plantcare.ui.util.ClickGuard.wrap(view ->
                    openLoginDialog(authMode.equals(MODE_REGISTER))));
        }

        // Toggle button: shows the OPPOSITE action to the email button.
        if (buttonToggleMode != null) {
            buttonToggleMode.setText(authMode.equals(MODE_LOGIN)
                    ? R.string.auth_toggle_to_register
                    : R.string.auth_toggle_to_login);
            // 2026-05-20 click-guard sweep: منع double-tap من فتح LoginDialog (opposite mode) مرّتين
            buttonToggleMode.setOnClickListener(com.example.plantcare.ui.util.ClickGuard.wrap(view ->
                    openLoginDialog(authMode.equals(MODE_LOGIN))));
        }

        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), getTheme())
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
            int id = getResources().getIdentifier(
                    "default_web_client_id", "string", requireContext().getPackageName());
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

    private void configureGoogleSignInRequests() {
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
    }

    private void setupGoogleIdentityLauncher() {
        oneTapLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK
                            && result.getData() != null) {
                        try {
                            SignInCredential credential =
                                    Identity.getSignInClient(requireContext())
                                            .getSignInCredentialFromIntent(result.getData());
                            String idToken = credential.getGoogleIdToken();
                            if (idToken == null || idToken.isEmpty()) {
                                setLoading(false);
                                showInlineError(getString(
                                        R.string.auth_google_one_tap_empty_token));
                                return;
                            }
                            signInFirebaseWithIdToken(
                                    idToken, credential.getDisplayName(), credential.getId());
                        } catch (ApiException e) {
                            setLoading(false);
                            showInlineError(getString(
                                    R.string.auth_google_one_tap_error,
                                    e.getMessage()));
                        }
                    } else {
                        // Pre-fix: silently set loading=false and walked away,
                        // so the user saw the chooser, picked an account, then
                        // nothing happened (RESULT_CANCELED is the typical
                        // outcome of OneTap on devices where the SHA-1 cert
                        // doesn't match the Firebase OAuth client). Now we
                        // fall back to the legacy chooser so the user has a
                        // second chance and we surface a visible error if
                        // that also fails.
                        if (legacyGoogleClient != null) {
                            startLegacyGoogleChooser();
                        } else {
                            setLoading(false);
                            showInlineError(getString(R.string.auth_google_cancelled));
                        }
                    }
                }
        );
    }

    private void startOneTap(BeginSignInRequest request, boolean fallbackToLegacyChooser) {
        if (request == null) {
            // signInRequest/signUpRequest are only built when webClientId is
            // present — defensively short-circuit to the legacy chooser.
            if (fallbackToLegacyChooser && legacyGoogleClient != null) {
                startLegacyGoogleChooser();
            } else {
                setLoading(false);
                showInlineError(getString(R.string.auth_google_not_configured));
            }
            return;
        }
        oneTapClient.beginSignIn(request)
                .addOnSuccessListener(result -> {
                    if (!isAdded()) return;
                    try {
                        IntentSenderRequest isr =
                                new IntentSenderRequest.Builder(
                                        result.getPendingIntent().getIntentSender()).build();
                        oneTapLauncher.launch(isr);
                    } catch (Exception e) {
                        setLoading(false);
                        showInlineError(getString(
                                R.string.auth_google_one_tap_launch_error, e.getMessage()));
                    }
                })
                .addOnFailureListener(e -> {
                    // The Play Services round-trip can outlast the dialog
                    // (user back-presses out of "Choose account"). Without
                    // this guard the showInlineError → getString call
                    // crashes with IllegalStateException.
                    if (!isAdded()) return;
                    // signInRequest (authorized-accounts only) commonly fails
                    // on the first attempt for a fresh user — retry once with
                    // signUpRequest before falling back to the legacy chooser.
                    if (request == signInRequest && signUpRequest != null) {
                        startOneTap(signUpRequest, fallbackToLegacyChooser);
                        return;
                    }
                    if (fallbackToLegacyChooser && legacyGoogleClient != null) {
                        startLegacyGoogleChooser();
                    } else {
                        setLoading(false);
                        showInlineError(getString(
                                R.string.auth_google_unavailable, e.getMessage()));
                    }
                });
    }

    // ---------------- Google Legacy (Chooser) ----------------
    private void configureGoogleLegacyClient() {
        if (!canUseGoogleIdToken) {
            legacyGoogleClient = null;
            return;
        }
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(webClientId)
                .build();
        legacyGoogleClient = GoogleSignIn.getClient(requireContext(), gso);
    }

    private void setupGoogleLegacyLauncher() {
        legacyLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK
                            && result.getData() != null) {
                        Task<GoogleSignInAccount> task =
                                GoogleSignIn.getSignedInAccountFromIntent(result.getData());
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
                            // statusCode 10 = DEVELOPER_ERROR, almost always
                            // signals a SHA-1 cert mismatch between the
                            // signing key and the Firebase OAuth client.
                            // We surface this verbatim so the dev/QA team
                            // can spot it quickly during installs.
                            showInlineError(getString(
                                    R.string.auth_google_chooser_error,
                                    e.getStatusCode() + ": " + e.getMessage()));
                        }
                    } else {
                        // Pre-fix: just setLoading(false). Now we tell the
                        // user the chooser came back empty so they know to
                        // retry instead of being stuck staring at a silent
                        // dialog.
                        setLoading(false);
                        showInlineError(getString(R.string.auth_google_cancelled));
                    }
                }
        );
    }

    private void startLegacyGoogleChooser() {
        if (legacyGoogleClient == null) {
            setLoading(false);
            showInlineError(getString(R.string.auth_google_not_configured));
            return;
        }
        legacyLauncher.launch(legacyGoogleClient.getSignInIntent());
    }

    private void signInFirebaseWithIdToken(String idToken, String displayName, String emailFromCred) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener(t -> {
                    // Detach guard: if the user dismissed the auth dialog
                    // before Firebase returned, every getString/requireContext
                    // below would crash with IllegalStateException.
                    if (!isAdded()) return;
                    setLoading(false);
                    if (t.isSuccessful()) {
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        String email = (user != null && user.getEmail() != null)
                                ? user.getEmail() : emailFromCred;
                        onSignedIn(email, displayName);
                    } else {
                        showInlineError(getString(R.string.auth_firebase_signin_failed));
                    }
                });
    }

    private void onSignedIn(String email, String name) {
        // If the dialog was already dismissed (user back-pressed during the
        // Google round-trip), the fragment is detached and every
        // requireContext() below would crash with IllegalStateException. The
        // Firebase user is signed in either way; MainActivity's auth-state
        // listener will pick up the session on the next foreground tick.
        if (!isAdded()) return;

        final String emailFinal = (email != null) ? email : "nouser@unknown";
        // Phase 0.3: fall back to email prefix if Google didn't supply a display name.
        final String nameFinal  = AuthValidation.nameFromEmail(name, emailFinal);

        // Audit fix #7 (2026-05-06): any successful sign-in clears the local
        // rate limiter — otherwise a user who failed email/password 4 times
        // then signed in via Google would still see the 30 s lockout when
        // they next try email.
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

        // Wave 2: register FCM token under the new user's subtree.
        try {
            com.example.plantcare.feature.share.FcmTokenManager
                    .INSTANCE.registerCurrentToken(requireContext());
        } catch (Throwable t) { CrashReporter.INSTANCE.log(t); }

        // NEW: أرسل نتيجة للمستمع في MainActivity لتحديث الواجهات فورًا
        Bundle result = new Bundle();
        result.putString("email", emailFinal);
        getParentFragmentManager().setFragmentResult("auth_result", result);

        Analytics.INSTANCE.logLogin(requireContext(), "google");
        try { DataChangeNotifier.notifyChange(); } catch (Exception e) { CrashReporter.INSTANCE.log(e); }

        dismissAllowingStateLoss();
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
        if (buttonToggleMode != null) buttonToggleMode.setEnabled(!loading);
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
