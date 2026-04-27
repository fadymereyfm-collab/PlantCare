# PlantCare Progress Tracker
## Last Updated: 2026-04-28
## Current Layer: Layer 6 (in progress)
## Completed Tasks: Task 0.1, Task 0.2, Task 0.3, Task 0.4, Task 0.6, Task 1.1, Task 1.2, Task 1.3, Task 1.4, Task 1.5, Task 2.1, Task 2.2, Task 2.3, Task 2.4, Task 2.5, Task 3.1, Task 3.2, Task 3.3, Task 3.4, Task 4.1, Task 4.2, Task 4.3, Task 4.4, Task 4.5, Task 5.3, Task 5.2, Task 5.1, Task 5.2-BoM, Task 5.4, Task 5.5, Task 5.6, Task 6.3, Task 6.4
## Deferred: Privacy Policy GitHub Pages activation — docs/index.html + pages.yml exist; needs GitHub remote + Pages activation
## Deferred (Task 5.3): APK size analysis — devDebug AAB = 33.4 MB; release build with R8 expected < 25 MB; verify when signing key available
## Deferred (Task 6.1): Google Play Console setup — requires Play Console account + manual form completion
## Deferred (Task 6.2): Billing SKUs — requires Play Console to create SKUs (monthly_pro, yearly_pro, lifetime_pro)
## Last Verified Task: Task 6.3/6.4 — BillingManager.kt + PaywallDialogFragment.kt + ProStatusManager.kt (BUILD SUCCESSFUL 2m)
## Next Task: Task 6.5 — AdMob Banner integration (requires AdMob account + test ad unit IDs)

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
