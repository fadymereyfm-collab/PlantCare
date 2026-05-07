# PlantCare — Feature Inventory

> **Purpose:** قائمة موحّدة بكل وظائف التطبيق مع معرّف فريد لكل ميزة.
> **Usage:** مرجع لـ Cloud Coding routines، اختبار، تتبّع تنفيذ، debugging.
> **Last sync:** 2026-05-05 — يطابق الكود في `app/src/main/java/com/example/plantcare/`.
>
> **Status legend:** ✅ مكتملة | ⏳ مؤجّلة لـ v1.1 | 🔴 معطّلة | ⚠️ جزئية

---

## How to use this file

- **عند تنفيذ ميزة:** ارجع لـ ID (مثل `F5.3`) في commit / PR / PROGRESS.md.
- **عند الـ debugging:** الـ Code reference يحدّد الملف الرئيسي للقراءة الأولى.
- **في Cloud Coding routine:** أمثلة prompts:
  - `"Verify F8.1-F8.8 (PlantNet identification flow) on a sample image"`
  - `"List all features with ⏳ status"`
  - `"Refactor F11.x (Plant Journal) — extract MemoEntry rendering to its own helper"`
- **عند الاختبار اليدوي:** مرّ على كل ID وعلّم النتيجة (Pass/Fail/N/A).

---

## Master inventory table

| ID | Feature | Description | Status | Code |
|----|---------|-------------|:------:|------|
| **F1 — Authentication** | | | | |
| F1.1 | Onboarding | 4 شاشات + DSGVO consent | ✅ | `ui/onboarding/OnboardingActivity.kt` |
| F1.2 | Guest mode | استخدام بدون تسجيل (`guest@local`) | ✅ | `ui/onboarding/OnboardingActivity.kt:40` |
| F1.3 | Email signup | تسجيل بـ BCrypt | ✅ | `EmailEntryDialogFragment.java` |
| F1.4 | Email login | تسجيل دخول بكلمة سر | ✅ | `LoginDialogFragment.java` |
| F1.5 | Google Sign-In | Firebase Auth + GMS | ✅ | `AuthStartDialogFragment.java` |
| F1.6 | Auth entry dialog | نقطة دخول من أي مكان | ✅ | `AuthStartDialogFragment.java` |
| F1.7 | SHA-256 → BCrypt migration | ترقية تلقائية شفّافة | ✅ | `PasswordUtils.java` |
| F1.8 | Change password | في Settings | ✅ | `SettingsDialogFragment.java` |
| F1.9 | Edit user name | في Settings | ✅ | `SettingsDialogFragment.java:401` |
| F1.10 | Delete account | cascade لكل البيانات | ✅ | `SettingsDialogFragment.java:445` |
| F1.11 | Logout | clear secure prefs | ✅ | `data/repository/AuthRepository.kt:117` |
| **F2 — Plant management** | | | | |
| F2.1 | Add plant from catalog | تفصيلي مع form كامل | ✅ | `AddToMyPlantsDialogFragment.java` |
| F2.2 | Quick Add | بضغطة واحدة من catalog | ✅ | `ui/util/QuickAddHelper.kt` |
| F2.3 | Add custom plant | بدون كاتالوج | ✅ | `AddCustomPlantDialogFragment.java` |
| F2.4 | Edit plant | care fields + personal note | ✅ | `EditPlantDialogFragment.java` |
| F2.5 | Delete plant | cascade reminders + photos | ✅ | `PlantsInRoomActivity.java:287` |
| F2.6 | Move to room | tap → select destination | ✅ | `PlantsInRoomActivity.java:316` |
| F2.7 | Custom nickname | per-plant override | ✅ | `Plant.java:nickname` |
| F2.8 | Personal notes | free-text per plant | ✅ | `Plant.java:personalNote` |
| F2.9 | Favorite toggle | star icon | ✅ | `PlantAdapter.java:102` |
| F2.10 | Free-tier limit (8) | enforced via Paywall | ✅ | `billing/ProStatusManager.kt` |
| **F3 — Rooms** | | | | |
| F3.1 | 5 default rooms | seeded for new users | ✅ | `MyPlantsFragment.java:DEFAULT_ROOMS` |
| F3.2 | Create new room | dialog from spinner | ✅ | `AddRoomDialogFragment.java` |
| F3.3 | Plants per room view | full-screen activity | ✅ | `PlantsInRoomActivity.java` |
| F3.4 | Plant count per room | live count badge | ✅ | `RoomAdapter.java:62` |
| F3.5 | Room icons (16) | bedroom / kitchen / etc | ✅ | `res/drawable/sage_ic_room_*.xml` |
| F3.6 | Race-safe defaults init | @Synchronized helper | ✅ | `RoomCategoryRepository.kt:ensureDefaultsForUserBlocking` |
| **F4 — Plant catalog** | | | | |
| F4.1 | 506+ plants (DE) | seeded from CSV | ✅ | `assets/plants.csv` |
| F4.2 | Search catalog | filter by name | ✅ | `AllPlantsFragment.java` |
| F4.3 | Category filter | indoor/outdoor/herbal/cacti | ✅ | `AllPlantsFragment.java:169` |
| F4.4 | Auto-classification | heuristic on insert | ✅ | `ui/util/PlantCategoryUtil.kt` |
| F4.5 | Local catalog images (17) | drawable resources | ✅ | `res/drawable/aloe_vera.jpg` etc |
| F4.6 | Wikipedia image fetcher | lazy on-demand | ✅ | `WikiImageHelper.java` |
| F4.7 | Care info per plant | lighting/soil/fertilizing/watering | ✅ | `Plant.java` |
| **F5 — Watering reminders** | | | | |
| F5.1 | Auto reminder generation | from wateringInterval | ✅ | `ReminderUtils.java:generateReminders` |
| F5.2 | Manual reminder | custom date + repeat | ✅ | `AddReminderDialogFragment.java` |
| F5.3 | Repeat patterns | every N days | ✅ | `WateringReminder.java:repeat` |
| F5.4 | Edit reminder | including chain of repeats | ✅ | `EditManualReminderDialogFragment.java` |
| F5.5 | Delete reminder | single + chain | ✅ | `EditManualReminderDialogFragment.java:225` |
| F5.6 | Mark done (single) | checkbox | ✅ | `TodayAdapter.java` |
| F5.7 | Bulk mark done | one tap for whole day | ✅ | `DailyWateringAdapter.java:155` |
| F5.8 | "Watered by" tracking | multi-user attribution | ✅ | `WateringReminder.java:wateredBy` |
| F5.9 | Free-text note per watering | long-press in Journal | ✅ | `WateringReminder.java:notes` |
| F5.10 | Reschedule from today | when overdue | ✅ | `ReminderUtils.java:rescheduleFromToday` |
| F5.11 | Today + Overdue list | Today fragment | ✅ | `TodayFragment.java` |
| F5.12 | Treatment plan reminders | linked to disease check | ✅ | `feature/treatment/TreatmentPlanBuilder.kt` |
| **F6 — Calendar (Compose)** | | | | |
| F6.1 | Monthly Compose calendar | full-screen | ✅ | `weekbar/MainScreenCompose.kt` |
| F6.2 | Week bar | current week strip | ✅ | `weekbar/WeekBarCompose.kt` |
| F6.3 | Reminder dots | per-day indicator | ✅ | `weekbar/MainScreenCompose.kt:onDisplayedMonthChanged` |
| F6.4 | Day click → details | reminders + photos | ✅ | `weekbar/RemindersListCompose.kt` |
| F6.5 | Photo grid for day | mini gallery | ✅ | `weekbar/CalendarPhotoGridCompose.kt` |
| F6.6 | Camera FAB | adds dated photo | ✅ | `weekbar/PhotoCaptureCoordinator.kt` |
| F6.7 | Add reminder FAB | manual creation | ✅ | `weekbar/MainScreenCompose.kt` |
| F6.8 | Month picker | year + month grid | ✅ | `weekbar/MonthPickerCompose.kt` |
| F6.9 | Go-to-today shortcut | jump button | ✅ | `weekbar/MainScreenCompose.kt:303` |
| **F7 — Photos** | | | | |
| F7.1 | Camera capture | FileProvider | ✅ | `MainActivity.java:takePhotoForPlant` |
| F7.2 | Gallery import | image picker | ✅ | `MainActivity.java` |
| F7.3 | Cover photo selector | one cover per plant | ✅ | `PlantPhotoDao.java:getCoverPhoto` |
| F7.4 | Archive viewer | scrollable per plant | ✅ | `PlantPhotosViewerDialogFragment.java` |
| F7.5 | Full-screen image dialog | tap to enlarge | ✅ | `FullScreenImageDialogFragment.java` |
| F7.6 | Edit photo date | date picker | ✅ | `PlantPhotosViewerDialogFragment.java:251` |
| F7.7 | Delete photo | local + cloud | ✅ | `weekbar/MainScreenCompose.kt:255` |
| F7.8 | Photo type tagging | regular / inspection / cover | ✅ | `PlantPhoto.java:photoType` |
| F7.9 | Cloud upload | Firebase Storage | ✅ | `FirebaseSyncManager.java` |
| F7.10 | Cover cross-device sync | imageUri mirroring | ✅ | `media/CoverCloudSync.kt` |
| F7.11 | Pending state | PENDING_DOC: prefix | ✅ | `FirebaseSyncManager.java` |
| F7.12 | Path-aware loading | content://, file://, http, raw | ✅ | `weekbar/PlantImageLoader.kt` |
| **F8 — Plant identification (PlantNet)** | | | | |
| F8.1 | Camera + identify | top-level toolbar button | ✅ | `ui/identify/PlantIdentifyActivity.kt` |
| F8.2 | PlantNet API call | `/v2/identify/all` | ✅ | `data/plantnet/PlantNetService.kt` |
| F8.3 | Top-3 results | ranked by confidence | ✅ | `ui/identify/IdentificationResultAdapter.kt` |
| F8.4 | Organ selector | leaf/flower/fruit/bark/habit/auto | ✅ | `ui/identify/PlantIdentifyActivity.kt` |
| F8.5 | 7-day cache | SHA-256 image hash | ✅ | `data/plantnet/CachedIdentification.kt` |
| F8.6 | Plant comparison dialog | side-by-side check | ✅ | `ui/identify/PlantCompareDialogFragment.kt` |
| F8.7 | Add to My Plants | one tap promotion | ✅ | `ui/identify/PlantIdentifyActivity.kt:433` |
| F8.8 | Catalog auto-fill | care info from local match | ✅ | `data/plantnet/PlantCatalogLookup.kt` |
| F8.9 | wateringInterval auto-detection | from family defaults | ✅ | `data/plantnet/PlantCareDefaults.kt` |
| **F9 — Disease diagnosis (Gemini)** | | | | |
| F9.1 | Camera + diagnose | top-level toolbar button | ✅ | `ui/disease/DiseaseDiagnosisActivity.kt` |
| F9.2 | Gemini 2.5 Flash API | cloud vision | ✅ | `data/gemini/GeminiVisionService.kt` |
| F9.3 | Top-3 candidates | with confidence % | ✅ | `ui/disease/DiseaseCandidateAdapter.kt` |
| F9.4 | Reference images | Wikimedia/iNaturalist/PlantVillage | ✅ | `data/disease/DiseaseReferenceImage.kt` |
| F9.5 | Plant species verification | ID + sanity check | ✅ | `ui/disease/DiseaseDiagnosisActivity.kt:381` |
| F9.6 | "Keine passt" re-prompt | exclude + retry | ✅ | `data/repository/DiseaseDiagnosisRepository.kt:82` |
| F9.7 | Diagnosis history | all-time list | ✅ | `ui/disease/DiagnosisHistoryActivity.kt` |
| F9.8 | Diagnosis detail | photo + name + advice | ✅ | `ui/disease/DiagnosisDetailDialog.kt` |
| F9.9 | Save diagnosis to plant | linked record | ✅ | `data/disease/DiseaseDiagnosis.kt` |
| F9.10 | Treatment plan reminders | 4 follow-up steps | ✅ | `feature/treatment/TreatmentPlanBuilder.kt` |
| F9.11 | DSGVO consent dialog | first-use cloud disclosure | ✅ | `ui/disease/DiseaseDiagnosisActivity.kt` |
| **F10 — Plant Journal** | | | | |
| F10.1 | Unified timeline | waterings+photos+diagnoses+memos | ✅ | `data/repository/PlantJournalRepository.kt` |
| F10.2 | Filter chips | All/Watering/Photos/Checks/Notes | ✅ | `ui/journal/PlantJournalDialogFragment.kt:82` |
| F10.3 | Summary header card | counters + days since start | ✅ | `data/journal/JournalModels.kt:JournalSummary` |
| F10.4 | Add free-text memo | FAB → dialog | ✅ | `ui/journal/PlantJournalDialogFragment.kt:showMemoEditor` |
| F10.5 | Edit memo | long-press → editor | ✅ | `ui/viewmodel/PlantJournalViewModel.kt:updateMemo` |
| F10.6 | Delete memo | long-press → confirm | ✅ | `ui/viewmodel/PlantJournalViewModel.kt:deleteMemo` |
| F10.7 | Watering note editor | long-press entry | ✅ | `ui/journal/PlantJournalDialogFragment.kt:showNoteEditor` |
| F10.8 | Watering note delete | neutral button | ✅ | `ui/journal/PlantJournalDialogFragment.kt:199` |
| F10.9 | Open diagnosis details | tap entry | ✅ | `ui/journal/PlantJournalAdapter.kt:DiagnosisVH` |
| **F11 — Weather** | | | | |
| F11.1 | OpenWeatherMap current | with city + temp | ✅ | `data/weather/WeatherService.kt:getCurrentWeather` |
| F11.2 | 5-day forecast | 3h slots, 40 windows | ✅ | `data/weather/WeatherService.kt:getForecast` |
| F11.3 | Auto location | coarse + GPS fallback | ✅ | `WeatherAdjustmentWorker.kt:48` |
| F11.4 | 12h periodic worker | WorkManager | ✅ | `WeatherAdjustmentWorker.kt` |
| F11.5 | Cache (3h current / 12h forecast) | local SharedPrefs | ✅ | `data/repository/WeatherRepository.kt` |
| F11.6 | Weather card on Today | city + temp + tip | ✅ | `weekbar/MainScreenCompose.kt:WeatherTipCard` |
| F11.7 | Forecast 72h aggregation | 7 categories | ✅ | `data/repository/WeatherRepository.kt:getForecastBasedAdvice` |
| F11.8 | Reminder shift (-2..+3 days) | graduated thresholds | ✅ | `WeatherAdjustmentWorker.kt:computeDayShift` |
| F11.9 | Fallback to current snapshot | when forecast fails | ✅ | `WeatherAdjustmentWorker.kt:88` |
| **F12 — Notifications** | | | | |
| F12.1 | Morning notification | 6:00–10:00 window | ✅ | `PlantNotificationHelper.java:53` |
| F12.2 | Evening notification | 18:00–22:00 window | ✅ | `PlantNotificationHelper.java:57` |
| F12.3 | Weather shift summary | per worker run | ✅ | `PlantNotificationHelper.java:71` |
| F12.4 | Welcome-back from vacation | on return day | ✅ | `PlantNotificationHelper.java:112` |
| F12.5 | 40+ Duolingo-style messages | DE + EN locales | ✅ | `res/values*/notifications.xml` |
| F12.6 | Notification channel | required for Android 8+ | ✅ | `PlantNotificationHelper.java:36` |
| F12.7 | Monochrome small icon | ic_notification_plant | ✅ | `res/drawable/ic_notification_plant.xml` |
| **F13 — Vacation Mode** | | | | |
| F13.1 | Set start + end date | in Settings | ✅ | `SettingsDialogFragment.java:202` |
| F13.2 | Reminders pause during | bypass in Worker | ✅ | `PlantReminderWorker.java:90` |
| F13.3 | Today screen banner | visible while active | ✅ | `TodayFragment.java:106` |
| F13.4 | Welcome-back notification | next morning | ✅ | `PlantReminderWorker.java:79` |
| F13.5 | Streak gap protection | doesn't break streak | ✅ | `feature/streak/StreakBridge.java:42` |
| F13.6 | Clear vacation | manual remove | ✅ | `feature/vacation/VacationPrefs.kt:clearVacation` |
| **F14 — Streaks & Challenges** | | | | |
| F14.1 | Watering streak counter | consecutive days | ✅ | `feature/streak/StreakTracker.kt` |
| F14.2 | Best streak tracking | per user | ✅ | `feature/streak/StreakTracker.kt:getBestStreak` |
| F14.3 | Challenge: WATER_STREAK_7 | 7 consecutive days | ✅ | `feature/streak/ChallengeRegistry.kt:43` |
| F14.4 | Challenge: ADD_FIVE_PLANTS | 5 plants in account | ✅ | `feature/streak/ChallengeRegistry.kt:44` |
| F14.5 | Challenge: MONTHLY_PHOTO | one photo per month | ✅ | `feature/streak/ChallengeRegistry.kt:45` |
| F14.6 | Streak bar in Today | days + best | ✅ | `TodayFragment.java:75` |
| F14.7 | Challenges progress display | text rows | ✅ | `TodayFragment.java:renderChallenges` |
| F14.8 | Celebration dialog | 🎉 on completion | ✅ | `TodayFragment.java:showChallengeCompleteDialog` |
| **F15 — Family Share (local)** | | | | |
| F15.1 | Add email to plant | local list | ✅ | `feature/share/FamilyShareManager.kt:addEmail` |
| F15.2 | Remove email | toggle | ✅ | `feature/share/FamilyShareManager.kt:removeEmail` |
| F15.3 | Email validation | Patterns.EMAIL_ADDRESS | ✅ | `feature/share/FamilyShareManager.kt:isValidEmail` |
| F15.4 | wateredBy multi-user attribution | journal display | ✅ | `WateringReminder.java:wateredBy` |
| F15.5 | Cloud invite (email + Cloud Function) | (post-launch) | ⏳ | — |
| **F16 — Memoir PDF** | | | | |
| F16.1 | Multi-page PDF report | A4 portrait | ✅ | `feature/memoir/MemoirPdfBuilder.kt` |
| F16.2 | Cover page | title + plant + room + range | ✅ | `feature/memoir/MemoirPdfBuilder.kt:drawCoverPage` |
| F16.3 | Stats page | 6 counter tiles | ✅ | `feature/memoir/MemoirPdfBuilder.kt:drawStatsPage` |
| F16.4 | Timeline pages | paginated all entries | ✅ | `feature/memoir/MemoirPdfBuilder.kt:drawTimelinePages` |
| F16.5 | Photo grid page | 3×4 thumbnails | ✅ | `feature/memoir/MemoirPdfBuilder.kt:drawPhotoGridPage` |
| F16.6 | Share intent | application/pdf | ✅ | `PlantDetailDialogFragment.java:486` |
| **F17 — Main UI shell** | | | | |
| F17.1 | Today tab | reminders + streak + weather + vacation | ✅ | `TodayFragment.java` |
| F17.2 | Calendar tab | full-screen Compose | ✅ | `weekbar/CalendarScreenBridge.kt` |
| F17.3 | All Plants tab | catalog browser | ✅ | `AllPlantsFragment.java` |
| F17.4 | My Plants tab | rooms + plants grouped | ✅ | `MyPlantsFragment.java` |
| F17.5 | Toolbar: Identify button | camera icon | ✅ | `MainActivity.java:identifyButton` |
| F17.6 | Toolbar: Disease check | health icon | ✅ | `MainActivity.java:diseaseButton` |
| F17.7 | Toolbar: Settings | gear icon | ✅ | `MainActivity.java:settingsButton` |
| F17.8 | Tab navigation | bottom-style tabs | ✅ | `activity_main.xml` |
| **F18 — Settings** | | | | |
| F18.1 | Edit name | live update | ✅ | `SettingsDialogFragment.java:401` |
| F18.2 | Email display | read-only | ✅ | `SettingsDialogFragment.java:371` |
| F18.3 | Auth providers display | Email + Google | ✅ | `SettingsDialogFragment.java:380` |
| F18.4 | Change password | when has local pwd | ✅ | `SettingsDialogFragment.java:groupChangePassword` |
| F18.5 | Set password (Google users) | one-time | ✅ | `SettingsDialogFragment.java:groupSetPassword` |
| F18.6 | Vacation mode controls | start/end pickers | ✅ | `SettingsDialogFragment.java:163` |
| F18.7 | Theme switcher | light / dark | ✅ | `SettingsDialogFragment.java` |
| F18.8 | Language picker | DE / EN (Per-App Language) | ✅ | `SettingsDialogFragment.java` |
| F18.9 | Privacy Policy link | opens browser | ✅ | `SettingsDialogFragment.java` |
| F18.10 | DSGVO Data Export | JSON via FileProvider | ✅ | `DataExportManager.kt` |
| F18.11 | Pro upgrade button | opens Paywall | ✅ | `SettingsDialogFragment.java` |
| F18.12 | Restore purchases | Billing recover | ✅ | `billing/BillingManager.kt` |
| F18.13 | Delete account | full cascade | ✅ | `SettingsDialogFragment.java:445` |
| F18.14 | Logout | clear secure state | ✅ | `SettingsDialogFragment.java` |
| **F19 — Home Screen Widget** | | | | |
| F19.1 | RemoteViews widget | Android 4.x compatible | ✅ | `widget/PlantCareWidget.kt` |
| F19.2 | Today + Overdue display | inline list | ✅ | `widget/PlantCareWidgetDataFactory.kt` |
| F19.3 | Plant thumbnails | inline images | ✅ | `widget/PlantCareWidgetDataFactory.kt:50` |
| F19.4 | Click → app | full launch | ✅ | `widget/PlantCareWidget.kt` |
| **F20 — Pro / Billing / Ads** | | | | |
| F20.1 | 3 SKUs | lifetime / yearly / monthly | ✅ | `billing/BillingManager.kt` |
| F20.2 | Paywall dialog | upsell on limit | ✅ | `billing/PaywallDialogFragment.kt` |
| F20.3 | Free 8-plant limit | enforced before insert | ✅ | `billing/ProStatusManager.kt:FREE_PLANT_LIMIT` |
| F20.4 | Pro status check | local + Play | ✅ | `billing/ProStatusManager.kt` |
| F20.5 | Restore purchases | manual trigger | ✅ | `billing/BillingManager.kt:restorePurchasesAsync` |
| F20.6 | AdMob banner (free only) | hidden when Pro | ✅ | `ads/AdManager.kt` |
| **F21 — Cloud sync (Firebase)** | | | | |
| F21.1 | Firestore plants sync | UID-based path | ✅ | `FirebaseSyncManager.java:syncPlant` |
| F21.2 | Firestore reminders sync | per-user | ✅ | `FirebaseSyncManager.java:syncReminder` |
| F21.3 | Firebase Storage photos | upload + URL store | ✅ | `FirebaseSyncManager.java:uploadPlantPhotoWithPending` |
| F21.4 | Pull on login | full state hydrate | ✅ | `MainActivity.java:importCloudDataForUser` |
| F21.5 | Push on local change | per-row sync | ✅ | `FirebaseSyncManager.java` |
| F21.6 | Cross-device cover sync | imageUri mirroring | ✅ | `media/CoverCloudSync.kt` |
| F21.7 | Firestore deny-by-default rules | UID isolation | ✅ | `firestore.rules` |
| F21.8 | Firestore size limits | per-field caps | ✅ | `firestore.rules` |
| **F22 — Security & privacy** | | | | |
| F22.1 | EncryptedSharedPreferences | email + tokens | ✅ | `SecurePrefsHelper.kt` |
| F22.2 | DSGVO Consent Manager | opt-in defaults off | ✅ | `ConsentManager.kt` |
| F22.3 | Analytics opt-in/out | Firebase Analytics | ✅ | `Analytics.kt` |
| F22.4 | Crashlytics opt-in/out | Firebase Crashlytics | ✅ | `CrashReporter.kt` |
| F22.5 | usesCleartextTraffic=false | Manifest | ✅ | `AndroidManifest.xml:25` |
| F22.6 | DSGVO Data Export (Article 20) | JSON dump | ✅ | `DataExportManager.kt` |
| F22.7 | Privacy Policy hosted | GitHub Pages | ✅ | external (`fadymereyfm-collab.github.io/plantcare-privacy/`) |
| F22.8 | Encrypted email migration | from plain prefs | ✅ | `SecurePrefsHelper.kt:migrateIfNeeded` |
| **F23 — Architecture & infra** | | | | |
| F23.1 | Room DB v13 | 8 entities, 8 migrations | ✅ | `AppDatabase.java` |
| F23.2 | Reactive LiveData DAOs | observe* methods | ✅ | `PlantDao.java`, `ReminderDao.java`, `PlantPhotoDao.java` |
| F23.3 | Repository pattern (10 repos) | one per entity | ✅ | `data/repository/` |
| F23.4 | Java-friendly blocking helpers | for legacy fragments | ✅ | `data/repository/*Repository.kt` |
| F23.5 | WorkManager periodic | reminder + weather | ✅ | `App.java:53` |
| F23.6 | Core library desugaring | java.time on API 24+ | ✅ | `app/build.gradle` |
| F23.7 | ProGuard + R8 minify | release builds | ✅ | `app/build.gradle:release` |
| F23.8 | Schema export | for migration tests | ✅ | `app/schemas/` |
| F23.9 | Migration tests (5→13) | androidTest | ✅ | `androidTest/.../MigrationTest.kt` |
| F23.10 | Unit tests (50) | Auth + Plant + Reminder + SecurePrefs + WateringReminder | ✅ | `src/test/` |
| F23.11 | Per-App Language API | DE / EN switcher | ✅ | `SettingsDialogFragment.java` |
| F23.12 | LeakCanary (debug) | memory-leak detection | ✅ | `app/build.gradle:debugImplementation` |
| F23.13 | Baseline profiles | startup speedup | ✅ | `app/src/main/baseline-prof.txt` |

---

## Code map — quick navigation

| Folder | Role |
|--------|------|
| `app/src/main/java/com/example/plantcare/` (root) | Legacy Java fragments + Activities + DAOs + entities |
| `data/repository/` | All Repositories (Plant, Reminder, PlantPhoto, RoomCategory, Auth, Weather, Journal, Disease*, Identification) |
| `data/journal/` | Plant Journal entity + DAO + sealed JournalEntry |
| `data/disease/` | Disease diagnosis DAOs + reference image cache |
| `data/plantnet/` | PlantNet API service + catalog lookup + care defaults |
| `data/weather/` | OpenWeather Retrofit + models (current + forecast) |
| `data/gemini/` | Gemini 2.5 Flash vision service |
| `data/db/` | Room migrations 5→13 |
| `feature/memoir/` | MemoirPdfBuilder (PDF report) |
| `feature/share/` | FamilyShareManager (local) |
| `feature/streak/` | StreakTracker + ChallengeRegistry + StreakBridge |
| `feature/treatment/` | TreatmentPlanBuilder (4-step disease follow-up) |
| `feature/vacation/` | VacationPrefs |
| `ui/onboarding/` | OnboardingActivity + ConsentDialog |
| `ui/identify/` | PlantNet UI |
| `ui/disease/` | Disease diagnosis UI + history |
| `ui/journal/` | Plant Journal UI |
| `ui/util/` | QuickAddHelper, PlantCategoryUtil, FragmentBg |
| `ui/viewmodel/` | All ViewModels |
| `weekbar/` | Compose calendar (MainScreenCompose, WeekBarCompose, MonthPickerCompose, CalendarPhotoGridCompose, RemindersListCompose) |
| `widget/` | Home-screen widget |
| `billing/` | Pro purchase + paywall |
| `ads/` | AdMob banner |
| `media/` | PhotoStorage + CoverCloudSync |

---

## Status summary

| Status | Count | Notes |
|--------|------:|-------|
| ✅ Complete | ~140 | Shipping-ready |
| ⏳ Deferred to v1.1 | 1 | F15.5 (Family Share cloud invite) |
| 🔴 Broken | 0 | — |
| ⚠️ Partial | 0 | — |

**Manual gates outside this inventory** (not features, but blockers before publish):
- M3 — Google Play Console + Store Listing + 3 SKUs
- M6 — Manual upgrade-scenario test (v0.1 → v1.0)
- M7 — Pro purchase License Tester run
- M8 — 10-scenario QA matrix on multiple devices
- M9 — TalkBack accessibility pass
- M10 — Final security checklist

---

## Cross-reference index

- **Schema migration history:** `data/db/DatabaseMigrations.java` — `MIGRATION_5_6` … `MIGRATION_12_13`
- **String resources:** DE in `res/values/strings.xml`, EN in `res/values-en/strings.xml`
- **Notification copy:** `res/values*/notifications.xml`
- **Disease label set:** `res/values/strings_disease.xml`
- **Theme:** `res/values/themes.xml` + `values-night/themes.xml`
- **PROGRESS log:** `PROGRESS.md`
- **Project rules:** `CLAUDE.md`
- **Functional report (origin):** `PlantCare_Functional_Report.md`
- **Pre-release audit:** `PlantCare_Pre_Release_Audit.md`

---

*End of inventory. Update this file whenever a feature is added, removed, or changes status.*
