# PlantCare — Deferred Issues Tracker
## Last Updated: 2026-05-07

> Session 2026-05-07 (Final all-23-features deep audit) added five new
> entries (#18-#22) — bugs found by the comprehensive sweep that were
> too narrow / context-specific to be worth fixing in the same session.
> Headline numbers: 24 findings total, 14 fixed inline, 5 deferred,
> rest already covered by earlier entries (e.g. #17a thread safety).

This file collects issues that audits surfaced but the originating session
intentionally did NOT fix — either because they were out of scope for the
feature being audited, or because they require their own dedicated task
(architectural change, monetisation hook, app-wide sweep, etc.).

Format: smallest-blast-radius first within each category. Each entry
links back to the originating PROGRESS.md session for context.

---

## 1. Cross-cutting `Locale.getDefault()` for wire formats (HIGH)
**Origin:** Session 2026-05-06 Notifications+Vacation re-audit (A2),
later confirmed in Streaks+Challenges audit (C7).

`SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())` is used in 13+
files for the wire format that goes into SQLite. On ar/fa/ur devices
`Locale.getDefault()` formats with Eastern-Arabic digits and the SQL
`WHERE date <= today` comparison stops matching. Already fixed in the
worker layer + `TodayViewModel.buildRoomGroups`, but still present in:

- `AddReminderDialogFragment.java` (3 sites)
- `EditManualReminderDialogFragment.java` (3 sites)
- `EditPlantDialogFragment.java`
- `MainActivity.java` (2 sites)
- `ReminderUtils.java` (3 sites — generation + parse)
- `WateringReminder.java` (2 sites)
- `WateringEventStore.java`
- `widget/PlantCareWidgetDataFactory.kt`
- `feature/treatment/TreatmentPlanBuilder.kt`
- `data/repository/PlantJournalRepository.kt`
- `ui/disease/DiseaseDiagnosisActivity.kt`
- `app/src/test/java/com/example/plantcare/WateringReminderTest.kt` (test only — fine)

**Action:** Single-pass sweep replacing every wire-format
`Locale.getDefault()` with `Locale.US`. Audit each site for whether the
output is wire (DB / sync / file path → must be `Locale.US`) or display
(UI label → keep `Locale.getDefault()`).

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

## 19. ArchiveStore.addCalendarPhoto: read-modify-write race (MED)
**Origin:** Session 2026-05-07 final audit.

`weekbar/ArchiveStore.kt:26` reads JSON array from prefs, mutates,
writes back — two concurrent photo captures (rapid-fire from
`PhotoCaptureCoordinator`'s IO dispatcher) can race and one's write
overwrites the other → silent photo loss in the archive view.

**Action:** Synchronize over the singleton (same shape as the just-
fixed ChallengeRegistry/StreakTracker). One-line wrap.

---

## 20. MyPlantsFragment.mainHandler.post bypasses lifecycle (MED)
**Origin:** Session 2026-05-07 final audit.

`MyPlantsFragment.java:139, 172` posts to a Fragment-field Handler
that's not tied to viewLifecycleOwner. Posts continue running
after `onDestroyView` / `onDetach`. The post calls
`requireContext()` immediately → IllegalStateException on the
narrow window of dialog dismiss → user back gesture (~50 ms).

**Action:** Replace mainHandler use with FragmentBg.runIO three-arg
overload that already guards isAdded. Remove the field.

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

---

## Process notes
- Each entry should be moved out of this file the moment a session
  actually fixes it, with the originating PROGRESS.md session entry
  citing this file.
- New entries go to the bottom of their category; renumbering is fine
  on each update.
- Don't use this file for "ideas" or "wouldn't it be nice" — only for
  audit findings the team has reviewed and consciously postponed.
