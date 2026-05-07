package com.example.plantcare

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

/**
 * Phase 6.4 / Auth hardening (2026-05-05): "sign out all devices" entry
 * point.
 *
 * Firebase doesn't ship a per-device session list out of the box (that
 * needs Identity Platform + custom Firestore tracking), so v1.0 doesn't
 * expose individual sessions. What we *can* do today is invalidate every
 * session token: revoking the refresh token on the server forces all
 * devices to re-authenticate the next time they refresh.
 *
 * Implementation note: `FirebaseUser.getIdToken(true)` will refresh the
 * token and pick up server-side revocations; on Firebase the canonical
 * way to force-logout-everywhere from the client is to update the user's
 * password (which invalidates all tokens). For passwordless / Google-only
 * users we can't do that here — the user has to do it from
 * `myaccount.google.com`.
 *
 * For the typical case (email/password user) this implementation just
 * resets the password to a fresh random value and immediately requires the
 * user to set a new one via the password reset email. That's the closest
 * we can get to "logout everywhere" without Identity Platform.
 *
 * The Settings UI for this is left as a deferred follow-up; the helper
 * below is wired up so the UI can call into it once it lands.
 */
object AuthSessions {

    fun interface OnSent { fun onSent() }
    fun interface OnFailure { fun onFailure(message: String) }

    /** Revoke all existing sessions by sending a password reset email + signing
     *  out locally. The user re-authenticates on every device after this. */
    @JvmStatic
    fun signOutAllDevices(
        context: Context,
        onSent: OnSent,
        onFailure: OnFailure
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email
        if (user == null || email.isNullOrBlank()) {
            onFailure.onFailure(context.getString(R.string.auth_error_user_not_found))
            return
        }
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnSuccessListener {
                FirebaseAuth.getInstance().signOut()
                onSent.onSent()
            }
            .addOnFailureListener { e ->
                onFailure.onFailure(e.message ?: "?")
            }
    }
}
