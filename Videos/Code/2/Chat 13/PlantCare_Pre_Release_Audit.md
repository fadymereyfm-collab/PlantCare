# PlantCare — تدقيق ما قبل النشر وخطة عمل تنفيذية لـ Cloud Coding
**التاريخ:** 28 أبريل 2026
**المنفّذ:** Fady — التطبيق الفعلي في `C:\Users\fadym\Videos\Code\2\Chat 13`
**الهدف:** قائمة عمل مرتّبة منطقياً تنقل التطبيق من الحالة الراهنة إلى نشر تجاري آمن على Google Play (السوق الألماني).
**أسلوب الفحص:** قراءة مباشرة لـ 144 ملف مصدري + Manifest + Gradle + Resources. **لم نفترض أن أي ميزة تعمل — كل بند يستند إلى مرجع كود فعلي**.

---

## 0 — ملخص تنفيذي (اقرأ أولاً)

التطبيق متقدّم على الورق (PROGRESS.md يصرّح بإنجاز Layer 0–6) لكن **التنفيذ الفعلي ينحرف عن خطة العمل في طبقتَين كاملتَين** ويترك ميزتَي تجارية كأنها كود ميّت:

| رمز | الفئة | الحالة الموثّقة من الكود |
|-----|------|----------------------------|
| 🔴 | **Billing/Paywall** | الكود موجود لكنه **لا يُستدعى من أي مكان**. `BillingManager.connect()` لا يُنادى في أي Activity. `PaywallDialogFragment` لا يُعرض في أي مسار مستخدم. حد `FREE_PLANT_LIMIT = 8` ليس مُطبَّقاً. |
| 🔴 | **DSGVO Migration Bug** | `SecurePrefsHelper.migrateIfNeeded()` يحذف `current_user_email` و `is_guest` من ملف `prefs` العادي، لكن **23 موضعاً** في الكود لا تزال تقرأ من ملف `prefs` بعد الترقية ⇒ المستخدمون سيُسجَّل خروجهم تلقائياً عند أول تحديث وتتوقّف الإشعارات. |
| 🔴 | **Disease Diagnosis** | `plant_disease_model.tflite` **غير موجود** في `app/src/main/assets/`. الميزة تتعطّل لحظة الضغط على الزر. |
| 🔴 | **Weather** | `OPENWEATHERMAP_API_KEY = "YOUR_API_KEY_HERE"` نص ثابت في `WeatherRepository.kt:155`. كل طلب طقس يفشل بـ 401. |
| 🔴 | **Layer 4 (i18n)** | لم يُنفَّذ. `resConfigs "de"` لا يزال في `app/build.gradle:26`، ولا يوجد `values-en/`، و`locale_config.xml` يحوي `de` فقط. لا يمكن استخدام التطبيق بأي لغة أخرى. |
| 🔴 | **Layer 3 (Architecture)** | لم يُنفَّذ. **102 استدعاء** مباشر لـ `AppDatabase.getInstance` / `DatabaseClient.` خارج `data/repository/` (مقابل ادعاءات سابقة بـ 10 أو 54). كل ViewModels الجديدة (5 من 6) تستدعي قاعدة البيانات مباشرة. لا Hilt. 28 `new Thread(...)` في طبقة الواجهة. **50 instance من `catch (... ignored)`**. |
| 🔴 | **Layer 7 (QA)** | لم يبدأ. لا مصفوفة اختبار يدوي، لا اختبار أداء، لا checklist أمان نهائي. |
| 🟠 | **AdMob IDs** | لا يزال يستخدم Test IDs من Google. |
| 🟠 | **Tests** | 6 ملفات اختبار فقط (~500 سطر). Layer 2 طلب 25+ اختبار. |
| 🟠 | **Reactive UI** | `Repositories` تُرجع `LiveData` عبر `liveData{}` builder الذي **يبثّ مرّة واحدة** ولا يستجيب لتغيّرات قاعدة البيانات. UI لا يتحدّث تلقائياً. |
| 🟠 | **Notification strings** | 250 سطر في `PlantNotificationHelper.java` بنصوص ألمانية ثابتة (Layer 3 Task 3.4 لم يُنفَّذ). |
| 🟡 | **Repository inconsistency** | بعض مسارات تسجيل الدخول (`LoginDialogFragment`, `EmailEntryDialogFragment`, `MainActivity`, `OnboardingActivity`) تكتب البريد إلى `prefs` العادي بدلاً من `SecurePrefsHelper` ⇒ تناقض في مصدر الحقيقة. |

**الترجمة العملية:** التطبيق **غير قابل للنشر التجاري** بحالته الحالية. Layer 6 الذي اعتُبِر مكتملاً يحتوي وظيفتَي تجاريّتَين معطّلتَين، وLayer 3+4+7 لم يُنفَّذا أصلاً.

---

## 1 — الوظائف المطلوبة من التطبيق وحالتها الفعلية

استُخرجت من المرجع المعماري للكود + الذاكرة (`PlantCare Android App`) + التعليقات التوثيقية في الملفات.

| # | الوظيفة | المرجع في الكود | التصنيف | الملاحظة الموثّقة |
|---|---------|-------------------|---------|---------------------|
| 1 | **Onboarding بأربع شاشات + DSGVO Consent** | `ui/onboarding/OnboardingActivity.kt`, `ConsentDialogFragment.kt`, `dialog_consent.xml` | ✅ مكتملة | تعمل كLauncher وتعيد التوجيه بعد الإكمال. ConsentManager يطفئ Analytics/Crashlytics افتراضياً ⇒ متوافق DSGVO. |
| 2 | **حساب محلي (BCrypt) + Firebase Auth (Google + Email/Password)** | `data/repository/AuthRepository.kt`, `AuthStartDialogFragment.java`, `LoginDialogFragment.java`, `PasswordUtils.java` | ⚠️ شبه مكتملة | منطق محلي + Firebase موجود لكن مسارات الكتابة إلى prefs **غير متّسقة** (بعضها يكتب إلى المشفّر، بعضها إلى العادي). |
| 3 | **Guest Mode (`guest@local`)** | `OnboardingActivity:40, 121`, `MainActivity:85-93` | ✅ مكتملة | يعمل، الإيميل يُحفظ مباشرة في prefs. |
| 4 | **إدارة النباتات (إضافة/تعديل/حذف/أرشفة + غرف)** | `Plant.java`, `PlantDao.java`, `data/repository/PlantRepository.kt`, `MyPlantsFragment.java`, `RoomCategoryRepository`, `PlantsInRoomActivity.java` | ✅ مكتملة | DAO شامل، لكن تُستخدم بشكل غير منهجي (أحياناً عبر Repository، أحياناً مباشرة من Fragment). |
| 5 | **كاتالوج النباتات (CSV)** | `app/src/main/assets/plants.csv` (85 KB), `AllPlantsFragment.java`, `data/plantnet/PlantCatalogLookup.kt` | ✅ مكتملة | الكاتالوج بالألمانية فقط — لا أعمدة EN. |
| 6 | **التذكير اليومي (مرّتان: صباح/مساء) + WorkManager دورياً كل 6 ساعات** | `App.java:53-67`, `PlantReminderWorker.java`, `PlantNotificationHelper.java`, `data/repository/ReminderRepository.kt` | ⚠️ شبه مكتملة | يعمل عند تسجيل دخول جديد، **لكنه ينكسر بعد ترقية تطبيق** (انظر مشكلة Migration). كل النصوص ألمانية ثابتة. |
| 7 | **إشعارات Duolingo-style فكاهية + Vacation Mode** | `PlantNotificationHelper.java` (40+ نص ثابت)، `feature/vacation/VacationPrefs.kt` | ✅ مكتملة وظيفياً، 🔴 شكلية للترجمة | كل الـ 40 نص داخل ملف Java غير قابل للترجمة. |
| 8 | **التعرّف على النبات (PlantNet API + Top-3 + Cache 7 أيام)** | `data/repository/PlantIdentificationRepository.kt`, `data/plantnet/PlantNetService.kt`, `ui/identify/PlantIdentifyActivity.kt`, `CachedIdentification.kt` | ✅ مكتملة | API key عبر BuildConfig، Top-3 يعمل، caching بـ SHA-256 مفعَّل. |
| 9 | **تشخيص الأمراض (TFLite محلي + 38 صنف)** | `data/disease/PlantDiseaseClassifier.kt`, `DiseaseDiagnosisRepository.kt`, `ui/disease/*` | 🔴 مكسورة | **ملف `plant_disease_model.tflite` مفقود من assets/**. عند الضغط على الزر يرمي Exception. |
| 10 | **كاميرا + معرض + FileProvider** | `MainActivity.java`, `CalendarPhotoCaptureHandler.java`, `ImageUtils.java`, `media/PhotoStorage.kt` | ✅ مكتملة | تتعامل مع API 33+ permissions. |
| 11 | **Cloud Sync Firestore (UID-based)** | `FirebaseSyncManager.java` (UID-based) | ✅ مكتملة | المسارات `users/{uid}/...` صحيحة، حقل `userEmail` في Room يبقى لأغراض الفهرسة المحلية فقط (ليس Firestore key). الادعاء السابق "email كمعرّف Firestore" **خاطئ** — تم إصلاحه فعلاً. |
| 12 | **firestore.rules** | `firestore.rules` | ✅ مكتملة | UID-only، deny-by-default، حدود حجم على الحقول. |
| 13 | **EncryptedSharedPreferences للبيانات الحسّاسة** | `SecurePrefsHelper.kt` | ⚠️ شبه مكتملة | الأداة موجودة، Migration موجودة، **لكنها تُستخدم في AuthRepository فقط**. باقي الكود يكتب/يقرأ من prefs العادي ⇒ Migration تُسبّب فقدان بيانات في الترقيات. |
| 14 | **Room v10 + 5 Migrations (5→10)** | `AppDatabase.java` (v10), `DatabaseMigrations.java` (5→6, 6→7, 7→8, 8→9, 9→10), `app/schemas/com.example.plantcare.AppDatabase/` | ✅ مكتملة | exportSchema=true، MigrationTest يغطّي 5→6، 6→7. |
| 15 | **Widget بشاشة الرئيسية** | `widget/PlantCareWidget.kt`, `PlantCareWidgetService.kt`, `PlantCareWidgetDataFactory.kt` | ✅ مكتملة | RemoteViewsService، يعرض مهام اليوم. |
| 16 | **Weather Worker (تعديل التذكير حسب الطقس)** | `WeatherAdjustmentWorker.kt`, `data/repository/WeatherRepository.kt`, `data/weather/WeatherService.kt` | 🔴 مكسورة | API key نص ثابت `"YOUR_API_KEY_HERE"` ⇒ كل استدعاء يفشل 401. |
| 17 | **Streak + Challenges + Family Sharing + Treatment Plan + Memoir** | `feature/streak/`, `feature/share/`, `feature/treatment/`, `feature/memoir/` | ⚠️ شبه مكتملة | الكود موجود ومدمج في TodayFragment، لكنه **ينكسر بعد Migration** لاعتماده على البريد من prefs العادي. |
| 18 | **PlantCare Pro (Billing 3 SKUs + AdMob Banner)** | `billing/BillingManager.kt`, `billing/PaywallDialogFragment.kt`, `billing/ProStatusManager.kt`, `ads/AdManager.kt` | 🔴 شكلية فقط | **`BillingManager.getInstance()` لا يُستدعى من أي مكان**. **`PaywallDialogFragment` لا يُعرض من أي مكان**. حد 8 نباتات لا يُطبَّق. AdMob يستخدم Test IDs. AdView يعمل (Banner ظاهر). |
| 19 | **DSGVO Data Export (المادة 20)** | `DataExportManager.kt` | ✅ مكتملة | تُصدِّر بيانات المستخدم. |
| 20 | **Crashlytics + Analytics (مع Consent)** | `Analytics.kt`, `CrashReporter.kt`, `ConsentManager.kt`, `App.java:24` | ✅ مكتملة معمارياً | معطّلة افتراضياً، تُفعَّل بعد Consent. **لكن** 50 `catch (X ignored)` ابتلعت أخطاء كثيرة ولا تصل لـ Crashlytics. |
| 21 | **Onboarding language picker** | — | ❌ ناقصة | Per-App Language API غير مُستخدم. |

---

## 2 — الفجوات المعمارية الموثّقة بالكود

### 2.1 ❌ Layer 3.1 — DAO مباشر في طبقة UI/ViewModel
**الواقع المُقاس:** `grep -rn "AppDatabase.getInstance\|DatabaseClient\." app/src/main/java | grep -v "data/repository/"` ⇒ **102 نتيجة** عبر **47 ملف**.

**أكثر الملفات إثارة للقلق (يجب أن تعتمد على Repository فقط):**
```
ui/viewmodel/AllPlantsViewModel.kt:20      private val db = AppDatabase.getInstance(application)
ui/viewmodel/MyPlantsViewModel.kt:21       private val db = AppDatabase.getInstance(application)
ui/viewmodel/OnboardingViewModel.kt:21     private val db = AppDatabase.getInstance(application)
ui/viewmodel/TodayViewModel.kt:31          private val db = AppDatabase.getInstance(application)
ui/viewmodel/PlantIdentifyViewModel.kt:25  private val plantDao = DatabaseClient.getInstance(application).plantDao()
ui/disease/DiseaseDiagnosisActivity.kt     import com.example.plantcare.DatabaseClient
weekbar/MainScreenCompose.kt
weekbar/PhotoCaptureCoordinator.kt
weekbar/PlantImageLoader.kt
weekbar/RemindersListCompose.kt
widget/PlantCareWidgetDataFactory.kt
+ 35 Java Fragment/Activity/Adapter files
```

### 2.2 ❌ Layer 3.2 — لا Hilt (DI يدوي)
`grep -rln "@HiltAndroidApp\|@Inject\|@AndroidEntryPoint\|@HiltViewModel"` ⇒ **0 نتائج**. كل الـRepositories تستخدم `private constructor + getInstance(context)` (Singleton يدوي). كل الـViewModels بدون Factory ⇒ لا يمكن حقن Repositories في الاختبارات.

### 2.3 ❌ Layer 3.3 — Threading موزّع
`grep -rn "new Thread(" app/src/main/java` ⇒ **28 نتيجة** في 7 ملفات (DailyWateringAdapter, MainActivity, PlantAdapter, PlantDetailDialogFragment, PlantsInRoomActivity, TodayAdapter, ui/util/FragmentBg.kt). `AsyncTask` ⇒ 0 (جيّد).

### 2.4 ❌ Layer 3.4 — نصوص الإشعارات ثابتة
`PlantNotificationHelper.java`: 250 سطر، فيه ~40 سلسلة ألمانية ثابتة (`"Guten Morgen, Pflanzenflüsterer! 🌱"` إلخ) داخل arrays في Java مباشرة.

### 2.5 ⚠️ Reactive UI معطَّل
DAOs تُرجع `List<X>` (وليس `LiveData<List<X>>` ولا `Flow<List<X>>`):
```java
// ReminderDao.java:30-34
@Query("SELECT * FROM WateringReminder WHERE userEmail = :userEmail")
List<WateringReminder> getAllRemindersForUser(@Nullable String userEmail);
```
بينما Repository يلفّها بـ `liveData { emit(dao.getXxx()) }` الذي يبثّ **مرّة واحدة** فقط ⇒ UI لا يتحدّث تلقائياً عند إضافة/حذف نبات. التحديث يعتمد حالياً على `DataChangeNotifier` (مُلاحظات يدوية) — مهترئ ومُعرَّض للتسرّب (انظر تعليق في `TodayFragment.java:42`).

### 2.6 ⚠️ Exceptions مبتلعة
`grep -rn "catch (.*ignored" app/src/main/java | wc -l` ⇒ **50 نتيجة**. Layer 2 Task 2.4 طلب صفر.

---

## 3 — مشاكل الأمان والتشغيل الموثّقة

### 3.1 🔴 Bug ترقية SecurePrefs (data integrity)
**الموضع:** `SecurePrefsHelper.kt:42-58`

`migrateIfNeeded()` يقرأ `current_user_email` و `is_guest` من ملف `prefs` العادي، يكتبهما في الملف المشفّر، ثم **يحذف الأصلي**. لكنّ هذه المواضع **لا تزال تقرأ من `prefs` العادي**:

| الملف:السطر | السلوك | الأثر |
|--------------|--------|-------|
| `App.java:39` | يقرأ theme_mode (غير حسّاس) | ✅ سليم |
| `MainActivity.java:81-82` | `prefs.getString("current_user_email", null)` | 🔴 تسجيل خروج صامت |
| `MainActivity.java:386` | `getCurrentUserEmail()` يُرجع null | 🔴 يُكسر كل query معتمد على email |
| `PlantReminderWorker.java:71-72` | الإشعارات تتوقّف نهائياً | 🔴 critical |
| `MyPlantsFragment.java:55-56` | لا تُعرض النباتات | 🔴 |
| `AddPlantDialogFragment.java:236-237` | إضافة نبات يفشل | 🔴 |
| `AddReminderDialogFragment.java:53-54` | تعذّر إضافة تذكير | 🔴 |
| `AddToMyPlantsDialogFragment.java:85-86` | تعذّر النقل من الكاتالوج | 🔴 |
| `PlantsInRoomActivity.java:76-77, 213-214` | الغرف تظهر فارغة | 🔴 |
| `PlantAdapter.java:89-90` | adapter يفقد filter | 🟠 |
| `PlantDetailDialogFragment.java:449-450, 772-773` | فقد سياق الصور | 🟠 |
| `StreakBridge.java:79-80` | لا streak | 🟠 |
| `EmailEntryDialogFragment.java:60-61` | يكتب إلى `prefs` العادي | 🔴 (إنشاء طريق ثانٍ للحقيقة) |
| `LoginDialogFragment.java:249-251` | يكتب إلى `prefs` العادي | 🔴 |
| `AuthStartDialogFragment.java:265-267` | يكتب إلى `prefs` العادي | 🔴 |

**خلاصة:** تركيبة "هجرة + قراءة من المصدر القديم + كتابة من مسارات لم تُهاجَر" = طريقتان متناقضتان لتخزين البريد. مستخدم يقوم بترقية التطبيق بعد التحديث الذي يحوي SecurePrefsHelper سيُسجَّل خروجه فوراً.

### 3.2 🔴 Weather API Key نص ثابت
```kotlin
// data/repository/WeatherRepository.kt:155
private const val OPENWEATHERMAP_API_KEY = "YOUR_API_KEY_HERE"
```
نفس النمط في `app/build.gradle` المتّبَع لـ `PLANTNET_API_KEY` (BuildConfigField) **غير مُطبَّق** على الطقس.

### 3.3 🔴 ProGuard + Billing بدون تحقّق سيرفر
- `ProStatusManager` يخزّن `is_pro` في SharedPreferences عادي.
- لا Cloud Function ولا Google Play Developer API verification.
- مستخدم Root يستطيع فلب الفلاج وفتح Pro مجاناً.
- Layer 6 Task 6.3 طلب server-side verification — لم يُنفَّذ.

### 3.4 🔴 TFLite Model مفقود
```
app/src/main/assets/
├── README_DISEASE_MODEL.md  ← موجود
├── plant_disease_labels.txt ← موجود (985 bytes)
├── plants.csv               ← موجود (85 KB)
└── plant_disease_model.tflite ← مفقود
```
`PlantDiseaseClassifier.create()` سيرمي IOException عند أول استخدام.

### 3.5 🟠 networkSecurityConfig غير صريح
لا `android:usesCleartextTraffic="false"` في Manifest ولا `networkSecurityConfig` مُعرَّف. السلوك يعتمد على default API 28+، لكنّ Layer 7 Task 7.4 طلب التحقّق الصريح.

### 3.6 🟠 AdMob Test IDs
`strings.xml:281-282` لا يزال:
```xml
<string name="admob_app_id">ca-app-pub-3940256099942544~3347511713</string>
<string name="admob_banner_unit_id">ca-app-pub-3940256099942544/6300978111</string>
```

---

## 4 — Layer 4 (i18n) — لم يُنفَّذ

| البند | الحالة | المرجع |
|-------|--------|--------|
| إزالة `resConfigs "de"` | ❌ موجود | `app/build.gradle:26` |
| `values-en/strings.xml` | ❌ غير موجود | `ls app/src/main/res/values-en` ⇒ NO SUCH DIRECTORY |
| Translations Editor | ❌ — | لا ملف ترجمة |
| Per-App Language API | ❌ — | لا استدعاء `setApplicationLocales` |
| `locale_config.xml` | ⚠️ يحوي `de` فقط | `app/src/main/res/xml/locale_config.xml` |
| `plants.csv` بالإنجليزية | ❌ — | كاتالوج ألماني فقط |

**النتيجة:** التطبيق غير قابل للاستخدام بأي لغة غير الألمانية، حتى لو كان نظام المستخدم بلغة أخرى.

---

## 5 — Layer 6 المُعلن مكتملاً يحتوي وظائف ميتة

### 5.1 🔴 Billing لا يتّصل أصلاً
```bash
$ grep -rn "BillingManager.getInstance" app/src/main/java | grep -v "billing/"
(no results)
```
**الدلالة:** لا Activity / Fragment / Worker يستدعي `BillingManager.getInstance(ctx).connect()`. عند تشغيل التطبيق، BillingClient لا يبدأ اتصال مع Google Play. حتى لو فتح المستخدم Paywall سيُعرض بقائمة منتجات فارغة.

### 5.2 🔴 Paywall لا يُفتح من أي مكان
```bash
$ grep -rn "PaywallDialogFragment\b" app/src/main/java | grep -v "billing/PaywallDialogFragment.kt"
(no results)
```
لا `MyPlantsFragment` ولا `AddPlantDialogFragment` يفحصان `ProStatusManager.isPro()` قبل إضافة النبات التاسع. لا `SettingsDialogFragment` يحوي زر "Upgrade to Pro".

### 5.3 🔴 FREE_PLANT_LIMIT = 8 ليس مُلزَماً
الثابت `FREE_PLANT_LIMIT = 8` يُعرَّف في `ProStatusManager.kt:11` لكن لا يُستخدم في أي شرط `if`. مستخدم مجاني يستطيع إضافة 1000 نبات.

### 5.4 🟢 AdManager سليم — Banner ظاهر
`AdManager.start()` في `MainActivity:181` يجعل AdView VISIBLE لغير-Pro. لكن Test IDs تجعل Google يحظر النشر.

---

## 6 — Layer 7 (QA) — لم يبدأ

- [ ] لا مصفوفة 15-سيناريو × 4-أجهزة.
- [ ] لا تقارير Memory baseline / Startup time.
- [ ] لا فحص Accessibility Scanner.
- [ ] لا Penetration / Security checklist.
- [ ] LeakCanary غير مدمج.

ملاحظة: contentDescription أُضيف (Task 5.2)، لكن لم يُختبر يدوياً مع TalkBack.

---

## 7 — مصفوفة الإحصاءات (Snapshot 2026-04-28)

| المقياس | القيمة المُقاسة | الهدف | Δ |
|---------|----------------|-------|---|
| `AppDatabase.getInstance` خارج repository | 102 | 0 | +102 |
| `getEmail()` (Firestore) | 4 (display/Room field فقط — مقبول) | — | — |
| `new Thread(...)` | 28 | 0 (UI/VM) | +28 |
| `catch (X ignored)` | 50 | 0 | +50 |
| Hilt usage | 0 | كامل | — |
| اختبارات Unit | 5 ملفات / ~80 اختبار صغير | 25+ | — |
| اختبارات Migration | 1 ملف (5→6, 6→7) | يغطّي 5→10 | جزئي |
| ملفات بدون terjeme EN | 100% | 0 | — |
| Hardcoded API keys | 1 (Weather) | 0 | +1 |
| AdMob Test IDs | نعم | لا | — |
| TFLite asset | مفقود | موجود | — |
| versionCode / versionName | 2 / 1.0.0 | — | جاهز للنشر |
| AAB حجم | 20 MB (prodRelease) | < 30 MB | ✅ |
| ProGuard signing | مفعَّل، keystore حتى 2053 | — | ✅ |

---

## 8 — خطة عمل تنفيذية مُعاد ترتيبها لـ Cloud Coding

تنبيه: **ترتيب الخطة الأصلية لم يُحترَم — سنُعيد ترتيب المهام المتبقية بحيث لا تُعاد كتابة كود لاحقاً**.

### المرحلة A — إصلاحات حجب نشر فورية (3-4 أيام، blockers)

#### A1 🔴 إصلاح SecurePrefs Migration (data integrity)
**المشكلة:** قراءة/كتابة من ملف prefs العادي بعد ترحيل البريد إلى المشفّر.

**خياران مقبولان — اختر A1.a (الأسرع) أو A1.b (الأنظف):**

**A1.a — توحيد كل الكود على SecurePrefsHelper (موصى به):**
1. أنشئ `EmailContext.kt` (utility object) في الجذر:
   ```kotlin
   object EmailContext {
       fun current(context: Context): String? =
           SecurePrefsHelper.get(context).getString(SecurePrefsHelper.KEY_USER_EMAIL, null)
       fun setCurrent(context: Context, email: String?, isGuest: Boolean = false) {
           SecurePrefsHelper.get(context).edit()
               .putString(SecurePrefsHelper.KEY_USER_EMAIL, email)
               .putBoolean(SecurePrefsHelper.KEY_IS_GUEST, isGuest)
               .apply()
       }
       fun isGuest(context: Context): Boolean =
           SecurePrefsHelper.get(context).getBoolean(SecurePrefsHelper.KEY_IS_GUEST, false)
   }
   ```
2. استبدل **كل** `prefs.getString("current_user_email", ...)` بـ `EmailContext.current(ctx)` في 23 الموضع المذكورة في القسم 3.1.
3. استبدل **كل** كتابة `prefs.edit().putString("current_user_email", x).apply()` بـ `EmailContext.setCurrent(ctx, x)` (وهي 4 مواضع: `LoginDialogFragment`, `EmailEntryDialogFragment`, `AuthStartDialogFragment`, `MainActivity:93`).
4. تحقّق: `grep -rn '"current_user_email"' app/src/main/java` يجب أن يُرجع نتيجة في `SecurePrefsHelper.kt` و `EmailContext.kt` فقط.

**A1.b — العودة الجزئية (Fallback): جعل migrateIfNeeded يحتفظ بنسخة في prefs العادي لكن يبقى الجوهري في المشفّر.**
بسيط لكن يفقد فائدة التشفير.

**معايير القبول:**
- [ ] اختبار يدوي: تثبيت v0.1 (قبل migration) → إضافة نبات → ترقية إلى الحالي → المستخدم لا يزال مسجَّلاً، النبات موجود، تذكيره يعمل.
- [ ] `PlantReminderWorker` يجد البريد بعد الترقية.
- [ ] لا اختفاء بيانات.

---

#### A2 🔴 ربط Billing وPaywall فعلياً
**الملفات:**
- `App.java` — أضف `BillingManager.getInstance(this).connect()` في `onCreate()` (داخل coroutine — استخدم `GlobalScope.launch` المُطفَى أو `applicationScope`).
- `MainActivity.java` أو `MyPlantsFragment.java` — قبل إضافة النبات التاسع:
   ```java
   if (!ProStatusManager.isPro(getContext())
           && currentPlantCount >= ProStatusManager.FREE_PLANT_LIMIT) {
       new PaywallDialogFragment().show(getSupportFragmentManager(),
               PaywallDialogFragment.TAG);
       return;
   }
   ```
- `SettingsDialogFragment.java` — أضف زر "PlantCare Pro" يفتح `PaywallDialogFragment.newInstance("Du brauchst mehr Platz für deine grüne Familie!")`.
- `MainActivity.java` — في `onResume`، نادِ `BillingManager.getInstance(this).restorePurchasesAsync()` (تحديث Pro بعد إعادة فتح التطبيق).

**Server-side verification (مفتوح للنقاش — يُمكن تأجيله إلى المرحلة B):**
- يُضاف Cloud Function لاحقاً يستخدم Google Play Developer API.
- مؤقّتاً، أضف validation محلي بسيط: تحقّق `purchaseToken != null && purchaseState == PURCHASED && isAcknowledged` (بالفعل موجود).

**معايير القبول:**
- [ ] فتح Settings ⇒ زر "PlantCare Pro" يظهر إذا `!isPro`.
- [ ] محاولة إضافة نبات #9 لمستخدم مجاني تفتح Paywall.
- [ ] شراء على حساب Test يحدّث `ProStatusManager.isPro` فوراً، Banner يختفي.
- [ ] Restore Purchases يعمل من Paywall.

---

#### A3 🔴 إصلاح Weather API
**الخطوات:**
1. أضف في `local.properties`:
   ```
   OPENWEATHER_API_KEY=<actual_key>
   ```
2. في `app/build.gradle:32`:
   ```gradle
   def owmKey = localProps.getProperty("OPENWEATHER_API_KEY")
                ?: System.getenv("OPENWEATHER_API_KEY") ?: ""
   buildConfigField "String", "OPENWEATHER_API_KEY", "\"${owmKey}\""
   ```
3. في `data/repository/WeatherRepository.kt:155`:
   ```kotlin
   private val OPENWEATHERMAP_API_KEY = BuildConfig.OPENWEATHER_API_KEY
   ```
4. أضف SECRET في GitHub Actions (`OPENWEATHER_API_KEY`) لـ ci.yml.

**معايير القبول:**
- [ ] استدعاء WeatherAdjustmentWorker يُرجع response 200 مع بيانات.
- [ ] `grep -rn "YOUR_API_KEY_HERE" app/src/main/java` ⇒ 0.

---

#### A4 🔴 إعادة تنزيل/إضافة TFLite Model
1. اتّبع `app/src/main/assets/README_DISEASE_MODEL.md` (موجود).
2. ضع `plant_disease_model.tflite` في `app/src/main/assets/`.
3. ابنِ بـ `./gradlew assembleProdRelease` وتأكّد أن AAB يحوي الملف (التحقّق: `unzip -l app-prod-release.aab | grep tflite`).
4. اختبر سيناريو: التقاط صورة ورقة معروفة (مثلاً Tomato Late Blight) → التحقّق من ظهور Top-3 نتائج.

**خيار بديل (إن أردت تأجيل الميزة للنشر الأول):**
- أخفِ زر "Krankheit erkennen" في `activity_main.xml` (`visibility="gone"` على `R.id.btn_disease_diagnose`).
- احذف Activity من Manifest.
- أعِد الميزة في إصدار 1.1.

**معايير القبول:**
- [ ] `app/src/main/assets/plant_disease_model.tflite` موجود (>= 5 MB).
- [ ] لا crash عند فتح DiseaseDiagnosisActivity.
- [ ] أو الزر مخفي بالكامل.

---

#### A5 🟠 استبدال AdMob Test IDs
- أنشئ AdMob Console app + Banner Ad Unit + املأ معرّفات حقيقية في `strings.xml:281-282`.
- ضع التحويل وراء BuildConfig إن أردت (test في dev، prod في prod).

**معايير القبول:**
- [ ] لا `ca-app-pub-3940256099942544` في `strings.xml`.
- [ ] AdMob Console يُظهر Impressions في dev test.

---

### المرحلة B — Layer 4 (i18n) — يجب قبل النشر العالمي، يمكن تأجيله للسوق الألماني فقط (3-4 أيام)

#### B1 🟠 إزالة `resConfigs "de"` (إن قرّرت دعم EN)
- في `app/build.gradle:26` احذف السطر أو غيِّره إلى `resConfigs "de", "en"`.

#### B2 🟠 إنشاء `values-en/strings.xml`
1. افتح Translations Editor في Android Studio.
2. ترجم 203 مفتاح في `values/strings.xml` + ملفات `strings_*.xml` (disease, paywall).
3. ترجم arrays في `PlantNotificationHelper.java` بعد تنفيذ B5.

#### B3 🟠 ترجمة `plants.csv`
- أضف أعمدة `name_en, lighting_en, soil_en, fertilizing_en, watering_en` (85 KB ⇒ ~140 KB بعد التوسيع).
- `PlantCatalogLookup.kt` يقرأ العمود حسب `Locale.getDefault().language`.

#### B4 🟠 Per-App Language API + شاشة اختيار لغة
- في `SettingsDialogFragment.java`، أضف Spinner للغات (DE, EN).
- استخدم `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))`.
- احفظ في SecurePrefsHelper.

#### B5 🟠 نقل نصوص PlantNotificationHelper إلى strings.xml
- أنشئ `values/notifications.xml` بـ string-array للـ40 رسالة.
- استبدل arrays في الكود بـ `getStringArray(R.array.notif_morning_bodies)`.
- يفتح الباب لـ B2 (الترجمة الإنجليزية).

#### B6 🟠 تحديث `locale_config.xml`
- أضف `<locale android:name="en" />`.

**معايير القبول الإجمالية للمرحلة B:**
- [ ] تغيير لغة النظام إلى EN ⇒ كل الواجهة بالإنجليزية + الإشعارات.
- [ ] `Settings → Sprache → English` يعمل فوراً بدون إعادة تشغيل.

---

### المرحلة C — Layer 3 (Architectural Hardening) — لا blocker لكن يقلّل ديون التقنية (5-7 أيام)

#### C1 🟠 إضافة Hilt
- اتّبع Layer 3.2 في `PlantCare_Action_Plan.md`.
- ابدأ بـ `App` + `DatabaseModule` + `RepositoryModule`.
- نقل ViewModels واحداً واحداً.

#### C2 🟠 إزالة `AppDatabase.getInstance` من ViewModels (5 ملفات)
- `AllPlantsViewModel`, `MyPlantsViewModel`, `OnboardingViewModel`, `PlantIdentifyViewModel`, `TodayViewModel`.
- مرّر Repository مباشرة (مع Hilt: `@Inject constructor(private val plantRepo: PlantRepository) : ViewModel()`).

#### C3 🟠 تحويل DAOs إلى LiveData<List<...>> (إصلاح Reactive UI)
في `PlantDao.java` و `ReminderDao.java`:
```java
// قبل
@Query("...")
List<WateringReminder> getAllRemindersForUser(@Nullable String email);

// بعد
@Query("...")
LiveData<List<WateringReminder>> observeAllRemindersForUser(@Nullable String email);
```
ثم احذف `liveData{}` builders في Repository — مرّر LiveData من DAO مباشرة. هذا يجعل UI يتحدّث عند أي تغيير في Room بدون `DataChangeNotifier`.

#### C4 🟠 إزالة `new Thread(...)` من UI (28 موضع)
- بدّل بـ `viewModelScope.launch(Dispatchers.IO)` للـ Kotlin.
- بدّل بـ `Executors.newSingleThreadExecutor()` للـ Java الذي لا يُحوَّل.

#### C5 🟠 ربط `catch (X ignored)` بـ Crashlytics
- استبدل النمط بـ `catch (e: Throwable) { CrashReporter.log(e) }`.
- 50 موضع — يمكن أتمتته بـ regex sed.

**معايير القبول:**
- [ ] `grep -rn "AppDatabase.getInstance" app/src/main/java/com/example/plantcare/ui/` ⇒ 0.
- [ ] `grep -rn "@HiltAndroidApp\|@AndroidEntryPoint"` ⇒ تظهر في كل Activity/Fragment.
- [ ] `grep -rn "new Thread(" app/src/main/java/com/example/plantcare/ui/` ⇒ 0.
- [ ] UI يتحدّث تلقائياً عند إضافة نبات (بدون pull-to-refresh).

---

### المرحلة D — Layer 7 (QA) — قبل Production Rollout (4-5 أيام)

#### D1 🔴 رفع Tests إلى 25+
- `AuthRepositoryTest`: 8-10 اختبار (signUp, signIn, logout, guest mode, error paths).
- `PlantRepositoryTest`: توسيع إلى 10 (CRUD + countByRoom + categories).
- `ReminderRepositoryTest`: توسيع إلى 8 (today + overdue + range + delete).
- `BillingManagerTest`: 5 اختبارات (mock BillingClient).
- `MigrationTest`: إضافة 7→8، 8→9، 9→10.

#### D2 🔴 مصفوفة اختبار يدوي
استخدم نفس مصفوفة Layer 7.1 في `PlantCare_Action_Plan.md` (15 سيناريو × 4 أجهزة). أنشئ Google Sheet أو ملف Markdown checklist.

#### D3 🟠 Accessibility Scanner + TalkBack
- ثبّت Accessibility Scanner على جهاز.
- أكمل سيناريو "إضافة نبات" بـ TalkBack مفعّل.
- أصلح أي عنصر بدون `contentDescription`.

#### D4 🔴 Security Checklist
- [ ] `adb shell run-as com.fadymerey.plantcare cat /data/data/.../shared_prefs/prefs.xml` ⇒ لا email خام.
- [ ] `adb backup` ⇒ لا tokens.
- [ ] محاولة قراءة `users/foreign_uid/plants` من حساب آخر ⇒ Permission Denied.
- [ ] `apktool d app-prod-release.apk` ⇒ كود مبهم (R8 + ProGuard).
- [ ] أضِف `android:usesCleartextTraffic="false"` في Manifest صراحة.

#### D5 🟠 LeakCanary
- أضف `debugImplementation 'com.squareup.leakcanary:leakcanary-android:2.13'`.
- شغّل سيناريو: فتح/إغلاق DiseaseDiagnosisActivity 10 مرات ⇒ لا تسرّبات.

**معايير القبول:**
- [ ] جميع البنود في القسم 8 من PlantCare_Action_Plan.md معلَّمة ✅.

---

### المرحلة E — النشر (Soft Launch + Production)

#### E1 🟢 Privacy Policy على GitHub Pages
- مفعّل `.github/workflows/pages.yml`، فقط يحتاج تفعيل Pages في settings للريبو.

#### E2 🟢 Play Console Internal Testing
- Upload `app-prod-release.aab` إلى Play Console → Internal Testing track.
- إضافة 5-10 mistesters.
- مدة: 3-7 أيام لجمع feedback + crashlytics.

#### E3 🟢 Open Beta (Germany only)
- بعد A-D + 0 Critical bugs في Internal.
- مدة: 2 أسبوع.

#### E4 🟢 Production Rollout 10% → 50% → 100%
- 24-48 ساعة بين كل مرحلة، مراقبة Crashlytics + Reviews.

---

## 9 — جدول أولويات الـ Cloud Coding (نسخ-لصق جاهز)

```
[Phase A — Blockers]
  A1. Unify SecurePrefsHelper email read/write across 23 files (~6h)
  A2. Wire BillingManager.connect() + Paywall trigger + FREE_PLANT_LIMIT enforcement (~4h)
  A3. Move OPENWEATHER_API_KEY to BuildConfig (~1h)
  A4. Add plant_disease_model.tflite OR hide disease feature (~2h)
  A5. Replace AdMob Test IDs with prod IDs (~1h)

[Phase B — i18n]
  B1. Drop resConfigs "de" (5 min)
  B2. Translate values/strings.xml + strings_disease.xml + strings_paywall.xml (~6h)
  B3. Add EN columns to plants.csv (~3h)
  B4. Per-App Language picker in SettingsDialogFragment (~2h)
  B5. Extract PlantNotificationHelper strings to resources (~2h)
  B6. Add <locale name="en"/> to locale_config.xml (5 min)

[Phase C — Architecture]
  C1. Add Hilt + DatabaseModule + RepositoryModule (~6h)
  C2. Refactor 5 ViewModels to use Repository (no AppDatabase.getInstance) (~3h)
  C3. Convert DAOs to LiveData<List<X>> + remove liveData{} wrappers (~4h)
  C4. Replace 28 new Thread() with coroutines/Executors (~3h)
  C5. Replace 50 catch (X ignored) with CrashReporter.log(e) (~2h)

[Phase D — QA]
  D1. Add 15+ unit tests (Auth, Plant, Reminder, Billing, Migrations) (~6h)
  D2. Manual matrix 15 scenarios × 4 devices (~3 days)
  D3. Accessibility Scanner + TalkBack pass (~2h)
  D4. Security checklist (adb backup, prefs grep, ProGuard verify) (~2h)
  D5. LeakCanary integration + check (~1h)

[Phase E — Release]
  E1. Activate GitHub Pages for Privacy Policy (~30 min)
  E2. Internal Testing 5-10 testers (3-7 days)
  E3. Open Beta Germany (2 weeks)
  E4. Production rollout 10% → 50% → 100% (3-5 days)
```

---

## 10 — قواعد العمل لـ Cloud Coding

1. **لا تُعلِن مهمّة "مكتملة" إلا بعد grep يثبت 0 تطابق للحالة المرفوضة** (مثلاً للـA1: `grep -rn '"current_user_email"' app/src/main/java` يجب أن يُظهر فقط `SecurePrefsHelper.kt` و `EmailContext.kt`).
2. **اعتمد التصنيف الرباعي:** «مكتملة / شبه مكتملة / شكلية فقط / مكسورة» — لا «موجودة».
3. **بعد كل مهمة، شغّل `./gradlew assembleProdRelease` + قِس عدد الـwarnings**. لا تسمح بزيادة warnings.
4. **حدّث PROGRESS.md بصدق** — لا تنقل المهمة إلى "Completed Tasks" إن كانت Layer 3 ادّعت إكمال DAO cleanup بينما `grep` يقول 102.
5. **إذا اكتشفت أنّ مهمة من Layer 5 أو 6 المعلَنة منجزة في الواقع غير منجزة، أعِدها إلى pending** (مثل Task 6.3 — Billing لم تُربط).
6. **اختبر يدوياً سيناريو الترقية (`adb install -r`) قبل اعتبار A1 منجزة**.

---

## 11 — أسئلة مفتوحة للمستخدم (Fady)

1. **Server-side billing verification**: هل تريد إضافة Cloud Function الآن (يومان عمل + Firebase Cloud Functions billing) أو تأجيلها لإصدار 1.1؟
2. **TFLite model**: هل تنزّل نموذج جاهز من Kaggle (دقّة ~85% متغيّرة) أو تدرّب نموذجك (4-7 أيام GPU)؟ أو **تخفي الميزة** للإصدار الأول وتعيدها لاحقاً؟
3. **Languages at launch**: هل النشر الأول للسوق الألماني فقط (يبقى `resConfigs "de"`) أو ألمانيا + إنجليزية معاً (مرحلة B إلزامية)؟
4. **OpenWeather subscription**: هل لديك مفتاح API للطقس؟ Free tier محدود بـ60 طلب/دقيقة — كافٍ لتطبيق بهذا الحجم.

---

## 12 — التحديثات المطلوبة على PROGRESS.md

تصحيحات يجب إضافتها:

```diff
- ## Completed Tasks: ..., Task 3.1, Task 3.2, Task 3.3, Task 3.4, Task 4.1, Task 4.2, Task 4.3, Task 4.4, Task 4.5, ...
+ ## Completed Tasks: Task 0.*, Task 1.*, Task 2.* (partial), Task 5.* (partial), Task 6.5 (test IDs)
+ ## Re-opened: Task 3.1 (102 DAO calls outside repo), Task 3.2 (no Hilt), Task 3.3 (28 new Thread), Task 3.4 (PlantNotificationHelper not externalized)
+ ## Re-opened: Task 4.1 (resConfigs de still present), Task 4.2 (no values-en), Task 4.3 (no per-app language)
+ ## Re-opened: Task 6.3 (BillingManager.getInstance never called outside billing/), Task 6.4 (PaywallDialogFragment never displayed), Task 6.5 (still test ad IDs)
+ ## Critical bugs found: SecurePrefs migration breaks email lookup in 23 sites, OPENWEATHERMAP_API_KEY hardcoded placeholder, plant_disease_model.tflite missing
+ ## Untouched: All of Layer 7 (QA matrix, perf tests, accessibility manual, security checklist)
```

---

**نهاية التقرير.** الترتيب الموصى به هو **A1 → A3 → A2 → A5 → A4** (داخل المرحلة A) لأنّ A1 يفك سدّاً يمنع باقي المهام، وA3+A2 يفعّلان وظائف، وA5+A4 إصلاحات أصول. ثم B → C → D → E.

استخدم هذا التقرير ضمن prompt `Cloud Coding` كمستند مرجعي في كل جلسة، وأمر المهام بترتيبها هنا واحدة-واحدة دون تخطّي. لا تسمح لـCloud Coding بالقفز إلى مرحلة جديدة قبل أن يُثبت إكمال السابقة بـ `grep` فعلي.
