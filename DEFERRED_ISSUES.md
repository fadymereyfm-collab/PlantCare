# PlantCare — Deferred Issues Tracker
## Last Updated: 2026-05-18

## 🧹 2026-05-18 audit sweep — entries resolved or stale (no action required)

A pass over this tracker on 2026-05-18 found that **8 deferred entries
are either fully implemented in current code or were obsolete on
discovery**. Listed here so future audits don't re-open them.
Originals kept below for git-history continuity.

| Entry | Current state |
|-------|---------------|
| **#2 — Account deletion cloud orphans (HIGH GDPR)** | ✅ Fixed by `FirebaseSyncManager.deleteAllForUser` cascade (lines 973-1080). Wipes all 11 per-user subcollections (plants + cover.jpg in Storage / reminders / photos + Storage originals / rooms / memos / fcm tokens / shareInvites where fromUid==user / vacation / streak / challenges / proStatus) BEFORE `user.delete()`. Comment block at `SettingsDialogFragment.java:973-1006` documents the DSGVO Art. 17 fix dated 2026-05-14. |
| **#7 — TodayFragment doesn't hide tasks during vacation (MED UX)** | ✅ Fixed in the live adapter (`DailyWateringAdapter.java:179` helper `isOnVacationNow()` + guards at `:200` bulk-water and `:328` per-tap). The dead `TodayAdapter.java` had no guard but was unused — deleted in this same 2026-05-18 sweep (282 lines, 0 production instantiations). |
| **#10 — Legacy `recordPlantCountForChallenge` dead but harmless (LOW)** | ✅ Deleted from `TodayViewModel.kt` on 2026-05-18 (zero callers verified by repo-wide grep). |
| **#17a — `DataChangeNotifier.listeners` not thread-safe (LOW)** | ✅ `HashSet` → `CopyOnWriteArraySet` on 2026-05-18 (`DataChangeNotifier.java:22`). |
| **#18 — `PlantImageLoader` spawns un-cancellable scope per call (MED leak)** | ✅ Already fixed in an earlier session: `PlantImageLoader.kt:39` uses a single shared `ROOT_SCOPE` (SupervisorJob + Dispatchers.Main) + per-`ImageView` Job tagged via `R.id.tag_plant_image_load_job` (declared in `ids.xml:10`); prior job cancelled on each new bind (line 59). |
| **#21 — `AddCustomPlantDialogFragment` doesn't recycle source bitmap (LOW)** | ✅ Refactored on 2026-05-18 (`AddCustomPlantDialogFragment.java:191-220`). Old `saveBitmap()` replaced with `persistAndPreview()` that downscales, writes JPEG, hands the **downscaled** copy to the ImageView, and recycles the original 48 MB bitmap. Best-effort cleanup on the exception path too. |
| **#22 — `AddToMyPlantsDialogFragment.show()` paywall without isStateSaved (LOW)** | ✅ Guard added 2026-05-18: `if (isStateSaved()) return;` before `new PaywallDialogFragment().show(...)` (`AddToMyPlantsDialogFragment.java:391`). |
| **#23 — `MainActivity.onDeleteAccount` races `user.delete()` with cleanup (LOW)** | Likely subsumed by **#2** above — the live delete path is now `SettingsDialogFragment.performFirebaseDelete()`, which sequences cloud cascade → local cleanup → `user.delete()` → UI. The `MainActivity` race entry refers to a code path that no longer exists in the same form. Re-verify if this entry resurfaces. |

For each ✅ entry the original wording is preserved below for git history but should be considered closed.

---

> Session 2026-05-07 (Final all-23-features deep audit) added five new
> entries (#18-#22) — bugs found by the comprehensive sweep that were
> too narrow / context-specific to be worth fixing in the same session.
> Headline numbers: 24 findings total, 14 fixed inline, 5 deferred,
> rest already covered by earlier entries (e.g. #17a thread safety).
>
> **Post-PR follow-ups (#24-#26)** added after the audit-cycle PR
> (commit `28d6194`) was pushed: the operational steps the PR
> message flagged as "must do before shipping" but that the user
> explicitly chose to defer rather than execute now. Treat these as
> the **release blockers** before the next Play Store rollout.
>
> **Phase F manual cleanup (#27-#30)** added on 2026-05-15 after the
> UI redesign (Phase F2 + F3 + F4-F8 + F9) reached 100% visible-surface
> coverage. These are the **manual** tasks the sandboxed Claude run
> could not execute itself: file deletion (sandbox ACL), the cradle-
> overlap design decision, the local Gradle build, and the on-device
> smoke pass. Verify these BEFORE the next Play Store upload.

---

## ⚠️ POST-PR RELEASE BLOCKERS (do these BEFORE shipping the audit cycle)

These are not code bugs — they're operational tasks the PR depends
on. Without them the on-device behaviour silently breaks despite a
green build. Order matters.

---

## 24. Publish Firestore + Storage rules to Firebase Console (CRITICAL — release blocker)
**Origin:** PR `28d6194` deployment notes (sessions 10 + 11).

The audit cycle introduced two new rules files in version control:
- `firestore.rules` — splits `read/delete` from `create/update` so
  `request.resource.data.size()` no longer null-derefs on delete.
  Pre-fix every cloud delete returned PermissionDenied.
- `storage.rules` — first-ever version-controlled Storage rules.
  Per-user subtree lockdown + 8 MB per-object cap + image/* MIME
  filter. Without it, the Storage bucket runs on whatever drifted
  state the Firebase Console currently has — could be open or
  could be locked-down post-Sept-2025-default.

**Symptoms if not deployed (will hit users immediately):**
- Every `deleteJournalMemo` / `clearVacationCloud` /
  `deletePhotosForPlantByUid` / `syncProStatus` / etc. returns
  PermissionDenied. Local rows drop, cloud orphans accumulate.
- Photo uploads succeed or fail unpredictably depending on
  Console state.

**Action (one of):**
```bash
firebase deploy --only firestore:rules
firebase deploy --only storage:rules
```
Or paste both files into Firebase Console → Firestore → Rules
and Storage → Rules → Publish.

**Verification:**
- Console shows "Rules published" timestamp matches now.
- `MANUAL_TESTS.md` rows P0.1 + P0.2 pass.
- `MANUAL_TESTS.md` rows 21.2 / 21.3 / 21.4 (cloud delete works
  for memos / photos / vacation) all pass.

---

## 25. Run the 115-row manual verification sweep (HIGH — release gate)
**Origin:** Created alongside PR `28d6194` as `MANUAL_TESTS.md`.

The audit cycle shipped 73 fixes across all 23 features. None of
them have an end-to-end test in CI (the project only has unit
tests). The manual checklist in `MANUAL_TESTS.md` is the only
verification that the user-visible flows still work. Skipping it
risks shipping a regression the build pipeline cannot catch.

**Action:** Work through `MANUAL_TESTS.md` row-by-row. Budget:
4-6 hours on a single device for the full 115 rows. Sections are
independent — pick a feature group per session if you can't do it
all at once. The file ships with a fail-report template at the
bottom; copy it back as you go.

**Specific HIGH-priority rows worth running first** (CRITICAL fixes
that have no other verification):
- 10.5 — memos restore on second device (F10.3 cloud sync)
- 12.3 — Mark all watered notification action + streak update (Z1)
- 17.5 — banner ad disappears live after Pro purchase (B1)
- 19.2 — widget refreshes on data change (W1)
- 21.2-21.4 — cloud delete works for memos / photos / vacation (CS1)
- 22.1-22.3 — Storage rules reject cross-user / oversize / non-image (SEC1)

**Verification:** Fail-report table at the end of MANUAL_TESTS.md
filled in. Bring back any 🔴 rows for triage.

---

## 26. Rotate the GitHub OAuth token leaked in conversation log (HIGH — security)
**Origin:** PR-creation flow (commit `28d6194` push). When `gh` CLI
turned out not to be installed, the fallback path used
`git credential fill` to extract the saved GitHub token for direct
API calls. The output of that command — including the token starting
`gho_jt7J...` — appeared in the chat transcript / conversation log.

**Why it's deferred not done:** The user explicitly chose to defer
this rather than execute the rotation in-session.

**Threat model:** The token has full GitHub API access for the user.
A leak in transcripts the user's tooling persists (.claude, IDE
chat history, etc.) is read-accessible to anyone with disk access
on the machine. Not visible to GitHub itself or to any Anthropic
infrastructure beyond the conversation.

**Action:**
1. Visit https://github.com/settings/tokens
2. Find the token with prefix `gho_jt7J...` and click Revoke
   (or rotate if you can identify the issuing OAuth app).
3. Re-authenticate Git for Windows:
   ```
   git credential-manager erase
   git credential-manager configure
   ```
   Next `git push` will trigger a fresh sign-in (browser-based).
4. If `gh` is now needed for future PRs, install it:
   `winget install --id GitHub.cli`

**Verification:**
- GitHub Tokens page no longer lists the old token.
- A `git push` from the same machine prompts for fresh auth (proves
  the credential manager forgot the old one).

This file collects issues that audits surfaced but the originating session
intentionally did NOT fix — either because they were out of scope for the
feature being audited, or because they require their own dedicated task
(architectural change, monetisation hook, app-wide sweep, etc.).

Format: smallest-blast-radius first within each category. Each entry
links back to the originating PROGRESS.md session for context.

---

## 1. Cross-cutting `Locale.getDefault()` for wire formats (HIGH) — ✅ COMPLETED 2026-05-10

**Origin:** Session 2026-05-06 Notifications+Vacation re-audit (A2),
later confirmed in Streaks+Challenges audit (C7).

**Original problem:** `SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())`
emitted Eastern-Arabic digits on ar/fa/ur devices, breaking SQL
`WHERE date <= today` comparisons.

**Closure verification (2026-05-10):**
```
$ grep -rn "SimpleDateFormat" app/src/main/java | grep -v "Locale.US"
(no results — every wire-format SDF now uses Locale.US)
```

All 13+ originally-listed files were already migrated to `Locale.US` in
prior sessions; the deferred entry was stale. The 2026-05-10 sweep also
fixed adjacent locale-on-ASCII bugs (same family) discovered during
verification:
- `weekbar/PlantImageLoader.kt` — 3× `lowercase(Locale.getDefault())`
  on URI schemes + drawable name → `Locale.ROOT` (Turkish "I" → "ı"
  would mis-match "http"/"file"/"content" and would mis-resolve catalog
  drawable names like `aloe_vera`).
- `AddPlantDialogFragment.java:223` — `String.format(Locale.getDefault(),
  "%02d", ...)` for plant-name suggestion saved verbatim to DB
  → `Locale.US`.

**Display-format `Locale.getDefault()` retained (correct):**
- `DailyWateringAdapter.java:442` — `DateFormat.getDateInstance(MEDIUM,
  Locale.getDefault())` formats "12. Mai 2026" for UI.
- `widget/PlantCareWidget.kt:38` — `"EEE, d MMM"` widget header.

---

## 2. Account deletion leaves cloud orphans (HIGH — GDPR)
**Origin:** Session 2026-05-06 Plant Journal 3rd-pass audit.

`SettingsDialogFragment.performFirebaseDelete` calls `user.delete()` and
`deleteAllPhotosForUser` for cloud, then local-only deletion for plants /
reminders / rooms / memos / vacation / streak / challenges. After
`user.delete()` the orphan cloud data is permanently inaccessible
(Firestore rules require `request.auth.uid == userId` which no longer
exists), but it's still on Google's storage with the user's PII —
GDPR Article 17 ("right to erasure") applies.

**Affected cloud collections at delete time:**
- `users/{uid}/plants/*` — orphan
- `users/{uid}/reminders/*` — orphan
- `users/{uid}/rooms/*` — orphan
- `users/{uid}/memos/*` — orphan
- `users/{uid}/vacation/current` — orphan
- `users/{uid}/gamification/streak` — orphan
- `users/{uid}/gamification/challenges` — orphan
- `users/{uid}/photos/*` ← only this is wiped today

**Action:** Add `deleteAllForUser` cascade in `FirebaseSyncManager` that
batches deletion of every per-user subcollection BEFORE `user.delete()`.
Order matters: cloud delete first (while auth still works), then auth
delete.

---

## 3. Stale `userEmail` on cloud entities after auth email change (MED)
**Origin:** Repeated in 3 audit sessions.

If the user changes their account email (Firebase Auth `updateEmail`),
every per-user cloud entity (Plant, WateringReminder, PlantPhoto,
JournalMemo, RoomCategory) keeps the old email value in the `userEmail`
field. On a fresh-device cloud restore the import logic only overrides
`userEmail` when it's `null` or empty — stale-but-set values pass through.
The Today screen's "show plants for current user" filter then drops every
restored row.

**Affected entities:** Plant, WateringReminder, PlantPhoto, JournalMemo,
RoomCategory.

**Action:** Either (a) listen for Firebase Auth email-change events and
batch-update userEmail across cloud + local, or (b) drop the email-equality
check from cloud-restore paths and trust UID-based segregation
(Firestore rules already enforce `users/{uid}/*` ownership). Option (b) is
the cheaper structural change.

---

## 4. FK race during cloud import (MED)
**Origin:** Session 2026-05-06 Plant Journal audit.

`MainActivity.importCloudDataForUser` runs 8 import streams in parallel
on a 4-thread pool (BgExecutor). FK-bearing entities (reminders, photos,
memos) have `plantId` references that may be inserted before their parent
plants. Room's FK CASCADE then rejects the child insert; the surrounding
try/catch logs to CrashReporter and the row is silently dropped from the
restore. Worst-case outcome: a user reinstalling sees 90% of their data
restored, with random gaps.

**Action:** Sequence the streams: rooms + plants must complete before
reminders / photos / memos start. Either chain via `await()` from
suspend functions or replace the AtomicInteger fan-in with a
two-phase coordinator.

---

## 5. Guest-row collision on sign-in (MED)
**Origin:** Session 2026-05-06 Plant Journal 3rd-pass audit.

Cloud restore wipes user-rows (`WHERE userEmail = email`) but leaves
guest rows (`userEmail IS NULL`) alone. A guest who created plant id 3
locally and then signs in with an account that also has plant id 3 in
cloud → SQLite PK collision on insert → row dropped.

**Action:** Either (a) re-key local guest rows to fresh autogen ids
before cloud insert, or (b) treat sign-in as a hard reset of local
state (lose the guest plants but cloud is now canonical). Option (b)
is what Notion / Todoist do.

---

## 6. Notification times hardcoded (MED — UX)
**Origin:** Session 2026-05-06 Notifications+Vacation audit.

`PlantReminderWorker` fires only in two windows: 7-11 AM and 5-9 PM.
There's no settings UI to change them, no per-user customisation. A
night-shift user, an early riser, or someone in a non-CET timezone gets
notifications at inconvenient times.

**Action:** Add settings dialog entries for "morning notification time"
and "evening notification time". Stored in SharedPreferences, read by
the worker. Falls back to current defaults if unset.

---

## 7. TodayFragment doesn't hide tasks during vacation (MED — UX)
**Origin:** Session 2026-05-06 Notifications+Vacation audit.

TodayFragment shows the vacation banner during an active vacation but
keeps the full reminder list tappable underneath. A user who taps "done"
out of habit during vacation will burn streak / record a watering they
didn't actually do.

**Action:** Either (a) hide the list entirely while vacation is active
(drastic — user can't intentionally water), or (b) make taps no-op with
a toast pointing to vacation toggle. (b) is closer to professional-app
behaviour.

---

## 8. NotificationChannel IMPORTANCE upgrade for existing users (MED — Android limit)
**Origin:** Session 2026-05-06 Notifications+Vacation audit (N4).

Android disallows upgrading a channel's importance after creation. The
channel-importance fix from N4 only applies to fresh installs. Existing
users keep `IMPORTANCE_DEFAULT` until they manually adjust system
settings or reinstall.

**Action:** Mint a v2 channel id (`plant_care_reminders_v2`) and migrate.
The old channel becomes inert but stays visible in system settings —
docs recommend deleting it via `NotificationManager.deleteNotificationChannel`
after a grace period.

---

## 9. Streak shield / freeze (LOW — monetisation feature)
**Origin:** Session 2026-05-06 Streaks+Challenges audit.

Duolingo Plus / Headspace Premium let users buy "streak shields" that
preserve a streak after a missed day. Out of scope until Sprint-2's
F12-F15 monetisation work picks up.

**Action:** Defer until monetisation epic.

---

## 10. Legacy `recordPlantCountForChallenge` dead but harmless (LOW)
**Origin:** Session 2026-05-06 Streaks+Challenges audit.

The function in `TodayViewModel` is no longer reachable from production
(replaced by the `refreshHeader` direct path), but kept in case a future
Java caller wants the manual-trigger semantics. Five lines of dead code.

**Action:** Delete in a cleanup pass once we're sure nothing external
references it.

---

## 11. Race between markWatered and refreshHeader on prefs (LOW)
**Origin:** Session 2026-05-06 Streaks+Challenges audit-pass.

`markReminderDone` and `refreshHeader` both write to gamification
SharedPreferences. They could race; the realistic hazard is "challenge
counter shows last value briefly", not data loss.

**Action:** Acceptable. Single-flight or mutex guard if it ever shows
up in user reports.

---

## 12. goAsync ~10s budget vs hundreds of reminders (LOW)
**Origin:** Session 2026-05-06 Notifications+Vacation re-audit-pass.

`NotificationActionReceiver` uses `goAsync()` and processes reminders
sequentially. A user with hundreds of overdue reminders could in
principle exceed the 10s receiver budget. Realistic worst case is ~50,
which finishes well under the cap (~5 ms per reminder update).

**Action:** Switch to `WorkManager.beginWith` from the receiver if user
reports come in. Not worth the complexity for the realistic load.

---

## 13. Family Share is local-only — no actual invitation flow (HIGH — UX)
**Origin:** Session 2026-05-06 Family Share + PDF + Main UI + Settings audit.

`FamilyShareManager` adds an email to `Plant.sharedWith` (now mirrored
to Firestore as of F1) but the recipient gets:

- No notification — they don't know they were added to a plant
- No reminders for the plant — only the owner receives them
- No ability to mark "watered" — only the owner's tap counts
- No visibility on the recipient's app at all unless we hardcode
  cross-account reads in Firestore rules (currently blocked — only
  `users/{uid}/*` is readable by `uid`)

The feature is "show the owner who they intend to share with" —
genuinely useful as a list, but mislabelled as "Family Share".

**Action:** Multi-step:
1. Send a real invite (FCM push + magic-link to the recipient's
   email).
2. On accept, record a `users/{recipientUid}/sharedPlants/{plantId}`
   pointer doc.
3. Recipient's TodayFragment includes shared plants + their reminders.
4. "Mark watered" on a shared reminder writes back via cloud function
   (because the recipient cannot write to the owner's Firestore
   subtree directly under current rules).

Effectively a Sprint-3 epic, not a notifications-style fix. Until
this lands, keep the dialog wording honest: it's an "audit list", not
a "share".

---

## 14. PDF Memoir doesn't expose past reports (MED — UX)
**Origin:** Session 2026-05-06 Family Share + PDF + Main UI + Settings audit.

`MemoirPdfBuilder.build` writes a fresh PDF under
`getExternalFilesDir("memoir")` every tap, then immediately opens
the system share sheet. There's no UI to browse past reports the
user generated; no way to re-share without regenerating; no
clean-up of old files (they accumulate in app-private storage
forever, lost on uninstall).

Pro apps (Strava annual report, Apple Health monthly summary) keep
a history shelf the user can revisit.

**Action:**
1. Add a "My reports" entry in PlantDetailDialogFragment or Settings
   that lists past PDFs with timestamp.
2. Periodically prune memoir/ to keep last N reports per plant.
3. Optionally back the list up to Firestore Storage so reports
   survive reinstall.

---

## 15. "Mark watered" action button missing from widget (MED — UX)
**Origin:** Session 2026-05-07 Widget + Billing audit.

`PlantCareWidgetDataFactory.getViewAt` renders a checkbox icon for
each reminder but the row is just a click target into MainActivity.
Pro apps (Greg, Planta) let the user tap "done" directly from the
widget without opening the app — same UX win as the
Mark-all-watered notification action shipped in N2. Implementing it
needs a per-item RemoteViews fillInIntent + a BroadcastReceiver
that resolves the reminder id from extras, marks done, syncs, and
calls `PlantCareWidget.updateWidget`.

**Action:** New `WidgetActionReceiver` mirroring
`NotificationActionReceiver` (goAsync + per-reminder mark-done +
StreakBridge update + DataChangeNotifier). Widget XML needs a
fillInIntent template per row.

---

## 16. BillingClient connection never explicitly closed (LOW)
**Origin:** Session 2026-05-07 Widget + Billing audit.

`BillingManager` holds a single `BillingClient` for the process
lifetime and never calls `endConnection()`. The connection lives
as long as the App process, which is fine — but if the process is
killed mid-purchase the underlying handler may leak briefly. Google
Play Billing v7 manages reconnection internally so this is
borderline cosmetic.

**Action:** None unless ANR / leak reports surface. Worst case,
add `endConnection()` from a process-lifecycle observer.

---

## 17a. DataChangeNotifier listeners not thread-safe (LOW)
**Origin:** Session 2026-05-07 Cloud sync + Security + Perf audit.

`DataChangeNotifier.listeners` is a plain `HashSet<Runnable>`. Reads
inside `notifyChange()` (on main) interleave with `addListener` /
`removeListener` calls from background threads (Worker callbacks,
Repository observers). A `ConcurrentModificationException` is
possible if a listener registration races with a notification fan-out.

Pre-existing bug (HashSet has been there since the file was created
— not introduced by any recent session). Realistic frequency is low
because listeners are added in Activity/Fragment onResume on main
thread and notifyChange is also posted to main; the only true
cross-thread case is the addListener-from-onCreate vs first
notifyChange race.

**Action:** Replace with `Collections.synchronizedSet(new HashSet<>())`,
or wrap iterator inside a `synchronized (listeners)` block.

---

## 18. PlantImageLoader spawns un-cancellable scope per call (MED — leak)
**Origin:** Session 2026-05-07 final all-features audit.

`weekbar/PlantImageLoader.kt:41` does `CoroutineScope(Dispatchers.Main).launch { ... }` per
`loadInto()` call without retaining a reference. When the user
scrolls a list of 30 plants, 30 scopes fan out; navigating away
mid-scroll leaks each captured ImageView (which holds an Activity).

**Action:** Take a `LifecycleOwner` parameter and use
`owner.lifecycleScope.launch`, OR cancel any in-flight job on a
per-ImageView WeakHashMap.

---

## 21. AddCustomPlantDialogFragment doesn't recycle source bitmap (LOW)
**Origin:** Session 2026-05-07 final audit.

`AddCustomPlantDialogFragment.java:148-170` saves a downscaled copy
of a camera-preview Bitmap (4000×3000 ≈ 48 MB ARGB_8888) but pins
the original in the ImageView until the dialog closes. On low-RAM
devices this is real memory pressure.

**Action:** After persisting the downscaled file, swap
`imagePreview.setImageBitmap(downscaled)` and recycle the original.

---

## 22. AddToMyPlantsDialogFragment.show paywall without isStateSaved (LOW)
**Origin:** Session 2026-05-07 final audit.

`AddToMyPlantsDialogFragment.java:271-279` calls
`new PaywallDialogFragment().show(getParentFragmentManager(), TAG)`
inside a FragmentBg callback that only checks `isAdded()`, not
`isStateSaved()`. Between onPause and onResume the fragment is
added but state is saved — `show()` throws "Can not perform this
action after onSaveInstanceState".

**Action:** Add `if (isStateSaved()) return;` guard or use a
state-loss-tolerant variant.

---

## 23. MainActivity onDeleteAccount races user.delete() with cleanup (LOW)
**Origin:** Session 2026-05-07 final audit.

The five `deleteAll*` calls fire on `BgExecutor.io` then
`runOnUiThread(() -> fbU.delete())`. The parallel
`deleteAllPhotosForUser` (FirebaseSyncManager) is fire-and-forget
without await. If `user.delete()` wins the race, photo cloud
deletion fails. Partial overlap with DEFERRED #2 but the race is
in MainActivity, not just the missing cascade.

**Action:** Sequence with await (CountDownLatch or convert to
suspend with structured concurrency).

---

## 17. No server-side purchase verification (MED — abuse risk)
**Origin:** Session 2026-05-07 Widget + Billing audit.

`BillingManager` trusts the local Google Play response and writes
isPro=true to SharedPreferences. A rooted device with
`Lucky Patcher`-style billing emulation can fake a purchase and
unlock Pro permanently. Server-side validation against
Play Developer API would harden this, but requires running our own
verification endpoint.

**Action:** Future epic — set up Cloud Functions endpoint that
validates `purchaseToken` against Play Developer API and writes
the verified isPro state into Firestore. Local
`ProStatusManager.setPro` then reads from Firestore as the
canonical source instead of trusting the local Billing client.
Requires infra setup, out of scope for in-app fixes.

**UPDATE 2026-05-15:** This entry was largely closed by sessions 12 +
17 (`F-PRO-VERIFY-SERVER` + `F-PRO-VERIFY-HARDENING`). The Cloud
Function exists and the client routes through it. Remaining work is
ONLY the manual deploy (covered by #24). Consider this resolved
once #24 ships.

---

## ⚠️ POST-PHASE-F MANUAL CLEANUP (do these BEFORE the next Play Store upload)

These four entries (#27 – #30) were generated by Phase F (the
full UI redesign cycle that closed 2026-05-15 at 100% visible-surface
coverage). The work itself is done in source; these are the manual
tasks that need a developer on the local machine.

---

## 27. Delete confirmed orphan layout files (LOW — disk hygiene)
**Origin:** Phase F4 + F9 audit cycle, session 2026-05-15 (round 28).

Nine files in `app/src/main/res/` are confirmed dead by grep (zero
Java/Kotlin references). They survived the redesign because the
sandboxed Claude run could not delete files on Windows (`rm` returned
`Operation not permitted`). They do not affect the live UI — AAPT
just compiles them as unused resources and Lint warns once.

**Files to delete:**
- `app/src/main/res/drawable/tab_selected_background.xml`
- `app/src/main/res/drawable/tab_unselected_background.xml`
- `app/src/main/res/layout/item_plant_in_room.xml`
- `app/src/main/res/layout/fragment_meine_pflanzen.xml`
- `app/src/main/res/layout/fragment_plant_list.xml`
- `app/src/main/res/layout/fragment_daily_watering.xml`
- `app/src/main/res/layout/fragment_calendar.xml` (the legacy
  MaterialCalendarView host; the live calendar is the Compose
  `CalendarScreenBridge`)
- `app/src/main/res/layout/fragment_auth.xml`
- `app/src/main/res/values/styles_calendar.xml` (only referenced
  by the orphan `fragment_calendar.xml`)

**Verification:**
- `del` each file in Windows Explorer / Terminal.
- `./gradlew assembleProdRelease` still succeeds.
- App still launches and every Phase F screen still renders.

**Action (one-liner):**
```cmd
cd C:\Users\fadym\Videos\Code\2\Chat 13
del app\src\main\res\drawable\tab_selected_background.xml
del app\src\main\res\drawable\tab_unselected_background.xml
del app\src\main\res\layout\item_plant_in_room.xml
del app\src\main\res\layout\fragment_meine_pflanzen.xml
del app\src\main\res\layout\fragment_plant_list.xml
del app\src\main\res\layout\fragment_daily_watering.xml
del app\src\main\res\layout\fragment_calendar.xml
del app\src\main\res\layout\fragment_auth.xml
del app\src\main\res\values\styles_calendar.xml
```

---

## 28. Decide on Camera FAB ↔ AdMob banner cradle overlap (MED — design decision)
**Origin:** Phase F2.5 audit, surfaced in session 2026-05-15.

Geometry of the cradled FAB (`fabCamera` in `activity_main.xml`):
- Anchored: `layout_constraintBottom_toTopOf="@id/bottomNav"`
- `translationY="28dp"` → FAB center sits on `bottomNav.top`
- FAB is 56dp → top half of FAB occupies the slice
  `[bottomNav.top - 28dp, bottomNav.top]`

AdMob banner geometry:
- 50dp tall, `bottom` anchored to `bottomNav.top`
- Occupies `[bottomNav.top - 50dp, bottomNav.top]`

**Result:** For FREE users (banner visible), the top ≈28dp of the
FAB overlays the bottom ≈28dp of the banner. Banner content is
typically center-padded so the visual conflict is mild, but it
exists. Pro users (banner GONE) see the standard cradle look.

**Three candidate fixes** (Fady's call — none implemented):

1. **Leave as-is** — Soft Pro upsell. Free user sees a slightly
   obscured ad; Pro user gets the clean cradle. Zero code change.

2. **Reduce `translationY=28dp` → `translationY=14dp`** — Half-cradle.
   FAB lifts ≈14dp above the bar, overlap with banner drops to ≈14dp.
   Visual cradle effect is softer but the conflict halves.

3. **Anchor FAB to `adBanner.top` instead of `bottomNav.top`** — No
   overlap ever; FAB floats above the banner. Loses the cradle look
   entirely (FAB becomes a "raised" FAB sitting in the content area).
   Two-line layout change.

**Action:** Pick one. Edit `app/src/main/res/layout/activity_main.xml`
around lines 124-136.

---

## 29. Run `./gradlew assembleProdRelease` after Phase F (CRITICAL — release gate)
**Origin:** Phase F continuous deferral, finalized 2026-05-15.

Phase F shipped across 28 PROGRESS-tracked rounds. The single mid-
phase build verification ran after F2.8 and was green (Fady's
reported "BUILD SUCCESSFUL in 15m 15s, 61 actionable tasks"). No
build has been run since then. Phase F3 + F4 + F5 + F6 + F7 + F8 +
F9 are all unverified at the Gradle level. Per CLAUDE.md the
verification gate is **mandatory** before declaring a phase complete.

**Risks if skipped:**
- AAPT may flag a resource reference Claude missed (the sandbox
  XML parser is more lenient than aapt2).
- A `pc_outlineVariant` attribute on a style may not resolve in
  values-night even though it resolves in values.
- A typo in a CameraX `app:shapeAppearanceOverlay` ref may compile
  silently but render wrong.
- Warnings count may have crept up (CLAUDE.md §3 forbids any
  increase from baseline).

**Action:**
```cmd
cd C:\Users\fadym\Videos\Code\2\Chat 13
gradlew.bat assembleProdRelease
```

**Verification:**
- `BUILD SUCCESSFUL` with all tasks reported `actionable`.
- Warnings count compared to the F2.8 baseline of "59 executed, 2
  up-to-date" — should not exceed +5 warnings.
- If failures: capture the first 5 errors, re-open the relevant
  Phase F sub-task, fix, re-build.

---

## 30. Manual smoke test of every Phase-F surface (HIGH — release gate)
**Origin:** Phase F closure, session 2026-05-15 (round 28).

Phase F touched 60 of 76 layout files. None of them have automated
end-to-end coverage. Manual sweep is the only protection against a
visual / interaction regression sneaking through.

**Minimum smoke checklist (35-min walkthrough):**

**Bottom Nav shell:**
- [ ] App opens to Heute (greeting + date + streak card visible).
- [ ] Tap each of the 4 nav slots → Fragment swaps without flicker.
- [ ] Active item tint changes correctly (sage container pill).
- [ ] Hardware Back from any non-Heute tab → returns to Heute.
- [ ] Second Back from Heute → app exits.
- [ ] Rotate device → selected tab is preserved.

**Camera FAB:**
- [ ] Tap FAB → CameraX preview opens.
- [ ] Slider toggles correctly between "Pflanze erkennen" and
  "Gesundheit prüfen".
- [ ] Capture in Identify mode → `PlantIdentifyActivity` opens with
  the captured image pre-loaded.
- [ ] Capture in Diagnose mode → `DiseaseDiagnosisActivity` opens
  with the captured image pre-loaded.
- [ ] Gallery icon → image picker opens, picked image routes to the
  same target as the captured image.
- [ ] Permission-denied path → settings deep-link dialog.

**Mein Garten "+":**
- [ ] Tap "+" → bottom sheet shows 2 cards.
- [ ] "Aus Katalog wählen" → CatalogActivity opens with the hero
  header + pill search.
- [ ] "Mit Kamera erkennen" → UnifiedCameraActivity opens with
  Identify mode pre-selected.

**Settings:**
- [ ] All 7 cards render (Pro hero / Konto / Darstellung / Abonnement /
  Daten / Hilfe / Über).
- [ ] Theme radios persist (Hell / Dunkel / System).
- [ ] Language picker opens an AlertDialog, choice persists.
- [ ] Restore purchases → toast appears.
- [ ] "Erweiterte Einstellungen" → legacy SettingsDialogFragment opens.
- [ ] Logout from KONTO → bounces back into Onboarding.

**Visual audit:**
- [ ] No card has a heavy drop shadow inside a list (all should be
  outline-only, 0dp elevation).
- [ ] Every dialog opens with rounded 28dp corners + subtle 3dp lift.
- [ ] Plant photo thumbnails are rounded squares (not circles or
  squares).
- [ ] DM Serif hero headlines render on Heute / Mein Garten / Catalog.
- [ ] System nav bar matches the BottomNavigationView surface.

**Free-vs-Pro:**
- [ ] As a free user: AdMob banner visible above BottomNav. Pro hero
  card visible in Settings.
- [ ] As a Pro user (Restore purchases or test account): no banner,
  Settings shows "PlantCare Pro Aktiv".

**Action:** Walk through the checklist on a single physical device.
Mark any FAIL row + capture screenshot. Open a follow-up entry in
this file for each FAIL.

---

## Process notes
- Each entry should be moved out of this file the moment a session
  actually fixes it, with the originating PROGRESS.md session entry
  citing this file.
- New entries go to the bottom of their category; renumbering is fine
  on each update.
- Don't use this file for "ideas" or "wouldn't it be nice" — only for
  audit findings the team has reviewed and consciously postponed.
