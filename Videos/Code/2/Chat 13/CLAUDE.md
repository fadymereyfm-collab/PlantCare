 Read PlantCare_Features_Inventory.md before any feature work
# PlantCare — Claude Code Instructions

> هذا الملف يُحمَّل تلقائياً في كل جلسة Claude Code داخل هذا المجلد.
> لا تتجاهل تعليماته، حتى لو طلب المستخدم العمل في طبقة لاحقة.

---

## 0 — الهوية والمستندات المرجعية

أنت تعمل على تطبيق **PlantCare Android** (Java + Kotlin، Room، Firebase، MVVM، تطلّق على متجر Google Play، السوق الألماني، نموذج Freemium).

**اقرأ هذه الملفات في كل جلسة جديدة قبل اتخاذ أي قرار:**

1. **`PlantCare_Pre_Release_Audit.md`** — مصدر الحقيقة لما يخصّ الأمن والبنية والنشر. مرتّب بمراحل A → E. اقرأه كاملاً (لا تكتفِ بـ Phase الحالية).
2. **`PlantCare_Functional_Report.md`** ⭐ — تقرير وظائف التطبيق (29 أبريل 2026). **مصدر الحقيقة لكل ما يخصّ الوظائف، الميزات، الأخطاء المرئية للمستخدم، ومتطلّبات الميزات الجديدة** (مثل Plant Journal، إصلاح عرض الصور، wateringInterval بعد PlantNet، تفعيل Disease Diagnosis، أيقونات Toolbar). اقرأه كاملاً قبل أي مهمة من نوع "إصلاح ميزة" أو "إضافة ميزة".
3. **`PROGRESS.md`** — السجلّ الزمني للجلسات السابقة. اقرأ آخر 200 سطر.
4. **`PlantCare_Action_Plan.md`** — الخطة الأصلية (مرجع تاريخي فقط — معايير قبولها لا تزال صالحة).
5. **`GEMINI.md`** — معلومات قديمة عن البنية، **لا تتّبعه** كقاعدة (مثلاً يقول "use SharedPreferences" وهذا نُسخ بـ SecurePrefsHelper).

في حال تعارض هذه الملفات:
- **للأمن/البنية/النشر:** Audit > Functional Report > PROGRESS > Action Plan > GEMINI.
- **للوظائف/الميزات/UI bugs:** Functional Report > Audit > PROGRESS > Action Plan > GEMINI.

---

## 1 — قاعدة ذهبية صارمة: لا ادّعاء بدون دليل grep

تقارير الجلسات السابقة ادّعت "10 استدعاءات DAO خارج repository" بينما الواقع 102. هذه الأخطاء أضاعت أسابيع.

**قبل وضع علامة "مكتمل" على أي مهمة:**

1. شغّل أمر التحقّق المذكور في معايير القبول داخل `PlantCare_Pre_Release_Audit.md`.
2. **انسخ ناتج الأمر حرفياً** إلى Session entry في PROGRESS.md.
3. إذا فشل أمر التحقّق ⇒ المهمة غير مكتملة. أعِدها إلى pending. لا تُجمِّل.
4. صنّف كل ميزة بأحد الأربعة فقط: **مكتملة / شبه مكتملة / شكلية فقط / مكسورة**. لا تستخدم "موجودة" أو "تقريباً".

أمثلة على grep-evidence المطلوب:

| المهمة | أمر التحقّق | الناتج المقبول |
|--------|-------------|----------------|
| A1 (SecurePrefs unify) | `grep -rn '"current_user_email"' app/src/main/java` | فقط `SecurePrefsHelper.kt` و `EmailContext.kt` |
| C2 (DAO out of UI) | `grep -rn "AppDatabase.getInstance\|DatabaseClient\." app/src/main/java/com/example/plantcare/ui/` | 0 |
| C4 (no new Thread in UI) | `grep -rn "new Thread(" app/src/main/java/com/example/plantcare/ui/ app/src/main/java/com/example/plantcare/weekbar/` | 0 |
| B1 (no resConfigs de) | `grep -n 'resConfigs' app/build.gradle` | لا نتيجة |
| A3 (no hardcoded weather key) | `grep -rn "YOUR_API_KEY_HERE" app/src/main/java` | 0 |
| A4 (TFLite present) | `ls -la app/src/main/assets/plant_disease_model.tflite` | الملف موجود |

---

## 2 — ترتيب التنفيذ صارم

**لا تقفز** من مرحلة إلى أخرى. الترتيب:

```
Phase F (Functional bugs)  →  Phase A متبقّي  →  Phase B (i18n)  →  Phase C (Architecture)  →  Phase D (QA)  →  Phase E (Release)
```

> **تحديث 2026-04-29:** المستخدم وجّه أن تكون **إصلاحات الميزات الوظيفية المرئية** (انظر `PlantCare_Functional_Report.md` قسم 6) **ذات أولوية حادّة قبل** متابعة باقي المراحل، لأن التطبيق حالياً فيه أخطاء عرض صور + ميزات معطّلة + ميزة جديدة مطلوبة (Plant Journal).

**Phase F — ترتيب موصى به (من Functional Report قسم 6):**
1. F1: إصلاح عرض الصور في Today list (`CalendarPhotoGridCompose.kt`).
2. F2: إصلاح عرض الصور في Archive (`ArchivePhotosDialogFragment.java`).
3. F3: تفعيل Disease Diagnosis (نقل tflite + استبدال Toast بـ Intent).
4. F4: تغيير `ic_disease.xml` لأيقونة معبّرة عن الفحص الصحي.
5. F5: إصلاح wateringInterval بعد PlantNet (CSV column + defaults + draft passing).
6. F6-F8: تحسين Weather adjustment.
7. F9-F11: Plant Journal (Schema migration + ViewModel + UI).
8. F12-F15 (تأجيل ممكن لـ 1.1): كشف ميزات Vacation/Streak/Family Share/Memoir-PDF.

**داخل Phase A المتبقّي، الترتيب الموصى به: A1 → A3 → A2 → A5 → A4** (سبب: A1 يحلّ أعطالاً جذرية تُؤثّر على بقية المهام).

قبل الانتقال من مرحلة لأخرى، تحقّق من **كل** "معايير القبول" المذكورة في تلك المرحلة في Audit أو Functional Report. إن تعذّرت إحدى الخطوات، **اطلب قراراً من المستخدم**، لا تتخطّاها.

---

## 3 — قواعد البناء والاختبار

### Build verify بعد كل مهمة
```bash
./gradlew assembleProdRelease
```
- يجب أن ينجح.
- **لا تسمح بزيادة عدد warnings** عمّا كان قبل المهمة. إذا زاد، أصلح أو أعِد المهمة pending.

### Test verify بعد كل مهمة في طبقة data/repository أو data/db
```bash
./gradlew test --rerun-tasks
```

---

## 4 — قواعد الكود

### اللغة
- **كود جديد بـ Kotlin** فقط.
- لا تحوّل ملفات Java الموجودة إلى Kotlin إلا إذا كانت المهمة تطلب ذلك صراحة.

### Threading
- لا `new Thread()`. استخدم `viewModelScope.launch(Dispatchers.IO)` أو `Executors.newSingleThreadExecutor()` للـJava.
- لا `AsyncTask`.

### Error handling
- لا `catch (Throwable ignored) {}`. استبدل بـ `catch (e: Throwable) { CrashReporter.log(e) }`.
- إن كان الاستثناء متوقّعاً ولا يحتاج إبلاغاً: علّق `// expected: <reason>` بدلاً من `ignored`.

### Persistence
- **بيانات حسّاسة (email, is_guest, tokens):** `SecurePrefsHelper.get(context)` فقط.
- **غير حسّاس (theme, last_widget_refresh):** `getSharedPreferences("prefs", MODE_PRIVATE)` مقبول.
- لا تكتب `current_user_email` إلى `prefs` العادي تحت أي ظرف.

### Reactive UI
- DAO methods الجديدة تُرجع `LiveData<List<X>>` أو `Flow<List<X>>` — **ليس `List<X>`**.
- لا `liveData { emit(dao.xxx()) }` builders جديدة (تنتج LiveData تبثّ مرّة واحدة).

### Repositories
- ViewModel جديد لا يستدعي `AppDatabase.getInstance` أبداً. يستقبل Repository في الـconstructor.

---

## 5 — قواعد التسجيل في PROGRESS.md

بعد كل مهمة منجزة، أضِف entry في الأعلى بنفس صيغة الجلسات الموجودة:

```markdown
## Session: <date> (Phase <X>.<n> — <task name>)
### Task Completed: <task code> — <description>
### Layer: Phase <letter>
### Evidence:
  - <files changed>
  - <grep verification command and output>
  - Acceptance criteria checklist:
    - [✅] criterion 1 (<grep result>)
    - [✅] criterion 2
### Build Status: ✅ assembleProdRelease passed (Xm Ys)
### Regressions: <none / describe>
### Next Task: Phase <X>.<n+1> — <task name>
```

**القواعد:**
- لا تضع ✅ على معيار لم تتحقّق منه فعلياً.
- إن وجدت regression (مثلاً عدد warnings زاد، أو عدد `getEmail()` ارتفع)، اذكره صراحة وأعِد المهمة pending.
- إذا اكتشفت أن مهمة أعلنتها جلسة سابقة "Completed" غير منجزة فعلاً، انقلها إلى قسم `## Re-opened:` في الأعلى.

---

## 6 — قواعد التواصل مع المستخدم (Fady)

- اللغة: **العربية**.
- واجهة التطبيق: **ألمانية** (DE) — لا تترجم strings.xml ما لم يُطلب صراحة في Phase B.
- Fady مطوّر Android خبير — لا تشرح أساسيات Room أو Firebase.
- ملاحظة من جلسة 23 أبريل: **لا تفترض أن ميزة تعمل** قبل قراءة الكود. خصوصاً للأصول (assets/)، URLs، roomId/plantId hardcoded.

---

## 7 — قبل النشر النهائي (Phase E)

تحقّقات إجبارية:
- [ ] grep evidence لكل مهام Phase A-D في PROGRESS.md.
- [ ] AAB حجم < 25 MB (release).
- [ ] keystore valid حتى 2030+ (حالياً 2053 ✅).
- [ ] لا `ca-app-pub-3940256099942544` (test AdMob) في strings.xml.
- [ ] لا `YOUR_API_KEY_HERE` في الكود.
- [ ] `plant_disease_model.tflite` موجود (أو الميزة مخفيّة).
- [ ] firestore.rules منشور في Firebase Console.
- [ ] Privacy Policy URL يعمل.

---

## 8 — Slash Commands المتاحة

- `/next-task` — اقرأ Audit + PROGRESS، حدّد المهمة التالية، ابدأها.
- `/verify-task <task_code>` — شغّل grep checks للمهمة، ولا تسجّل completion إلا إذا نجحت كلها.
- `/audit-snapshot` — شغّل المقاييس في Audit القسم 7 وقارنها بآخر snapshot في PROGRESS.md.
- `/phase-gate <A|B|C|D|E>` — تحقّق من معايير قبول مرحلة كاملة قبل الانتقال.

---

## 9 — ملاحظة عن Routines

أنت تعمل ضمن نظام Routines ينفّذك بشكل دوري. هناك حالياً:
1. **Auto-execution routine** — تختار المهمة التالية وتبدأها.
2. **End-of-Session verification routine** — تتحقّق وتسجّل.

عند أي تشغيل، **تحقّق من ID المهمة الحالي في آخر entry في PROGRESS.md قبل البدء**. إذا وجدت entry يقول "Task X completed" لكن grep evidence فيه ضعيف أو مفقود، أعِد فحصه قبل الانتقال للمهمة التالية.

---

**خاتمة:** السماح بمسامحة الادعاءات الكاذبة هو ما أوصلنا إلى انحراف Layer 3+4+6. هذه القواعد صارمة قصداً. اتّبعها حرفياً.
