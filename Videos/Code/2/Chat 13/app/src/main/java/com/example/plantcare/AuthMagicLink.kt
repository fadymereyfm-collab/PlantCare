package com.example.plantcare

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth

/**
 * Phase 6.2 / Auth hardening (2026-05-05): passwordless e-mail-link sign-in.
 * The user enters their email, Firebase emails them a one-time link,
 * tapping the link in the same app instance authenticates them and
 * finishes the sign-in.
 *
 * Required Firebase setup (one-time):
 *   1. Authentication → Sign-in method → Email/Password → enable
 *      "Email link (passwordless sign-in)".
 *   2. Add the app's package name to the `androidPackageName` whitelist
 *      (already covered by the default Firebase Android app).
 *   3. (Optional) Configure a custom domain for the action handler.
 *
 * Required app setup:
 *   - The deep link `https://<your-domain>/finishSignIn` must be handled
 *     by an Activity with `<intent-filter android:autoVerify="true">`.
 *     Until the domain is set up, [start] still works for sending the
 *     email, but [finishFromIntent] will return null because no Activity
 *     is registered to receive the link. Add the intent filter when
 *     you go live.
 */
object AuthMagicLink {

    fun interface OnSent { fun onSent() }
    fun interface OnFailure { fun onFailure(message: String) }
    fun interface OnSuccess { fun onSuccess(email: String) }

    private const val PREFS = "auth_magic_link"
    private const val KEY_PENDING_EMAIL = "pending_email"

    /** Send a sign-in link to the email. The email is stored locally
     *  (plain prefs — not sensitive) so [finishFromIntent] can confirm it
     *  matches when the user returns. */
    @JvmStatic
    fun start(
        context: Context,
        email: String,
        onSent: OnSent,
        onFailure: OnFailure
    ) {
        if (!AuthValidation.isEmailValid(email)) {
            onFailure.onFailure(context.getString(R.string.auth_error_email_invalid))
            return
        }

        val pkg = context.packageName
        val settings = ActionCodeSettings.newBuilder()
            // After clicking the link, Firebase will redirect here. The
            // domain must be added to Firebase Hosting / Dynamic Links.
            .setUrl("https://${pkg.lowercase()}.firebaseapp.com/finishSignIn")
            .setHandleCodeInApp(true)
            .setAndroidPackageName(pkg, /*installIfNotAvailable*/ true, /*minimumVersion*/ null)
            .build()

        FirebaseAuth.getInstance()
            .sendSignInLinkToEmail(email, settings)
            .addOnSuccessListener {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_PENDING_EMAIL, email).apply()
                onSent.onSent()
            }
            .addOnFailureListener { e ->
                onFailure.onFailure(e.message ?: "?")
            }
    }

    /** Finish the sign-in if the incoming Intent was a Firebase magic
     *  link. Returns null if it wasn't (host should fall through to its
     *  normal flow). */
    @JvmStatic
    fun finishFromIntent(
        context: Context,
        intent: Intent?,
        onSuccess: OnSuccess,
        onFailure: OnFailure
    ) {
        val link = intent?.data?.toString() ?: return
        val auth = FirebaseAuth.getInstance()
        if (!auth.isSignInWithEmailLink(link)) return

        val email = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_EMAIL, null)
        if (email.isNullOrBlank()) {
            onFailure.onFailure("missing pending email")
            return
        }

        auth.signInWithEmailLink(email, link)
            .addOnSuccessListener {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().remove(KEY_PENDING_EMAIL).apply()
                onSuccess.onSuccess(email)
            }
            .addOnFailureListener { e ->
                onFailure.onFailure(e.message ?: "?")
            }
    }
}
