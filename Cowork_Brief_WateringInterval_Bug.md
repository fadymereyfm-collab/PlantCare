# PlantCare — Bug #7 Brief for Cowork

**Date prepared:** 2026-05-14
**App:** PlantCare Android (Java + Kotlin, Room, Firebase, MVVM, MaterialComponents v2 + a few Compose surfaces, MinSdk 24 / TargetSdk 35).
**Branch:** `session-2026-05-10-cleanup`
**Reporter:** On-device test by app owner (Fady), confirmed by reading the source.
**Scope:** One concrete bug + one user-facing product question. Code-only changes — no Firebase Console / Google Cloud Console work needed.

---

## 1. One-line summary

In the "Pflanze bearbeiten" dialog (Plant edit), saving a manually-changed **watering interval** silently reverts to the previously-displayed value. Every other edited field (name, lighting, soil, fertilizing, personal note, AND the three sibling intervals: Düngen / Sprühen / Umtopfen) saves correctly. Only the watering interval is affected.

---

## 2. Reproduction

1. Cold-launch the app, sign in (or guest mode).
2. Open any plant — either a catalog plant from the "Alle Pflanzen" tab, or one of the user's own plants from "Meine Pflanzen".
3. In the plant-detail dialog, tap **"Bearbeiten"**.
4. The lower section of the edit dialog renders a **"Pflege-Plan (anpassbar)"** group with four numeric inputs labelled
   `Gießen` / `Düngen` / `Sprühen` / `Umtopfen`, each with the suffix "Tage".
5. Change ONLY the **Gießen** field. For example: clear it, type `7`. Leave the descriptive "Bewässerung" free-text field above untouched (it still reads e.g. *"Alle 14 Tage. Wenig Wasser genügt."*).
6. Tap **"Speichern"**.
7. A "Gespeichert" toast appears. Re-open the edit dialog.

**Expected:** `Gießen` reads `7`.
**Actual:** `Gießen` reads `14` (the original value). Reminders also continue firing on the 14-day cadence.

The other three intervals (Düngen / Sprühen / Umtopfen) work as expected — change → save → re-open → new value shown and reminders rescheduled.

---

## 3. Root cause

`saveChanges()` in [`app/src/main/java/com/example/plantcare/EditPlantDialogFragment.java`](app/src/main/java/com/example/plantcare/EditPlantDialogFragment.java) lines **212–223**:

```java
// Watering interval: text-parse takes priority over the dialog
// field so a user editing the watering description ("alle 14 Tage"
// → "alle 7 Tage") still gets the new interval applied.
int parsedFromText = TextUtils.isEmpty(watering)
        ? 0
        : ReminderUtils.parseWateringInterval(watering);
final int newWaterIv = parsedFromText > 0
        ? parsedFromText
        : readInterval(editIntervalWater, oldWaterIv);
final int newFertIv  = readInterval(editIntervalFertilize, oldFertIv);
final int newMistIv  = readInterval(editIntervalMist, oldMistIv);
final int newRepotIv = readInterval(editIntervalRepot, oldRepotIv);
```

Note the asymmetry: **only the watering branch** has the `parsedFromText > 0 ? parsedFromText : …` short-circuit. The other three intervals read straight from their numeric `EditText` via `readInterval(field, oldValue)`.

`ReminderUtils.parseWateringInterval` (lines 245–258 of [`ReminderUtils.java`](app/src/main/java/com/example/plantcare/ReminderUtils.java)) is a naive digit grabber:

```java
public static int parseWateringInterval(String watering) {
    if (watering == null || watering.isEmpty()) return 0;
    try {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(watering);
        int interval = 0;
        while (matcher.find()) {
            interval = Integer.parseInt(matcher.group(1));
        }
        return interval;
    } catch (Exception e) { return 0; }
}
```

It returns the **last integer** in the string. So for the unchanged catalog text *"Alle 14 Tage. Wenig Wasser genügt."* it returns `14`. Because `14 > 0`, the ternary picks `parsedFromText` and discards whatever the user typed into `editIntervalWater`. The user's `7` never reaches the DB.

### Why this branch was added

The original intent (per the comment) was: *"if the user edits the free-text description, e.g. `Alle 14 Tage` → `Alle 7 Tage`, persist the new interval automatically."* Without this fallback, a user editing only the description string wouldn't see the schedule update.

The trade-off is that **the descriptive text becomes a stronger source of truth than the explicit numeric input** — which is the inverse of what every other field in the dialog does, and the inverse of what users expect.

### A related regression (companion bug #6 already landed in round 5)

Round 5 (commit pending on `session-2026-05-10-cleanup`) added `derivedInterval()` so the `Gießen / Düngen / Sprühen / Umtopfen` inputs **pre-fill** with parsed defaults for catalog plants (previously they were blank). This makes bug #7 more visible to users: they now see "14" pre-filled, change it to "7", save, then see it revert to "14" — strongly suggesting a save failure even though the data layer wrote correctly.

The fix to bug #7 needs to coexist with the pre-fill behaviour from round 5.

---

## 4. Impact

- **Functional:** Users cannot customise the watering interval via the dialog. They can only customise it indirectly by editing the German description string, which is fragile (regex picks the wrong number if the user writes "Alle 7 Tage, max. 14 Tage zwischen Gaben") and not discoverable.
- **Trust:** Toast says "Gespeichert" but the value visibly reverts. This is a "lying save" — worst-case UX category. Two on-device users have already flagged it.
- **Reminders:** Watering reminders continue firing on the pre-edit cadence. For users who shortened the interval (e.g. summer = more frequent watering), this means the app under-waters their plants. For users who lengthened it (low-light winter), it over-reminds.
- **Severity:** Medium-high. Blocks a primary user task. Workaround exists (edit the description text) but it's undiscoverable.

---

## 5. Proposed fixes — three options

### Option A — Numeric field always wins (minimum-change, recommended)

Drop the text-parse branch entirely from `saveChanges()`. Use the same code path as the other three intervals:

```java
final int newWaterIv = readInterval(editIntervalWater, oldWaterIv);
```

**Pros:** Symmetric with Düngen/Sprühen/Umtopfen. Fixes the bug. One-line change.
**Cons:** A user who edits ONLY the description string ("Alle 14 Tage" → "Alle 7 Tage") and leaves the numeric field at 14 will keep the 14-day cadence. Mitigation: this is fine because the round-5 pre-fill already shows the parsed interval in the numeric field, so the user sees the canonical source clearly.

### Option B — Field wins, but parse text when description changed (most user-respecting)

Compare the submitted `watering` string against the loaded `plant.getWatering()`. If the description was modified AND the numeric field is empty/unchanged, parse the new description. Otherwise the numeric field wins.

```java
final boolean wateringTextChanged = !java.util.Objects.equals(
        watering, plant.getWatering());
final int fieldValue = readInterval(editIntervalWater, -1); // -1 = blank/invalid
final int parsedFromText = wateringTextChanged && !TextUtils.isEmpty(watering)
        ? ReminderUtils.parseIntervalDays(watering)  // smarter parser, unit-aware
        : 0;

final int newWaterIv;
if (fieldValue >= 0) {
    // User typed something explicit → respect it.
    newWaterIv = fieldValue;
} else if (parsedFromText > 0) {
    // User cleared the field but rewrote the description → use the description.
    newWaterIv = parsedFromText;
} else {
    newWaterIv = oldWaterIv;
}
```

Notes:
- This requires `readInterval` to distinguish "blank" from "0" (currently it returns 0 for both). Either add an overload that returns `-1` for blank, or read the EditText directly here.
- Switch from `parseWateringInterval` (last-int-found) to `parseIntervalDays` (unit-aware, lines 280+ of `ReminderUtils.java`) — it already correctly handles "alle 4 Wochen" → 28, "einmal pro Monat" → 30, etc.
- Snapshot `plant.getWatering()` BEFORE `plant.setWatering(watering)` on line 194 — otherwise the comparison is meaningless.

**Pros:** Handles both flows correctly. Symmetric with the round-5 pre-fill UX (parse → number → field).
**Cons:** Larger diff, more conditions to test.

### Option C — Lock the numeric field (only available via family defaults)

Hide / disable the `editIntervalWater` input. Force users to either accept the default or edit the description text. **NOT recommended** — see §6 below.

---

## 6. Design question — Should watering interval be user-editable at all?

The app owner asked: *"Is it in the app's interest for these values to be editable by the user?"*

### Arguments to LOCK the interval

- The catalog (`plants.csv`) and `PlantCareDefaults.forFamily()` (34 botanically-curated families in [`PlantCareDefaults.kt`](app/src/main/java/com/example/plantcare/data/plantnet/PlantCareDefaults.kt)) provide good defaults. Family-level data was sourced from verified botanical references (per `PROGRESS.md` v17.11).
- A user who sets the wrong interval can kill their plant, then blame the app. Plant-care apps that auto-schedule everything (without user override) protect themselves against this.
- For users on the German market who aren't botanists (the target audience), surfacing fewer dials = less paradox of choice.

### Arguments to KEEP it editable (recommended)

- **Microclimate is hyper-local.** A Berlin apartment with central heating in winter dries soil twice as fast as a humid Hamburg flat. Even within the same species, the same cultivar, the same pot — humidity, light intensity, draught, pot material (terracotta vs. glazed), and substrate all shift the right interval by 30–100%. No per-family default can be right for every user's home.
- **Cultivar variance.** "Aloe Vera" in the catalog assumes mature plants. A freshly-rooted offset needs more frequent watering for the first 6–8 weeks. A user who knows this needs an override.
- **Existing reminder semantics.** The whole point of the v16 multi-type reminder system was to give users control over four independent cadences. Locking watering specifically would create UX inconsistency — Düngen/Sprühen/Umtopfen stay editable, Gießen doesn't.
- **Competitor parity.** Picture This, Planta, and Greg all expose watering frequency as a user-editable field. Hiding it would feel "less powerful" relative to those.
- **The catalog is a SUGGESTION, not GROUND TRUTH.** `plants.csv` rows are curated but not species-specific to every cultivar.
- **Misuse protection is already partial.** `readInterval` clamps at `v >= 0` so the user can't set negative days. Saving 0 means "disable this reminder type" — which is a documented, intentional feature.

### Recommendation

**Keep watering interval editable AND fix Option A or B.** The cost of locking it (loss of user control, UX inconsistency, competitor gap) far exceeds the cost of someone setting it wrong (one plant death, recoverable). The fix is small and reverses a backwards priority that's the actual source of the bug.

If extra safety is desired, add a soft validation hint: warn the user inline (not block save) if they set an interval that's > 2× or < 0.5× the family default, with a "set anyway" button. This preserves control while flagging suspicious values.

---

## 7. Acceptance criteria for the fix

The Cowork agent picking up this brief should verify all of the following before marking the task done:

1. **Functional fix:**
   - Open any plant in Bearbeiten → change Gießen from `14` to `7` → Speichern.
   - Re-open the same plant in Bearbeiten. Gießen reads `7`.
   - DB inspection (`adb exec-out run-as com.fadymerey.plantcare … sqlite3 plant_care.db "select wateringInterval from plant where id=?"`) returns `7`.
   - Next-fire WateringReminder rows are regenerated for the new 7-day cadence (`reminderRepo.deleteFutureRemindersForPlantBlocking` followed by `generateAllReminders` already happens in the IO block of `saveChanges` — verify it still triggers because the `scheduleChanged` flag on line 187 evaluates to true).

2. **No regression to round 5 pre-fill (bug #6 from the same on-device test):**
   - Open a catalog plant (e.g. Aloe Vera) in Bearbeiten. The four interval fields pre-fill: Gießen=14, Düngen=28, Sprühen=blank (Asphodelaceae has mistingIntervalDays=0), Umtopfen=1095. None of the four should be blank for plants whose family is set.

3. **No regression to the "edit only the description text" path** (whichever option you pick):
   - **Option A:** This path stops working — document this in the Speichern button's hint or as a one-line snackbar after save when the description text changed but the field didn't.
   - **Option B:** Verify by changing only the description from "Alle 14 Tage..." to "Alle 7 Tage...". After save, Gießen=7.

4. **No regression to Düngen/Sprühen/Umtopfen edits** (they already work correctly today — make sure your change doesn't break them).

5. **Build:**
   - `./gradlew :app:assembleProdRelease` → BUILD SUCCESSFUL with no new warnings beyond the existing baseline.
   - `./gradlew test --rerun-tasks` if any data/repository tests exist (round-5 didn't touch this area, but the schedule-changed branch in `saveChanges` does reminder-repo work).

6. **PROGRESS.md entry** added at the top following the existing template (see "Session: 2026-05-13 round 5" entry for the format — Tasks Completed, Layer, Bug catalog, Fixes, Verification, Build Status, Regressions, Next Task).

7. **No new `getEmail()` calls and no new `AppDatabase.getInstance` outside `data/repository/`.** Current counters (from PROGRESS.md round 5): DAO-out-of-UI=4 across 2 files (PlantIdentifyActivity ×2, DiseaseDiagnosisActivity ×2). Do not increase these.

---

## 8. Files most likely to be touched

| File | Why |
|---|---|
| [`app/src/main/java/com/example/plantcare/EditPlantDialogFragment.java`](app/src/main/java/com/example/plantcare/EditPlantDialogFragment.java) | `saveChanges()` lines 212–223 — the actual fix. Possibly `readInterval` (lines 151–162) if you adopt Option B and need a "blank vs 0" distinction. |
| [`app/src/main/java/com/example/plantcare/ReminderUtils.java`](app/src/main/java/com/example/plantcare/ReminderUtils.java) | NOT touched for the fix itself, but if Option B switches the parser from `parseWateringInterval` (line 245) to `parseIntervalDays` (line 280), note that the latter is unit-aware (Tage/Wochen/Monate) — already used by the round-5 prefill helper so it's known-good. |

No layout, no DB schema, no Firebase, no Manifest changes are required.

---

## 9. Out of scope for this task

- Firebase Console / Google Cloud Console work (not the bug).
- The other six on-device bugs from the same test session — those landed in `session-2026-05-10-cleanup` round 5 (see PROGRESS.md).
- Hardening the German parser for unusual descriptions like "Alle 7–10 Tage, im Sommer öfter" (already handled by `parseIntervalDays` lower-bound logic).
- Adding the soft-validation warning suggested in §6 (a separate, optional follow-up).

---

## 10. Background context (for the Cowork agent)

This codebase is in a pre-release cleanup phase. Conventions enforced by `CLAUDE.md` at the repo root:

- **No claim without grep evidence.** Every "completed" claim in PROGRESS.md must be backed by a verifiable grep command and its output.
- **New code in Kotlin.** Don't convert existing Java files unless the task explicitly asks.
- **Threading:** No `new Thread()`. Use `viewModelScope.launch(Dispatchers.IO)` (Kotlin) or `com.example.plantcare.ui.util.FragmentBg.runIO(...)` (Java) — the latter is already used in `saveChanges`.
- **Comments:** Default to no comments. Only add when the WHY is non-obvious. Don't reference tasks/PRs in comments.
- **UI language:** German (DE) — strings.xml has DE as default, values-en/strings.xml mirrors. Don't translate user-visible strings unless the change requires it.
- The fix is small and surgical. The brief is long because the design question requires context, not because the code change does.
