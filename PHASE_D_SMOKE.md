# Phase D — Pre-Release Smoke Test (35 min)

> **Scope:** focused smoke for the changes shipped 2026-05-17 → 2026-05-18.
> Faster than `MANUAL_TESTS.md` (the full 4-6h sweep); used as the gate
> before deciding whether the full sweep is needed.
>
> **Date executed:** _____________
> **Device:** _____________
> **Build:** `./gradlew :app:assembleDebug` from commit _______________
>
> Mark each row **PASS / FAIL / SKIP** + add a one-line note when FAIL.
> If any row in §1 fails, stop and report — do not continue to §2.

---

## 0. Prerequisites (2 min)

| # | Step | Pass criterion |
|---|------|----------------|
| 0.1 | `./gradlew :app:assembleDebug` from a clean checkout | BUILD SUCCESSFUL, no new warnings vs the build log in PROGRESS.md |
| 0.2 | `adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk` (or use the run config) | App icon appears, taps launch into Onboarding |
| 0.3 | **Clear app data** before first row of §1 (Settings → Apps → PlantCare → Storage → Clear) | App relaunches into Onboarding flow, not Home |

---

## 1. Today's regressions — STOP IF ANY FAIL (18 min)

These cover edits from 2026-05-17/18. If any fail, the build is not ship-ready.

### 1.1 — UI-BUG-1 + UI-BUG-2: login dialog toggle + Mein Garten heading (2 min)

| # | Step | Pass criterion |
|---|------|----------------|
| 1.1a | Onboarding → tap **"Anmelden"** → in the dialog tap **"Mit E-Mail anmelden"** | The login form opens. Top-down order: **E-Mail → Passwort → Anmelden button → Passwort vergessen? → Neu hier? Jetzt registrieren** (the registration toggle is at the BOTTOM, not above the email field). |
| 1.1b | Complete sign-in with a test account, then tap **Mein Garten** in the bottom nav | The screen shows toolbar **"Mein Garten"** + hero **"Willkommen"** + subtitle "Verwalte deine Räume und Pflanzen." The hero is NOT a duplicate "Mein Garten". |

### 1.2 — UI-BUG-4: branded forgot-password dialog (1 min)

| # | Step | Pass criterion |
|---|------|----------------|
| 1.2a | Sign out → Onboarding → Anmelden → Mit E-Mail anmelden → tap **"Passwort vergessen?"** | Dialog matches login dialog identity: rounded card (20dp), close X top-right, **Inter** title "Passwort zurücksetzen", outlined "Abbrechen" + filled **vivid green** "Link senden". NOT the plain Material AlertDialog from before. |

### 1.3 — UI-BUG-5: bottom-nav indicator (1 min)

| # | Step | Pass criterion |
|---|------|----------------|
| 1.3a | Tap each bottom-nav tab in turn: Home → Mein Garten → Diagnose → Einstellungen | The light-mint pill behind the selected tab's icon is **tight around the icon** (~48×32dp), not a wide blob that drifts off-centre. |

### 1.4 — UI-BUG-7 round 2: Pflanze hinzufügen no longer floating (1 min)

| # | Step | Pass criterion |
|---|------|----------------|
| 1.4a | Open Mein Garten — the 6 seed rooms (Wohnzimmer / Schlafzimmer / Flur / Küche / Bad / Toilette) | At the bottom: **two full-width stacked buttons** — vivid green "**+ Pflanze hinzufügen**" above tonal "**+ Zimmer hinzufügen**". The green button is NO LONGER floating over the Toilette row. |

### 1.5 — UI-BUG-8: distinct room colours (2 min)

| # | Step | Pass criterion |
|---|------|----------------|
| 1.5a | Mein Garten — look at the 6 default rooms' circle icons | Six **distinct** colours, not 6 identical sage-greens: Wohnzimmer terracotta orange, Schlafzimmer denim blue, Flur caramel brown, Küche sunset orange/yellow, Bad teal, Toilette slate grey. |
| 1.5b | Einstellungen → Design → switch to **Dunkel** → return to Mein Garten | The 6 rooms still distinct, ~12% darker palette. No grey blobs. |

### 1.6 — B4: branded language picker + B3: catalog switches to English (3 min)

| # | Step | Pass criterion |
|---|------|----------------|
| 1.6a | Einstellungen → tap **Sprache** row | A branded dialog opens (same MaterialCardView identity as forgot-password). 3 radio rows: System / Deutsch / English. Vivid green radio tint. |
| 1.6b | Select **English** | Dialog dismisses. App reloads. Most UI strings switch to English ("Home", "My Garden", "Settings", "Welcome"). |
| 1.6c | **CRITICAL:** Tap **Mein Garten** → tap any room → tap **"+ Pflanze hinzufügen"** → tap a plant from the catalog (e.g. Aloe Vera or Einblatt) | The plant's care text (lighting / soil / fertilising / watering) is in **English** ("Bright to medium light…" or "Loose well-draining soil…"), NOT in German. |
| 1.6d | Switch back to **Deutsch** via the Sprache picker | UI reverts to German immediately. Catalog plants opened from this point on show German care text. (Existing catalog data may stay in EN until app data is cleared — that's a known v1.0 limitation, not a fail.) |

### 1.7 — DEF-21: camera bitmap recycle (2 min)

| # | Step | Pass criterion |
|---|------|----------------|
| 1.7a | From Home → tap "Pflanze hinzufügen" → choose **Eigene Pflanze** → tap **Kamera** (or **Galerie**) and pick a 4000×3000 photo | The preview shows the downscaled (1280px) thumbnail. No OOM crash. Memory profiler in Android Studio (if available): heap shouldn't spike by 48 MB after pick — should be a few MB. |
| 1.7b | Repeat 1.7a 5× in a row choosing different gallery photos | No OOM. App stays responsive. |

### 1.8 — DEF-22: paywall guard on backgrounding (2 min)

| # | Step | Pass criterion |
|---|------|----------------|
| 1.8a | Free tier: add 8 plants to hit the FREE_PLANT_LIMIT. Try to add a 9th plant — but **press Home** on the device immediately after tapping "Speichern". Wait 3 s, then re-foreground the app. | No crash. The paywall either doesn't open (silently skipped due to state-saved guard) or opens cleanly. Specifically NO **"Can not perform this action after onSaveInstanceState"** in `adb logcat`. |

### 1.9 — Build-config sanity (1 min)

| # | Step | Pass criterion |
|---|------|----------------|
| 1.9a | `adb logcat | grep -i "permission\|crash\|exception"` while exercising any 3 features | No new exceptions in logcat that aren't already in the existing crash baseline. |

---

## 2. Critical happy paths (10 min)

If §1 all passed, run §2 to verify the unchanged code still works.

### 2.1 — Auth (2 min)

| # | Step | Pass criterion |
|---|------|----------------|
| 2.1a | Sign in with Google (one-tap) | Land on Today screen. Account email + name visible in Einstellungen → Konto. |
| 2.1b | Sign out → sign in with the **same** Google account again | Plants, rooms, reminders all reappear (cloud sync). |
| 2.1c | Sign out → sign in with **a different** Google account | Empty Today screen. No leak of the previous account's plants. |

### 2.2 — Plant + reminder happy path (4 min)

| # | Step | Pass criterion |
|---|------|----------------|
| 2.2a | Add a catalog plant (Aloe Vera) to Wohnzimmer | Card appears in Wohnzimmer; Today screen shows the next watering date based on the 14-day interval from the catalog. |
| 2.2b | Wait 5 s after add — does the bottom **+1 photo** challenge counter update? | Yes, "0 / 1 Monatsfoto" stays at 0/1 (no photo taken yet); "0/5 Pflanzen hinzufügen" updates to "1/5". |
| 2.2c | Tap a watering reminder on Today → "Markiere als erledigt" | Reminder disappears. Streak card updates to "1 day". |
| 2.2d | Force-trigger the next reminder: change device date forward 14 days. Open the app. | A new watering reminder appears for Aloe Vera. |

### 2.3 — Paywall (2 min)

| # | Step | Pass criterion |
|---|------|----------------|
| 2.3a | On a free account, add plants until you hit FREE_PLANT_LIMIT (8). Try to add the 9th. | Paywall opens. Cards show **Monthly €2.99 / Yearly €19.99 / Lifetime €39.99**. Vivid green "Subscribe" CTA on Yearly (recommended). |
| 2.3b | Tap "Käufe wiederherstellen" | Toast: "Käufe erfolgreich wiederhergestellt" (or "Purchases restored" in EN mode). No crash even if user has zero past purchases. |
| 2.3c | Close paywall (X). Are you back on the plants list? | Yes, no crash, count still 8 (the 9th wasn't added). |

### 2.4 — Diagnose (Gemini cloud) (2 min)

| # | Step | Pass criterion |
|---|------|----------------|
| 2.4a | Tap the **center FAB camera** → tap **Diagnose starten** (or the Diagnose tab) → tap **Kamera** or **Galerie** → choose a photo of any plant leaf | "Blatt analysieren" tapped → progress spinner → result card with disease assessment + recommended treatment from Gemini 2.5 Flash. |
| 2.4b | "Verlauf anzeigen" | Past diagnoses listed. |

---

## 3. Regression buffer (5 min)

| # | Step | Pass criterion |
|---|------|----------------|
| 3.1 | Force-stop the app, relaunch | Lands on the last screen + state restored from cloud. |
| 3.2 | Toggle dark mode in Einstellungen → relaunch | Dark mode persists. Room colours still distinct (per 1.5b). |
| 3.3 | Pull-to-refresh on Home | No crash, snackbar "Cloud-Daten importiert" appears. |
| 3.4 | Background the app for 30 s, then foreground | State restored, no flash of empty screens. |
| 3.5 | Rotate device (if a tablet test) | Layouts adapt, no overlapping text. |

---

## 4. Sign-off (if all PASS)

- [ ] **§1 all PASS** — no regressions vs today's edits.
- [ ] **§2 all PASS** — happy paths still work.
- [ ] **§3 all PASS** — basic stability.
- [ ] **No new exceptions in logcat** during the entire sweep.
- [ ] **APK size** check: `app-dev-debug.apk` is roughly ~30-40 MB (no surprise growth — the 3 TTF deletions + B3 CSV growth should net out close to baseline).

**If all 4 boxes checked → green-light Phase E (release sign + Play Console upload).**
If any FAIL → file the failing row(s) in PROGRESS.md as a re-opened task, fix, then re-run only the affected §.

---

## 5. Known limitations (NOT failures)

These are documented v1.0 trade-offs — do NOT count them as fails:

  - **B3 catalog text doesn't re-translate on language switch.** Catalog is seeded once on first launch. Switching DE↔EN via the picker changes UI strings + paywall + diagnose + system messages, but the 505 catalog plants keep the language they were seeded with. To force a re-seed: Settings → Apps → PlantCare → Storage → Clear data, then relaunch.
  - **Welcome illustration is the flat "3 leaves in pot" SVG.** Awaiting designer asset (UI-BUG-3).
  - **Centre FAB camera icon is the vector outline.** Awaiting 3D raster from Claude Design (UI-BUG-6 final).
  - **No animations / Lottie / haptics yet.** Deferred design call (G10.3).
