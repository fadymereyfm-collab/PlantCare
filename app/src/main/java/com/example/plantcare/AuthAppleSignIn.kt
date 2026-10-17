package com.example.plantcare

import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.OAuthProvider

/**
 * Phase 6.1 / Auth hardening (2026-05-05): Apple Sign-In via Firebase's
 * generic OAuthProvider. The actual flow is a Firebase-hosted web view
 * (no Apple Developer Account needed on the Android side beyond Firebase
 * Console configuration).
 *
 * Activation steps (Firebase Console — out-of-app, do once before publish):
 *   1. Authentication → Sign-in method → Apple → enable.
 *   2. Provide an Apple Service ID + private key (.p8) + Team ID + Key ID.
 *   3. Add `https://<project>.firebaseapp.com/__/auth/handler` as a
 *      Return URL on the Apple side.
 *
 * Once that config exists, this entry point is enough to launch the
 * sign-in chooser. No code changes needed when toggling between dev/prod
 * Firebase projects — the OAuthProvider picks up the active app config.
 *
 * Why this is a separate file: Apple Sign-In is *required* by App Store
 * Review Guideline 4.8 for any iOS port that already offers third-party
 * sign-in; even on Android it's friendly to the ~10% of EU users who
 * primarily use Apple devices and want a unified identity.
 */
object AuthAppleSignIn {

    private const val PROVIDER_ID = "apple.com"

    /** SAM interfaces so Java callers can pass plain lambdas. */
    fun interface OnSuccess {
        fun onSuccess(email: String?, name: String?)
    }
    fun interface OnFailure {
        fun onFailure(message: String)
    }

    /** Returns true if a sign-in attempt was started. False means we're
     *  short-circuiting (e.g. activity already finished). */
    @JvmStatic
    fun start(
        activity: FragmentActivity,
        onSuccess: OnSuccess,
        onFailure: OnFailure
    ): Boolean {
        if (activity.isFinishing) return false

        val provider = OAuthProvider.newBuilder(PROVIDER_ID).apply {
            scopes = listOf("email", "name")
        }.build()

        // Audit fix #6 (2026-05-06): the original code chained
        // `auth.pendingAuthResult ?: startActivityForSignInWithProvider(...)`.
        // pendingAuthResult is meant for resume-after-recreation, not for
        // the initial start — using it here meant a stale pending result
        // from a previous Activity could short-circuit the new attempt and
        // resolve with an outdated user. Always start fresh from this
        // entry point; resume is a separate concern handled by the host
        // Activity's onCreate if it ever needs to.
        val auth = FirebaseAuth.getInstance()
        auth.startActivityForSignInWithProvider(activity, provider)
            .addOnSuccessListener { result ->
                val u = result.user
                onSuccess.onSuccess(u?.email, u?.displayName)
            }
            .addOnFailureListener { e ->
                onFailure.onFailure(e.message ?: "?")
            }
        return true
    }

    /** Convenience for hosts that just want a Toast on failure. */
    @JvmStatic
    fun startWithToastErrors(activity: FragmentActivity, onSuccess: OnSuccess) {
        start(activity, onSuccess, OnFailure { msg ->
            Toast.makeText(
                activity,
                activity.getString(R.string.auth_apple_failed, msg),
                Toast.LENGTH_LONG
            ).show()
        })
    }
}
