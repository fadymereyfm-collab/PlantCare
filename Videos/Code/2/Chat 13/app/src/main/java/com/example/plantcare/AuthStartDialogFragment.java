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
        MaterialButton btnGoogle = v.findViewById(R.id.btnGoogle);
        MaterialButton btnEmail  = v.findViewById(R.id.btnEmail);
        android.widget.TextView textHaveAcc = v.findViewById(R.id.textHaveAccount);
        View closeBtn = v.findViewById(R.id.buttonClose);

        if (closeBtn != null) {
            closeBtn.setOnClickListener(x -> dismissAllowingStateLoss());
        }

        if (btnGoogle != null) {
            btnGoogle.setAlpha(canUseGoogleIdToken ? 1f : 0.6f);
            btnGoogle.setOnClickListener(view -> {
                if (!canUseGoogleIdToken) {
                    toast("Google Sign-In needs Firebase configuration.");
                    return;
                }
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
                                toast("Google One Tap failed: empty token");
                                return;
                            }
                            signInFirebaseWithIdToken(idToken, credential.getDisplayName(), credential.getId());
                        } catch (ApiException e) {
                            toast("Google One Tap error: " + e.getMessage());
                        }
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
                        toast("One Tap launch error: " + e.getMessage());
                    }
                })
                .addOnFailureListener(e -> {
                    if (fallbackToLegacyChooser && legacyGoogleClient != null) {
                        startLegacyGoogleChooser();
                    } else {
                        toast("Google Sign-In unavailable: " + e.getMessage());
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
                            if (acc == null) { toast("Google account is null"); return; }
                            String idToken = acc.getIdToken();
                            if (idToken == null || idToken.isEmpty()) {
                                toast("No idToken from GoogleSignIn (check Firebase web client id / SHA-1).");
                                return;
                            }
                            signInFirebaseWithIdToken(idToken, acc.getDisplayName(), acc.getEmail());
                        } catch (ApiException e) {
                            toast("Google chooser error: " + e.getMessage());
                        }
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
                    if (t.isSuccessful()) {
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        String email = (user != null && user.getEmail() != null) ? user.getEmail() : emailFromCred;
                        onSignedIn(email, displayName);
                    } else {
                        toast("Firebase Sign-In failed");
                    }
                });
    }

    private void onSignedIn(String email, String name) {
        final String emailFinal = (email != null) ? email : "nouser@unknown";
        final String nameFinal  = (name != null)  ? name  : "";

        SharedPreferences prefs = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("current_user_email", emailFinal)
                .putString("current_user_name", nameFinal)
                .apply();

        // إدخال المستخدم محلياً (إن لم يكن موجوداً)
        FragmentBg.runIO(this, () -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            UserDao userDao = db.userDao();
            User existing = userDao.getUserByEmail(emailFinal);
            if (existing == null) {
                userDao.insert(new User(emailFinal, nameFinal, ""));
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

    private void toast(String msg) {
        if (getContext() != null) {
            android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public int getTheme() {
        // يمكن استبدال الثيم هنا بثيم مخصص للحوار لو رغبت
        return super.getTheme();
    }
}