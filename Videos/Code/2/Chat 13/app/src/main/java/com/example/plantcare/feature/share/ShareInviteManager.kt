package com.example.plantcare.feature.share

import android.content.Context
import com.example.plantcare.CrashReporter
import com.example.plantcare.Plant
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions

/**
 * Wave 2 — Real Family Share invitations.
 *
 * Owner side: [createInvite] writes a doc under `/shareInvites/{inviteId}`.
 * The Cloud Function `onShareInviteCreated` resolves the invitee's uid by
 * email + sends an FCM push.
 *
 * Invitee side: [acceptInvite] flips the invite's status to "accepted",
 * which triggers `onShareInviteAccepted` to materialize the pointer doc
 * the recipient's Today screen will read.
 *
 * Watered-by-recipient: [markSharedReminderWatered] calls the
 * `markSharedReminderWatered` callable so the recipient can mark a shared
 * reminder done despite Firestore rules denying cross-user writes.
 */
object ShareInviteManager {

    private const val COLL = "shareInvites"

    sealed class CreateResult {
        object Ok : CreateResult()
        data class Failed(val reason: String) : CreateResult()
    }

    fun createInvite(
        context: Context,
        plant: Plant,
        toEmail: String,
        onResult: (CreateResult) -> Unit
    ) {
        val auth = FirebaseAuth.getInstance().currentUser
        if (auth == null) {
            onResult(CreateResult.Failed("not_signed_in"))
            return
        }
        if (!FamilyShareManager.isValidEmail(toEmail)) {
            onResult(CreateResult.Failed("invalid_email"))
            return
        }

        val payload = hashMapOf<String, Any?>(
            "fromUid" to auth.uid,
            "fromEmail" to (auth.email ?: ""),
            "fromName" to (auth.displayName ?: ""),
            "toEmail" to toEmail.trim().lowercase(),
            "toUid" to null,
            "plantId" to plant.id,
            "plantName" to (plant.name ?: ""),
            "status" to "pending",
            "deliveryState" to "pendingResolve",
            "createdAt" to FieldValue.serverTimestamp()
        )

        FirebaseFirestore.getInstance()
            .collection(COLL)
            .add(payload)
            .addOnSuccessListener {
                // Persist locally too so the per-plant share list still
                // reflects the new invitee even if the recipient hasn't
                // accepted yet.
                FamilyShareManager.addEmail(context, plant, toEmail)
                onResult(CreateResult.Ok)
            }
            .addOnFailureListener {
                CrashReporter.log(it)
                onResult(CreateResult.Failed(it.message ?: "firestore_failed"))
            }
    }

    /** Invitee flips status → accepted. Cloud Function picks it up and
     *  materializes the recipient-side pointer. */
    fun acceptInvite(inviteId: String, onResult: (Boolean) -> Unit) {
        val auth = FirebaseAuth.getInstance().currentUser
        if (auth == null) {
            onResult(false)
            return
        }
        FirebaseFirestore.getInstance()
            .collection(COLL).document(inviteId)
            .update(
                mapOf(
                    "status" to "accepted",
                    "toUid" to auth.uid,
                    "acceptedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener {
                CrashReporter.log(it)
                onResult(false)
            }
    }

    fun declineInvite(inviteId: String, onResult: (Boolean) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection(COLL).document(inviteId)
            .update("status", "declined")
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener {
                CrashReporter.log(it)
                onResult(false)
            }
    }

    /** Recipient-side mark-watered. Calls the Cloud Function which performs
     *  the cross-user write with admin privileges. */
    fun markSharedReminderWatered(
        ownerUid: String,
        reminderId: String,
        onResult: (Boolean) -> Unit
    ) {
        val data = hashMapOf(
            "ownerUid" to ownerUid,
            "reminderId" to reminderId
        )
        FirebaseFunctions.getInstance()
            .getHttpsCallable("markSharedReminderWatered")
            .call(data)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener {
                CrashReporter.log(it)
                onResult(false)
            }
    }
}
