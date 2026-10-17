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

    /**
     * Phase 1C (Wave 2 Family Share, 2026-05-14): owner revokes a share.
     *
     * Semantics: deleting the invite doc triggers the
     * `onShareInviteRevoked` Cloud Function, which cascades by deleting
     * the recipient-side pointer doc at
     * `users/{toUid}/sharedPlants/{plantId}` and pushes an
     * `share_invite_revoked` notification so the recipient's app can
     * drop the plant card from view immediately.
     *
     * Caller must have ownership of the invite (Firestore rule
     * `delete: if resource.data.fromUid == request.auth.uid`).
     *
     * Used by [FamilyShareManager.removeEmail] when the owner tells
     * us to drop a sharee. Best-effort callback — local CSV update
     * proceeds even on Firestore failure (the owner's local view
     * stays consistent; the recipient's view will diverge until the
     * next manual refresh).
     */
    fun revokeInvite(inviteId: String, onResult: (Boolean) -> Unit) {
        val auth = FirebaseAuth.getInstance().currentUser
        if (auth == null) {
            onResult(false)
            return
        }
        FirebaseFirestore.getInstance()
            .collection(COLL).document(inviteId)
            .delete()
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener {
                CrashReporter.log(it)
                onResult(false)
            }
    }

    /**
     * Phase 1C: query all open invites the owner sent for a given plant.
     * Used by [FamilyShareManager.removeEmail] to find the invite doc id
     * that needs revoking — the per-plant CSV stores only the email,
     * not the inviteId, so we need this lookup.
     *
     * Returns a list of (inviteId, toEmail) pairs for invites that are
     * NOT in terminal states (pending, accepted, awaitingSignup). A
     * declined or expired invite already has no pointer to clean up.
     */
    fun findOpenInvitesByOwnerForPlant(
        ownerUid: String,
        plantId: Int,
        onResult: (List<Pair<String, String>>) -> Unit
    ) {
        FirebaseFirestore.getInstance()
            .collection(COLL)
            .whereEqualTo("fromUid", ownerUid)
            .whereEqualTo("plantId", plantId)
            .get()
            .addOnSuccessListener { snap ->
                val openStates = setOf("pending", "accepted")
                val out = snap.documents.mapNotNull { d ->
                    val status = d.getString("status") ?: return@mapNotNull null
                    if (status !in openStates) return@mapNotNull null
                    val to = d.getString("toEmail") ?: return@mapNotNull null
                    d.id to to
                }
                onResult(out)
            }
            .addOnFailureListener {
                CrashReporter.log(it)
                onResult(emptyList())
            }
    }

    /**
     * Phase 5: query all pending invites addressed TO the current user.
     * Used by Settings → "Pending invitations" list when the user opens
     * the dialog without having tapped the FCM push (notification
     * permission denied, app reinstalled, etc.).
     *
     * Filters to status="pending" only — accepted/declined/expired
     * invites are out of scope.
     */
    fun queryPendingInvitesForCurrentUser(
        onResult: (List<PendingInvite>) -> Unit
    ) {
        val auth = FirebaseAuth.getInstance().currentUser
        val email = auth?.email?.lowercase()
        if (email.isNullOrBlank()) {
            onResult(emptyList())
            return
        }
        FirebaseFirestore.getInstance()
            .collection(COLL)
            .whereEqualTo("toEmail", email)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { d ->
                    PendingInvite(
                        inviteId = d.id,
                        fromEmail = d.getString("fromEmail") ?: return@mapNotNull null,
                        fromName = d.getString("fromName") ?: "",
                        plantId = (d.getLong("plantId") ?: return@mapNotNull null).toInt(),
                        plantName = d.getString("plantName") ?: ""
                    )
                }
                onResult(list)
            }
            .addOnFailureListener {
                CrashReporter.log(it)
                onResult(emptyList())
            }
    }

    /** Phase 5 — DTO for the pending invitations list in Settings. */
    data class PendingInvite(
        val inviteId: String,
        val fromEmail: String,
        val fromName: String,
        val plantId: Int,
        val plantName: String
    )

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
