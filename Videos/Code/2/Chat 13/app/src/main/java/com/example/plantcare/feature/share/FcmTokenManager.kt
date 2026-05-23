package com.example.plantcare.feature.share

import android.content.Context
import com.example.plantcare.CrashReporter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Wave 2 — FCM token registration.
 *
 * Persists the device's FCM token under
 * `users/{uid}/fcmTokens/{token}` so the Family Share Cloud Function
 * (`onShareInviteCreated`) knows where to send invite pushes.
 *
 * Called from:
 *  - App.onCreate    — registers the current token if user is signed in.
 *  - PlantCareMessagingService.onNewToken — refresh on rotation.
 *  - LoginDialogFragment / AuthStartDialogFragment — register after a
 *    successful sign-in (the user might have signed in *after* the token
 *    was already issued).
 */
object FcmTokenManager {

    private const val TAG = "FcmTokenManager"

    /** Fetches the current FCM token (cached after the first call) and writes
     *  it to Firestore under the signed-in user's subtree. No-op if the user
     *  isn't signed in — the next sign-in will retry. */
    fun registerCurrentToken(context: Context) {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (token.isNullOrBlank()) return@addOnSuccessListener
                writeToken(uid, token)
            }
            .addOnFailureListener { CrashReporter.log(it) }
    }

    /** Called by [PlantCareMessagingService.onNewToken] when FCM rotates the
     *  token. Same write path as [registerCurrentToken] — kept separate so
     *  the messaging service doesn't depend on the firebase-messaging client
     *  twice. */
    fun onTokenRefreshed(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (token.isBlank()) return
        writeToken(uid, token)
    }

    private fun writeToken(uid: String, token: String) {
        val data = mapOf(
            "token" to token,
            "platform" to "android",
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("fcmTokens").document(token)
            .set(data)
            .addOnFailureListener { CrashReporter.log(it) }
    }
}
