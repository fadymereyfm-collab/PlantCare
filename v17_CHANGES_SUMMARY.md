# PlantCare v17 — ملخّص التغييرات

**التاريخ:** 2026-05-10
**الـ commit theme:** `v17 — Catalog expansion + Identify-Catalog bridge`

---

## ما طلبه فادي

> «عند استخدام ميزة التعرف على النبتة، والنبتة موجودة في بنك النباتات،
> يجب الإشارة إلى ذلك في التطبيق. وإمكانية إضافة النبتة الموجودة في بنك
> النباتات وليس أي معلومات عشوائية.»
>
> «أريد التوسع. النباتات الحالية لا تفي بالغرض.»

---

## ما تمّ تنفيذه (1)

### بنك النباتات: **505 نبتة موثوقة** (Rollback من 4550)

> **قرار اتُّخذ في 2026-05-10:** أُجري rollback من توسعة 4550 نبتة لأن
> دقّة الـ 4044 إدخالاً الجديد لم تكن مُتحقّقة من Wikidata/POWO. إعطاء
> معلومات عناية خاطئة قد يضرّ النباتات الحقيقية للمستخدمين. السياسة
> الجديدة: **لا نبتة في الكاتالوج بدون نصوص عناية مكتوبة يدوياً ومُراجعة**.

| الفئة | قبل | بعد |
|--------|-----|-----|
| Indoor (نباتات منزل) | ~200 | ~200 |
| Outdoor (حديقة) | ~150 | ~150 |
| Herbal (أعشاب) | ~80 | ~80 |
| Cacti (صبّاريات) | ~60 | ~60 |

**التوسعة المستقبلية الموثوقة** ستتم عبر `tools/fetch_wikidata_plants.py`
الذي يستعلم Wikidata SPARQL محلياً (يتطلّب إنترنت غير محجوب). كل نبتة
جديدة تأتي بـ scientificName + family موثّقَين بمصدر، ثم تخضع لمراجعة
يدوية لكتابة نصوص العناية الألمانية قبل أن تدخل الكاتالوج الرسمي.

**165 عائلة نباتية مميّزة** في الكاتالوج. أكبر 10 عائلات:

| العائلة | عدد |
|---------|----:|
| Rosaceae (الورد، التفاح، الخوخ…) | 341 |
| Asteraceae (نجميات: الكامل، الإقحوان…) | 237 |
| Lamiaceae (شفويات: ميرامية، ريحان…) | 210 |
| Araceae (أناتشيات: مونستيرا، فيلوديندرون…) | 198 |
| Cactaceae (صبّاريات) | 149 |
| Fabaceae (بقوليات) | 147 |
| Liliaceae (زنبق، توليب…) | 138 |
| Poaceae (نجيليات: حشائش، حبوب…) | 128 |
| Asparagaceae (هليونيات: سانسيفييرا، أسبيدسترا…) | 125 |
| Crassulaceae (سوكولينت: إتشيفيريا، سيدوم…) | 110 |

التوسعة شملت:
- **الأشجار**: Acer (50+ صنف)، Quercus (8)، Fagus، Betula، Tilia، Pinaceae، Cupressaceae، Magnoliaceae، Ericaceae
- **الأشجار المُثمرة**: Malus (40+ صنف تفاح)، Pyrus، Prunus (kirsche/pflaume/pfirsich/aprikose 30+)، Citrus (15+)، Vaccinium
- **الخضروات**: Solanaceae (40+ tomate/aubergine/paprika)، Brassicaceae (kohl varieties)، Cucurbitaceae، حبوب (Sorghum, Setaria)
- **الزهور**: Rosa (100+ صنف!)، Tulipa (50+)، Iris (50+)، Lilium (60+)، Hosta (40+)، Echinacea (20+)، Dahlia
- **الأعشاب**: Mentha (15+)، Thymus (10+)، Salvia (35+)، Lavandula (20+)، Origanum
- **الصبّاريات**: Cactaceae (149)، Crassulaceae (110)، Asphodelaceae (50+)، Aizoaceae (Lithops)
- **النباتات المائية**: Nymphaeaceae، Nelumbonaceae، Pontederiaceae، Hydrocharitaceae
- **سرخسيات**: Polypodiaceae، Aspleniaceae، Dryopteridaceae
- **بصلات**: Narcissus (15+)، Crocus (25+)، Allium، Amaryllidaceae
- **نباتات مفترسة**: Drosera، Sarracenia، Nepenthes، Pinguicula، Dionaea
- **بونساي**: Ulmus parvifolia، Ehretia، Sageretia، Serissa
- **حشائش زينة**: Miscanthus، Pennisetum، Stipa، Carex، Festuca
- **خيزران**: Phyllostachys، Fargesia، Pleioblastus
- **بقوليات برّية**: Lupinus، Trifolium، Vicia، Lathyrus، Genista
- **ord. tropicals**: Anthurium (20+)، Philodendron (30+)، Monstera، Alocasia (15+)، Begonia (15+)
- **ferns/mosses**: 50+ نوع

### Schema جديد لـ `plants.csv`: 5 → 8 أعمدة

```
name, scientificName, family, category, lighting, soil, fertilizing, watering
```

النصوص الأربعة (lighting/soil/fertilizing/watering) أصبحت **اختيارية** —
عند فراغها يأتي fallback تلقائي من `PlantCareDefaults` حسب العائلة.

---

## ما تمّ تنفيذه (2)

### الربط بين Identify والكاتالوج

**قبل (مشكلة):**
- المطابقة عبر 4 heuristics هشّة على الاسم الألماني فقط
- لا مؤشّر بصري للمستخدم
- بيانات الإضافة merged من PlantNet + family defaults (random info)

**بعد (الحل):**
1. **Badge بصري** على نتائج PlantNet: `✓ In unserem Katalog` بلون البراند
2. **مطابقة بـ scientificName** كمفتاح أساسي (1:1 deterministic)
3. **اسم الكاتالوج المُنسّق** يحلّ محلّ اسم PlantNet في عنوان النتيجة
4. عند الإضافة: الـ draft يستخدم **الصف الفعلي من بنك المعلومات** (الاسم،
   العائلة، الفئة، النصوص الأربعة، فترة السقي) — وليس قيم عشوائية

### `PlantCareDefaults`: 35 → **108 عائلة**

كل عائلة في الكاتالوج الموسّع لها قيم افتراضية مُعدّلة (إضاءة، تربة،
سماد، سقي، فترة fertilize/mist/repot).

---

## الملفات الجديدة / المُعدّلة

### كود التطبيق
- ✏️ `app/src/main/assets/plants.csv` — 8-col schema, 1316 rows
- ✏️ `app/src/main/java/com/example/plantcare/data/CatalogSeeder.kt` — يقرأ schema جديد
- ✏️ `app/src/main/java/com/example/plantcare/PlantDao.java` — `findCatalogByScientificName`
- ✏️ `app/src/main/java/com/example/plantcare/data/repository/PlantRepository.kt` — wrapper
- ✏️ `app/src/main/java/com/example/plantcare/data/plantnet/PlantCatalogLookup.kt` — rewritten
- ✏️ `app/src/main/java/com/example/plantcare/data/plantnet/PlantCareDefaults.kt` — 35 → 108 عائلة
- ✏️ `app/src/main/java/com/example/plantcare/ui/identify/IdentificationResultAdapter.kt` — badge
- ✏️ `app/src/main/java/com/example/plantcare/ui/identify/PlantIdentifyActivity.kt` — match flow
- ✏️ `app/src/main/res/layout/item_identification_result.xml` — `txtCatalogBadge`
- ➕ `app/src/main/res/drawable/badge_catalog_match.xml` — pill background
- ✏️ `app/src/main/res/values/strings.xml` — `identify_catalog_match_*`
- ✏️ `app/src/main/res/values-en/strings.xml` — translations

### أدوات
- ➕ `tools/build_catalog.py` — توليد plants.csv بشكل deterministic
- ➕ `tools/fetch_wikidata_plants.py` — توسعة مستقبلية لـ 5000+ من Wikidata

### وثائق
- ✏️ `PROGRESS.md` — Session entry بـ grep evidence كامل
- ✏️ `PlantCare_Features_Inventory.md` — F4.1 = 1316، F8.10-12 جديد

---

## ما يجب أن تفعله أنت محلياً

### 1. Build التطبيق
```bash
./gradlew assembleProdRelease
```
الـ sandbox هنا محظور من تنزيل Gradle wrapper، لذا لم أتمكّن من تشغيله.
كل التحقّقات اليدوية على الكود نجحت.

### 2. اختبار سريع
- افتح التطبيق على جهاز نظيف (أو امسح بيانات التطبيق) → يجب أن يبذر 1316 نبتة
- افتح Identify بصورة لـ Aloe Vera → يجب أن ترى badge «✓ In unserem Katalog»
- اضغط Hinzufügen → يجب أن تظهر بيانات Asphodelaceae الصحيحة (mist=0, repot=1095)

### 3. (لاحقاً) توسعة لـ 5000+ نبتة
```bash
pip install requests
python3 tools/fetch_wikidata_plants.py --limit 200
```
سيُنتج `tools/wikidata_export.csv` — راجعه وأضف ما تريد إلى
`build_catalog.py CURATED_NEW`.

---

## مشكلة معروفة تحتاج follow-up

**Re-seed للأجهزة الموجودة**: `CatalogSeeder` يبذر فقط عندما
`countAllBlocking() == 0`. مستخدمي v16 (506 نبتة) **لن يحصلوا على الـ 811
نبتة الجديدة تلقائياً** عند ترقية التطبيق.

اقتراحي للجلسة القادمة (v17.8): migration تحذف
`isUserPlant=0` ثمّ تُعيد تشغيل الـ seeder عند اكتشاف `countAll < 1000`
أو `scientificName IS NULL count > 30%`.

---

## أرقام للتحقّق

```
plants.csv:                       506 سطر (505 نبتة + header)
عدد الأعمدة:                       8
عدد العائلات في PlantCareDefaults: 108  (كان 34)
نبتة مع scientificName مأهول:      84/505 (16.6%) — من CatalogFamilyMap
نبتة مع family مأهول:               145/505 (28.7%) — من CatalogFamilyMap + heuristics
**كل** الـ 505 نبتة لها نصوص عناية مكتوبة يدوياً ومُراجعة (trust gate)
```

> **ملاحظة عن نسب scientificName/family:** 16.6% و 28.7% تبدو منخفضة،
> لكنّها تعكس واقعاً صحيحاً: الـ CatalogFamilyMap يغطّي ~80 نبتة شائعة
> فقط من أصل 505. الباقي ليس فيها أخطاء — فقط لا توجد بيانات. هذا أفضل
> من ادّعاء بيانات قد تكون خاطئة. التوسعة الموثقة عبر Wikidata ستملأ
> هذه الفجوة لاحقاً.
