# Bug #10 — Server-side Pro verification — manual setup steps

**Created:** 2026-05-14 (round 11)
**Audience:** Fady (project owner)
**Time to complete:** ~30 minutes
**One-time setup, not per-deploy.**

---

## Why this is necessary

The code change in round 11 made `BillingManager` route every Pro grant through a Cloud Function (`verifyPurchase`) which validates the purchase against Google Play's Developer API. Without the manual setup below, the Cloud Function will start up but fail every verification call with `server_misconfigured` — which on the client side falls back to "optimistic provisional grant" (matching pre-Bug-#10 behavior). So the security fix only kicks in once these steps are done; before that, you're no worse off than today.

---

## Step 1 — Create a Service Account in Google Cloud Console

1. Open <https://console.cloud.google.com/iam-admin/serviceaccounts>.
2. Top-left selector: pick the GCP project that backs your Firebase project (the dropdown shows project IDs — match it to what's in `app/google-services.json` under `project_info.project_id`).
3. Click **+ Create Service Account**.
4. Fill in:
   - **Service account name:** `plantcare-play-verifier`
   - **Description:** "Verifies Google Play purchases for the PlantCare Pro server-side validation Cloud Function."
5. Click **Create and Continue**.
6. **Skip the optional roles** (we'll grant access via Play Console, not GCP IAM). Click **Continue** then **Done**.
7. You're back on the SA list. Click the new `plantcare-play-verifier@...` row.
8. Top tabs → **Keys** → **Add Key** → **Create new key** → **JSON** → **Create**.
9. Browser downloads `plantcare-play-verifier-XXXXX.json`. **Save it somewhere safe and DO NOT commit it to git.** You'll paste its contents in Step 3.

---

## Step 2 — Grant the Service Account access in Google Play Console

1. Open <https://play.google.com/console/>.
2. Pick the PlantCare app.
3. Left sidebar → bottom → **Setup** → **API access**.
4. Find the section **"Service accounts"** — your `plantcare-play-verifier@...` should appear automatically (Google links GCP and Play Console when both are under the same Google account).
   - If it's not there: click **Link Google Cloud project** at the top, link the same project from Step 1, then refresh.
5. Next to the SA, click **Grant access**.
6. **Account permissions** tab:
   - **App access:** Select PlantCare (the only app, or check it explicitly).
   - **Account permissions:** Enable **"View financial data, orders, and cancellation survey responses"**. This is the minimum needed for `purchases.subscriptions.get` and `purchases.products.get`.
   - All other permissions OFF.
7. Click **Invite user** (the button name even though it's a service account).
8. Wait ~5 minutes for the grant to propagate. Play Console grants are not instantaneous.

---

## Step 3 — Store the SA key as a Firebase Functions secret

The Cloud Function loads the key via `defineSecret("PLAY_SA_KEY")` — this is a Cloud Secret Manager-backed secret, available to the function at runtime without leaking into the build.

1. Open a terminal in the project root: `cd "C:\Users\fadym\Videos\Code\2\Chat 13"`.
2. Set the secret with the contents of the JSON file you downloaded in Step 1.9:

   **Windows PowerShell:**
   ```powershell
   $env:GOOGLE_APPLICATION_CREDENTIALS = $null  # avoid auth confusion
   firebase functions:secrets:set PLAY_SA_KEY < "C:\path\to\plantcare-play-verifier-XXXXX.json"
   ```

   **Or Linux/macOS bash:**
   ```bash
   firebase functions:secrets:set PLAY_SA_KEY < /path/to/plantcare-play-verifier-XXXXX.json
   ```

   The CLI prompts you to confirm the project + secret name. Approve.
3. The secret is now stored. The CLI prints "Done!" with the secret version (e.g. `Created PLAY_SA_KEY[1]`).

> **Security note:** The downloaded JSON key file from Step 1.9 should be deleted from your local disk AFTER the secret is set. The secret in Firebase / Cloud Secret Manager is the canonical store; the local copy was only needed for upload.

---

## Step 4 — Deploy the Cloud Functions

```bash
cd "C:\Users\fadym\Videos\Code\2\Chat 13\functions"
npm install
npm run build
firebase deploy --only functions
```

This deploys (or updates):
- `onShareInviteCreated` (existing)
- `onShareInviteAccepted` (existing)
- `onShareInviteRevoked` (existing)
- `markSharedReminderWatered` (existing)
- **`verifyPurchase`** (new in round 11)

The first deploy of `verifyPurchase` may take ~2 minutes because it pulls down the `googleapis` npm package on the Cloud Function build container. Subsequent deploys are cached.

---

## Step 5 — Deploy the updated Firestore rules

```bash
cd "C:\Users\fadym\Videos\Code\2\Chat 13"
firebase deploy --only firestore:rules
```

This makes the `users/{uid}/billing/proStatus` doc **write-only-via-admin-SDK** (i.e. only the `verifyPurchase` CF can mutate it). Without this deploy, the new tighter rule isn't active and the security model degrades to "client trusted on writes" — same as pre-Bug-#10.

> **Compatibility check before deploy:** the rule allows `read: if isOwner(userId)` so the existing `importProStatusForCurrentUser` cloud-restore on sign-in still works (it reads, doesn't write). The pre-Bug-#10 client write path in `ProStatusManager.setPro` is no longer attempted by the round-11 code, so the new "deny writes" rule won't trigger any client errors in practice.

---

## Step 6 — Verify with a real purchase

1. Open Google Play Console → **Setup** → **License testing**.
2. Add your test Google account to the licence-tester list. Save.
3. Install the app on a real device signed in with that test account (License Testing only works with the Internal Test track or signed-by-upload-key APKs, NOT with `assembleDebug`).
4. Trigger the paywall → make a purchase. Pick the cheapest SKU (`monthly_pro`).
5. **Expected:** purchase completes, Pro features unlock. Open Firebase Console → Firestore → navigate to `users/{your-uid}/billing/proStatus`. The doc should show:
   - `isPro: true`
   - `lastUpdatedMs: <very recent timestamp>`
   - `verifiedAt: <same timestamp>`
   - `productId: "monthly_pro"`
   - `productType: "subs"`

6. **Inspect Cloud Function logs:**
   ```bash
   firebase functions:log --only verifyPurchase
   ```
   You should see one entry: `Purchase verification complete { verified: true }`.

7. **Negative test:** revoke the test purchase from Play Console → cancel subscription. Wait 5 min for Play to propagate. Re-launch the app → connect() runs → `verifyPurchase` returns `verified: false` → Firestore proStatus updates to `isPro: false` → Pro UI gates revert.

---

## Cost expectations

- **Cloud Functions invocations:** ~1 per app launch + 1 per purchase. For 1k DAU that's ~30k invocations/month. Free tier covers 2M/month — comfortably free.
- **Cloud Secret Manager:** $0.06 per active secret per month. One secret. Negligible.
- **Play Developer API quota:** 200k requests/day per project (default). Each app launch uses 1 request per cached purchase. For 1k DAU * 1 sub ≈ 1k req/day — 0.5% of quota.
- **Cloud Firestore:** writes from CF cost the same as client writes. The `verifyPurchase` write happens only on actual purchase events (rare) + on revocation (also rare). Negligible.

Net incremental cost vs. pre-Bug-#10: **< $1/month** at the 1k DAU scale.

---

## Rollback

If anything in this chain misbehaves and you need to revert to the pre-Bug-#10 trust-the-client model:

1. **Revert the rule first** (so client writes work again):
   ```
   git revert <round-11-rules-commit>
   firebase deploy --only firestore:rules
   ```
2. **Re-deploy the pre-round-11 BillingManager + ProStatusManager** code (revert the round-11 code commit, re-build APK).

The Cloud Function can stay deployed (it'll just sit unused). Don't delete the SA or revoke its Play Console access during a temporary rollback — re-setting them up takes the full Step 1-2 again.

---

## Future hardening (post-MVP — NOT in scope for round 11)

- **Real-time webhook from Play → CF.** Right now we only re-verify on app launch. Play Developer Notifications can push a "subscription cancelled / refunded" event directly to a CF, which would then revoke Pro within minutes instead of waiting for the user's next launch.
- **Per-installation token binding.** Right now any rooted device with a stolen purchaseToken can pass verification. Binding the token to a specific Play installId would close that hole. Not common attack vector for an indie app.
- **Server-side replay protection.** Track verification attempts per token; rate-limit to 1/hour. Right now a malicious script could spam the CF call.
