# PlantCare — دليل الخطوات التشغيلية المفصّل
**التاريخ:** 18 مايو 2026
**الهدف:** الإجابة على الأسئلة الـ 12 في `PlantCare_Comprehensive_Review_2026-05-18.md` §11 بشكل عملي خطوة بخطوة.
**الجمهور:** Fady — يفترض هذا الدليل أنّك مطوّر Android خبير لكنّك لم تتعامل مع كل أدوات النشر/التشغيل من قبل.

---

## كيف تستخدم هذا الدليل

البنود مُرقَّمة حسب **الأولوية والتبعيّة**. ابدأ من البند 1 وانزل بالترتيب — كل بند قد يعتمد على البنود قبله. كل بند فيه:

- **🎯 ما المطلوب** — هدف بسيط بجملة
- **🤔 لماذا مهم** — الأثر لو لم تفعله
- **📋 الخطوات** — قائمة مرقّمة بالتفصيل
- **✅ كيف تتأكد أنه نجح** — معيار التحقّق
- **📸 ماذا أحتاج منك** — إن كنتُ بحاجة لمعلومات/صور لمتابعة العمل

في النهاية، **لائحة موصى بها بترتيب التنفيذ** لتمضي من 0 إلى 100% خلال أسبوع.

---

## 1️⃣ — Build verification (تثبيت + تجربة على الجهاز)

### 🎯 ما المطلوب
بناء `prodRelease` APK، تثبيته على جهاز فعلي أو محاكي، التقاط 7-8 سكرين شت من الشاشات الأساسية، مقارنتها مع التصميم المتوقَّع من Phase G.

### 🤔 لماذا مهم
آخر تعديلات Phase G (G-typography-fix، G-CRITICAL-1 Compose Inter+Manrope، G-PRO 1-3 elevation + selected-day + vivid green) **لم تُختبر بصرياً**. ربّما تبدو جميلة في الكود ومُتفاجِئة على الجهاز. هذه فرصة لاكتشاف bugs قبل النشر.

### 📋 الخطوات

**الخطوة A — بناء الـ APK (5 دقائق):**

افتح PowerShell أو CMD داخل مجلد المشروع `C:\Users\fadym\Videos\Code\2\Chat 13` ثم:

```powershell
./gradlew :app:assembleProdRelease
```

إذا أردتَ debug سريع بدلاً من release:
```powershell
./gradlew :app:assembleProdDebug
```

**الخطوة B — تثبيت على جهاز (دقيقة واحدة):**

1. وصِّل جهازك بـ USB ومكِّن "USB Debugging" في إعدادات المطوّر.
2. تأكّد أنّ الجهاز ظاهر:
   ```powershell
   adb devices
   ```
   يجب أن ترى سطرين: header + جهازك.
3. ثبّت:
   ```powershell
   adb install -r app/build/outputs/apk/prod/release/app-prod-release.apk
   ```
   (`-r` يعني "reinstall over existing")

**الخطوة C — التقاط 7 سكرين شت بالترتيب (10 دقائق):**

افتح التطبيق وخذ سكرين شت من كل من الشاشات التالية، احفظها بأسماء واضحة:

| # | الشاشة | الاسم المقترَح للملف |
|---|--------|-------------------|
| 1 | Onboarding أول شاشة | `01_onboarding_screen1.png` |
| 2 | Login أو Auth dialog | `02_auth_dialog.png` |
| 3 | **Home (Heute)** — Today list + Streak + Weather card | `03_home.png` |
| 4 | **Mein Garten** — الغرف + النباتات | `04_garden.png` |
| 5 | **Diagnose** — Disease activity | `05_diagnose.png` |
| 6 | **Einstellungen** — Settings (7 بطاقات) | `06_settings.png` |
| 7 | **UnifiedCamera** (اضغط FAB المركز) | `07_camera_fab.png` |
| 8 (اختياري) | Plant Detail dialog لأي نبتة | `08_plant_detail.png` |

طريقة سهلة في Android: زرّ Power + Volume Down في نفس الوقت.

**الخطوة D — مقارنة مع المتوقَّع:**

افتح `C:\Users\fadym\Downloads\PlantCare ScreenShots\` (المذكور في HANDOFF section 7) لو موجود.

لاحظ هذه التفاصيل تحديداً:
- **Typography:** هل العناوين تستخدم خط `Inter`؟ النصوص الصغيرة `Manrope`؟ النص الألماني واضح ومقروء؟
- **Cards elevation:** البطاقات لها ظلّ بسيط (1dp) — ليست مسطّحة 100%؟
- **Selected day في الـ Week bar:** أخضر مُمتلئ مع Inter SemiBold؟
- **Pro green color:** البطاقات/الأزرار خضراء `#2EB85F` صريحة (ليست خضراء باهتة)؟
- **Bottom Nav center FAB:** يبدو كأنّه "cradle" (نصف منقول لأعلى)؟
- **Card corners:** 20dp (لا 28dp أو 24dp قديمة)؟

### ✅ كيف تتأكد أنه نجح
الـ APK يُبنى بدون warnings جديدة، يُثبَّت بدون خطأ، التطبيق يفتح ويظهر Onboarding، الـ 7 شاشات تبدو متناسقة بصرياً.

### 📸 ماذا أحتاج منك
أرسل لي الـ 7-8 سكرين شت + اخبرني أي عنصر يبدو غريباً أو غير متوقَّع. لو كل شيء يبدو احترافياً، نمشي إلى البند 2. لو هناك عنصر مكسور، أحلّله وأقترح إصلاح.

---

## 2️⃣ — نشر firestore.rules و storage.rules إلى Firebase Console

### 🎯 ما المطلوب
رفع القواعد الأمنية الموجودة في ملفّي `firestore.rules` و `storage.rules` (محفوظة في الـ Git repo) إلى Firebase Console بحيث تطبَّق على المستخدمين الفعليين.

### 🤔 لماذا مهم
الملفّان موجودان في مجلد المشروع لكنّ Firebase لا يقرأ الكود من جهازك — هو يستخدم الـ rules المنشورة في Console. لو ما يزال عندك القواعد الافتراضية القديمة:
- كل cloud delete (حذف صورة، نبتة، memo) سيفشل بصمت ⇒ orphans في Firestore.
- ممكن أن يكون عندك "test mode" يسمح للجميع بالقراءة ⇒ خرق DSGVO خطير.

### 📋 الخطوات

**الطريقة A — عبر Firebase CLI (موصى بها، 5 دقائق):**

1. ثبّت Firebase CLI لو غير مثبَّت:
   ```powershell
   npm install -g firebase-tools
   ```
2. سجّل دخول:
   ```powershell
   firebase login
   ```
   يفتح متصفّحاً، سجّل دخول بنفس حساب Google الذي يملك Firebase project.
3. تأكّد من project الصحيح في مجلد المشروع:
   ```powershell
   cd "C:\Users\fadym\Videos\Code\2\Chat 13"
   firebase use --list
   ```
   يجب أن ترى Firebase project بالـ ID صحيح.
4. انشر:
   ```powershell
   firebase deploy --only firestore:rules
   firebase deploy --only storage
   ```
   كل أمر يستغرق 10-30 ثانية. سترى:
   ```
   ✔  Deploy complete!
   ```

**الطريقة B — عبر Firebase Console (يدوياً، 5 دقائق):**

1. اذهب إلى https://console.firebase.google.com
2. اختر project الـ PlantCare.
3. من القائمة الجانبية: **Firestore Database** → tab **Rules** → الصق محتوى `firestore.rules` (افتحه في Notepad وانسخه كاملاً) → **Publish**.
4. من القائمة الجانبية: **Storage** → tab **Rules** → الصق محتوى `storage.rules` → **Publish**.

### ✅ كيف تتأكد أنه نجح
1. في Firebase Console → Firestore → Rules، أعلى الصفحة يجب أن يظهر "Updated 18 May 2026" (التاريخ الحالي).
2. اختبار تطبيقي:
   - افتح التطبيق على الجهاز، احذف نبتة عندك.
   - في Firebase Console → Firestore Database، اذهب إلى `users/{uid}/plants` وتأكّد أنّ الـ doc انحذف فعلاً.

### 📸 ماذا أحتاج منك
لو سار كل شيء، أرسل لي screenshot من Firebase Console Rules tab يُظهر "Last published" timestamp. لو ظهر خطأ، انسخ نصّ الخطأ كاملاً.

---

## 3️⃣ — تأكيد اشتراك Gemini

### 🎯 ما المطلوب
معرفة هل أنت على Free tier (250 طلب/يوم) أم Paid tier (لا حدّ عملي) في Google AI Studio.

### 🤔 لماذا مهم
ميزة Disease Diagnosis تستخدم Gemini 2.5 Flash. على Free tier:
- **250 طلب/يوم** للمشروع كلّه (ليس لكل مستخدم).
- **10 طلب/دقيقة**.
- لو لديك 50 مستخدم نشط يفحصون 6 صور يومياً، استنفدت الحصّة.
- بعد الحدّ، كل طلب يفشل بـ 429.

### 📋 الخطوات

1. اذهب إلى https://aistudio.google.com
2. سجّل دخول بحساب Google الذي تستخدمه في مشروع Firebase.
3. اضغط على أيقونة الإعدادات أو على اسمك أعلى يمين الصفحة → **API Keys**.
4. ابحث عن المفتاح الذي اسمه أو وصفه يطابق `GEMINI_API_KEY` (الذي وضعتَه في `local.properties`).
5. تحقّق من الـ tier:
   - **Free tier:** ستظهر علامة "Free" أو حدّ "1500 RPD على gemini-2.0-flash" (الحدّ القديم) أو "250 RPD على gemini-2.5-flash" (الحدّ الجديد).
   - **Paid:** ستظهر "Pay-as-you-go" أو "Tier 1" أو ما شابه.
6. لتفعيل Paid (إذا قرّرت):
   - في AI Studio: زرّ "Set up billing" أو اذهب مباشرة إلى https://aistudio.google.com/app/billing
   - اربط حساب Google Cloud billing موجود أو أنشئ جديداً.
   - أضف بطاقة ائتمان.
   - **Gemini 2.5 Flash السعر تقريبياً:**
     - $0.30 لكل مليون input tokens (للنصوص).
     - $0.075 لكل مليون input images (لكل صورة تحويلها لـ tokens).
     - عملياً كل تشخيص ~$0.001 = 1000 تشخيص بـ 1 دولار.

### ✅ كيف تتأكد أنه نجح
في AI Studio → API Keys → تجد المفتاح + tier واضح.

لو على Paid، اطلب من Claude في المستقبل أن يدمج "rate limit handling" في `GeminiVisionService.kt` ليتعامل مع 429 بأناقة (يعرض رسالة للمستخدم بدل crash).

### 📸 ماذا أحتاج منك
screenshot من صفحة API Keys في AI Studio (مع تغطية المفتاح نفسه — أحتاج فقط نوعية الـ tier).

### 💡 توصية
لو ستطلق التطبيق هذا الشهر للسوق الألماني، **فعّل Paid tier**. مخاطرة "free runs out at 250" أكبر من 5-10 دولارات شهرياً متوقَّعة في البداية.

---

## 4️⃣ — وضع PLAY_SA_KEY في Firebase Functions secrets

### 🎯 ما المطلوب
رفع Service Account JSON إلى Firebase Functions كـ "secret" بحيث Cloud Function `verifyPurchase` يستطيع التحدّث مع Google Play Developer API للتحقّق من المشتريات.

### 🤔 لماذا مهم
بدون هذا الـ secret، Cloud Function ترجع `verified=false` لكل شراء ⇒ المستخدم يدفع لـ Pro لكن التطبيق لا يفعّل Pro ⇒ تذكرة دعم + استرداد.

### 📋 الخطوات (هذه عملية طويلة — خذ نفساً عميقاً، 30 دقيقة)

**الخطوة A — إنشاء Service Account في Google Cloud Console (10 دقائق):**

1. اذهب إلى https://console.cloud.google.com/iam-admin/serviceaccounts
2. تأكّد أنك في **نفس Google Cloud project** المرتبط بـ Firebase project للـ PlantCare (يظهر في القائمة العليا).
3. اضغط **+ CREATE SERVICE ACCOUNT** أعلى الصفحة.
4. املأ:
   - **Service account name:** `play-billing-verifier`
   - **Service account ID:** يُملأ تلقائياً.
   - **Description:** `Verifies Play purchases via Play Developer API for PlantCare verifyPurchase function`
5. اضغط **CREATE AND CONTINUE**.
6. في "Grant this service account access to project": اضغط **CONTINUE** بدون اختيار أي role (سنعطيها الصلاحيات من Play Console، ليس Cloud).
7. اضغط **DONE**.
8. الآن سترى Service Account في القائمة. اضغط على بريدها (`play-billing-verifier@...iam.gserviceaccount.com`).
9. اذهب إلى tab **KEYS** → **ADD KEY** → **Create new key** → اختر **JSON** → **CREATE**.
10. سيُحمَّل ملف JSON تلقائياً (مثلاً `plantcare-12345-abc123.json`). **احفظه في مكان آمن** — هذا يحوي private key لا يُعاد توليده.

**الخطوة B — منح Service Account صلاحيات Play Console (5 دقائق):**

1. اذهب إلى https://play.google.com/console
2. سجّل دخول بحساب Google الذي يملك Play Developer Account ($25 رسوم لمرة واحدة — انظر البند 7 أدناه إن لم يكن مفعَّلاً).
3. من القائمة الجانبية اليسرى (icon menu): **Setup** → **API access**.
4. في القسم **Service accounts**، ابحث عن البريد الذي أنشأته للتو (`play-billing-verifier@...`).
5. لو لم يظهر، اضغط **Link new service account** → أدخل البريد → **Link**.
6. اضغط **Manage Play Console permissions** أو **Grant access**.
7. اختر:
   - **App access:** Add → اختر PlantCare app.
   - **Account permissions:** 
     - ✅ View app information and download bulk reports
     - ✅ View financial data
   - **App permissions:**
     - ✅ View app information
     - ✅ Manage orders and subscriptions (مهم!)
8. اضغط **Save** أو **Invite user**.
9. **انتظر 24-48 ساعة** لتفعيل الصلاحيات (Play API caches permissions — قد يكون أسرع لكن آمنً انتظر).

**الخطوة C — رفع الـ JSON كـ Firebase secret (5 دقائق):**

1. افتح PowerShell في مجلد المشروع.
2. تأكّد أنّ Firebase CLI مثبَّت ومسجَّل دخول:
   ```powershell
   firebase --version
   firebase projects:list
   ```
3. حدّد project:
   ```powershell
   firebase use --add
   ```
   اختر PlantCare project من القائمة.
4. رفع الـ secret:
   ```powershell
   firebase functions:secrets:set PLAY_SA_KEY --data-file="C:\path\to\plantcare-12345-abc123.json"
   ```
   استبدل `C:\path\to\...` بالمسار الفعلي للـ JSON الذي حمّلتَه في الخطوة A.
5. سيظهر:
   ```
   Secret PLAY_SA_KEY created (version 1)
   ```

**الخطوة D — إعادة نشر الـ Cloud Function (5 دقائق):**

```powershell
firebase deploy --only functions:verifyPurchase
```

سيستغرق 1-3 دقائق. النتيجة المتوقَّعة:
```
✔  functions[us-central1-verifyPurchase] Successful update operation.
```

### ✅ كيف تتأكد أنه نجح

1. في Firebase Console → Functions → سترى `verifyPurchase` آخر deploy "today".
2. في Firebase Console → Functions → Secret Manager link → سترى `PLAY_SA_KEY` بنسخة 1.
3. اختبار تطبيقي (بعد 24h من Step B):
   - شراء Pro من Internal Testing track (انظر بند 7).
   - مراقبة Logs: في Firebase Console → Functions → Logs → ابحث عن `verifyPurchase`.
   - السطر يجب أن يكون: `verified=true isPro=true`.
   - في التطبيق: AdMob banner يختفي + Settings → Pro Hero تتغيّر إلى "Pro aktiviert".

### 📸 ماذا أحتاج منك

1. تأكيد أنّك أنشأت Service Account بنجاح (screenshot من قائمة Service Accounts).
2. تأكيد أنّك ربطتَ الـ SA في Play Console (لو لم يكن عندك Play Developer Account، انتقل للبند 7 أولاً).
3. أرسل output من `firebase functions:secrets:set` و `firebase deploy --only functions:verifyPurchase`.

### 💡 ملاحظة
ملف `BUG10_MANUAL_SETUP.md` في مجلد المشروع يحوي تفصيلات إضافية وdebug steps. اقرأه لو واجهتَ مشكلة.

---

## 5️⃣ — التأكد من OpenWeather API key

### 🎯 ما المطلوب
معرفة هل المفتاح موجود في `local.properties` وفعّال (مفتاح بعد التسجيل في OpenWeather يستغرق ~10 دقائق ليفعّل).

### 🤔 لماذا مهم
بدون مفتاح صحيح، `WeatherAdjustmentWorker` يطلب طقساً، يأخذ 401 Unauthorized، يحفظ زيارة فاشلة كل 12 ساعة. ميزة الطقس (`WeatherTipCard` في Home + تعديل التذكيرات تبعاً للطقس) تتعطّل بالكامل بصمت.

### 📋 الخطوات

**التحقّق من وجود المفتاح (دقيقة واحدة):**

1. افتح ملف `local.properties` في مجلد المشروع:
   ```powershell
   notepad "C:\Users\fadym\Videos\Code\2\Chat 13\local.properties"
   ```
2. ابحث عن السطر:
   ```
   OPENWEATHER_API_KEY=<some string>
   ```
3. **افحص:**
   - **لو السطر غير موجود** ⇒ لا مفتاح، احصل عليه من https://home.openweathermap.org/api_keys (Free tier 60 req/min كافٍ).
   - **لو السطر موجود وفيه string طويلة hex مثل `7a3b9c...`** ⇒ مفتاح موجود.
   - **لو السطر `OPENWEATHER_API_KEY=` (فارغ)** ⇒ لم تُكمل التسجيل.
   - **لو السطر `OPENWEATHER_API_KEY=YOUR_API_KEY_HERE`** ⇒ placeholder قديم، لم تستبدله.

**التحقّق من أنّه يعمل فعلاً (3 دقائق):**

نفّذ هذا في PowerShell، استبدل `KEY_HERE` بمفتاحك:
```powershell
curl "https://api.openweathermap.org/data/2.5/weather?q=Berlin&units=metric&appid=KEY_HERE"
```

**النتيجة الناجحة:**
```json
{"coord":{"lon":13.4105,"lat":52.5244},"weather":[{"id":...
```

**النتيجة الفاشلة:**
```json
{"cod":401,"message":"Invalid API key. Please see https://openweathermap.org/faq#error401 ..."}
```

### ✅ كيف تتأكد أنه نجح
- الـ curl يُرجع JSON يحوي `"name":"Berlin"`.
- في التطبيق (بعد إعطاء صلاحية Location): Home → بطاقة Weather تعرض اسم مدينتك + درجة حرارة + tip.

### 📸 ماذا أحتاج منك
لو الـ curl نجح، نمشي. لو فشل بـ 401 وأنت متأكّد أنّ المفتاح حديث، انتظر 10 دقائق ثم أعد المحاولة (OpenWeather يحتاج وقت لتفعيل المفتاح الجديد).

---

## 6️⃣ — اختبار سيناريو ترقية التطبيق

### 🎯 ما المطلوب
محاكاة "مستخدم لديه نسخة قديمة، يحدّث عبر Play Store إلى v1.0.0" وتأكيد أنّ بياناته لا تضيع.

### 🤔 لماذا مهم
الـ Audit في 28 أبريل وجد أنّ `SecurePrefsHelper.migrateIfNeeded()` كان يحذف `current_user_email` من prefs العادي لكن 23 موضع في الكود لا يزال يقرأ منه. تم إصلاحه بـ EmailContext unification. **لكن لم يُختبر السيناريو الفعلي على جهاز**. لو فيه bug، كل المستخدمين الحاليين سيُسجَّل خروجهم تلقائياً.

### 📋 الخطوات

**الخطوة A — احصل على نسخة قديمة (10 دقائق):**

تحتاج APK من قبل تنفيذ EmailContext unification. لديك خيارات:

1. **إذا عندك Git tag قديم:**
   ```powershell
   cd "C:\Users\fadym\Videos\Code\2\Chat 13"
   git tag --list
   ```
   لو هناك tag قبل `v1.0.0`، استخدمه:
   ```powershell
   git checkout v0.2  # مثلاً
   ./gradlew :app:assembleProdRelease
   move app\build\outputs\apk\prod\release\app-prod-release.apk %TEMP%\plantcare-v0.2.apk
   git checkout main  # ارجع
   ```

2. **إذا ليس عندك tag:**
   شغّل النسخة الحالية مرّتين — هذا لا يحاكي ترقية فعلية، فقط install قبل وبعد. أفضل خيار: استخدم تقدير زمني من Git:
   ```powershell
   git log --oneline --since="2026-04-01" --until="2026-04-25"
   ```
   اختر commit قبل 25 أبريل (قبل A1 fix):
   ```powershell
   git checkout <commit-hash>
   ./gradlew :app:assembleProdDebug
   ```

3. **بديل أبسط — افحص الكود مباشرة (10 دقائق، آمن):**
   في `MainActivity.java:81-82` و `PlantReminderWorker.java:71-72` (المواضع التي كانت معطّلة)، تأكّد أنّ القراءة الآن تتمّ عبر `EmailContext.current(context)` وليس `prefs.getString("current_user_email", null)`. لو كل المواضع موحّدة، السيناريو سيعمل نظرياً. (هذا أنا فحصتُه بالفعل وكل المواضع موحّدة — A1 ✅ في grep.)

**الخطوة B — السيناريو الفعلي (إذا اخترت Path 1 أو 2):**

1. ثبّت النسخة القديمة:
   ```powershell
   adb install %TEMP%\plantcare-v0.2.apk
   ```
2. افتح التطبيق على الجهاز:
   - أكمل Onboarding (Email أو Guest).
   - أضف نبتة واحدة.
   - أضف تذكير.
   - أَخرِج من التطبيق.
3. اقتل العملية:
   ```powershell
   adb shell am force-stop com.fadymerey.plantcare
   ```
4. ثبّت النسخة الجديدة فوقها (`-r` للترقية، ليس re-install):
   ```powershell
   adb install -r app/build/outputs/apk/prod/release/app-prod-release.apk
   ```
5. افتح التطبيق:
   - هل أنت لا تزال مسجَّلاً (لم تطلب login مرة أخرى)؟
   - هل النبتة التي أضفتَها موجودة؟
   - هل التذكير لا يزال مجدولاً؟
6. تأكّد من الإشعارات:
   ```powershell
   adb shell dumpsys jobscheduler | findstr plantcare
   ```
   يجب أن ترى `plant_reminder_work` مجدولاً.

**الخطوة C — التأكّد من الـ logs (5 دقائق):**

```powershell
adb logcat -d | findstr -i "SecurePref\|EmailContext\|migrate"
```

ابحث عن:
- `SecurePrefsHelper: migrated current_user_email` ⇒ سار صحيحاً.
- لا أخطاء مثل `NullPointerException` على `getCurrentUserEmail`.

### ✅ كيف تتأكد أنه نجح
بعد الترقية:
- المستخدم لا يزال مسجَّلاً (الإيميل ظاهر في Settings → Konto).
- النبتة + التذكير محفوظتان.
- في Home: streak + reminders تظهر صحيحة.

### 📸 ماذا أحتاج منك
لو فشل (سُجِّل خروجك مثلاً) أرسل لي:
1. screenshot من شاشة التطبيق بعد الترقية.
2. نسخة من logcat output (الأوامر أعلاه).

أنا أحلّل وأجد السبب.

---

## 7️⃣ — إعداد Google Play Console + 3 SKUs

### 🎯 ما المطلوب
حساب Google Play Developer + Play Console مفعَّل + 3 منتجات داخل التطبيق منشأة بالـ IDs المطابقة لما في الكود.

### 🤔 لماذا مهم
بدون هذه الخطوة، Paywall في التطبيق يفتح لكن لا يعرض منتجات (BillingClient يستفسر Play، Play يقول "لا أعرف هذه الـ SKUs"). المستخدم لا يستطيع شراء Pro.

### 📋 الخطوات

**الخطوة A — التأكّد من Play Developer Account (دقيقتان):**

1. اذهب إلى https://play.google.com/console
2. سجّل دخول بحساب Google.
3. لو هذه أول مرّة:
   - رسوم تسجيل **$25 لمرة واحدة** (طالما لم تُلغَ).
   - تأكيد الهوية (D-Verification أحياناً يلزم).
   - لو لم تُدفَع، Play Console يطلب الدفع قبل أي شيء.

**الخطوة B — إنشاء التطبيق في Console (5 دقائق):**

1. في Play Console، زرّ **Create app** أعلى يمين الصفحة.
2. املأ:
   - **App name:** PlantCare
   - **Default language:** German (Germany) — Deutsch
   - **App or game:** App
   - **Free or paid:** Free (الـ Pro هو in-app، التطبيق الأساسي مجاني)
   - أي checkbox مطلوب: ✅
3. اضغط **Create app**.

**الخطوة C — رفع أوّل AAB إلى Internal Testing (10 دقائق):**

> أنت تحتاج هذا قبل إنشاء الـ SKUs لأن Play Console يحتاج "active build" قبل قبول in-app products.

1. في القائمة الجانبية اليسرى: **Testing** → **Internal testing**.
2. اضغط **Create new release**.
3. زرّ **Upload** → اختر `app/build/outputs/bundle/prodRelease/app-prod-release.aab`.
4. Console قد يطلب signing key — إذا اختار "Use Play App Signing" (recommended):
   - رفع `upload_certificate.pem` المُستخرَج من keystore (مكتوب الإجراء في Phase E1 أو يتولّى Play تلقائياً).
5. **Release name:** `1.0.0-internal-1`
6. **Release notes:**
   ```
   First internal test release.
   - Bottom Navigation (Heute / Mein Garten / Diagnose / Einstellungen)
   - PlantNet + Gemini diagnosis
   - Plant Journal, Streaks, Vacation Mode
   ```
7. **Save** → **Review release** → **Start rollout to Internal testing**.
8. سيستغرق 5-20 دقيقة لـ Play لمعالجة الـ AAB قبل أن تستطيع متابعة الـ SKU step.

**الخطوة D — إعداد Internal Testers (3 دقائق):**

1. في Internal testing → tab **Testers** → **Create email list**.
2. اسم القائمة: `PlantCare Internal Testers`
3. أضف 5-10 بريدات (تقدر تبدأ بـ نفسك + 1-2 من الأصدقاء).
4. **Save**.
5. اذهب إلى tab **Releases** → **Edit release** → نسخ الـ **opt-in URL** → افتحها في متصفّح + سجّل بنفس البريد.
6. بعد دقيقة، APK يصير متاحاً للتنزيل من Play Store على الجهاز المسجَّل بهذا البريد.

**الخطوة E — إنشاء الـ 3 In-app Products (10 دقائق):**

> الإسماء أدناه يجب أن تطابق ما في `BillingManager.kt`. لو لا تذكر، افتح ملف وانظر إلى constants الـ SKU IDs.

1. في القائمة الجانبية: **Monetize** → **Products** → **In-app products**.
2. اضغط **Create product**.

**Product 1 — Lifetime (one-time):**
- **Product ID:** ابحث في الكود في `BillingManager.kt` عن string مثل `"plantcare_pro_lifetime"` — استخدم نفسه.
- **Name:** PlantCare Pro Lifetime
- **Description:** Einmaliger Kauf — alle Pro-Funktionen für immer
- **Price:** €39.99 (Germany)
- **Status:** Active
- **Save**.

3. ارجع إلى **In-app products** ⇒ **Create another**.

**Product 2 — Monthly subscription:**
- **Subscription** (ليس in-app product) ⇒ اذهب إلى **Subscriptions** tab بدلاً من products.
- **Subscription ID:** كما في الكود (مثل `"plantcare_pro_monthly"`).
- **Name:** PlantCare Pro Monatlich
- **Description:** Monatliches Abo — alle Pro-Funktionen
- **Base plan:** Monthly, €2.99
- **Save → Activate**.

**Product 3 — Yearly subscription:**
- نفس Product 2 لكن:
- **Subscription ID:** `"plantcare_pro_yearly"`
- **Name:** PlantCare Pro Jährlich
- **Base plan:** Yearly, €19.99
- **Save → Activate**.

### ✅ كيف تتأكد أنه نجح

1. Play Console → Monetize → In-app products: ترى 1 product نشط.
2. Play Console → Subscriptions: ترى 2 subscriptions نشطة.
3. اختبار حيّ:
   - افتح التطبيق على جهاز Internal Tester مسجَّل.
   - أضف 8 نباتات ثم حاول إضافة 9. يفتح Paywall.
   - Paywall يعرض **Monthly €2.99 / Yearly €19.99 / Lifetime €39.99**.
   - لو ظهرت الـ 3 منتجات بالأسعار الصحيحة ⇒ نجح.
   - لو ظهرت "Keine Produkte verfügbar" ⇒ مشكلة بـ Product ID mismatch، راجع `BillingManager.kt` IDs.

### 📸 ماذا أحتاج منك
1. screenshot من Paywall في التطبيق بعد إكمال الخطوات.
2. لو الـ Paywall فارغ، أرسل لي:
   - screenshot من Play Console → In-app products list.
   - محتوى `BillingManager.kt` companion object (Product IDs constants).
أحلّل التطابق وأشير إلى الـ mismatch.

### 💡 ملاحظة على Play Console SKUs
- **Subscription يحتاج "Active build" في tracks محدّدة** — بعض البلدان تتطلّب الـ Production track مفعّلاً قبل قبول subscriptions. لو واجهت ذلك، أنشئ Closed Testing track أيضاً.
- **License Tester** للشراء الاختباري بدون دفع فعلي:
  - Play Console → Settings → License testing → أضف نفس بريد Internal Tester.
  - الشراءات تظهر "Test card, always approves" وتُلغى تلقائياً بعد 7 أيام.

---

## 8️⃣ — Google Sign-In OAuth Client ID + SHA-1

### 🎯 ما المطلوب
تأكيد أنّ Google Sign-In في التطبيق يعمل عبر إعداد OAuth client ID في Firebase Console مع SHA-1 من الـ keystore الصحيح (Play App Signing certificate).

### 🤔 لماذا مهم
لو SHA-1 المُسجَّل في Firebase Console لا يطابق SHA-1 لـ keystore الذي وقّع الـ APK، Google Sign-In يفشل بـ `DEVELOPER_ERROR (10)`. المستخدم يضغط "Mit Google anmelden" فيظهر "Fehler" أو dialog أبيض.

### 📋 الخطوات

**الخطوة A — استخراج SHA-1 من keystore (دقيقتان):**

```powershell
cd "C:\Users\fadym\Videos\Code\2\Chat 13"
keytool -list -v -keystore plantcare-upload.jks -alias plantcare
```

أدخل كلمة سر keystore (مذكورة في `local.properties` → `KEYSTORE_PASSWORD`).

ابحث في الـ output عن سطر `SHA1:` مثل:
```
SHA1: A1:B2:C3:D4:E5:F6:...
```

انسخ القيمة كاملة.

**الخطوة B — أيضاً استخرج SHA-1 من debug keystore (دقيقة واحدة):**

كل Android Studio يولّد debug keystore تلقائياً للـ debug builds. هذا يلزم لو تشغّل debug builds مع Google Sign-In أيضاً:

```powershell
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey
```

كلمة السر الافتراضية: `android`

انسخ SHA-1 الثاني.

**الخطوة C — مهم جداً: SHA-1 من Play App Signing (إذا Play يوقّع نيابة عنك) (3 دقائق):**

لو رفعت الـ AAB إلى Play Console واخترت "Use Play App Signing" (الـ recommended)، Play يولّد SHA-1 مختلف للـ release build:

1. اذهب إلى Play Console → اختر التطبيق → **Setup** → **App integrity**.
2. في قسم **App signing**, تأكّد أنّ Play App Signing مفعَّل.
3. في القسم نفسه، ابحث عن **App signing certificate** → انسخ **SHA-1 certificate fingerprint**.

هذا الـ SHA-1 (Play's) **مختلف** عن SHA-1 الذي استخرجتَه من `plantcare-upload.jks` (upload key).

**الخطوة D — إضافة SHA-1s إلى Firebase Console (5 دقائق):**

1. اذهب إلى https://console.firebase.google.com → اختر PlantCare project.
2. ⚙️ (Settings icon) → **Project settings**.
3. tab **General**.
4. اقذف إلى أسفل: قسم "Your apps" → اختر التطبيق Android (`com.fadymerey.plantcare`).
5. اضغط **Add fingerprint**.
6. أضف الثلاث SHA-1s واحداً واحداً:
   - Upload keystore SHA-1 (من Step A)
   - Debug keystore SHA-1 (من Step B)
   - Play App Signing SHA-1 (من Step C)
7. لكل واحد، اضغط **Save**.
8. تنزيل `google-services.json` الجديد (يظهر زرّ في أعلى يمين الصفحة بعد التغيير) → ضعه في `app/google-services.json` يستبدل القديم.

**الخطوة E — Re-build:**

```powershell
./gradlew :app:assembleProdRelease
```

ارفع AAB الجديد إلى Internal Testing (نسخة جديدة).

### ✅ كيف تتأكد أنه نجح

1. ثبّت APK من Internal Testing على جهاز.
2. افتح التطبيق → AuthStartDialog → اضغط **Mit Google anmelden**.
3. Account chooser يفتح → اختر حسابك → النتيجة:
   - **نجاح:** يفتح Home + Settings → Konto يعرض الإيميل + Auth provider "Google".
   - **فشل DEVELOPER_ERROR:** SHA-1 mismatch لا يزال موجود. أعد فحص الـ 3 SHA-1s.

### 📸 ماذا أحتاج منك
لو فشلت، أرسل لي:
1. الـ 3 SHA-1 strings (آمنة، ليست private).
2. screenshot من Firebase Console → Project settings → Android app → fingerprints list.
3. logcat output:
   ```powershell
   adb logcat -d | findstr -i "GoogleSignIn\|GMS\|10:"
   ```

---

## 9️⃣ — Privacy Policy

✅ **مكتمل** حسب إجابتك. لا حاجة لخطوات إضافية.

ملاحظة: تأكّد فقط من أنّ الـ URL `https://fadymereyfm-collab.github.io/plantcare-privacy/` يعمل (افتحه في متصفّح). لو أعطى 404، GitHub Pages قد لا يكون مفعَّلاً.

---

## 🔟 — دعم لغات إضافية (Arabic + غيرها)

### 🎯 ما المطلوب
إضافة لغات إضافية إلى التطبيق (Arabic أولاً، ثم غيرها).

### 🤔 ما الذي يلزم لإضافة لغة جديدة

لكل لغة، تحتاج 4 أشياء:

1. **`values-XX/strings.xml`** — كل الواجهات (~855 string).
2. **`values-XX/strings_disease.xml`** — Disease labels.
3. **`values-XX/notifications.xml`** — رسائل الإشعارات (~40 رسالة).
4. **عمود LANG في `plants.csv`** — أعمدة `name_ar`، `lighting_ar`، `soil_ar`، `fertilizing_ar`، `watering_ar`.
5. **`locale_config.xml`** — تسجيل اللغة الجديدة.
6. **`build.gradle: resConfigs`** — إضافة الـ code.
7. **`SettingsFragment.kt`** — إضافة اللغة في Language picker.

### 📋 خطّة العمل المقترحة (3-5 أيام لكل لغة)

**Sprint A — Arabic (5 أيام):**

1. **يوم 1:** إنشاء `values-ar/strings.xml` + ترجمة 855 string. أسرع طريق: استخدام Google Translate API أو ChatGPT/Claude لترجمة دفعات + مراجعة يدوية للـ technical terms. النقطة الحرجة: ترجمة "Pflanze" بـ "نبات" أو "نبتة" — اختر واحدة والتزم.

2. **يوم 2:** ترجمة `notifications.xml` (40 رسالة Duolingo-style) — أصعب لأنّها تتطلّب humor + tone مناسب للعربي. خذ وقتاً هنا.

3. **يوم 3:** إضافة أعمدة `name_ar` إلخ إلى `plants.csv` لـ 505 نبتة. أسرع طريق: استخدام Python script يقرأ CSV ويستدعي translation API. Claude يكتب الـ script.

4. **يوم 4:**
   - تحديث `locale_config.xml`: أضف `<locale android:name="ar"/>`.
   - تحديث `app/build.gradle:26`: `resConfigs "de", "en", "ar"`.
   - تحديث `PlantCatalogLookup.kt` ليقرأ العمود بالـ language الحالية:
     ```kotlin
     val nameCol = when (Locale.getDefault().language) {
         "ar" -> "name_ar"
         "en" -> "name_en"
         else -> "name"  // DE default
     }
     ```
   - إضافة "العربية" إلى Language picker في `SettingsFragment.kt`.

5. **يوم 5:** اختبار يدوي — تغيير اللغة في الـ Settings → كل النصوص تتحوّل عربياً + RTL يعمل تلقائياً (لأنّ Manifest فيه `supportsRtl="true"` ✅) + النصوص العربية لا تُقطع.

**Sprint B — لغات أخرى (يوم لكل واحدة):**

بعد كتابة Arabic، الإطار جاهز. كل لغة جديدة (FR، ES، IT، NL، إلخ) تأخذ يوماً واحداً إذا استخدمت AI translation + مراجعة.

### 💡 توصية الأولوية للغات
بناءً على سوق Android في أوروبا:

| اللغة | الأولوية | السبب |
|------|----------|--------|
| Arabic (ar) | 1 | طلبك المباشر + جالية كبيرة في DE/AT |
| English (en) | ✅ موجودة | السوق العالمي + tourists |
| French (fr) | 2 | السوق الفرنسي + Quebec |
| Spanish (es) | 3 | LatAm + Spain |
| Italian (it) | 4 | السوق الإيطالي |
| Turkish (tr) | 5 | جالية كبيرة في DE |
| Polish (pl) | 6 | الجوار الألماني |
| Dutch (nl) | 7 | NL + BE |

### 📸 ماذا أحتاج منك
لو أردتَ أن أبدأ الـ Sprint A الآن، فقط قل "ابدأ Arabic". سأنفّذ Sprint A خطوة بخطوة عبر Claude Code:
1. أكتب Python script لـ batch translation لـ strings.xml.
2. أراجع الـ output ترجمة بترجمة وأصحّحه.
3. أعدّل `plants.csv` و `PlantCatalogLookup` و `locale_config` و `build.gradle`.
4. أعطيك build جديد للاختبار.

---

## 1️⃣1️⃣ — G10.3 = "Lottie animations + haptics" بلغة بسيطة

### 🎯 ماذا يعني هذا
- **Lottie:** صور متحرّكة خفيفة الحجم (مثل GIF لكن أحدث وأنظف). تُستخدم في تطبيقات احترافية مثل Instagram، Airbnb، Headspace. مثال: نبتة تنمو، confetti عند نجاح، loading spinner مخصّص بشكل ورقة شجر.
- **Haptics:** اهتزاز خفيف من الجهاز عند اللمس. أمثلة:
  - عند ضغط زرّ "Mark Watered" يهتزّ الهاتف خفيفاً (50ms).
  - عند إنجاز Challenge، اهتزازة مزدوجة.
  - عند سحب يميناً لحذف شيء، اهتزازة قصيرة.

### 🤔 لماذا "آخر 10% premium"
التطبيقات العادية لها وظائف تعمل لكنّها "تبدو مسطّحة". التطبيقات الـ premium (Headspace، Calm، Duolingo) لها animations + haptics + transitions ناعمة. هذا الفرق بين تطبيق "يعمل" وتطبيق "يُحبّه المستخدم ولا يحذفه".

### 📋 الخيارات المتاحة لك

**خيار A — تأجيل بعد الإطلاق (موصى به):**
- النشر الآن **بدون** animations.
- بعد 1-2 شهر، عند جمع feedback، أضفها في v1.1.
- التكلفة: 0 الآن، 2-3 أيام عمل في v1.1.

**خيار B — إضافة الآن قبل النشر:**
- أنشئ 3-5 Lottie animations:
  - Welcome screen (نبتة تنمو).
  - Empty state للحديقة الفارغة.
  - Loading spinner.
  - Success confetti عند streak milestone.
- أضف haptic feedback في 5-10 نقاط تفاعل.
- التكلفة: 2-3 أيام عمل + 50-150€ لشراء Lottie files أو ساعة عمل designer.

**خيار C — نهائياً إلغاؤها:**
- التطبيق ينشَر "كما هو" نظيف لكن بدون "premium polish".
- يبقى ممتازاً بحدّ ذاته لكن لا يتنافس مع تطبيقات مثل Greg أو Planta على "feel".

### 💡 توصيتي
**خيار A — تأجيل.** الأولويّة الآن إكمال البنود 1-8 + نشر. بعدها، لو reviews تذكر "feels basic" أو "could be more polished"، اعمل G10.3 في v1.1.

### 📸 ماذا أحتاج منك
قل لي:
- A (تأجيل)، B (الآن)، C (إلغاء نهائي).

---

## 1️⃣2️⃣ — Designer Freelance بلغة بسيطة

### 🎯 ماذا يعني
استئجار **مصمّم UI/UX (graphic designer متخصّص في تطبيقات الموبايل)** لمدّة 3-5 أيام ليصمّم لك:

1. **3D illustrations:** صور 3D مرسومة بـ Blender أو Spline تستبدل أيقونات flat. مثل:
   - شخصية تحمل أصيص (Welcome).
   - نبتة كرتونية حزينة (Empty state).
   - حشرة قاتلة لطيفة (Disease selected).
2. **UnifiedCamera FAB raster:** الأيقونة في وسط Bottom Navigation الآن "vector outline" بسيطة. مصمّم يعمل أيقونة كاميرا بنمط 3D ميني أكثر جاذبية.
3. **Lottie animations files:** الـ animations المذكورة في 11 — مصمّم يصمّمها في After Effects ويصدّر JSON.
4. **Settings hero photography:** صورة عالية الجودة لنبتة في إناء أعلى صفحة Settings → Pro Hero.

### 🤔 لماذا
بدون هذا، التطبيق يبقى "code-only" — يعمل بشكل ممتاز لكنّه يبدو "made by a developer" بدلاً من "made by a design-driven product team". الفرق المالي = double revenue per user تقريباً (User Acquisition cheaper + Lifetime Value أعلى لأنّ المستخدمون يحبّونه أكثر).

### 📋 الخطوات لو قرّرت أن تستأجر

**الخطوة A — أين تجد المصمّم:**
- **Upwork:** ابحث "Android app UI designer" + filter بـ "Top Rated" + متاح للـ <$30/hour.
- **Dribbble:** ابحث "Plant app design" + بريد إلى أصحاب البروفايلات الممتازين.
- **Fiverr:** لو ميزانية ضيّقة، باقات من $100 لكن جودة متغيّرة.
- **توصية شخصية:** Upwork أكثر موثوقية لمشروع 3-5 أيام.

**الخطوة B — ما تطلب منه (Brief):**
```
Hi [Name],

I'm building "PlantCare" — an Android app for home plant care (lifecycle, watering, disease diagnosis with AI). Currently in beta, launching in Germany next month.

I need a designer for 3-5 days of work to elevate the "feel" of the app from "developer-made" to "premium":

1. 5 hero illustrations (3D style preferred, like Notion or Linear):
   - Welcome screen: someone holding a potted plant
   - Empty garden state: a single happy plant waving
   - Empty diagnosis state: a healthy leaf
   - Success state: confetti + plant
   - Pro upgrade hero: lush indoor jungle

2. Custom camera FAB icon (round, 3D, replaces current vector outline)
3. 3 Lottie animations (JSON exports):
   - Plant growing (used in Welcome)
   - Loading spinner (custom plant theme)
   - Confetti burst (for streak milestone)

Style references: Greg (greg.app), Planta (getplanta.com), Notion 3D illustrations.

Color palette:
- Primary: #2EB85F (vivid green)
- Accent: similar to Material green-700
- Background: white + #F5FAF6 (light green tint)

Format deliverables:
- Illustrations: PNG @2x and @3x + SVG source if vector
- Camera FAB: PNG @1x, @1.5x, @2x, @3x + SVG
- Lottie: JSON files validated on lottiefiles.com

Budget: $300-500 total, payable on milestone delivery.
Timeline: 3-5 calendar days.

Sample of current app UI: [attach 3 screenshots]
```

**الخطوة C — التكلفة المتوقَّعة:**
- على Upwork: $200-400 لمصمّم متوسط بجودة عالية.
- على Dribbble (designers مشهورين): $500-1500.
- على Fiverr: $50-150 (مخاطرة جودة أعلى).

**الخطوة D — كم يأخذ:**
- المصمّم: 3-5 أيام عمل.
- أنا (Claude في جلسة لاحقة): يوم لدمج الـ assets في الكود.
- إجمالي: 4-6 أيام calendar.

### 📋 خيارات بديلة (أرخص)

**خيار A — استخدام Lottie Files مجانية:**
- موقع https://lottiefiles.com فيه آلاف animations مجانية.
- ابحث "plant growing"، "confetti"، "loading spinner".
- 0 تكلفة + 30 دقيقة عمل.
- جودة 60-70% من المخصّصة.

**خيار B — Adobe Stock للـ illustrations:**
- اشتري 5 illustrations 3D من Adobe Stock (~$30 لكل واحدة، subscription $30/شهر للـ 10 assets).
- مزجها مع UI الحالي.
- جودة 80% من المخصّصة بـ ~$150.

**خيار C — أنا أرسم بـ AI:**
- يمكنني توليد illustrations بـ DALL-E أو Midjourney عبر API.
- جودة 70-80%، تكلفة 1-5€ لكل image.
- لكن لا أملك Midjourney/DALL-E بشكل مباشر في هذه الجلسة.

### 💡 توصيتي
**خيار B (Adobe Stock) + خيار A (LottieFiles)** = $150 + 0 = توفير ~$200 مقارنة بـ freelancer. الجودة 80%، كافٍ للإطلاق.

**أو:** أَجِّل التصميم لـ v1.1 بعد جمع revenue من Internal/Open Beta.

### 📸 ماذا أحتاج منك
قل لي:
- "استئجر designer" → أساعدك في كتابة الـ brief + اختيار profile.
- "Adobe Stock + LottieFiles" → أحدّد لك URLs محدّدة وأكتب كود الدمج.
- "أجّل لـ v1.1" → ننسى الموضوع الآن.

---

## 1️⃣3️⃣ — Internal Testing / Open Beta / Production Rollout بلغة بسيطة

### 🎯 ما الذي يحدث عندما تنشر تطبيقاً
Google Play لا تسمح بنشر "محاولة أولى" مباشرة لكل العالم. الأسلوب الصحيح **التدريج** على 3 مراحل لاكتشاف bugs دون كارثة:

### 1️⃣ Internal Testing (5-10 mistesters، 3-7 أيام)

**ما هو:**
- مرحلة "بين الأصدقاء". 5-10 أشخاص تعرفهم (أنت + شريكتك + 3 أصدقاء developers أو early adopters).
- يثبّتون التطبيق من Play Store (ليس APK مباشر) — يحاكي تجربة المستخدم الحقيقية.
- لا أحد آخر يستطيع تنزيل التطبيق.

**ماذا يفعلون:**
- يستخدمون التطبيق يومياً 3-7 أيام.
- يرسلون لك feedback على bugs، تجربة UX، شعور عام.
- يشترون Pro لاختبار Paywall (لو فعّلت License Tester، لن يُدفَع فعلياً).

**لماذا مهم:**
- يكتشف bugs لا يكتشفها build verification (مثل: نسيت SHA-1، Paywall بدون products، Sign-In broken).
- إذا انكسر شيء، يؤثّر على 10 أشخاص لا 1000.

**كيف:**
1. Play Console → Testing → Internal testing → Create release → Upload AAB.
2. Testers tab → Add 5-10 emails.
3. شارك "opt-in URL" مع الـ testers.
4. هم يفتحون URL → يضغطون "Become a tester" → بعد دقيقة، التطبيق يظهر في Play Store على هواتفهم.
5. اجمع feedback لمدّة 3-7 أيام.

### 2️⃣ Open Beta — Germany only (أسبوعان)

**ما هو:**
- مرحلة "بين الناس". أي شخص في ألمانيا يستطيع الانضمام للـ Beta من Play Store.
- يظهر التطبيق في Play Store البحث، لكن مع label "Beta".
- محدود بألمانيا (لو حدّدت ذلك).

**ماذا يفعلون:**
- نفس Internal لكن بحجم أكبر (10-100 شخص).
- ربّما تجد bugs مختلفة لأنّ أجهزتهم أحدث أو أقدم.

**لماذا مهم:**
- آخر فرصة قبل النشر الفعلي.
- تجمع Crashlytics + Play Console Pre-launch reports.
- لو ظهر crash على Samsung الجديد، تكتشفه قبل ما يكسر التطبيق على 1000 مستخدم.

**كيف:**
1. Play Console → Testing → Open testing → Create release.
2. Upload AAB (نفس الـ AAB أو نسخة جديدة بإصلاحات Internal).
3. Country availability → Add Germany.
4. أيّ شخص يفتح Play Store في ألمانيا ويبحث "PlantCare" يرى الـ Beta + يستطيع الانضمام.

### 3️⃣ Production Rollout (10% → 50% → 100% خلال 3-5 أيام)

**ما هو:**
- النشر الفعلي. التطبيق يصير متاحاً لكل العالم (أو الـ countries التي اختَرتها).
- لا تنشره فجأة لـ 100% من المستخدمين. ابدأ بـ 10% فقط.

**ماذا يحدث:**
- **اليوم 1 (10% rollout):** فقط 10% من المستخدمين الذين يبحثون عن التطبيق على Play Store يحصلون عليه. الـ 90% الباقون يرون "غير متاح".
- **اليوم 2:** راقب Crashlytics + Play Console Reviews + Vitals. لو لا crashes عالية، انتقل إلى 50%.
- **اليوم 3-4 (50% rollout):** نصف الناس يحصلون على التطبيق. مراقبة مكثّفة.
- **اليوم 5 (100% rollout):** كل العالم.

**لماذا مهم:**
- لو ظهرت كارثة (مثل crash كل لمسة) عند 10%، تتأثّر 100 مستخدم فقط لا 100,000.
- تستطيع "إيقاف الـ rollout" من Play Console في 5 دقائق.

**كيف:**
1. Play Console → Production → Create release.
2. Upload AAB.
3. **Rollout percentage:** 10%.
4. Review release → Start rollout.
5. بعد 24-48 ساعة من الرقابة، Production → Edit release → Update rollout percentage → 50%.
6. كرّر بعد 24-48 ساعة → 100%.

### 📋 إذا أردت Timeline موجز

```
أسبوع 1 — تجهيز:
  - يوم 1-2: إكمال البنود 1-8 (Build verify، Firebase rules، PLAY_SA_KEY، OpenWeather، Play SKUs، Google Sign-In).
  - يوم 3-7: Internal Testing (5-10 testers).

أسبوع 2-3 — Open Beta:
  - يوم 8-21: Open Beta Germany.
  - جمع Crashlytics + 5-stars.

أسبوع 4 — Production:
  - يوم 22: 10% rollout.
  - يوم 23-24: مراقبة.
  - يوم 25: 50% rollout.
  - يوم 26-27: مراقبة.
  - يوم 28: 100% rollout.
```

شهر واحد من اليوم الذي تنتهي فيه من البنود 1-8.

### 💡 ملاحظة مهمّة
- **Play Console يفرض 14 يوم على الـ Closed/Open Beta** قبل الإنتقال لـ Production لتطبيق جديد. هذا قانون Google.
- **لا تَرفع Production AAB لـ pre-launch** قبل ما تنشر Internal Testing على الأقل لـ 24 ساعة (Play يطلب).
- لو فيه CRITICAL bug ظهر في 50% rollout، **أوقف الـ rollout** فوراً + ارفع AAB إصلاحي + استأنف.

### 📸 ماذا أحتاج منك
لا شيء الآن. عندما تصل إلى مرحلة Internal Testing (بعد البنود 1-8)، تواصل معي وأقودك خطوة بخطوة.

---

## 🎯 الترتيب الموصى به للتنفيذ

استناداً إلى الـ dependency graph، إليك الترتيب الأمثل:

### الأسبوع الأول (أيام 1-7) — التحقّق التشغيلي

| # | المهمة | الوقت | البنود في هذا الدليل |
|---|--------|-------|---------------------|
| 1 | Build verification بصرياً + screenshots | 1h | البند 1 |
| 2 | نشر Firebase rules | 10 min | البند 2 |
| 3 | تحقّق OpenWeather key | 5 min | البند 5 |
| 4 | تحقّق Gemini tier + تفعيل Paid لو لزم | 30 min | البند 3 |
| 5 | تجهيز Google Play Developer Account ($25) | 1 day (intake) | البند 7 الخطوة A |
| 6 | إنشاء PlantCare app في Play Console | 30 min | البند 7 الخطوة B |
| 7 | رفع أوّل AAB إلى Internal Testing track | 30 min | البند 7 الخطوة C |
| 8 | إنشاء Service Account + ربط Play Console | 1h | البند 4 الخطوات A-B |
| 9 | تنزيل/نقل SHA-1s إلى Firebase Console | 30 min | البند 8 |
| 10 | إنشاء 3 SKUs (1 product + 2 subscriptions) | 30 min | البند 7 الخطوة E |
| 11 | رفع `PLAY_SA_KEY` كـ Firebase secret + redeploy function | 30 min | البند 4 الخطوات C-D |
| 12 | اختبار upgrade scenario | 1h | البند 6 |

**في نهاية الأسبوع الأول:** التطبيق على Internal Testing track، 5-10 testers يستخدمونه.

### الأسبوع الثاني (أيام 8-14) — Internal Testing
- جمع feedback، إصلاح bugs، إصدار نسخة جديدة.
- في أيام 12-14: إذا لا crashes، انتقل إلى Open Beta.

### الأسبوع 3-4 — Open Beta
- ألمانيا فقط، 14 يوم.

### الأسبوع 5 — Production
- 10% → 50% → 100% خلال 3-5 أيام.

**النشر الكامل:** خلال **شهر واحد** من اليوم.

---

## 📞 كيف نعمل معاً من الآن

بعد قراءتك هذا الدليل، أَخبِرني:

1. **بنود تحتاج توضيح إضافي؟** (مثلاً: لا أفهم Service Account)
2. **بنود تريد البدء بها فوراً؟** (مثلاً: "ابدأ بـ 1 + 2 الآن")
3. **بنود تريد تأجيلها؟** (مثلاً: "أجِّل Arabic لـ v1.1")
4. **هل تريدني أن أكتب أكواد محدّدة الآن؟** (مثلاً: "اكتب لي Python script لـ Arabic translation")

أنا متاح. تواصل خطوة بخطوة وأرشدك.

---

*انتهى الدليل التشغيلي. أتمنّى رحلة نشر هادئة!*
