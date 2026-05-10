/**
 * PlantCare Cloud Functions — Wave 2 Family Share epic.
 *
 * What lives here:
 *  1. onShareInviteCreated  — Firestore trigger that fires when the owner of a
 *     plant writes a new invite under `/shareInvites/{inviteId}`. Looks up the
 *     invitee's FCM token (if they're a registered PlantCare user) and sends
 *     them a push notification. If the invitee isn't a user yet, the invite
 *     stays "pending" and surfaces the next time they sign in.
 *
 *  2. onShareInviteAccepted — Firestore trigger that fires when an invitee
 *     flips `status` from "pending" to "accepted". Materializes a pointer doc
 *     under `/users/{recipientUid}/sharedPlants/{plantId}` so the recipient's
 *     Today screen can list shared plants without reading another user's
 *     subtree directly. Also sends a confirmation push back to the owner.
 *
 *  3. notifyMarkWatered     — Callable function used when the recipient marks
 *     a shared reminder as watered. Recipients cannot write to the owner's
 *     `users/{ownerUid}/reminders/*` subtree under current Firestore rules,
 *     so this CF performs the write with admin privileges after re-validating
 *     the share grant.
 *
 * Why callable + triggers (not a single REST endpoint): tying the work to
 * Firestore writes lets the Android app stay offline-first — the local
 * Firestore SDK queues the write, and once it lands, the CF kicks in. The
 * client never needs to know whether the CF succeeded synchronously.
 *
 * Deployment:
 *   cd functions && npm install && npm run deploy
 *
 * Required Firebase project plan: Blaze (pay-as-you-go) — free quota covers
 * normal usage but the upgrade is non-negotiable for any CF deploy.
 */

import * as admin from "firebase-admin";
import * as functions from "firebase-functions/v2";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";

admin.initializeApp();
const db = admin.firestore();

// ──────────────────────────────────────────────────────────────────────────
//  Types — mirror the Android-side ShareInvite model.
// ──────────────────────────────────────────────────────────────────────────

interface ShareInvite {
  inviteId: string;
  fromUid: string;
  fromEmail: string;
  fromName?: string;
  toEmail: string;
  toUid?: string | null;        // resolved at create time when invitee already has an account
  plantId: number;
  plantName: string;
  status: "pending" | "accepted" | "declined" | "expired";
  createdAt: admin.firestore.Timestamp;
  acceptedAt?: admin.firestore.Timestamp | null;
}

// ──────────────────────────────────────────────────────────────────────────
//  Trigger 1: onShareInviteCreated
//
//  Path: shareInvites/{inviteId}
//  Fires once per new invite. Tries to resolve invitee uid by email lookup
//  in the auth user record (admin SDK). If found, writes toUid back onto the
//  invite doc and sends an FCM push to the invitee's device. If not found,
//  marks the invite as `awaitingSignup` and exits — the invitee will see it
//  the next time they sign in (the Android app queries `shareInvites where
//  toEmail == currentUser.email`).
// ──────────────────────────────────────────────────────────────────────────

export const onShareInviteCreated = onDocumentCreated(
  "shareInvites/{inviteId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const invite = snap.data() as ShareInvite;
    const inviteId = event.params.inviteId;

    if (!invite || !invite.toEmail || !invite.fromUid || !invite.plantId) {
      logger.warn("Invalid invite payload, ignoring", { inviteId });
      return;
    }

    // Resolve invitee uid (best-effort).
    let toUid: string | null = null;
    try {
      const userRecord = await admin.auth().getUserByEmail(invite.toEmail.toLowerCase());
      toUid = userRecord.uid;
    } catch (e) {
      logger.info("Invitee email not yet a registered user", { email: invite.toEmail });
    }

    await snap.ref.update({
      toUid: toUid,
      // The Android client reads this to render "waiting for signup" vs
      // "delivered" UI on the owner side.
      deliveryState: toUid ? "delivered" : "awaitingSignup",
    });

    if (!toUid) return;

    // Read the invitee's most recent FCM token. We persist tokens in
    // `users/{uid}/fcmTokens/{token}` from the Android side (see
    // FcmTokenManager.kt). One device per token; the latest write wins.
    const tokensSnap = await db
      .collection(`users/${toUid}/fcmTokens`)
      .orderBy("updatedAt", "desc")
      .limit(1)
      .get();

    if (tokensSnap.empty) {
      logger.info("No FCM token for invitee, skipping push", { toUid });
      return;
    }
    const token = tokensSnap.docs[0].id;

    const ownerLabel = invite.fromName || invite.fromEmail;
    await admin.messaging().send({
      token,
      notification: {
        title: "Du wurdest zum Mitgießen eingeladen",
        body: `${ownerLabel} möchte „${invite.plantName}" mit dir teilen.`,
      },
      data: {
        type: "share_invite",
        inviteId: inviteId,
        plantId: String(invite.plantId),
        fromUid: invite.fromUid,
        fromEmail: invite.fromEmail,
      },
      android: {
        priority: "high",
        notification: {
          channelId: "plant_care_reminders",
          clickAction: "OPEN_SHARE_INVITE",
        },
      },
    });
    logger.info("Share invite push sent", { inviteId, toUid });
  }
);

// ──────────────────────────────────────────────────────────────────────────
//  Trigger 2: onShareInviteAccepted
//
//  Fires when an invite doc transitions to status="accepted". Writes the
//  pointer doc on the recipient side and notifies the owner.
// ──────────────────────────────────────────────────────────────────────────

export const onShareInviteAccepted = onDocumentUpdated(
  "shareInvites/{inviteId}",
  async (event) => {
    const before = event.data?.before.data() as ShareInvite | undefined;
    const after = event.data?.after.data() as ShareInvite | undefined;
    if (!before || !after) return;
    if (before.status === "accepted") return;
    if (after.status !== "accepted") return;
    if (!after.toUid) return;

    const inviteId = event.params.inviteId;

    // Materialize the pointer doc the recipient's Today screen reads.
    await db.doc(`users/${after.toUid}/sharedPlants/${after.plantId}`).set({
      ownerUid: after.fromUid,
      ownerEmail: after.fromEmail,
      plantId: after.plantId,
      plantName: after.plantName,
      addedAt: admin.firestore.FieldValue.serverTimestamp(),
      inviteId: inviteId,
    });

    // Push a confirmation back to the owner ("X has accepted your invite").
    const ownerTokensSnap = await db
      .collection(`users/${after.fromUid}/fcmTokens`)
      .orderBy("updatedAt", "desc")
      .limit(1)
      .get();

    if (!ownerTokensSnap.empty) {
      const token = ownerTokensSnap.docs[0].id;
      await admin.messaging().send({
        token,
        notification: {
          title: "Einladung angenommen",
          body: `${after.toEmail} mit-gießt jetzt „${after.plantName}".`,
        },
        data: {
          type: "share_invite_accepted",
          inviteId: inviteId,
          plantId: String(after.plantId),
        },
      });
    }
    logger.info("Share invite accepted, pointer doc materialized", { inviteId });
  }
);

// ──────────────────────────────────────────────────────────────────────────
//  Callable 3: markSharedReminderWatered
//
//  Body: { ownerUid: string, reminderId: string }
//  Validates that the caller has an accepted share grant for the parent plant
//  (reminder's plantId), then performs the watered-write with admin
//  privileges on the OWNER's reminders subtree. This sidesteps the
//  cross-user write block in firestore.rules without weakening the rules
//  themselves (the rules still deny direct cross-user writes; only this CF
//  can do it, and only for verified shared plants).
// ──────────────────────────────────────────────────────────────────────────

export const markSharedReminderWatered = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in to mark shared reminders.");
  }
  const callerUid = request.auth.uid;
  const { ownerUid, reminderId } = request.data as {
    ownerUid?: string;
    reminderId?: string;
  };
  if (!ownerUid || !reminderId) {
    throw new HttpsError("invalid-argument", "Missing ownerUid or reminderId.");
  }

  // Load the reminder to discover its plantId.
  const reminderRef = db.doc(`users/${ownerUid}/reminders/${reminderId}`);
  const reminderSnap = await reminderRef.get();
  if (!reminderSnap.exists) {
    throw new HttpsError("not-found", "Reminder not found.");
  }
  const reminder = reminderSnap.data() as { plantId?: number };
  if (!reminder.plantId) {
    throw new HttpsError("failed-precondition", "Reminder has no plantId.");
  }

  // Validate share grant. The pointer doc was written by trigger 2.
  const grantRef = db.doc(`users/${callerUid}/sharedPlants/${reminder.plantId}`);
  const grantSnap = await grantRef.get();
  if (!grantSnap.exists) {
    throw new HttpsError("permission-denied", "No share grant for this plant.");
  }
  const grant = grantSnap.data();
  if (grant?.ownerUid !== ownerUid) {
    throw new HttpsError("permission-denied", "Share grant ownerUid mismatch.");
  }

  // Perform the watered-write with admin privileges.
  const callerEmail = request.auth.token.email || "";
  await reminderRef.update({
    done: true,
    completedDate: admin.firestore.FieldValue.serverTimestamp(),
    wateredBy: callerEmail,
  });
  logger.info("Shared reminder marked watered by recipient", {
    callerUid,
    ownerUid,
    reminderId,
  });
  return { ok: true };
});

// ──────────────────────────────────────────────────────────────────────────
//  Helper export for unit tests / emulator interaction. Not invoked by
//  Firebase directly.
// ──────────────────────────────────────────────────────────────────────────

export const __test_only__ = { db, admin };

// Keep the import to avoid a "value is declared but never read" lint hit
// when the module surface above ever shrinks during refactors.
void functions;
