# PlantCare — Manual Test Plan
## Last Updated: 2026-05-07
## Coverage: 73 fixes shipped across 11 audit sessions, all 23 features

> Use this as your verification checklist before publishing the next
> release. Each test row tells you the *why* (which fix it covers),
> *how* (steps), and the *pass/fail criteria* (what you should and
> shouldn't see). Mark each row Pass / Fail / Skip and report back.
>
> **Rough budget:** 4–6 hours total for the full sweep on a single
> device. Sections are independent — pick a feature group per
> session if you can't do it all at once.

---

## P0 — PREREQUISITES (do these BEFORE any other test)

> Several fixes ship with new Firestore + Storage rules. They have
> NO effect until published. Without these you'll see false failures
> that look like sync bugs but are really un-deployed config.

| # | Step | Command / Action | Pass | Fail |
|---|------|-------------------|------|------|
| P0.1 | Publish Firestore rules | Firebase Console → Firestore → Rules → paste `firestore.rules` → Publish. Or run `firebase deploy --only firestore:rules`. | Console shows "Rules published" timestamp matches now. | Console shows old rules. PermissionDenied errors in next tests. |
| P0.2 | Publish Storage rules | Firebase Console → Storage → Rules → paste `storage.rules` → Publish. Or run `firebase deploy --only storage:rules`. | Console shows "Rules published" timestamp matches now. | Photo uploads fail with permission errors. |
| P0.3 | Build prod release APK | `./gradlew assembleProdRelease` | BUILD SUCCESSFUL, APK in `app/build/outputs/apk/prod/release/`. | Any compile error. |
| P0.4 | Install on test device | `adb install -r app-prod-release.apk` | App icon appears, opens to onboarding. | Install fails. |
| P0.5 | Sign in with a test Google account | Onboarding → Login → Google → grant. | Today screen loads. Account email visible in Settings. | Sign-in fails or stalls. |

---

## 1 — DXؤول والحساب (Auth)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 1.1 | Magic link sign-in writes User row | Sign out → "Sign in with email link" → tap link in email | Today loads. Settings shows account email. Crashlytics has no "magic link" error. | App opens but Settings shows "guest" or empty email. |
| 1.2 | Logout wipes all per-user prefs | Sign in → log out → check device | After logout, Settings shows "Login" button. **No** stale weather tip / streak / vacation banner from previous user. | Old user's data visible to next user. |
| 1.3 | Logout clears `weather_cache` (privacy) | Sign in as User A in Berlin → log out → sign in as User B in Munich | User B's first weather lookup happens fresh; **no** Berlin coords in `adb shell run-as ... weather_cache.xml`. | Berlin coords still cached. |
| 1.4 | Account deletion runs cloud-side too | Settings → Delete account → confirm → re-auth | Firebase Console: `users/{uid}/photos/*` is empty. App restarts to onboarding. | Cloud orphans remain (until DEFERRED #2 lands). |

---

## 2 — إدارة النباتات (Plants CRUD)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 2.1 | Add plant from catalog | Today → + → pick Monstera → Save | Plant appears in My Plants. Reminders generated for next 180 days. | No reminders appear. |
| 2.2 | Add plant cloud-syncs | Add plant → check Firebase Console | `users/{uid}/plants/{id}` doc exists with name + wateringInterval. | Doc missing. |
| 2.3 | Plant delete cascades correctly (CS1) | Long-press plant → delete | Local: row gone, FK CASCADE drops reminders/photos/memos. **Cloud:** plant doc + reminders + photos + memos all deleted. | Cloud orphans linger. |
| 2.4 | FREE_PLANT_LIMIT enforces at 9th plant | Free user → add 8 plants → try the 9th | Paywall appears with "Du brauchst mehr Platz" copy. | 9th plant added silently. |
| 2.5 | Plant edit syncs to other device | Edit watering interval on phone → open tablet | Tablet shows new interval after sign-in restore (or live if listener). | Tablet shows old interval. |

---

## 3 — الغرف (Rooms)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 3.1 | Default 5 German rooms seed on first sign-in | Fresh install → sign in to a fresh account | "Wohnzimmer", "Schlafzimmer", "Küche", "Bad", "Balkon" appear in My Plants. | Rooms list empty. |
| 3.2 | Room cloud sync (F3.11) | Add custom room "Büro" → sign in on second device | "Büro" appears on second device after restore. | Only the 5 defaults appear. |
| 3.3 | Drag-to-reorder persists | Drag "Bad" above "Wohnzimmer" → close app → re-open | Order preserved. | Order resets. |
| 3.4 | Room delete doesn't orphan plants | Delete a room with plants in it | Plants stay (move to Sonstige). Other rooms still show their plants correctly. | Plants disappear. |

---

## 4 — كاتالوج النباتات (Plant Catalog)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 4.1 | Catalog browse loads thumbnails (#1 leak fix) | Open catalog → scroll all 30+ plants quickly back-and-forth for 60 seconds | All thumbnails load. **No** "TLS handshake failed" / connection errors after 30+ scrolls. | Later thumbnails fail to load after some scrolling. |
| 4.2 | Catalog search returns matches | Search "Monstera" | Monstera deliciosa shows. | Empty results. |
| 4.3 | Quick-add from catalog | Long-press plant → Quick add → uses last room | Snackbar with "Undo". Plant in My Plants. Cloud doc created. | No undo or cloud doc missing. |
| 4.4 | Quick-add Undo works | Quick-add → tap Undo within 5s | Plant gone locally + in cloud. | Plant remains. |

---

## 5 — تذكيرات السقاية (Watering Reminders)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 5.1 | Generated reminders span 180 days | Add a plant with 14-day interval → check Calendar 6 months out | Reminders visible at week 24. | Reminders end early. |
| 5.2 | Mark watered triggers streak (Z1 fix) | Today → tap "tick" on one reminder → check streak counter | Counter +1 (was 0 → now 1). | Counter unchanged. |
| 5.3 | Mark watered cloud-syncs | Mark watered → check Firestore reminders/{id} | `done=true`, `wateredBy` = current email, `completedDate` set. | Field still false. |
| 5.4 | Edit recurring reminder doesn't crash on rotate (#4 fix) | Open Edit Manual Reminder → set to "Daily" → tap Save → **rotate device** during the save | No crash. After save completes, dismiss happens automatically (or save persists silently if rotated). | Crash with IllegalStateException. |
| 5.5 | Reminder top-up worker generates new dates daily | Use the app daily for 3 days | New reminder dates appear at the 180-day horizon. | Horizon stays static. |

---

## 6 — التقويم (Calendar)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 6.1 | Calendar shows reminders on correct dates | Today → Calendar → swipe to next month | Reminders appear on the dates the system says. | Off by one or empty. |
| 6.2 | Tap day shows reminder details | Tap a date with reminders | Bottom sheet lists plants. | Empty sheet. |
| 6.3 | Quick-add photo from calendar uses correct date | Calendar → tap day → Add photo → take picture | Photo dateTaken matches the calendar day, NOT today's date. | Photo dated today. |

---

## 7 — الصور (Photos)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 7.1 | Camera capture saves locally + uploads to cloud | Plant detail → camera | Photo in archive. Firestore `users/{uid}/photos/{id}` doc. Storage `users/{uid}/plant_photos/...jpg` exists. | Storage missing the image. |
| 7.2 | Photo dateTaken uses Latin digits (M1 fix) | If you can switch device to Arabic locale: take a photo | `dateTaken` field in DB stays `2026-05-07` not `٢٠٢٦-٠٥-٠٧`. Photo appears in calendar grid. | Photo invisible in calendar (Arabic numerals don't match SQL). |
| 7.3 | Cover photo upload works (#17 fix) | Plant detail → set cover → take picture | Cover thumbnail appears. CrashReporter has no "openInputStream returned null" entries. | Cover save silently does nothing. |
| 7.4 | CoverCloudSync no longer spawns raw threads (#2 fix) | Sign in fresh on a new device with 50+ plants | Logcat shows `plantcare-bg-N` thread names (BgExecutor), no `Thread-N` named threads from CoverCloudSync. | Many `Thread-NNN` entries during cover sync. |
| 7.5 | Photo archive shows monthly count for MONTHLY_PHOTO challenge | Take 1 photo → open Today | "Photo this month: 1/1" challenge marked complete. | Counter at 0/1. |

---

## 8 — التعرّف على النبتة (Plant Identification)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 8.1 | Identify works with online source | Identify button → camera → leaf photo → Identify | Top-3 candidates returned with confidence percentages. | Empty / "No match" on every photo. |
| 8.2 | Identification cache hits within 7 days | Identify same image twice within minutes | Second call instant (cache hit), no second network spinner. | Both calls hit network. |
| 8.3 | wateringInterval inherited after PlantNet identify (F5) | Identify → Save to my plants → check the new plant | wateringInterval shows a real value (not 0 or default). | wateringInterval is 0. |

---

## 9 — تشخيص الأمراض (Disease Diagnosis)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 9.1 | Disease button opens chooser dialog | Toolbar → 🩺 button | Dialog: "General check" / "Pick a plant" / Cancel. | No dialog. |
| 9.2 | Pick-plant flow lists user plants | Choose "Pick a plant" | Bottom-sheet lists current user's plants. | Empty list even though user has plants. |
| 9.3 | Diagnosis on EXIF-rotated portrait photo (F9.5) | Take portrait photo → Analyze | Diagnosis shows. Image shown upright. Body sent to Gemini ~600 KB not 12 MB. | Image rotated wrong. |
| 9.4 | Save diagnosis photo to plant archive | Diagnosis success → "Save to archive" → check plant detail | Photo in archive linked to diagnosis (no duplicate in journal). | Two cards (photo + diagnosis) for same image in journal. |
| 9.5 | Diagnosis cloud sync works | Run a diagnosis → Firebase Console | `users/{uid}/diagnoses/{id}` doc exists (or via subcollection). | Doc missing. |

---

## 10 — دفتر يوميّات النبتة (Plant Journal)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 10.1 | Add memo to plant journal | Plant detail → 📔 Journal → +FAB → type → Save | Memo at top of timeline. CrashReporter has no errors. | Save tap does nothing. |
| 10.2 | Memo length cap at 1000 chars (F10.1) | Journal → memo editor → paste 2000-char text | Editor stops accepting input at exactly 1000 chars. | Accepts arbitrary length. |
| 10.3 | Memo CRUD failure shows toast (F10.2) | (Hard to force; rely on no false toast in normal use) | Normal save → no toast. | False "Save failed" toast on every operation. |
| 10.4 | Memo cloud sync (F10.3) | Add memo → Firebase Console | `users/{uid}/memos/{id}` doc exists with text + plantId. | Doc missing → CS1 deploy gate not done OR proguard rule missing. |
| 10.5 | Memo restore on second device (F10.3) | Add 3 memos on phone → sign in on tablet (fresh install) | All 3 memos visible in tablet's journal after sign-in. | Tablet shows zero memos despite Firestore having them. |
| 10.6 | Memo delete cascades to cloud | Journal → long-press → Delete | Doc gone in Firestore. PermissionDenied gone (CS1 fix). | Doc still in cloud. |
| 10.7 | Memo dismiss-mid-save shouldn't false-toast (#7 fix) | Open editor → type → tap Save → **immediately rotate** | After rotation: NO "Save failed" toast. The memo IS saved when you reopen the journal. | False error banner / toast. |

---

## 11 — الطقس (Weather)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 11.1 | Weather fetched on first launch (M2) | Cold start → wait 5s | WeatherTipCard shows current temp + city. | Empty card or "—°C". |
| 11.2 | Weather worker only runs when online (F11.2) | Airplane mode → wait 12 hours → check Logcat | Worker doesn't fire while offline. After airplane off, next worker run succeeds. | "UnknownHostException" retries in airplane mode. |
| 11.3 | Worker constraint applies to existing users (F11.2) | Existing-install upgrade → check WorkManager state | `WorkManager.getWorkInfosForUniqueWorkLiveData("weather_adjustment_work")` shows constraint NetworkType.CONNECTED. | UPDATE didn't apply, KEEP semantics still active. |
| 11.4 | Cache key uses Locale.US (F11.1) | Switch device to German locale → trigger weather fetch | Logcat / SharedPreferences `cached_weather_location` key looks like `52.52,13.40` (dot, not comma). | Key shows `52,52,13,40`. |
| 11.5 | Weather worker stops cooperatively when canceled (#8 fix) | Trigger battery-saver / force WorkManager to cancel a running worker | Worker exits without `Result.retry()` log. No re-schedule. | Worker silently retries forever. |

---

## 12 — الإشعارات (Notifications)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 12.1 | Daily reminder notification fires in morning window | Wait for next 7-11 AM window with pending reminders | Notification appears with morning copy + plural count. | No notification despite pending reminders. |
| 12.2 | "Mark all watered" action present (N2) | Pull down notification | Action button "Alle gegossen" / "All watered" visible. | No action buttons. |
| 12.3 | Action button works without opening app (N2) | Tap "Alle gegossen" from lockscreen | Notification dismisses immediately. Open app afterward: all reminders for today marked done. **Streak +1** (Z1 fix). Other devices see updates. | Action does nothing OR streak doesn't bump. |
| 12.4 | IMPORTANCE_HIGH on fresh install (N4) | Fresh install → trigger notification | Notification peeks above lockscreen with sound. | Silent / no peek. |
| 12.5 | Empty pendingCount on Arabic locale (A2 fix) | If possible: switch to Arabic → wait for worker | Notification still shows correct count, NOT "no reminders today" cheery copy when there ARE reminders. | Empty-state notification despite pending tasks. |
| 12.6 | NotificationActionReceiver survives lock-screen (A1 fix) | Tap action then immediately power off screen | Verify in `adb shell dumpsys jobscheduler` that work actually completed. Reminders DB rows have `done=true`. | Reminders not marked done because process died. |

---

## 13 — وضع الإجازة (Vacation Mode)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 13.1 | Vacation date picker rejects past dates (V4) | Settings → Vacation → Start | DatePicker greyed out for yesterday and earlier. | Can pick last week. |
| 13.2 | Vacation set syncs to cloud (V2) | Set 14-day vacation → Firebase Console | `users/{uid}/vacation/current` doc with start, end, welcomeFired=false. | Doc missing. |
| 13.3 | Vacation suppresses worker notifications | Set vacation TODAY → wait for worker run | No "morning watering" notification fires. | Notification fires anyway. |
| 13.4 | Weather worker doesn't shift reminders during vacation (V3) | Set vacation → trigger weather worker → check reminder dates | Reminder dates unchanged after worker run. | Dates shifted ±days. |
| 13.5 | Welcome-back notification fires (catch-up logic, N3+V1) | Set vacation Mon-Fri → don't open app Mon-Wed → open Thu (day before end) | Welcome-back notification fires same day. | Never fires (the old `today != trigger` bug). |
| 13.6 | Vacation restore on second device (V2) | Set vacation on phone → sign in on tablet | Tablet shows banner with same end date. | Tablet shows no banner. |
| 13.7 | welcome_fired flag survives device hop (V2) | Trigger welcome-back on phone → sign in on tablet | Tablet does NOT replay welcome-back notification. | Duplicate notification on tablet. |

---

## 14 — السلاسل والتحدّيات (Streaks & Challenges)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 14.1 | Streak grows on consecutive days | Mark a reminder done today → and again tomorrow | Counter shows 2. | Counter shows 1. |
| 14.2 | Streak survives vacation gap | Mark done day 1 → vacation day 2-5 → mark done day 6 | Counter continues from 2 (not reset to 1). | Counter resets. |
| 14.3 | "5 plants added" challenge auto-detects (C1) | Free user adds 5 plants | Toast/dialog "Challenge complete!". Trophy badge. | Stays at 0/5. |
| 14.4 | "Monthly photo" challenge auto-detects (C2) | Take any photo | Challenge "Foto im Monat" marks done. | Stays at 0/1. |
| 14.5 | Monthly challenge resets on the 1st (C4+C16) | At month rollover (or change device clock to next month) | MONTHLY_PHOTO shows 0/1 again. Take photo → 1/1. | Stays "completed" forever. |
| 14.6 | Streak cloud-syncs (C3) | Build 7-day streak → uninstall → reinstall → sign in | Streak restored to 7. Best streak preserved. | Reset to 0. |
| 14.7 | Concurrent watering doesn't drop a streak day (#12) | Tap notification action AND open app + tap done within same second | Streak counter is incremented exactly once that day. | Streak +0 OR +2. |

---

## 15 — مشاركة العائلة (Family Share)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 15.1 | Add email to plant share list | Plant detail → Share → enter email → OK | Email appears in list. | Email not added. |
| 15.2 | Shared list cloud-syncs (F1) | Add email → check Firestore plant doc | `sharedWith` field contains the email. | Field empty in cloud. |
| 15.3 | Shared list survives reinstall (F1) | Add 3 emails → reinstall → sign in | All 3 emails restored. | List empty. |
| 15.4 | Recipient does NOT receive any notification | Verify no notification arrives at the shared email | Confirmed (this is intentional — see DEFERRED #13). | Recipient gets a notification we didn't intend. |

---

## 16 — تقرير النبتة (PDF)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 16.1 | PDF generates from plant detail | Plant detail → "Wachstumsbericht" / Memoir | Loading toast → share sheet appears with PDF. | Build silently fails. |
| 16.2 | PDF includes timeline + photos | Open the generated PDF | Cover + stats + timeline (waterings/photos/diagnoses/memos) + 3×4 photo grid. | Pages missing. |
| 16.3 | PDF doesn't crash on OOM (#18 fix) | Generate PDF for a plant with 30+ photos on a low-RAM device | Either PDF builds OR build aborts visibly with an error toast. **Never** silently produces a 2-page PDF missing photos. | Empty memoir generated, no error. |

---

## 17 — الواجهة الرئيسية (Main UI)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 17.1 | Today screen loads with reminders | Open app | Today list shows pending reminders, grouped by room. | Empty even though plants exist. |
| 17.2 | Vacation banner shows during active vacation | Set vacation → return to Today | Yellow banner: "Du bist im Urlaub bis ..." | No banner. |
| 17.3 | Plant photo capture date is correct (M1) | Take photo on a non-Latin-locale device | Photo appears in calendar grid for today. | Photo invisible. |
| 17.4 | Disease button doesn't leak threads on repeat tap (M2) | Tap 🩺 button 10 times rapidly | Each tap opens dialog. After 1 minute, `adb shell ps -T -p <pid> | wc -l` shows ≤50 threads (not climbing). | Thread count climbing per tap. |
| 17.5 | After-purchase ad banner disappears live (B1) | Free user → open paywall → buy Lifetime → confirm | Banner ad disappears immediately, no Activity recreate needed. | Banner stays until app restart. |

---

## 18 — الإعدادات (Settings)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 18.1 | Theme toggle (system/light/dark) | Settings → Theme → switch | UI updates immediately. Persists across restart. | Doesn't apply. |
| 18.2 | Language toggle (DE / EN / system) | Settings → Language → English | Strings switch to English. After restart still English. | Strings revert. |
| 18.3 | Data export contains ALL categories (S1) | Settings → Export Data → save JSON | JSON contains: plants, reminders, rooms, photos, memos, diagnoses, vacation, streak, challenges. `schema_version: 2`. | Export only has plants+reminders. |
| 18.4 | Export filename has timestamp (S4) | Export twice in same minute | Two distinct files: `plantcare_export_2026-05-07_14-30-15.json` and `plantcare_export_2026-05-07_14-30-42.json`. | Second export overwrites first. |
| 18.5 | Email verify long-press resends email | Settings → long-press email row → check inbox | "Verification email sent" toast. Email arrives. | Nothing happens. |
| 18.6 | Pro section shows correct status | Pro user: "Active" / Free user: "Free" + "Open Paywall" | Status matches actual purchase state. | Wrong label. |

---

## 19 — ويدجت الشاشة الرئيسية (Home-screen Widget)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 19.1 | Add widget to home screen | Long-press home screen → Widgets → PlantCare → drag | Widget shows today's date + reminder list. | Widget shows error. |
| 19.2 | Widget refreshes on data change (W1) | With widget on screen, mark a reminder done in app | Within 2 seconds, widget shows the reminder ticked / removed. | Widget unchanged. |
| 19.3 | Widget has data on Arabic-locale devices (W2) | If possible: switch to Arabic → check widget | Reminders visible. NOT permanently empty. | Empty widget despite reminders existing. |
| 19.4 | Widget refresh is fast on big collections (W4) | User with 30+ reminders → mark done → time the widget update | < 1 second. | Multi-second freeze on the widget host. |
| 19.5 | No broadcast spam without widget (ZZ3) | Run app for 10 minutes WITHOUT widget on screen → check logcat | No `APPWIDGET_UPDATE` broadcasts spamming logs. | Many broadcasts per minute. |

---

## 20 — النسخة المدفوعة والإعلانات (Pro / Ads)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 20.1 | Paywall shows monthly + yearly + lifetime | Trigger paywall (add 9th plant or Settings → Open Paywall) | All 3 buttons enabled with prices. | Buttons disabled. |
| 20.2 | Restore purchases works | Settings → Restore | Toast "Wiederherstellung abgeschlossen". If you DID purchase before, isPro becomes true. | Failed silently. |
| 20.3 | Acknowledgement retries on failure (B3) | Hard to test manually — trust the BUILD passing + Crashlytics watch | No "purchase auto-refunded after 3 days" complaints in support inbox. | Many such reports. |
| 20.4 | Pro status syncs across devices (B7) | Buy Pro on phone → sign in on tablet → wait for restore | Tablet's banner disappears after sign-in completes. | Tablet still shows banner. |
| 20.5 | Paywall auto-dismisses after purchase (ZZ4) | Open paywall → Subscribe → complete Google Play sheet | Paywall vanishes automatically after Play sheet closes. | Paywall still visible behind Play. |
| 20.6 | Pro status doesn't write storm to cloud (ZZ2) | Watch Firestore writes for 1 hour of normal use | `users/{uid}/billing/proStatus` write count is small (1-2 per session). | Hundreds of writes. |

---

## 21 — المزامنة السحابية (Cloud Sync)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 21.1 | All 9 import streams complete on sign-in | Sign in fresh → wait for "Cloud import success" toast | Plants, reminders, photos, rooms, memos, vacation, streak, challenges, proStatus all populated. | Some categories empty. |
| 21.2 | Cloud delete works for memos (CS1) | Delete memo → check Firestore | Doc gone. No PermissionDenied. | Doc still in cloud. |
| 21.3 | Cloud delete works for photos (CS1) | Delete photo → check Firestore | Doc gone in `users/{uid}/photos`. | Doc still in cloud. |
| 21.4 | Cloud delete works for vacation (CS1) | Clear vacation → check Firestore | `users/{uid}/vacation/current` gone. | Doc still in cloud. |
| 21.5 | DCL race fix verifies (#5) | Force two threads to call PlantRepository.getInstance() simultaneously (developer harness only) | Same instance returned both times. | Two distinct instances. |

---

## 22 — الأمان والخصوصية (Security & Privacy)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 22.1 | Storage rules reject cross-user write | Use Firebase Storage Emulator → try to write to `users/<other-uid>/file` while signed in as a different user | PermissionDenied. | Write succeeds. |
| 22.2 | Storage rules reject >8 MB upload | Try uploading a 15 MB file via Storage API | PermissionDenied. | Upload accepted. |
| 22.3 | Storage rules reject non-image | Try uploading a .pdf to your own subtree | PermissionDenied. | Upload accepted. |
| 22.4 | EncryptedSharedPreferences cached (PERF1) | Cold start → measure to first frame | Cold start is fast (≤2s on mid-tier device). | Slow startup. |
| 22.5 | Sensitive prefs migrated from plain | Check `secure_prefs.xml` exists; check old `prefs.xml` doesn't have `current_user_email` | Migrated. | Email leaks in plain prefs. |
| 22.6 | Logout clears `weather_cache` (privacy) | Logged in user → log out → inspect `weather_cache.xml` | File empty / cleared. No previous coords visible. | Coordinates persist. |

---

## 23 — البنية التحتية والأداء (Infrastructure & Perf)

| # | Test | Steps | Pass | Fail |
|---|------|-------|------|------|
| 23.1 | Cloud import faster after pool fix (PERF3) | Sign in fresh on a 50-plant account → measure time to "import success" toast | Faster than before (no exact threshold; expect ~2-3 seconds, not 8+). | Multi-second delay. |
| 23.2 | No `new Thread()` regressions | `grep -rn "new Thread(" app/src/main/java/` | 0 matches (or only in BgExecutor itself). | Many matches. |
| 23.3 | No `catch (_: Throwable) {}` regressions | `grep -rn "catch.*Throwable.*) {}" app/src/main/java/` | 0 matches. | Some matches. |
| 23.4 | Repository singletons return same instance | Programmatic check via debugger or instrumentation | Identity equal across calls. | Different instances. |
| 23.5 | Cancellation re-throw in coroutines | Code review grep: `grep -rn "CancellationException" app/src/main/java/` | 12+ explicit re-throws spread across FragmentBg, ViewModels, workers. | Few or zero — regression. |

---

## Summary — pass/fail report template

> Copy this table back to me with each row marked.

| Section | Tests | Passed | Failed | Skipped | Notes |
|---------|-------|--------|--------|---------|-------|
| P0 Prerequisites | 5 | | | | |
| 1 Auth | 4 | | | | |
| 2 Plants | 5 | | | | |
| 3 Rooms | 4 | | | | |
| 4 Catalog | 4 | | | | |
| 5 Reminders | 5 | | | | |
| 6 Calendar | 3 | | | | |
| 7 Photos | 5 | | | | |
| 8 Identify | 3 | | | | |
| 9 Disease | 5 | | | | |
| 10 Journal | 7 | | | | |
| 11 Weather | 5 | | | | |
| 12 Notifications | 6 | | | | |
| 13 Vacation | 7 | | | | |
| 14 Streaks | 7 | | | | |
| 15 Family Share | 4 | | | | |
| 16 PDF | 3 | | | | |
| 17 Main UI | 5 | | | | |
| 18 Settings | 6 | | | | |
| 19 Widget | 5 | | | | |
| 20 Pro / Ads | 6 | | | | |
| 21 Cloud Sync | 5 | | | | |
| 22 Security | 6 | | | | |
| 23 Infra & Perf | 5 | | | | |
| **Total** | **115** | | | | |

## Reporting back

For any failure, paste:
- **Test ID** (e.g. 10.7)
- **Reproduction steps** you actually performed (if they differ
  from the table)
- **Actual result** (what you saw)
- **Logcat** filtered to `tag:plantcare OR tag:CrashReporter OR tag:FirebaseSyncMgr`
  for the period of the test
- **Screenshot or screen recording** if visual

I'll triage each failure into:
- 🔴 Bug → fix in next session
- 🟡 Test-step error → I'll clarify the row
- 🟢 Already fixed in another path → mark Pass with note
- ⚪ Out of scope (DEFERRED) → expected
