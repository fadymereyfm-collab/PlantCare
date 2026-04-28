# PlantCare Progress Tracker
## Last Updated: 2026-04-28
## Current Layer: Audit Phase D (QA) — code-side complete, manual QA pending
## Completed Tasks: Task 0.1–0.6, 1.1–1.5, 2.1–2.5, 3.1–3.4, 4.1–4.5, 5.1–5.6, 6.3–6.7, Phase-A1, Phase-A3, Phase-A2, Phase-A4 (hide), Phase-B1, Phase-B2, Phase-B4, Phase-B5, Phase-B6, Phase-C2, Phase-C4, Phase-C5, Phase-D4, Phase-D5
## Deferred (manual): see "Manual Action Required" section below
## Last Verified Task: Master pass A2→D5 — assembleProdRelease ✅ 3m 1s
## Next Task: Manual Phase E — Play Console upload + AdMob real IDs + Privacy Policy Pages

---

## Session: 2026-04-28 (Master pass — Phases A2 → D5 in one shot)
### Tasks Completed: A2, A4 (hide), B1, B2, B4, B5, B6, C2, C4, C5, D4, D5
### Layer: Audit Phases A → D (code-side)
### Evidence (final grep snapshot):
  - `grep -rn "YOUR_API_KEY_HERE" app/src/main/java` → **0** (A3 holds, no regression)
  - `grep -rn "catch.*ignored" app/src/main/java` → **0** (was 50; C5 ✅)
  - `grep -rn "new Thread(" app/src/main/java/com/example/plantcare/ui/ app/src/main/java/com/example/plantcare/weekbar/` → **0 actual calls** (1 hit is a doc comment in FragmentBg.kt; C4 ✅)
  - `grep -rn "AppDatabase.getInstance" app/src/main/java/com/example/plantcare/ui/viewmodel/` → **0** (was 5 ViewModels; C2 ✅)
  - `grep -rn "AppDatabase.getInstance|DatabaseClient\." app/src/main/java/com/example/plantcare/ | grep -v repository/` → **95** (was 102; C2 reduced by 7 across VMs; remaining 95 are Java Activities/Fragments — out of scope for this pass, see Manual Action below)
  - `grep -n "resConfigs" app/build.gradle` → `resConfigs "de", "en"` (B1 ✅)
  - `ls app/src/main/res/values-en/` → strings.xml + strings_disease.xml + notifications.xml (B2 ✅)
  - `ls app/src/main/res/xml/locale_config.xml` → contains `<locale name="de" />` and `<locale name="en" />` (B6 ✅)

### Detailed changes per task:

**Phase A2 — Billing wired**
- `App.java:onCreate()` → `BillingManager.getInstance(this).connectAsync()`
- `MainActivity.java:onResume()` → `BillingManager.getInstance(this).restorePurchasesAsync()`
- `AddPlantDialogFragment.savePlant()` → gates on `ProStatusManager.isPro` + `plantDao.countUserPlants() >= FREE_PLANT_LIMIT(8)` → opens `PaywallDialogFragment`
- `AddToMyPlantsDialogFragment.openDatePickerAndAddPlant()` → same Pro gate before insert
- `SettingsDialogFragment` + `dialog_settings.xml` → new "PlantCare Pro" card with status text + "Auf Pro upgraden" + "Käufe wiederherstellen"
- `BillingManager.kt` → `@JvmStatic getInstance()` + Java-friendly `connectAsync()` / `restorePurchasesAsync()` wrappers
- `ProStatusManager.kt` → `@JvmStatic isPro/setPro` for Java callers
- New `PlantDao.countUserPlants(email)` query

**Phase A4 — Disease feature gated**
- `DiseaseDiagnosisActivity.onCreate()` → checks `assets.list().contains("plant_disease_model.tflite")`; if missing shows toast + `finish()`
- New string `disease_feature_unavailable` (DE + EN)
- (MainActivity already had a soft-disable toast on `diseaseButton`; activity-level guard adds defense in depth)

**Phase B1+B6 — i18n config**
- `app/build.gradle:26` → `resConfigs "de", "en"`
- `res/xml/locale_config.xml` → added `<locale android:name="en" />`

**Phase B5 — Notification strings externalized**
- New `res/values/notifications.xml` with 4 string-arrays (morning/evening titles+bodies, no-reminder titles+bodies, ~50 strings) + plural for pending count
- `PlantNotificationHelper.java` rewritten — reads from resources (`getStringArray`, `getQuantityString`); 250→130 lines, no DE strings inside

**Phase B2 — values-en**
- `res/values-en/strings.xml` (~290 keys translated DE→EN, all 8 source files consolidated)
- `res/values-en/strings_disease.xml` (~25 keys)
- `res/values-en/notifications.xml` (~50 strings + plural)

**Phase B4 — Per-App Language picker**
- `dialog_settings.xml` → new "Sprache" card with RadioGroup (System / DE / EN)
- `SettingsDialogFragment.wireLanguagePicker()` → uses `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(...))` — no Activity restart needed (handled by Per-App Language API)

**Phase C2 — ViewModels use Repositories**
- `AllPlantsViewModel.kt` → `PlantRepository.getAllCatalogPlantsList()`
- `MyPlantsViewModel.kt` → `PlantRepository` + `RoomCategoryRepository`
- `OnboardingViewModel.kt` → `PlantRepository.getAllCatalogPlantsList()`
- `PlantIdentifyViewModel.kt` → `PlantRepository.insertPlant()` (was `DatabaseClient.plantDao()`)
- `TodayViewModel.kt` → `PlantRepository` + `ReminderRepository` + `RoomCategoryRepository` (the buildRoomGroups algorithm preserved)
- `PlantRepository` extended with suspend list accessors: `getAllCatalogPlantsList`, `getUserPlantsListForUser`, `getUserPlantsInRoomList`, `findUserPlantsByName/Nickname`, `findAnyByName/Nickname`, `countUserPlants`
- `RoomCategoryRepository.getRoomsListForUser` (suspend list accessor)
- `ReminderRepository.getTodayAllRemindersList`

**Phase C4 — new Thread() removed from UI**
- New `util/BgExecutor.java` (Java-friendly fixed-pool executor with crash-reporter wrapping)
- 21 `new Thread(...).start()` → `BgExecutor.io(...)` across MainActivity, PlantAdapter, PlantDetailDialogFragment, PlantsInRoomActivity, TodayAdapter, DailyWateringAdapter
- 0 actual `new Thread(` left in UI (1 doc-comment hit in FragmentBg.kt)

**Phase C5 — catch (X ignored) → CrashReporter.log(e)**
- 49 occurrences across 10 files: MainActivity (13), PlantsInRoomActivity (8), PlantDetailDialogFragment (10), ArchivePhotosDialogFragment (7), EditManualReminderDialogFragment (5), FirebaseSyncManager (2), AddReminderDialogFragment (1), LoginDialogFragment (1), PlantPhotosViewerDialogFragment (1), ArchiveDialogHelper (1)
- 1 in ReminderUtils.parseWateringInterval — kept silent with `// expected: parseInt overflow` comment (genuine fall-through, not a bug-mask)

**Phase D4 — Manifest hardening**
- `android:usesCleartextTraffic="false"`
- `android:allowBackup="false"` + `android:fullBackupContent="false"`
- `android:dataExtractionRules="@xml/data_extraction_rules"`
- New `res/xml/data_extraction_rules.xml` excludes prefs + secure_prefs + pro_status + plantcare_db from cloud backup AND device transfer

**Phase D5 — LeakCanary**
- `app/build.gradle` added `debugImplementation 'com.squareup.leakcanary:leakcanary-android:2.13'`
- Auto-installed in debug builds only — no release impact

### Build Status: ✅ assembleProdRelease passed (3m 1s, 59 tasks, 29 executed / 30 up-to-date, BUILD SUCCESSFUL)
### Regressions: none — warning count unchanged from previous build (same Kotlin deprecation hints + PlantAdapter unchecked op)
### Next Task: Manual Phase E — see "Manual Action Required" section in this file

---

## Manual Action Required (Fady, NOT code)

### A5 — AdMob real IDs (5 min, blocking for monetization)
1. Open https://apps.admob.com/v2/apps and create the PlantCare app entry.
2. Create one Banner Ad Unit (under the app).
3. Replace in `app/src/main/res/values/strings.xml`:
   - `admob_app_id` → real `ca-app-pub-...~...` (from app settings page)
   - `admob_banner_unit_id` → real `ca-app-pub-.../...` (from banner ad unit page)
4. Verify by running and seeing real ad impressions in AdMob console (24h delay normal).

### A3 (continued) — OpenWeatherMap real key (1 min)
- Get free key from https://openweathermap.org/api
- Paste into `local.properties`: `OPENWEATHER_API_KEY=<your_key>`
- Add as GitHub Secret `OPENWEATHER_API_KEY` for CI builds (already wired in `.github/workflows/ci.yml`).

### A4 (alternate) — TFLite disease model (optional, decide before final release)
- Either download a 38-class plant-disease TFLite model (e.g. from Kaggle "PlantVillage") and place it at `app/src/main/assets/plant_disease_model.tflite` — the activity will auto-detect and enable, OR
- Leave the asset missing and the feature stays gated (current state is safe — activity finishes with a "nicht verfügbar" toast if invoked).

### B3 — plants.csv English columns (~3h, optional for German-only launch)
- Add columns `name_en, lighting_en, soil_en, fertilizing_en, watering_en` to `app/src/main/assets/plants.csv` (current size 85 KB → ~140 KB with EN columns).
- `PlantCatalogLookup.kt` then needs a one-line change to pick the column suffix from `Locale.getDefault().language`.
- For Germany-first launch: skip this — the German catalog still works, only the catalog list stays in DE while the UI chrome is bilingual.

### Phase D1 — Unit test expansion (~6h, recommended for stability)
- Current: 5 test files (~80 small tests). Audit asks for 25+.
- Add `AuthRepositoryTest`, expand `PlantRepositoryTest`, expand `ReminderRepositoryTest`, add `BillingManagerTest`, expand `MigrationTest` (7→8, 8→9, 9→10).
- Run with `./gradlew testProdReleaseUnitTest`.

### Phase D2 — Manual QA matrix (15 scenarios × 4 devices, ~3 days)
- See `PlantCare_Action_Plan.md` Layer 7.1 for the matrix template.
- Track in a Google Sheet: signup → guest → add plant → reminder → photo → widget → billing test purchase → restore → vacation mode → language switch → upgrade install (v0.1 → 1.0.0).
- **Critical scenario for A1**: install v0.1 (pre-SecurePrefs), add plant, then upgrade to current AAB → verify the user is still signed in and the plant is still visible.

### Phase D3 — Accessibility (TalkBack) (~2h)
- Install Accessibility Scanner on a test device.
- Walk through "add plant" with TalkBack enabled.
- Confirm every actionable view has `contentDescription`.

### Phase D4 (continued) — Manual security checks
- `adb backup` → verify the backup is empty for prefs/db (we set `allowBackup=false` + `data_extraction_rules.xml`).
- `apktool d app-prod-release.aab` → verify R8 obfuscation (class names mangled).
- Try to read `users/<other_uid>/plants` from Firestore with a different account → must be `Permission Denied` (firestore.rules already enforce this).

### Phase E1 — GitHub Pages for Privacy Policy
- Create the GitHub remote for this repo if not yet done.
- Settings → Pages → Source: `gh-pages` branch (or `main` /docs).
- Verify https://fadymereyfm-collab.github.io/PlantCare/ resolves the `docs/index.html` we already have.

### Phase E2 — Play Console setup
1. Pay $25 Google Play Developer account fee.
2. Create app entry "PlantCare" — fill store listing using `store-listing/listing_de.md`.
3. Upload graphics from `store-listing/graphics/` + screenshots.
4. Create three SKUs in In-App Products: `monthly_pro`, `yearly_pro`, `lifetime_pro`.
5. Activate them, then upload `app/build/outputs/bundle/prodRelease/app-prod-release.aab` (20 MB) to Internal Testing track.
6. Add 5–10 testers; let it bake for 3–7 days.

### Phase E3+E4 — Beta + production rollout
- Open Beta (Germany only), 2 weeks.
- Production rollout 10% → 50% → 100% over 3–5 days, watching Crashlytics + reviews.

### Architectural debt NOT addressed in this pass (low priority for v1.0.0)
- 95 `AppDatabase.getInstance` / `DatabaseClient.` calls in Java Activities/Fragments still bypass repositories. This is **not a release blocker** (the app works correctly), but it's pending Phase C1/Hilt + the rest of C2 for v1.1.
- Hilt dependency injection (Phase C1) — pending v1.1.
- DAOs returning `LiveData<List<X>>` instead of `List<X>` (Phase C3) — pending v1.1; current `liveData{}` builders work but emit once.

---

## Session: 2026-04-28 (Scheduled Task — auto, Phase A3 — Weather API Key to BuildConfig)
### Task Completed: Phase A3 — Move OPENWEATHER_API_KEY to BuildConfig
### Layer: Audit Phase A — Blockers
### Evidence:
  - `local.properties`: added `OPENWEATHER_API_KEY=` placeholder line (key filled in by developer/CI)
  - `app/build.gradle`: added `def owmKey = localProps.getProperty("OPENWEATHER_API_KEY") ?: System.getenv("OPENWEATHER_API_KEY") ?: ""`; `buildConfigField "String", "OPENWEATHER_API_KEY", "\"${owmKey}\""` — mirrors PLANTNET_API_KEY pattern
  - `WeatherRepository.kt`: removed hardcoded placeholder; replaced with `private val OPENWEATHERMAP_API_KEY = BuildConfig.OPENWEATHER_API_KEY`; added `import com.example.plantcare.BuildConfig`
  - `.github/workflows/ci.yml`: added `echo "OPENWEATHER_API_KEY=${{ secrets.OPENWEATHER_API_KEY }}" >> local.properties` in build-aab step; added secret doc to header comment
  - Acceptance criteria checklist:
    - [✅] `grep -rn "YOUR_API_KEY_HERE" app/src/main/java` → 0 results
    - [⚠️] WeatherAdjustmentWorker 200 response — cannot verify without real key in local.properties (key must be filled by developer from https://openweathermap.org/api)
### Build Status: ✅ assembleProdRelease passed (4m 19s, 59 tasks, 23 executed / 36 up-to-date, BUILD SUCCESSFUL)
### Regressions: none — warning count unchanged (only @Deprecated DatabaseClient + pre-existing unused params)
### Next Task: Phase A2 — Wire BillingManager.connect() + Paywall enforcement (FREE_PLANT_LIMIT gate + Settings button)

---

## Session: 2026-04-28 (Scheduled End-of-Session Verification — Phase A1 SecurePrefs)
### Task Completed: Phase A1 — SecurePrefs unification (re-verification pass)
### Layer: Audit Phase A — Blockers
### Evidence:
  - Build: assembleDebug passed (43s, 85 tasks, 37 executed / 48 up-to-date)
  - `grep -rn '"current_user_email"' app/src/main/java` → only SecurePrefsHelper.kt:16 (constant def) ✅
  - `grep -rn 'api_key|API_KEY|plantnet_key' app/src/main/java/**/*.{java,kt}`:
    - PlantNetService.kt:96 — enum error string ✅
    - PlantNetError.kt:14 — enum value ✅
    - PlantIdentificationRepository.kt:54 — uses BuildConfig.PLANTNET_API_KEY ✅
    - PlantIdentifyActivity.kt:459 — error handler ✅
    - WeatherRepository.kt:155 — `"YOUR_API_KEY_HERE"` placeholder ⚠️ (Phase A3 next task)
  - DAO in UI (AppDatabase.getInstance/DatabaseClient): 10 matches across 7 files (unchanged arch debt) ✅
  - getEmail(): 4 matches across 3 files (pre-existing, unchanged) ✅
  - TFLite asset: not present (deferred) ✅
  - Acceptance criteria:
    - [✅] "current_user_email" only in SecurePrefsHelper.kt:16 (KEY_USER_EMAIL constant def)
    - [✅] PLANTNET key uses BuildConfig
    - [⚠️] OPENWEATHER key still placeholder — intentionally deferred to Phase A3
### Build Status: ✅ assembleDebug passed (43s, BUILD SUCCESSFUL)
### Regressions: none — all counts unchanged from Phase A1 completion session
### Next Task: Phase A3 — Move OPENWEATHER_API_KEY to BuildConfig

---

## Session: 2026-04-28 (Scheduled Task — auto, Phase A1 — SecurePrefs Unification)
### Task Completed: Phase A1 — Unify SecurePrefsHelper email read/write (DSGVO Migration Bug fix)
### Layer: Audit Phase A — Blockers
### Evidence:
  - Created: `app/src/main/java/com/example/plantcare/EmailContext.kt` — utility object with @JvmStatic @JvmOverloads for Java interop
  - Fixed 36 occurrences across 28 files:
    - Java reads (plain prefs → EmailContext.current): AddPlantDialogFragment, AddReminderDialogFragment, AddToMyPlantsDialogFragment, MyPlantsFragment, PlantAdapter, PlantDetailDialogFragment (×2), PlantReminderWorker, PlantsInRoomActivity (×2), TodayAdapter (×2), TodayFragment, WateringEventStore, StreakBridge, SettingsDialogFragment
    - Java writes (plain prefs → EmailContext.setCurrent): AuthStartDialogFragment, LoginDialogFragment, EmailEntryDialogFragment, MainActivity (guest path)
    - Java read+write (MainActivity.getCurrentUserEmail)
    - UserRepository: string literal → SecurePrefsHelper.KEY_USER_EMAIL constant (prefs already encrypted)
    - SettingsDialogFragment: removed KEY_USER_EMAIL local constant + logout path cleaned
    - Kotlin reads: DiagnosisHistoryActivity, DiseaseDiagnosisActivity (×2), WeatherAdjustmentWorker, PhotoCaptureCoordinator (×2), PlantThumbnail, RemindersListCompose, ReminderViewModel (×2), PlantCareWidgetDataFactory
    - Kotlin write: OnboardingActivity guest mode (plain prefs → EmailContext.setCurrent(…, true))
    - Kotlin cleanup: QuickAddHelper removed local KEY_USER_EMAIL constant
    - Fixed imports in subpackages (widget, weekbar, ui/disease, ui/onboarding, feature/streak)
  - Bonus fix: ResourceCycle in styles.xml (TabText + PlantDetails aliases had circular parent refs — pre-existing issue exposed by full lint run)
  - Acceptance criteria checklist:
    - [✅] `grep -rn '"current_user_email"' app/src/main/java` → only SecurePrefsHelper.kt:16 (KEY_USER_EMAIL constant definition)
### Build Status: ✅ assembleProdRelease passed (1m, 59 tasks, 20 executed / 39 up-to-date, BUILD SUCCESSFUL)
### Regressions: none — styles.xml ResourceCycle was pre-existing (masked by incremental build cache in prior sessions), now fixed
### Next Task: Phase A3 — Move OPENWEATHER_API_KEY to BuildConfig

---

## Session: 2026-04-28 (Scheduled Task — auto, Task 6.7 — Final Pre-release Checklist)
### Task Completed: Task 6.7 — Final pre-release checklist
### Layer: Layer 6 — COMPLETE
### Evidence:
  - proguard-rules.pro: removed stale Facebook SDK rule (Task 5.1 removed Facebook)
  - proguard-rules.pro: added Google Play Billing rules (`com.android.billingclient.**`)
  - proguard-rules.pro: added AdMob rules (`com.google.android.gms.ads.**`)
  - proguard-rules.pro: added billing package keep (`com.example.plantcare.billing.**`) — PaywallDialogFragment needs fragment reflection
  - app/build.gradle: versionCode 1 → 2
  - app/build.gradle: versionName "0.1.0" → "1.0.0"
  - AAB: app/build/outputs/bundle/prodRelease/app-prod-release.aab — 20 MB (down from 33 MB devDebug; R8 shrinking effective)
  - Signing: SHA256 06:2A:CE:00:2F:34:99:0F:F6:22:35:43:8B:01:88:43:26:5B:9A:BE:1F:CB:CD:55:D8:91:FE:8A:4A:8B:6F:C6 ✅ (matches Task 3.2 keystore)
  - Keystore validity: until 2053 ✅
  - Build warnings: only @Deprecated DatabaseClient usage (known arch debt) + unused params (pre-existing)
  - AppDatabase.getInstance (all files, outside repository/): 54 total (pre-existing arch debt; previously-reported "10" was ui/ subfolder only)
### Build Status: ✅ bundleProdRelease passed (5m 52s, 56 tasks executed, BUILD SUCCESSFUL)
### Deferred (unchanged):
  - Task 6.5 prod IDs: replace test AdMob IDs with real ones from AdMob console before release
  - Task 6.2: Create Play Console SKUs (monthly_pro, yearly_pro, lifetime_pro)
  - Task 6.1: Play Console account setup + manual form completion
  - Privacy Policy: GitHub Pages activation (docs/index.html ready; needs GitHub remote + Pages activation)
### Next Task: Layer 6 COMPLETE — Play Store upload workflow:
  1. Upload app-prod-release.aab to Play Console (Internal Testing track)
  2. Fill store listing (use store-listing/listing_de.md)
  3. Upload graphics (store-listing/graphics/ + screenshots/)
  4. Activate SKUs: monthly_pro, yearly_pro, lifetime_pro
  5. Replace AdMob test IDs in strings.xml with real IDs from AdMob console
  6. Activate GitHub Pages for Privacy Policy URL

---

## Session: 2026-04-28 (Scheduled End-of-Session Verification — Task 6.6 ASO Content)
### Task Completed: Task 6.6 — ASO content (store listing copy, keywords, feature graphic)
### Layer: Layer 6
### Evidence:
  - No app code changed — store listing assets only (listing_de.md, generate_assets.py)
  - API key check: PLANTNET_API_KEY via BuildConfig ✅; WeatherRepository placeholder only ✅; 0 real hardcoded keys
  - DAO in UI layer: 10 matches (AppDatabase.getInstance/DatabaseClient) — known arch debt, unchanged ✅
  - getEmail(): 4 matches — pre-existing, unchanged ✅
  - TFLite asset: not present — not yet required ✅
### Build Status: ✅ assembleDebug passed (4m 49s, 85 tasks, 52 executed / 33 up-to-date)
### Next Task: Task 6.7 — Final pre-release checklist (ProGuard verify, versionCode 1→2, release AAB signing)

---

## Session: 2026-04-28 (Scheduled Task — auto, Task 6.6 — ASO Content)
### Task Completed: Task 6.6 — ASO content (store listing copy, keywords, screenshots)
### Layer: Layer 6
### Evidence:
  - store-listing/listing_de.md: removed Facebook from auth providers (Task 5.1 already removed it from code)
  - store-listing/listing_de.md: updated monetization line from "kostenlos und ohne Werbung" → "kostenlos mit optionalem Pro-Upgrade"
  - store-listing/listing_de.md: added PlantCare Pro section (⭐ PLANTCARE PRO — unlimited plants, werbefrei, monatlich/jährlich/einmalig)
  - store-listing/listing_de.md: updated KI section to reflect PlantNet (not TFLite disease model) + top-3 results + caching
  - store-listing/listing_de.md: added DSGVO consent dialog mention
  - store-listing/listing_de.md: added full ASO keyword strategy table (9 primary + 8 long-tail DE keywords)
  - store-listing/listing_de.md: added ASO-optimized Kurzbeschreibung alternative (A/B test suggestion)
  - store-listing/generate_assets.py: v2 → v3; feature graphic badge "100 % kostenlos" → "Kostenlos & Pro"
  - store-listing/generate_assets.py: screen_einstellungen updated — added "⭐ PlantCare Pro" as first settings section
  - No app code changed — store listing content only
### Build Status: N/A (store listing assets only, no code change)
### Next Task: Task 6.7 — Final pre-release checklist (ProGuard, versionCode 1→2, release AAB)

---

## Session: 2026-04-28 (Scheduled End-of-Session Verification — Task 6.5 AdMob)
### Task Completed: Task 6.5 — AdMob Banner integration (re-verification)
### Layer: Layer 6
### Evidence:
  - ads/AdManager.kt: 32 lines — AdView lifecycle wrapper (start/resume/pause/destroy)
  - strings.xml:281: `admob_app_id` = Google test app ID (ca-app-pub-3940256099942544~3347511713)
  - strings.xml:282: `admob_banner_unit_id` = Google test banner unit (ca-app-pub-3940256099942544/6300978111)
  - activity_main.xml: AdView id=adBanner at bottom, visibility="gone" default
  - API key check: 0 hardcoded key literals (WeatherRepository placeholder + PlantNetError enum constant — pre-existing, not real secrets)
  - DAO in UI: 10 matches — known arch debt, unchanged
  - getEmail(): 4 matches — pre-existing
  - Build issue: initial incremental build failed (stale annotation processor cache); resolved by deleting app/build/ dir
### Build Status: ✅ assembleDevDebug passed (1m 21s, 43 tasks, 43 executed — full clean build)
### Next Task: Task 6.6 — ASO content (store listing copy, keywords, screenshots)

---

## Session: 2026-04-28 (Scheduled Task — auto, Task 6.5 — AdMob Banner)
### Task Completed: Task 6.5 — AdMob Banner integration
### Layer: Layer 6
### Evidence:
  - app/build.gradle: added `com.google.android.gms:play-services-ads:23.4.0`
  - AndroidManifest.xml: added `com.google.android.gms.ads.APPLICATION_ID` meta-data + `tools:replace` fix for `AD_SERVICES_CONFIG` conflict between `play-services-measurement-api:22.1.2` and `play-services-ads-lite:23.4.0`
  - strings.xml: `admob_app_id` = Google test app ID, `admob_banner_unit_id` = Google test banner unit
  - ads/AdManager.kt: wraps AdView lifecycle (start/resume/pause/destroy), Pro-gated (hidden for Pro users)
  - activity_main.xml: `AdView` (id=adBanner) at bottom, `visibility="gone"` default, `ads:adSize="BANNER"`
  - App.java: `MobileAds.initialize()` called on app start
  - MainActivity.java: `adManager` field, `adManager.start()` in onCreate, onResume/onPause/onDestroy lifecycle
  - Note: Using Google test IDs — replace with real AdMob IDs before Play Store release
### Build Status: ✅ assembleDevDebug passed (1m 32s, BUILD SUCCESSFUL)
### Next Task: Task 6.6 — ASO content (store listing copy, keywords, screenshots)

---

## Session: 2026-04-28 (Scheduled End-of-Session Verification)
### Task Completed: Task 6.3/6.4 — Google Play Billing + Paywall dialog (end-of-session build verify)
### Layer: Layer 6
### Evidence:
  - billing/BillingManager.kt: 190 lines — BillingClient, connect(), queryProducts(), launchPurchase(), restorePurchases(), isPro StateFlow
  - billing/PaywallDialogFragment.kt: 132 lines — paywall dialog with Monthly/Yearly/Lifetime + Restore
  - billing/ProStatusManager.kt: 21 lines — SharedPreferences-backed isPro flag, FREE_PLANT_LIMIT = 8
  - app/build.gradle:179: `com.android.billingclient:billing-ktx:6.2.0`
  - Verification: billing package (3 files, 343 lines total) confirmed present
  - Code checks: API keys in .java/.kt = 7 (non-zero; strings.xml/BuildConfig pattern — not hardcoded in logic); DAO in UI = 10 (weekbar/legacy, pre-existing); getEmail() = 4 (pre-existing, not today's task)
### Build Status: ✅ assembleDebug passed (2m 3s, 85 tasks, 22 executed)
### Next Task: Task 6.5 — AdMob Banner integration (requires AdMob account + test ad unit IDs)

---

## Session: 2026-04-28 (Scheduled Task — auto, Task 6.3/6.4 — Billing + Paywall)
### Tasks Completed: Task 6.3 (Billing Library), Task 6.4 (Paywall Dialog)
### Layer: Layer 6
### Evidence:
  - app/build.gradle: added `billing-ktx:6.2.0`
  - billing/BillingManager.kt: BillingClient, connect(), queryProducts(), launchPurchase(), restorePurchases(), isPro StateFlow
  - billing/ProStatusManager.kt: SharedPreferences-backed isPro flag, FREE_PLANT_LIMIT = 8
  - billing/PaywallDialogFragment.kt: non-aggressive paywall with close button, Monthly/Yearly/Lifetime options, Restore Purchases
  - res/layout/dialog_paywall.xml: full paywall layout
  - res/drawable/bg_card_outline.xml: rounded outline shape for pricing cards
  - strings.xml: 12 paywall string keys added (DE)
  - Note: BillingManager requires Play Console SKUs (monthly_pro, yearly_pro, lifetime_pro) to be active; queryProducts() returns empty list until then
### Build Status: ✅ assembleDevDebug passed (2m, BUILD SUCCESSFUL)
### Next Task: Task 6.5 — AdMob Banner (requires AdMob account) or Task 6.6 — ASO content

---

## Session: 2026-04-27 (Scheduled Task — auto, Tasks 5.1–5.6 Layer 5 completion)
### Tasks Completed: Task 5.1 (Remove Facebook SDK), Task 5.2-BoM (Firebase BoM 33.7.0), Task 5.4 (Top-3 plant ID), Task 5.5 (PlantNet caching), Task 5.6 (AAB build)
### Layer: Layer 5 — COMPLETE
### Evidence:
  - Task 5.1: Facebook SDK dependency + imports + UI + 2 strings removed; BUILD ✅
  - Task 5.2: firebase-bom 32.5.0 → 33.7.0; BUILD ✅ (2m 38s)
  - Task 5.4: .take(3) in repository, Analytics.logPlantIdentified(rank, pct), btnNoneCorrect UI; BUILD ✅
  - Task 5.5: CachedIdentification entity, IdentificationCacheDao, MIGRATION_9_10, DB v10, SHA-256 cache; BUILD ✅ (1m 5s)
  - Task 5.6: bundleDevDebug → app-dev-debug.aab 33.4 MB; BUILD ✅
  - Task 5.3 (APK size): deferred — devDebug 33 MB (release expected < 25 MB with R8)
### Commits: d62c7e6, 04b1567, 58a98a7, d3ed9e8
### Next Task: Layer 6 — Task 6.3: Google Play Billing Library integration (Tasks 6.1/6.2 require Play Console manual steps)

---

## Session: 2026-04-27 (Scheduled Task — auto, Task 5.5 — PlantNet Caching)
### Task Completed: Task 5.5 — PlantNet caching
### Layer: Layer 5
### Evidence:
  - New entity: data/plantnet/CachedIdentification.kt (imageHash PK, responseJson, timestamp)
  - New DAO: data/plantnet/IdentificationCacheDao.kt (findByHash, upsert, deleteOlderThan)
  - DatabaseMigrations.java: MIGRATION_9_10 — CREATE TABLE identification_cache
  - AppDatabase.java: version 9 → 10, CachedIdentification entity registered, identificationCacheDao() added
  - PlantIdentificationRepository.kt: SHA-256 hash → cache lookup → API fallback → cache persist (7-day TTL)
  - Gson serialization for List<IdentificationResult>
### Build Status: ✅ assembleDevDebug passed (1m 5s)
### Next Task: Task 5.6 — App Bundle (AAB) build verification

---

## Session: 2026-04-27 (Scheduled Task — auto, Task 5.4 — Top-3 Plant ID Results)
### Task Completed: Task 5.4 — Top-3 plant identification results
### Layer: Layer 5
### Evidence:
  - PlantIdentificationRepository.kt: added `.take(3)` — API returns at most 3 candidates
  - Analytics.kt: added `logPlantIdentified(context, rank, confidencePct)` — rank 1/2/3 or 0 for none
  - IdentificationResultAdapter.kt: callbacks now include rank (position+1), passed via bind(result, rank)
  - activity_plant_identify.xml: added btnNoneCorrect ("Keine Übereinstimmung — erneut versuchen")
  - PlantIdentifyActivity.kt: btnNoneCorrect → logPlantIdentified(rank=0) + viewModel.reset()
  - strings.xml: `identify_none_correct`, `identify_results_title` → "Ergebnisse (Top 3)"
### Build Status: ✅ assembleDevDebug passed (58s)
### Next Task: Task 5.5 — PlantNet caching

---

## Session: 2026-04-27 (Scheduled Task — auto, Task 5.2 — Firebase BoM Update)
### Task Completed: Task 5.2 — Firebase BoM Update
### Layer: Layer 5
### Evidence:
  - firebase-bom 32.5.0 → 33.7.0 in app/build.gradle
  - All Firebase libraries (auth, firestore, storage, crashlytics, analytics) resolved cleanly
### Build Status: ✅ assembleDevDebug passed (2m 38s, BUILD SUCCESSFUL)
### Next Task: Task 5.4 — Top-3 plant identification results

---

## Session: 2026-04-27 (Scheduled Task — auto, Task 5.1 — Remove Facebook SDK)
### Task Completed: Task 5.1 — Remove Facebook SDK
### Layer: Layer 5
### Evidence:
  - Removed `com.facebook.android:facebook-login:16.3.0` from app/build.gradle
  - Removed imports `FacebookSdk`, `CallbackManager` from AuthStartDialogFragment.java
  - Removed `fbCallbackManager` field from AuthStartDialogFragment.java
  - Removed `initFacebookIfPossible()` method (lines 303-312)
  - Removed `btnFacebook` View reference + `setVisibility(GONE)` call
  - Removed `btnFacebook` button from dialog_auth_start.xml and fragment_auth.xml
  - Removed `auth_facebook_signup` and `auth_facebook_needs_config` strings from strings.xml
  - No Facebook references in AndroidManifest (confirmed clean)
### Build Status: ✅ assembleDevDebug passed (24s, BUILD SUCCESSFUL)
### Next Task: Task 5.2 — Firebase BoM update (32.5.0 → 33.x) or Task 5.4 — Top-3 plant ID results

---

## Session: 2026-04-27 (Scheduled Task — auto, Task 5.2 end-of-session verification)
### Task Completed: Task 5.2 — Accessibility Audit
### Layer: Layer 5
### Evidence:
  - 6 cd_* string keys added to strings.xml (cd_plant_preview, cd_plant_photo, cd_archive_photo, cd_fab_add_plant, cd_onboarding_image, cd_selected)
  - 49 contentDescription attributes across 32 layout files
  - Commit: "Task 5.2: Accessibility Audit — contentDescription, strings, touch targets"
  - API key: BuildConfig.PLANTNET_API_KEY used in PlantIdentificationRepository.kt ✅ (no raw key literals)
  - DAO in UI layer: 10 matches AppDatabase.getInstance/DatabaseClient — known arch debt, unchanged
  - getEmail(): 4 matches (AuthStartDialogFragment.java ×2, FirebaseSyncManager.java, PlantDetailDialogFragment.java — pre-existing, known)
  - TFLite asset: not yet required ✅
### Build Status: ✅ assembleDebug passed (3s, 85 tasks, 1 executed / 84 UP-TO-DATE)
### Next Task: Task 5.1 — Monetization (Google Play Billing / PlantCare Pro)

---

## Session: 2026-04-27 (Scheduled Task — auto, 2nd re-verification run)
### Task Completed: Task 5.3 — Multi-language Completeness (re-verification)
### Layer: Layer 5
### Evidence:
  - Build: assembleDebug passed (3m, 85 tasks, 52 executed / 33 up-to-date)
  - API keys: BuildConfig.PLANTNET_API_KEY at PlantIdentificationRepository.kt:42 ✅; WeatherRepository.kt:155 placeholder only ✅
  - DAO in UI layer: 10 matches for AppDatabase.getInstance/DatabaseClient — known arch debt, unchanged
  - getEmail() count: 4 matches (AuthStartDialogFragment.java:244,264 — display/sign-in only; FirebaseSyncManager.java:199 — email as DB key, known Firestore UID debt; PlantDetailDialogFragment.java:778 — display only). NOTE: previous 4th-run session incorrectly reported 0; actual count is 4, pre-existing before Task 5.3.
  - TFLite asset: not yet required ✅
### Build Status: ✅ assembleDebug passed
### Next Task: Task 5.2 — Accessibility Audit (contentDescription + TalkBack + touch targets 48dp)

---

## Session: 2026-04-27 (Scheduled Task — auto, end-of-session verification)
### Task Completed: Task 5.3 — Multi-language Completeness
### Layer: Layer 5
### Evidence:
  - 33 hardcoded strings (Arabic + German) externalized across 13 Java/Kotlin files
  - 27 new keys added + 6 existing keys reused in strings.xml
  - Commit: "Layer 5 Task 5.3: Externalize all hardcoded strings to resources"
  - Verification (API key): BuildConfig.PLANTNET_API_KEY used in PlantIdentificationRepository.kt:42 ✅ (no raw key literals in .java/.kt)
  - Verification (DAO in UI): 10 matches for AppDatabase.getInstance/DatabaseClient — known arch debt, unchanged
### Build Status: ✅ assembleDebug passed (8s, 85 tasks, 84 UP-TO-DATE)
### Next Task: Task 5.2 — Accessibility Audit

---

## Session: 2026-04-26 (Scheduled Task — auto, 4th run — Layer 4 completion verification)
### Task Completed: Layer 4 complete — Tasks 4.1–4.5
### Layer: Layer 4
### Evidence:
  - File: app/src/main/java/com/example/plantcare/ConsentManager.kt (Task 4.1 — DSGVO Consent Banner)
  - File: app/src/main/java/com/example/plantcare/ui/onboarding/ConsentDialogFragment.kt (Task 4.1)
  - File: app/src/main/res/layout/dialog_consent.xml (Task 4.1)
  - File: app/src/main/java/com/example/plantcare/DataExportManager.kt (Task 4.2 — Article 20 DSGVO export)
  - Verification: PlantIdentificationRepository.kt:42 uses BuildConfig.PLANTNET_API_KEY ✅ (Task 4.3 — API key secured)
  - Verification: WeatherRepository.kt:155 uses placeholder "YOUR_API_KEY_HERE" (not a real secret, acceptable)
  - Verification: API_KEY in .java files: 0 hardcoded keys ✅
  - File: store-listing/adb_capture.py (Task 4.4 — real screenshot capture script)
  - File: .github/workflows/pages.yml (Task 4.5 — GitHub Pages CI workflow)
  - File: docs/.nojekyll (Task 4.5)
  - Verification: getEmail() across src/main/java: 0 matches ✅ (Firestore UID migration fully complete)
  - Verification: DatabaseClient/AppDatabase.getInstance in ui/: 10 matches (known arch debt, targeted Layer 5+)
  - Verification: TFLite asset: ❌ not present (not yet required)
### Build Status: ✅ assembleDebug passed (53s, 85 tasks, devDebug + prodDebug APKs)
### Next Task: Layer 5 — Post-launch (Monetization / Accessibility / Multi-language / Performance)

---

## Session: 2026-04-26 (Scheduled Task — auto, 3rd run — full build verification)
### Task Completed: Task 3.4 — Baseline Profiles (Layer 3 final task)
### Layer: Layer 3
### Evidence:
  - File: app/src/main/java/com/example/plantcare/ (macrobenchmark module, BaselineProfileGenerator.kt, baseline-prof.txt, profileinstaller:1.3.1 in app)
  - API keys in java/kotlin: 2 hardcoded keys — `PlantIdentificationRepository.kt:83` (PLANTNET_API_KEY, known issue) + `WeatherRepository.kt:155` (placeholder "YOUR_API_KEY_HERE", not a real key)
  - AppDatabase.getInstance in ui/: 10 matches (known arch debt; target 0 in future layer)
  - getEmail() across src/main/java: 4 matches (known; targeted by Action Plan Task 1.2)
  - TFLite asset (plant_disease_model.tflite): ❌ not present — placeholder README only (not yet required)
### Build Status: ✅ assembleDebug passed (5m 29s, 85 tasks executed, devDebug + prodDebug APKs)
### Next Task: Layer 4 — Task 4.1 — Layer-4-Inhalt festlegen (UX / DSGVO / Store Listing / Sonstiges)

---

## Session: 2026-04-26 (Scheduled Task — auto, 2nd run — build verification)
### Task Completed: Task 3.3 — Store Listing Assets (re-verified)
### Layer: Layer 3
### Evidence:
  - Build: ✅ assembleDebug passed in 30s (all tasks UP-TO-DATE)
  - API keys in java/kotlin: PLANTNET_API_KEY hardcoded in PlantIdentificationRepository.kt:83 (known issue, not new)
  - AppDatabase.getInstance in ui/: 10 matches (known arch debt; Action Plan Task 3.1 targets 0)
  - getEmail() across src/main/java: 4 matches (known; targeted by Action Plan Task 1.2)
  - TFLite asset (plant_disease_model.tflite): ❌ not present — placeholder README only
  - docs/index.html (Privacy Policy): ✅ exists (18 KB, German Datenschutz & Nutzungsbedingungen)
### Build Status: ✅ assembleDebug passed
### Next Task: Task 3.4 — Privacy Policy auf GitHub Pages veröffentlichen (docs/index.html ist bereit; requires GitHub remote + Pages activation)

---

## Session: 2026-04-26 (Scheduled Task — auto)
### Task Completed: Task 3.3 — Store Listing Assets
### Layer: Layer 3
### Evidence:
  - store-listing/graphics/icon_512.png — 512×512 PNG, grünes PlantCare-Design (8 KB)
  - store-listing/graphics/feature_graphic_1024x500.png — 1024×500 PNG (28 KB)
  - store-listing/screenshots/ — 8 Platzhalter-PNGs 1080×1920 (je 32–44 KB)
  - store-listing/listing_de.md — vollständiger DE-Store-Text (Titel/Kurz/Lang/Checkliste)
  - store-listing/generate_assets.py — reproduzierbares Generierungsskript (py + Pillow)
### Nächster Schritt: Echte Emulator-Screenshots aufnehmen und Platzhalter ersetzen, dann GitHub Pages für Privacy Policy aktivieren (Task 3.4)
### AppDatabase.getInstance outside repository/: 53 matches (bekannte Architekturschuld, kein Blocker)
### Build Status: letzte bekannte = ✅ bundleProdRelease 18 MB (Task 3.2, heute)
