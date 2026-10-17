# PlantCare — تقرير وظائف التطبيق

**التاريخ:** 29 أبريل 2026
**نطاق التقرير:** الوظائف الوظيفية (Functional) فقط — لا أمن، لا نشر، لا i18n.
**المنهج:** قراءة مباشرة للكود + تتبّع تدفّقات المستخدم مقابل الواقع المُشاهد في الصور المرفقة.
**المراجع:** `app/src/main/java/com/example/plantcare/...` — 144 ملف مصدر.

---

## 0 — ملخّص تنفيذي بالتصنيف الرباعي

| # | الميزة | التصنيف | الأثر على المستخدم |
|---|---------|----------|---------------------|
| 1 | إضافة/حذف/تعديل النباتات + غرف | ✅ مكتملة | تعمل |
| 2 | كاتالوج CSV (85 KB، 100+ نبتة بالألمانية) | ✅ مكتملة | تعمل |
| 3 | تذكير السقاية اليومي + Worker كل 6h | ✅ مكتملة | يعمل |
| 4 | Camera capture + FileProvider | ✅ مكتملة (التقاط) | الصور تُلتقَط وتُحفظ |
| 5 | Plant Identification (PlantNet API + Top-3) | ⚠️ شبه مكتملة | تتعرّف وتضيف، لكن جدول السقاية ثابت 5 أيام |
| 6 | Weather Card (OpenWeather) | ⚠️ شبه مكتملة | البطاقة تظهر، لكن تأثيرها على السقاية ضعيف |
| 7 | Plant Disease Diagnosis (TFLite) | 🔴 مكسورة | ملف النموذج مكان خاطئ + الزر مُعطَّل بـ Toast |
| 8 | عرض الصور في Today list (Calendar) | 🔴 مكسورة | يظهر placeholder بدل الصورة |
| 9 | عرض الصور في Archive (نباتاتي) | 🔴 مكسورة | تظهر أيقونة "broken image" |
| 10 | أيقونات الزرّين في Toolbar (Identify/Disease) | 🟠 شكلية ضعيفة | غير معبّرة وظيفياً |
| 11 | ملف/سجل لكل نبتة (Journal/History) | 🔴 ناقصة | لا توجد شاشة موحَّدة — البيانات موجودة لكن بلا واجهة |
| 12 | Growth Memoir (collage صور) | ⚠️ شبه مكتملة | الزر يعمل، لكن لا يعرض history غير صور |
| 13 | Family Share (محلي) | ⚠️ شبه مكتملة | تخزين محلي فقط — لا دعوة فعلية |
| 14 | Vacation Mode | 🔴 مخفية | كود موجود — لا UI |
| 15 | Streak / Challenges | 🔴 مخفية | كود tracking موجود — لا شاشة عرض |
| 16 | Treatment Plan Builder (4 خطوات بعد التشخيص) | ✅ مكتملة | جاهز يعمل بمجرد إصلاح Disease feature |
| 17 | Widget الشاشة الرئيسية | ✅ مكتملة | يعمل |
| 18 | Today / Calendar / All plants / My plants tabs | ✅ مكتملة | تعمل |

**الخلاصة:** التطبيق يحوي بنية تحتية غنية. المشاكل ليست في غياب الكود بل في:
1. **خلل عرض الصور** (مسار content:// لا يُعالَج صحيحاً في طبقة UI).
2. **فقدان وصلة بيانات** (PlantNet → wateringInterval).
3. **ميزة Disease Diagnosis** معطّلة بقرار يدوي بسيط (Toast)، رغم أن النموذج وملفاته جاهزة.
4. **عدم وجود واجهة موحَّدة** لعرض history النبتة (الميزة الجديدة المطلوبة).
5. **ميزات شُيِّدت ولم تُكشَف لـUI** (Vacation, Streak).

---

## 1 — المشاكل المُؤكَّدة من الصور المرفقة

### 1.1 🔴 الصور لا تُعرض في قائمة اليوم في تبويبة Calendar

**ما يراه المستخدم (الصورة 1):**
بعد التقاط صورة من زر الكاميرا في FAB أسفل تبويبة Calendar وإسنادها للنبتة، يظهر في قائمة اليوم مربّع أخضر فيه أيقونة شجرة صغيرة (placeholder افتراضي) وليس الصورة الفعلية.

**سلسلة الكود (تم تتبّعها):**

```text
PhotoCaptureCoordinator.kt:202-208
└─ createImageUri()
   └─ يحفظ في: getExternalFilesDir(Pictures)/PlantCare/IMG_<UUID>.jpg
   └─ يُرجِع: FileProvider URI (content://com.example.plantcare.provider/...)

PhotoCaptureCoordinator.kt:252-278
└─ savePhotoToDb()
   └─ photo.imagePath = uri.toString()  ← يُخزَّن content://... كـ String

ReminderViewModel.kt:178-214
└─ يقرأ PlantPhoto من DB
└─ uri = Uri.parse(p.imagePath)

CalendarPhotoGridCompose.kt:59-72  ← المشكلة
└─ Glide.with(ctx).load(p.uri)
                  .placeholder(R.drawable.ic_default_plant)
                  .error(R.drawable.ic_default_plant)
                  .into(iv)
```

**التشخيص:**
- المسار المُخزَّن `content://...` صحيح.
- لكن `Glide.load(Uri)` يحتاج FileProvider grant ساري المفعول، وعند فتح الـ Composable لاحقاً تكون الصلاحية قد سقطت.
- لا fallback logic — أي خلل في تحميل الصورة يُعرَض كـ placeholder صامت.
- المُقارَنة مع `PlantImageLoader.kt` (الذي يعمل بشكل صحيح في أماكن أخرى) تُظهر أن `PlantImageLoader` يفحص مسبقاً نوع المسار (file://, content://, http, raw path) ويتعامل معها بطرق مختلفة، بينما `CalendarPhotoGridCompose` يمرّر URI واحدة لـGlide بدون فحص.

**الحل:**
- إعادة استخدام `PlantImageLoader.load(...)` نفسه في `CalendarPhotoGridCompose`.
- أو حفظ الصورة كملف داخلي ثابت (`context.filesDir/photos/<uuid>.jpg`) بدل URI الـFileProvider — ثم تخزين المسار المطلق.

---

### 1.2 🔴 الصور لا تُعرض في أرشيف النبتة (تبويبة نباتاتي)

**ما يراه المستخدم (الصورة 2):**
في حوار "Photos from Einblatt"، يظهر إطار بأيقونة broken image (مع علامة تعجّب) وتاريخ `2026-04-29` تحته.

**سلسلة الكود (المشكلة الفعلية):**

```java
ArchivePhotosDialogFragment.java:61-86
String path = p.imagePath;
if (path.startsWith("PENDING_DOC:")) {
    iv.setImageResource(android.R.drawable.ic_menu_report_image);
} else if (path.startsWith("http")) {
    Glide.with(ctx).load(path)...
} else if (path.startsWith("content://") || path.startsWith("file://")) {
    Glide.with(ctx).load(Uri.parse(path))...
} else {
    Glide.with(ctx).load(new File(path))...   ← يُعامِل المسار كـFile مطلق
}
```

**التشخيص:**
- شرط `path.startsWith("content://")` صحيح **في النظرية**.
- لكن صور الكاميرا الجديدة تُحفظ بـ `FileProvider.getUriForFile(...)` وقد تُرجِع URI تبدأ بـ `content://com.example.plantcare.provider/...` — تتطابق مع الشرط.
- إذا كان `path` فارغاً أو يحوي `null` toString أو URI غير قابل للتحويل بسبب revoked grant → Glide.error → broken image.
- التاريخ يظهر لأنه مُخزَّن مستقلاً عن imagePath.

**الحل:**
- نفس حل 1.1 — توحيد طبقة تحميل الصور عبر `PlantImageLoader`.
- بالإضافة: حفظ الصورة كنسخة دائمة في `context.filesDir/plant_photos/<plantId>/<uuid>.jpg` بدل الاعتماد على FileProvider URI الذي يفقد grant بعد إعادة فتح التطبيق.

---

### 1.3 🟠 أيقونتا الزرّين في Toolbar ليستا معبّرتَين

**ما يراه المستخدم (الصورة 4):**
في toolbar الرئيسية بجانب ⚙️، يظهر زرّان:
- 📷 (كاميرا) — يفترض: التعرّف على النبتة وإضافتها
- 🍐 (شكل ثمرة) — يفترض: فحص حالة النبتة

**الواقع (من الكود):**

```xml
activity_main.xml:38-60
<ImageButton android:id="@+id/identifyButton"
             android:src="@drawable/ic_identify" />   ← كاميرا+ورقة
<ImageButton android:id="@+id/diseaseButton"
             android:src="@drawable/ic_disease" />   ← ورقة+علامة تحذير
```

**التشخيص:**
- `ic_identify` (كاميرا+ورقة) **معبّرة جيداً** عن "تعرّف على نبتة".
- `ic_disease` (ورقة + علامة تحذير ⚠️) **غير معبّرة** عن "فحص صحة" — تبدو وكأنها تنبيه عام.
- الذي يظهر في الصورة كـ"كمثرى/ثمرة" هو في الواقع `ic_disease.xml` بشكله الحالي (ورقة دائرية مع cross-mark) — مرئيّاً يبدو كثمرة.

**الحلول المقترحة (Material Icons):**
- `ic_health_check` — قلب + ✓ (الأنسب للتشخيص الصحي).
- `ic_medical_services` — رمز طبي.
- `ic_spa` — ورقة ناعمة (لن تساعد كثيراً، تشبه الحالية).
- **أفضل خيار:** أيقونة custom: ورقة + microscope صغير، أو ورقة + ❤️‍🩹 (heart with bandage).

ملاحظة: زر التعرّف الحالي جيد، التغيير المطلوب في زر الفحص الصحي فقط.

---

### 1.4 🔴 جدول السقاية بعد PlantNet ثابت دائماً 5 أيام

**ما يراه المستخدم:**
بعد التعرّف على نبتة من PlantNet وإضافتها، يظهر في Calendar تذكيرات سقاية كل 5 أيام بصرف النظر عن نوع النبتة (صبّار، أو نبتة استوائية، أو شجرة، إلخ).

**سلسلة الكود (السبب الجذري):**

```kotlin
PlantIdentifyActivity.kt:433-443
val draft = Plant().apply {
    lighting = care?.lighting ?: defaults.lighting
    soil = care?.soil ?: defaults.soil
    fertilizing = care?.fertilizing ?: defaults.fertilizing
    watering = care?.watering ?: defaults.watering
    // ❌ wateringInterval لا يُمرَّر — يبقى = 0 (default int)
}
```

```java
AddToMyPlantsDialogFragment.java:295-296
int interval = ReminderUtils.parseWateringInterval(newPlant.watering);
newPlant.wateringInterval = interval > 0 ? interval : 5;   ← ⚠️ HARDCODED FALLBACK
```

```java
ReminderUtils.java:107-120
parseWateringInterval(String watering) {
    // يستخرج أول رقم يجده في النص
    // مثل: "Alle 14 Tage" → 14
    // مثل: "Sparsam gießen. Erde durchtrocknen lassen." → 0
}
```

**التشخيص:**
- `plants.csv` يحوي أعمدة: `name, lighting, soil, fertilizing, watering` فقط — **لا عمود `wateringInterval` رقمي**.
- نص `watering` للنباتات الموجودة في الكاتالوج عادةً يحوي رقماً (`"Alle 14 Tage. ..."`) → يعمل.
- لكن `PlantCareDefaults.kt` (للنباتات غير الموجودة في CSV) يستخدم نصوصاً عامة بدون أرقام:
  ```kotlin
  "cactaceae" to CareTexts(
      watering = "Sparsam gießen. Erde zwischen den Wassergaben komplett durchtrocknen…"
  )
  ```
- → `parseWateringInterval` يُرجِع 0 → fallback `= 5` → كل النباتات الجديدة المُتعرَّف عليها تأخذ 5 أيام.

**الحل (3 طبقات):**
1. **إضافة عمود `wateringInterval` لـ CSV:**
   ```csv
   name,lighting,soil,fertilizing,watering,wateringInterval
   Aloe Vera,...,...,...,Alle 14 Tage,14
   Einblatt,...,...,...,Alle 10 Tage,10
   ```
2. **إضافة الحقل لـ `CareInfo` data class:**
   ```kotlin
   data class CareInfo(..., val wateringInterval: Int = 0)
   ```
3. **تحديث `PlantCareDefaults`:**
   ```kotlin
   data class CareTexts(..., val wateringInterval: Int = 7)
   "cactaceae" to CareTexts(..., wateringInterval = 21)
   "araceae"  to CareTexts(..., wateringInterval = 7)
   "succulentaceae" to CareTexts(..., wateringInterval = 21)
   ```
4. **تمرير القيمة في `PlantIdentifyActivity.kt:433`:**
   ```kotlin
   wateringInterval = care?.wateringInterval
       ?: defaults.wateringInterval
   ```

**معايير القبول:**
- إضافة Aloe Vera → جدول سقاية كل 14 يوم.
- إضافة Tomate → كل 3 أيام.
- إضافة نبتة غير موجودة في CSV (مثل Cactus غير معروف) → defaults حسب العائلة.

---

## 2 — ميزة الطقس (Weather → Watering Adjustment)

### 2.1 ما يعمل الآن

| المكون | الحالة |
|--------|---------|
| OpenWeather API + Key (BuildConfig) | ✅ |
| Cache 3 ساعات | ✅ |
| WeatherTipCard (UI الخضراء) | ✅ تعرض موقع + درجة + نصيحة |
| Worker دوري كل 12 ساعة | ✅ مجدول من `App.java:79-96` |
| Worker فوري عند منح صلاحية الموقع | ✅ من `MainActivity.java:298-313` |
| تعديل تواريخ التذكيرات في DB | ⚠️ موجود لكن قيوده صارمة جداً |

### 2.2 خوارزمية التعديل الحالية وقيودها

```kotlin
WeatherRepository.kt:119-151 — getWateringAdvice()
└─ مطر: factor = 0.5
└─ حرارة > 30°: factor = 1.5
└─ رطوبة > 80%: factor = 0.7
└─ برودة < 5°: factor = 0.5
└─ غير ذلك: factor = 1.0

WeatherAdjustmentWorker.kt:151-155 — adjustReminders()
val dayShift = when {
    factor < 0.8f -> 1   // تأخير يوم واحد فقط
    factor > 1.2f -> -1  // تقديم يوم واحد فقط
    else          -> 0   // عدم تعديل
}
```

**مشاكل وظيفية:**
1. **عتبات صارمة:** factor بين 0.8 و 1.2 لا يُسبّب أي تغيير. مطر خفيف (factor 0.9) لا يؤجِّل السقاية.
2. **يوم واحد فقط:** حتى الطقس المتطرّف يُؤجِّل يوماً واحداً فقط — حتى لو كانت توقّعات الأسبوع كله مطر.
3. **تذكير واحد لكل نبتة:** السطر 139-144:
   ```kotlin
   val processedPlants = mutableSetOf<Int>()
   for (reminder in futureReminders) {
       if (reminder.plantId in processedPlants) continue
       processedPlants.add(reminder.plantId)
   }
   ```
   إذا نبتة لها 3 تذكيرات في الأسبوع المقبل، فقط الأول يُعدَّل، الباقي لا.
4. **خروج صامت بدون email:** `EmailContext.current(context) ?: return` — إذا فشل الـmigration، الميزة تُحبَط بصمت.
5. **لا يأخذ بـ Forecast (تنبّؤ):** يستخدم API "current weather" فقط — لا يقرّر بناء على توقع الـ72 ساعة المقبلة.

### 2.3 ما يحتاج لكي يعمل بشكل تام

```kotlin
// عتبات أكثر تدرّجاً
val dayShift = when {
    factor <= 0.5 -> 3  // مطر غزير: تأخير 3 أيام
    factor <= 0.7 -> 2
    factor <= 0.9 -> 1
    factor >= 1.5 -> -2 // حرارة شديدة: تقديم يومين
    factor >= 1.2 -> -1
    else -> 0
}

// معالجة كل التذكيرات المستقبلية في الأسبوع المقبل
for (reminder in futureReminders.filter { it.daysFromNow <= 7 }) {
    // عدّل بدون تخطّي
}

// استخدام Forecast API
WeatherService.getForecast(lat, lon, 5)  // 5 أيام
// قرّر بناء على متوسط هطول الأمطار
```

**إشعار للمستخدم:** عند تعديل تذكير، أرسل notification "تم تأجيل سقاية Einblatt يوماً بسبب الأمطار المتوقّعة."

---

## 3 — ميزة Disease Diagnosis (فحص الحالة الصحية)

### 3.1 الوضع الحالي

| المكون | الحالة | الموقع |
|--------|---------|---------|
| نموذج TFLite (926 KB) | ✅ موجود | `app/src/main/plant_disease_model.tflite` |
| Labels (38 صنف PlantVillage) | ✅ موجود | `app/src/main/assets/plant_disease_labels.txt` |
| `PlantDiseaseClassifier.kt` | ✅ مكتمل | محرّك التصنيف |
| `DiseaseDiagnosisActivity.kt` | ✅ مكتمل | UI + flow |
| `DiseaseDiagnosisRepository.kt` + entity | ✅ مكتمل | حفظ النتائج في DB |
| `TreatmentPlanBuilder.kt` | ✅ مكتمل | يبني 4 تذكيرات علاجية |
| Activity-level guard | ⚠️ يُغلق Activity إن لم يجد ملف TFLite في `assets/` | `DiseaseDiagnosisActivity.kt:96-104` |
| زر فحص في MainActivity | 🔴 معطّل بـToast | `MainActivity.java:174-183` |

### 3.2 السبب الجذري لعدم العمل

**خطآن مرتبطان:**

1. **مكان النموذج خاطئ:**
   - الموجود: `app/src/main/plant_disease_model.tflite`
   - المطلوب: `app/src/main/assets/plant_disease_model.tflite`
   - `assets.list("")?.contains("plant_disease_model.tflite")` يفحص داخل assets فقط → يُرجِع false.

2. **زر MainActivity مُسوَّر بـToast بدلاً من intent فعلي:**
   ```java
   diseaseButton.setOnClickListener(v -> {
       Toast.makeText(this,
           "Krankheitserkennung: bald verfügbar",  // "قريباً"
           Toast.LENGTH_SHORT).show();
   });
   ```

### 3.3 خطوات التفعيل

1. **نقل ملف:**
   ```bash
   mv app/src/main/plant_disease_model.tflite \
      app/src/main/assets/plant_disease_model.tflite
   ```
2. **استبدال Toast بـIntent الفعلي:**
   ```java
   diseaseButton.setOnClickListener(v ->
       startActivity(new Intent(this, DiseaseDiagnosisActivity.class))
   );
   ```
3. **اختبار:** التقاط صورة لورقة طماطم مصابة → التحقق من ظهور Top-3 + advice + خطة علاج 4 خطوات في Calendar.

### 3.4 ملاحظة على tags

نموذج PlantVillage يدعم 38 صنفاً (تومات، عنب، تفاح، ذرة، إلخ + healthy). **لا يدعم النباتات المنزلية** (Einblatt, Aloe Vera، Bonsai…). للأسف الميزة محدودة بمجموعة محددة من المحاصيل الزراعية.

**اقتراح:** بعد التفعيل، أضِف تنبيهاً في `DiseaseDiagnosisActivity` يُعرَّف المستخدم على النباتات المدعومة فقط (38 صنف + healthy)، ويرفض الصور غير المتطابقة.

---

## 4 — ميزة "ملف لكل نبتة" (Per-Plant Journal) — الميزة الجديدة المطلوبة

### 4.1 ماذا تحتوي البيانات الحالية (90% من المطلوب)

| العنصر | المصدر | الحالة |
|---------|---------|---------|
| تاريخ بدء العمل مع النبتة | `Plant.startDate` | ✅ موجود |
| تواريخ السقايات المُجدوَلة | `WateringReminder.date` | ✅ موجود |
| تواريخ السقايات المُنجزة (history) | `WateringReminder.completedDate` + `done` + `wateredBy` | ✅ موجود |
| تواريخ الصور المُلتقَطة | `PlantPhoto.dateTaken` + `imagePath` | ✅ موجود |
| تواريخ فحوص الصحة | `DiseaseDiagnosis.createdAt` (epoch) | ✅ موجود |
| نتيجة كل فحص | `DiseaseDiagnosis.displayName + confidence + note` | ✅ موجود |
| ربط فحص → نبتة | `DiseaseDiagnosis.plantId` | ✅ موجود (حقل اختياري) |

### 4.2 ما ينقص

| البند | السبب | الحل المقترح |
|-------|--------|-----------------|
| تمييز "صورة عادية" عن "صورة فحص" في `PlantPhoto` | لا حقل `photoType` | إضافة `photoType TEXT DEFAULT 'regular'` (regular/inspection/cover) |
| ربط صورة فحص بنتيجة تشخيصها | `DiseaseDiagnosis` و `PlantPhoto` منفصلان | إضافة `diagnosisId INTEGER` لـ`PlantPhoto` (FK) |
| ملاحظات على كل سقاية | لا حقل في `WateringReminder.notes` | إضافة `notes TEXT` |
| Migration 10→11 | — | كتابة `MIGRATION_10_11` يضيف الأعمدة |
| ViewModel يجمع المصادر الثلاثة | لا يوجد | `PlantJournalViewModel` يدمج reminders+photos+diagnoses في timeline |
| Fragment / Activity للعرض | لا يوجد | `PlantJournalFragment` بـRecyclerView (ItemTypes متعدّدة) |
| دخول من PlantDetailDialogFragment | لا زر "فتح السجل" | زر جديد فوق "Archivfotos ansehen" |

### 4.3 تصميم UI مقترح للـJournal

```
┌─────────────────────────────────────────────┐
│  ← Einblatt — Wohnzimmer                    │
├─────────────────────────────────────────────┤
│  📅 منذ 15 كانون الثاني 2025  (15 شهر)     │
│  💧 آخر سقاية: قبل يومين | 47 سقاية كاملة  │
│  📷 18 صورة | 🩺 3 فحوص صحية              │
├─────────────────────────────────────────────┤
│  Filter:  [الكل] [💧] [📷] [🩺]            │
├─────────────────────────────────────────────┤
│                                              │
│  ● 2026-04-29 — اليوم                       │
│  └─ 📷 صورة عادية                           │
│      [thumbnail]                             │
│                                              │
│  ● 2026-04-27                                │
│  └─ 💧 سقاية مُنجزة                         │
│      • أنجزها: Fady                         │
│      • ملاحظة: "بدت ظمآنة"                  │
│                                              │
│  ● 2026-04-20                                │
│  └─ 🩺 فحص صحة                              │
│      [thumbnail]                             │
│      • النتيجة: Healthy ✅ (96%)             │
│                                              │
│  ● 2026-04-15                                │
│  └─ 🩺 فحص صحة                              │
│      [thumbnail]                             │
│      • النتيجة: Tomato Late Blight (87%)    │
│      • خطة علاج: 4 تذكيرات تم إنشاؤها      │
│      • [فتح خطة العلاج →]                   │
│                                              │
│  ● 2025-01-15 — اليوم الأول 🌱              │
└─────────────────────────────────────────────┘
```

### 4.4 خطّة تنفيذ مقترحة (3 sprints صغيرة)

**Sprint 1 — Schema + Migration (2 ساعة):**
- `MIGRATION_10_11` يضيف 3 أعمدة.
- اختبار يدوي على build.

**Sprint 2 — ViewModel + Repository (3 ساعات):**
- `PlantJournalViewModel`: `Flow<List<JournalEntry>>` يجمع من 3 جداول.
- `JournalEntry` sealed class: `WateringEvent`, `PhotoEntry`, `DiagnosisEntry`.
- ترتيب حسب timestamp تنازلياً.

**Sprint 3 — UI (4 ساعات):**
- `fragment_plant_journal.xml` بـRecyclerView متعدد الـviewTypes.
- 3 ItemAdapters (Watering, Photo, Diagnosis).
- Filter chips (Material).
- زر فتح من `PlantDetailDialogFragment`.

**الإجمالي:** ~9 ساعات عمل.

---

## 5 — ميزات أخرى مكتشفة (مُضافة للكود لكن غير ظاهرة في UI)

### 5.1 🔴 Vacation Mode — موجود في الكود فقط

**الموقع:** `feature/vacation/VacationPrefs.kt`
**الحالة:** Class جاهز يحفظ تواريخ بداية/نهاية الإجازة في SharedPreferences.
**المفقود:**
- لا dialog لتفعيله.
- لا قائمة في Settings.
- لا ربط مع `PlantReminderWorker` لتجاوز الإشعارات خلال الإجازة.

**الاستخدام المتوقَّع:** المستخدم يضع تاريخَي البداية/النهاية → التذكيرات تُؤجَّل تلقائياً + يُعرَض banner "الوضع الإجازة فعّال حتى …".

### 5.2 🔴 Streak / Challenges — موجود في الكود فقط

**الموقع:** `feature/streak/StreakTracker.kt`, `ChallengeRegistry.kt`, `StreakBridge.java`
**الحالة:** كود tracking كامل (يسجّل عدد السقايات المتتالية، يُحدِّث challenges مثل WATER_STREAK_7).
**المفقود:**
- لا شاشة لعرض streak الحالي.
- لا badge في Profile.
- لا dialog احتفالي عند إنجاز challenge.
- لا أيقونة 🔥 في Toolbar.

**الاستخدام المتوقَّع:** بعد كل سقاية، إذا أكمل المستخدم تحدّياً (مثل 7 أيام متتالية)، يظهر dialog "🎉 تم إنجاز تحدّي 7 أيام متتالية!".

### 5.3 ⚠️ Family Share — تخزين محلي فقط

**الموقع:** `feature/share/FamilyShareManager.kt`
**الحالة:** dialog يضيف/يحذف emails إلى `Plant.sharedWith`. **لا دعوة فعلية تُرسَل**، لا cloud functions، لا notifications للطرف الآخر.

**الاستخدام المتوقَّع:** Cloud Function يرسل invite email + يُضيف Firestore permission لرؤية النبتة.

### 5.4 ⚠️ Growth Memoir — يعمل لكن للصور فقط

**الموقع:** `feature/memoir/GrowthMemoirBuilder.kt`
**الحالة:** ينشئ collage 2x2 أو 3x4 من صور الأرشيف + headline.
**التحسين:** بعد إضافة Plant Journal، اجعل Memoir يولّد PDF/HTML يدمج: timeline السقاية + الصور + الفحوصات الصحية + إحصائيات.

---

## 6 — قائمة الإصلاحات بترتيب الأولوية

### الأولوية الحادّة (User-facing bugs مرئية في الصور)

| # | المهمة | الزمن المُقدَّر | الملفات |
|---|---------|------------------|---------|
| 1 | إصلاح عرض الصور في Today list (Calendar) — توحيد عبر `PlantImageLoader` | 2h | `CalendarPhotoGridCompose.kt` |
| 2 | إصلاح عرض الصور في Archive — نفس الحل | 1h | `ArchivePhotosDialogFragment.java` |
| 3 | تفعيل Disease Diagnosis: نقل tflite + استبدال Toast بـIntent | 30 min | `MainActivity.java`, ملف tflite |
| 4 | تغيير `ic_disease.xml` لأيقونة معبّرة عن الفحص الصحي | 1h | `res/drawable/ic_disease.xml` |
| 5 | إصلاح wateringInterval بعد PlantNet (CSV column + defaults + draft passing) | 4h | `plants.csv`, `PlantCatalogLookup.kt`, `PlantCareDefaults.kt`, `PlantIdentifyActivity.kt` |

### الأولوية المتوسّطة (تحسين الميزات الموجودة)

| # | المهمة | الزمن |
|---|---------|--------|
| 6 | تحسين خوارزمية Weather adjustment (عتبات متدرّجة + كل تذكيرات الأسبوع) | 3h |
| 7 | إضافة Forecast API (5 أيام) للقرارات المستقبلية | 4h |
| 8 | إشعار للمستخدم عند تأجيل/تقديم سقاية | 1h |

### الأولوية الجديدة (الميزة المطلوبة من Fady)

| # | المهمة | الزمن |
|---|---------|--------|
| 9 | Plant Journal — Sprint 1: Schema migration 10→11 (photoType, diagnosisId, notes) | 2h |
| 10 | Plant Journal — Sprint 2: ViewModel + Repository يجمع 3 مصادر | 3h |
| 11 | Plant Journal — Sprint 3: UI Fragment + Filter chips + ربط بـPlantDetailDialog | 4h |

### الأولوية المنخفضة (كشف ميزات مخفية)

| # | المهمة | الزمن |
|---|---------|--------|
| 12 | Vacation Mode UI — dialog + ربط مع PlantReminderWorker | 4h |
| 13 | Streak / Challenges UI — شاشة مع badge + احتفالات | 5h |
| 14 | Family Share — Cloud Function + invite email (إذا أردت ربطاً سحابياً) | يومان |
| 15 | تحسين Growth Memoir ليصبح PDF report يدمج timeline + photos + diagnoses | 4h |

---

## 7 — الإجمالي

**المجموع الزمني للإصلاحات الحادّة + الميزة الجديدة:** ~24 ساعة عمل = 3 أيام عمل.

**التطبيق بعد هذه الجولة:**
- كل صورة تُلتقَط تظهر في مكانها.
- كل نبتة جديدة من PlantNet تأخذ جدول سقاية مناسب لنوعها.
- Disease Diagnosis تعمل بالكامل.
- كل نبتة لها ملف history واضح يجمع كل ما يخصّها.
- Weather يؤثر فعلاً على جدول السقاية بشكل متدرّج.

**ميزات إضافية يمكن تأجيلها لإصدار 1.1:**
- Vacation Mode UI.
- Streak UI.
- Family Share سحابي.

---

**نهاية التقرير الوظيفي.**
