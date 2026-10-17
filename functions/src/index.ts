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
import { onDocumentCreated, onDocumentDeleted, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { defineSecret } from "firebase-functions/params";
import { google } from "googleapis";

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
          // Bug #9 (2026-05-14): channel id migrated to v2. The legacy
          // `plant_care_reminders` channel is deleted on the recipient's
          // first PlantNotificationHelper.createNotificationChannel call
          // after upgrade, so any FCM push routed to it would land
          // ungrouped. Using the v2 id keeps share-invite pushes inside
          // the same category as watering reminders — same user-controlled
          // channel settings, no separate permission re-prompt.
          channelId: "plant_care_reminders_v2",
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
//  Trigger 4: onShareInviteRevoked  (Phase 1B, 2026-05-14)
//
//  Path: shareInvites/{inviteId}
//  Fires when an invite doc is DELETED (the owner revokes the share, or
//  the recipient declines and the owner cleans up the doc later). If the
//  invite had reached "accepted" state (toUid populated), we cascade by
//  deleting the recipient-side pointer doc at
//  `users/{toUid}/sharedPlants/{plantId}` so the recipient loses access
//  immediately on their next listener tick.
//
//  Rationale: without this, an owner who removes a sharee from their
//  per-plant share list would still leave the recipient's app showing
//  the plant indefinitely. The Firestore rules check the pointer doc
//  for read access, so removing the pointer cuts off all reads + the
//  markSharedReminderWatered callable on the next attempt (it
//  re-validates the grant on every call).
//
//  Best-effort: logs and exits if anything is missing — there's nothing
//  to retry meaningfully.
// ──────────────────────────────────────────────────────────────────────────

export const onShareInviteRevoked = onDocumentDeleted(
  "shareInvites/{inviteId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const invite = snap.data() as ShareInvite;
    const inviteId = event.params.inviteId;

    if (!invite || !invite.toUid || !invite.plantId) {
      // No grant ever materialized — nothing to clean up.
      logger.info("Revoked invite had no recipient pointer to clean", { inviteId });
      return;
    }

    const pointerRef = db.doc(`users/${invite.toUid}/sharedPlants/${invite.plantId}`);
    try {
      await pointerRef.delete();
      logger.info("Recipient pointer doc deleted on invite revocation", {
        inviteId,
        toUid: invite.toUid,
        plantId: invite.plantId,
      });
    } catch (e) {
      logger.warn("Failed to delete recipient pointer on revocation", {
        inviteId,
        error: String(e),
      });
    }

    // No FCM push on revoke. The recipient's SharedPlantsRepository
    // listener on `users/{toUid}/sharedPlants/*` will tick automatically
    // when the pointer doc deletion above propagates — both online
    // (sub-second) and on reconnect for offline devices. A push here
    // would produce a notification with empty body (data-only pushes
    // get rendered with the app_name fallback by the Android
    // PlantCareMessagingService) which is worse UX than silent
    // background reconciliation. If the recipient ISN'T inside
    // SharedPlantsActivity at the time of revoke, the listener is
    // stopped — but the next time they open it, .start() pulls the
    // current (post-deletion) pointer collection and the plant simply
    // doesn't appear. No data loss either way.
  }
);

// ──────────────────────────────────────────────────────────────────────────
//  Callable 5: verifyPurchase  (Bug #10 — server-side Pro verification, 2026-05-14)
//
//  Body: { productId: string, purchaseToken: string, productType: "subs" | "inapp" }
//  Returns: { verified: boolean, isPro: boolean, reason?: string }
//
//  Validates a Google Play purchase against the Play Developer API and
//  writes the verified state into Firestore at
//  `users/{uid}/billing/proStatus`. The Firestore rule (Bug #10 Phase 4)
//  forbids client-side writes to that doc, so this is the ONLY path to
//  set Pro=true cloud-side; client mirrors the doc into local prefs but
//  the cloud is the canonical source of truth.
//
//  Pre-fix: BillingManager trusted the local Billing SDK response and
//  set Pro=true directly. Lucky Patcher / billing-emulator apps on
//  rooted devices intercept the SDK callback and forge a "PURCHASED"
//  state — granting permanent Pro with no payment. Industry standard
//  fix is server-side validation against the Play Developer API which
//  cross-checks the purchaseToken against Google's records.
//
//  Required infra (manual setup by Fady — see PROGRESS round 11
//  "Manual deploys required" section):
//   1. Create a service account in Google Cloud Console with role
//      "Service Account User".
//   2. Grant that SA "View financial data, orders, and cancellation
//      survey responses" in Google Play Console → Settings → API
//      access → grant access on the published app.
//   3. Generate a JSON key for the SA and store it as a Firebase
//      Functions secret named `PLAY_SA_KEY` (full JSON contents,
//      escaped). The function loads it via the `defineSecret` API at
//      cold start.
//
//  Without that secret configured, the function logs a clear error
//  and returns verified=false — purchases stay in their pre-CF
//  provisional grant state on the client (no security regression vs.
//  the pre-Bug #10 baseline).
// ──────────────────────────────────────────────────────────────────────────

const PLAY_SA_KEY = defineSecret("PLAY_SA_KEY");
// Android packageName as it appears in Google Play Console — must match
// the applicationId in app/build.gradle EXACTLY (com.fadymerey.plantcare
// for prod-release; the .dev suffix flavor is not server-validated).
const ANDROID_PACKAGE_NAME = "com.fadymerey.plantcare";

interface VerifyPurchaseRequest {
  productId?: string;
  purchaseToken?: string;
  productType?: "subs" | "inapp";
}

interface VerifyPurchaseResponse {
  verified: boolean;
  isPro: boolean;
  reason?: string;
}

export const verifyPurchase = onCall(
  { secrets: [PLAY_SA_KEY] },
  async (request): Promise<VerifyPurchaseResponse> => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in to verify purchases.");
    }
    const callerUid = request.auth.uid;
    const { productId, purchaseToken, productType } = request.data as VerifyPurchaseRequest;

    if (!productId || !purchaseToken || !productType) {
      throw new HttpsError("invalid-argument",
        "Missing productId, purchaseToken, or productType.");
    }
    if (productType !== "subs" && productType !== "inapp") {
      throw new HttpsError("invalid-argument",
        `productType must be "subs" or "inapp" (got "${productType}").`);
    }

    // Load service account credentials from Firebase Functions secret.
    // PLAY_SA_KEY contains the FULL JSON of the SA key (including
    // private_key, client_email, etc.). If the secret isn't configured
    // we fail soft — log + return verified=false rather than throwing
    // an unhandled exception that the client would render as a generic
    // error toast.
    let saKey: { client_email: string; private_key: string };
    try {
      saKey = JSON.parse(PLAY_SA_KEY.value());
    } catch (e) {
      logger.error("PLAY_SA_KEY secret missing or malformed — see PROGRESS Bug #10 manual setup", { error: String(e) });
      return { verified: false, isPro: false, reason: "server_misconfigured" };
    }

    // Build authenticated Play Developer API client.
    const auth = new google.auth.JWT({
      email: saKey.client_email,
      key: saKey.private_key,
      scopes: ["https://www.googleapis.com/auth/androidpublisher"],
    });
    const androidpublisher = google.androidpublisher({ version: "v3", auth });

    // Query the appropriate endpoint based on product type.
    let purchaseValid = false;
    let reason: string | undefined;
    try {
      if (productType === "subs") {
        const resp = await androidpublisher.purchases.subscriptions.get({
          packageName: ANDROID_PACKAGE_NAME,
          subscriptionId: productId,
          token: purchaseToken,
        });
        const data = resp.data;
        // paymentState 1 = received, 2 = free trial.
        // expiryTimeMillis must be in the future for the sub to count as
        // "currently entitled". cancelReason absent OR present-but-still-
        // entitled (Google maintains entitlement until the period ends
        // even after cancel).
        const expiryMs = Number(data.expiryTimeMillis ?? 0);
        const stillEntitled = expiryMs > Date.now();
        const validPayment = data.paymentState === 1 || data.paymentState === 2;
        if (stillEntitled && validPayment) {
          purchaseValid = true;
        } else {
          reason = !stillEntitled ? "subscription_expired" : "payment_state_invalid";
        }
      } else {
        // inapp (lifetime) — purchases.products.get returns purchaseState
        // 0=purchased, 1=cancelled, 2=pending. Only 0 grants Pro.
        const resp = await androidpublisher.purchases.products.get({
          packageName: ANDROID_PACKAGE_NAME,
          productId,
          token: purchaseToken,
        });
        const data = resp.data;
        if (data.purchaseState === 0) {
          purchaseValid = true;
        } else {
          reason = data.purchaseState === 1 ? "purchase_cancelled" : "purchase_pending";
        }
      }
    } catch (e: unknown) {
      const err = e as { code?: number; message?: string };
      logger.warn("Play Developer API rejected the verification request", {
        callerUid,
        productId,
        productType,
        code: err.code,
        message: err.message,
      });
      // 404 = unknown token (forged or wrong env). 401/403 = SA mis-scoped.
      // Either way we return verified=false; client treats it as not Pro.
      return {
        verified: false,
        isPro: false,
        reason: err.code === 404 ? "purchase_not_found" : "play_api_error",
      };
    }

    // Persist the verified state. lastUpdatedMs uses serverTimestamp
    // semantics via Date.now() (Firestore serverTimestamp() requires a
    // FieldValue which complicates the merged write — Date.now() on the
    // CF runtime is close enough to server time; the Android side reads
    // it as a long).
    const now = Date.now();
    const proStatusDoc = {
      isPro: purchaseValid,
      lastUpdatedMs: now,
      verifiedAt: now,
      productId,
      productType,
      // We DON'T persist the purchaseToken — it's secret. Storing it in
      // Firestore expands the blast radius of a hypothetical billing/
      // doc leak. The client retains the token locally and re-sends it
      // on every verification call.
    };
    try {
      await db.doc(`users/${callerUid}/billing/proStatus`).set(proStatusDoc, { merge: true });
    } catch (e) {
      logger.error("Failed to persist verified Pro status", { callerUid, error: String(e) });
      throw new HttpsError("internal", "Failed to persist verification result.");
    }

    logger.info("Purchase verification complete", {
      callerUid,
      productId,
      productType,
      verified: purchaseValid,
      reason,
    });

    return { verified: purchaseValid, isPro: purchaseValid, reason };
  }
);

// ──────────────────────────────────────────────────────────────────────────
//  Helper export for unit tests / emulator interaction. Not invoked by
//  Firebase directly.
// ──────────────────────────────────────────────────────────────────────────

export const __test_only__ = { db, admin };

// Keep the import to avoid a "value is declared but never read" lint hit
// when the module surface above ever shrinks during refactors.
void functions;
