# PlantCare — Phase G: Premium Visual Overhaul (خطة العمل التفصيلية)

> **التاريخ:** 2026-05-15
> **المؤلّف:** Claude (جلسة Cowork) بإشراف Fady
> **النطاق:** إعادة بناء كاملة لطبقة التصميم البصري — تُلغي ولا تستكمل قرارات Phase F.
> **الهدف النهائي:** أن يكون مظهر PlantCare متطابقاً في الإحساس والاحترافية مع تطبيقات المرجع (Plant Identifier / Set care reminders / Settings) المُقدَّمة من Fady في جلسة 2026-05-15.
> **مرتبة بحسب:** Layers G0 → G10 — لا يجوز تخطّي layer.

---

## 0 — لماذا Phase G ضرورية (السياق المُلزِم)

`PROGRESS.md` يُسجِّل أن **Phase F (F2 + F3 + F4 + F5 + F6 + F7 + F8 + F9) رسمياً مُغلَقة بـ100% coverage** في جلسة 2026-05-15 round 30. الكود يعتبر التصميم الحالي "النهائي". لكن مقارنة لقطات الشاشة الفعلية (5 لقطات من PlantCare على جهاز Fady) مع لقطات المرجع (3 لقطات لتطبيقات احترافية) أظهرت **فجوة جوهرية في الإحساس البصري**.

**القرارات المُتَّخذة في Phase F التي ستُنقَض في Phase G:**

| القرار في Phase F | البديل في Phase G | السبب |
|---|---|---|
| Sage cream background `#FAF6EF` (F2.1) | Sage mint green background `#EAF1E2` | يطابق خلفية المرجع |
| Primary `#6B9080` Sage باهت | Primary `#3FA856` Vibrant Leaf | حدّة + هوية برّاقة |
| Hero font: DM Serif Display Regular (F2 typography) | Hero font: Manrope ExtraBold 800 | حضور بصري أقوى |
| 4 tabs + FAB (F2.1 bottom nav) | 4 tabs (Home/Garden/Diagnose/Settings) + FAB — Calendar مدمج في Home | يُحرّر مكاناً لـ Diagnose بصرياً |
| 7 sectioned cards في Settings (F2.6) | قائمة Pills مفردة بـ glassmorphism | يطابق المرجع |
| Section labels UPPERCASE (F2 typography) | محذوفة كلياً | تقليل ضجيج بصري |
| Empty states نصّ فقط | Empty states 3D illustration + brackets + CTA | "Wow moment" |
| 2dp inner card elevation → 0dp + outline (F4-F8) | يُحتفظ به (✅) | لا حاجة للنقض |
| 28dp dialog corners (F9.2) | يُحتفظ به (✅) | متّفق مع المرجع |
| 12-18dp photo thumbnails (F4) | 16-22dp photo thumbnails | تنعيم أكبر |

**النتيجة المتوقَّعة:** بعد إنجاز G0 وحدها، **ستتغيّر كل شاشة في التطبيق بصرياً فوراً** لأن `colors.xml` و `themes.xml` يُغذِّيان 100% من السطح المرئي (مؤكَّد في PROGRESS.md F9.3 و F9.5).

---

## 1 — Prerequisites والإعداد قبل البدء

### 1.1 — Branch Strategy

```bash
# نقطة العودة الآمنة
git checkout main
git pull origin main
git tag phase-f-final-baseline                # رقم تذكاري قبل النقض
git push origin phase-f-final-baseline

# Branch جديد للعمل
git checkout -b phase-g-premium-overhaul
```

### 1.2 — متطلبات البيئة المحلية

- **Android Studio:** Iguana أو أحدث (يدعم Compose stable).
- **Gradle:** 8.0+ (موجود).
- **AGP:** 8.1+ (موجود).
- **JDK:** 17.
- **Material Components:** ≥ 1.10.0 — يجب التحقّق من `app/build.gradle` قبل البدء (Pill indicator يتطلّب 1.6+، الأفضل 1.10).
- **حجم AAB قبل البدء:** يجب توثيقه كـ baseline قبل أي تغيير:

```bash
gradlew.bat :app:bundleProdRelease
dir app\build\outputs\bundle\prodRelease\*.aab
# سجِّل الرقم في PROGRESS.md → "G0 baseline AAB size = X.YZ MB"
```

### 1.3 — Build Verification بعد كل Layer (إلزامي)

بعد كل Layer من G0 إلى G10، يجب تشغيل:

```bash
gradlew.bat :app:assembleProdRelease --console=plain > build_log_layer_<N>.txt
```

ثم نسخ آخر 30 سطراً إلى `PROGRESS.md` تحت Session entry للـlayer.

### 1.4 — Warnings Baseline

قبل البدء، توثيق عدد warnings الحالي:

```bash
gradlew.bat :app:assembleProdRelease 2>&1 | findstr /C:"warning:" | find /c "warning:"
# مثلاً: 19 warnings (مذكور في PROGRESS round 30)
```

**قاعدة CLAUDE.md §3:** لا يُسمَح بزيادة الرقم. إن زاد بعد layer، يُعاد الـ layer إلى pending.

---

## 2 — Phase G Master Roadmap (نظرة شاملة)

```
G0 — Design System Rewrite (Foundation)
G1 — Bottom Navigation (5→4 tabs + Pill indicator + bigger FAB)
G2 — Home Screen Hero Redesign (greeting + banner + search + week strip + empty state)
G3 — Streak Card Rebuild (لا divider داخلي)
G4 — Calendar Merge into Home (Calendar tab يصبح bottom sheet)
G5 — My Garden Redesign (بطاقات عمودية مع صور 16:9)
G6 — Diagnose Tab Creation (landing screen جديد)
G7 — Settings Premium Redesign (Glassmorphism pills)
G8 — Dialogs Modernization (TextInputLayout صحيح + Material 3)
G9 — Asset Pipeline (5 صور 3D + 6 vector drawables + leaf pattern)
G10 — Polish (animations + haptics + system bars)
```

**وقت تنفيذ تقديري:** كل Layer بين 2-6 ساعات عمل مركّز ⇒ Phase G كاملة بين 30-50 ساعة عمل (موزَّعة على ~10 جلسات).

---

## 3 — Layer G0: Design System Rewrite (الأساس)

> **الهدف:** استبدال palette + typography + shape system كاملاً بـtokens جديدة. لا layouts، لا fragments، فقط ملفات `values/` + `font/` + `drawable/` للأساسيات.

### 3.1 — G0.1: Color Palette الجديدة

**الملف:** `app/src/main/res/values/colors.xml`

**العمل:** كتابة ملف جديد كلياً (نسخ احتياطي للقديم في `colors_phase_f_backup.xml`).

**الـ Tokens الجديدة (Light theme):**

| Token | القيمة | الاستخدام |
|---|---|---|
| `pc_background` | `#EAF1E2` | خلفية الشاشة الأساسية |
| `pc_background_dim` | `#DCE7D1` | للـ surface containers |
| `pc_surface` | `#FFFFFF` | بطاقات صلبة |
| `pc_surface_translucent` | `#B3FFFFFF` (70% opacity) | Glassmorphism pills |
| `pc_surface_elevated` | `#FFFFFF` | dialogs / popups |
| `pc_primary` | `#3FA856` | Brand + CTAs + active tabs |
| `pc_primary_dark` | `#2F8242` | pressed states |
| `pc_primary_container` | `#D6E9C0` | Pro banner + indicator pill background |
| `pc_on_primary` | `#FFFFFF` | text-on-primary |
| `pc_on_primary_container` | `#1F3D2A` | text-on-pill-indicator |
| `pc_accent_amber` | `#F5D27A` | "Complete" buttons في reminders |
| `pc_accent_amber_dark` | `#D9B05F` | pressed amber |
| `pc_accent_coral` | `#E8918F` | Overdue indicator |
| `pc_on_surface` | `#1B3A2A` | عناوين Bold ثقيلة |
| `pc_on_surface_strong` | `#0E2218` | Hero titles فقط |
| `pc_on_surface_secondary` | `#7D8A7E` | نصوص ثانوية |
| `pc_outline` | `#A8B7AB` | حدود واضحة |
| `pc_outline_variant` | `#DCE5DD` | حدود glassmorphism |
| `pc_success` | `#3FA856` | ✓ done states (= primary) |
| `pc_error` | `#BA1A1A` | يبقى |
| `pc_scrim_42` | `#6B000000` | dialog scrim |

**القرار:** الحفاظ على الـtokens القديمة كـ `<color name="pc_primary_legacy">#6B9080</color>` لمدة layer واحد فقط (G0)، ثم حذفها في G1.

**معيار قبول G0.1:**

```bash
grep -n 'name="pc_primary">' app/src/main/res/values/colors.xml
# المتوقع: سطر واحد بقيمة #3FA856

grep -rn '#6B9080' app/src/main/res/ --include='*.xml' | grep -v colors.xml | grep -v _legacy
# المتوقع: 0 نتائج
```

### 3.2 — G0.2: Typography System الجديد

**الخطوط المطلوبة (تنزيل من Google Fonts):**

1. **Manrope** — Static `Manrope-ExtraBold.ttf` (weight 800) — للـ Hero.
2. **Inter** — Static `Inter-Regular.ttf` + `Inter-SemiBold.ttf` — للـ Body و Buttons.

**روابط التنزيل:**
- https://fonts.google.com/specimen/Manrope (تحميل العائلة، استخراج ExtraBold فقط)
- https://fonts.google.com/specimen/Inter (تحميل العائلة، استخراج Regular + SemiBold)

**الملفّات الجديدة:**

```
app/src/main/res/font/
├── manrope_extra_bold.ttf        ← جديد
├── manrope.xml                    ← جديد (font-family declaration)
├── inter_regular.ttf              ← جديد
├── inter_semibold.ttf             ← جديد
└── inter.xml                      ← جديد (font-family declaration)
```

**محتوى `manrope.xml`:**

```xml
<?xml version="1.0" encoding="utf-8"?>
<font-family xmlns:android="http://schemas.android.com/apk/res/android">
    <font android:fontStyle="normal"
          android:fontWeight="800"
          android:font="@font/manrope_extra_bold" />
</font-family>
```

**محتوى `inter.xml`:**

```xml
<?xml version="1.0" encoding="utf-8"?>
<font-family xmlns:android="http://schemas.android.com/apk/res/android">
    <font android:fontStyle="normal" android:fontWeight="400" android:font="@font/inter_regular" />
    <font android:fontStyle="normal" android:fontWeight="600" android:font="@font/inter_semibold" />
</font-family>
```

**تعديل `themes.xml`:**

```xml
<!-- استبدال هذه الـ blocks: -->

<style name="TextAppearance.PlantCare.Headline" parent="TextAppearance.MaterialComponents.Headline5">
    <item name="android:fontFamily">@font/manrope</item>
    <item name="fontFamily">@font/manrope</item>
    <item name="android:textColor">@color/pc_on_surface_strong</item>
    <item name="android:textStyle">bold</item>
    <item name="android:textSize">32sp</item>
    <item name="android:letterSpacing">-0.015</item>
    <item name="android:lineSpacingMultiplier">1.1</item>
</style>

<style name="TextAppearance.PlantCare.Title" parent="TextAppearance.MaterialComponents.Headline6">
    <item name="android:fontFamily">@font/manrope</item>
    <item name="fontFamily">@font/manrope</item>
    <item name="android:textColor">@color/pc_on_surface</item>
    <item name="android:textStyle">bold</item>
    <item name="android:textSize">20sp</item>
    <item name="android:letterSpacing">-0.005</item>
</style>

<style name="TextAppearance.PlantCare.Body" parent="TextAppearance.MaterialComponents.Body1">
    <item name="android:fontFamily">@font/inter</item>
    <item name="fontFamily">@font/inter</item>
    <item name="android:textColor">@color/pc_on_surface</item>
    <item name="android:textSize">15sp</item>
    <item name="android:lineSpacingExtra">2dp</item>
</style>

<style name="TextAppearance.PlantCare.Button" parent="TextAppearance.MaterialComponents.Button">
    <item name="android:fontFamily">@font/inter</item>
    <item name="fontFamily">@font/inter</item>
    <item name="android:textFontWeight">600</item>
    <item name="android:textAllCaps">false</item>
    <item name="android:textSize">15sp</item>
    <item name="android:letterSpacing">0</item>
</style>

<!-- حذف هذا الـ block بالكامل: -->
<!-- <style name="TextAppearance.PlantCare.SectionLabel"> ... </style> -->
```

**معيار قبول G0.2:**

```bash
grep -n 'fontFamily">@font/manrope' app/src/main/res/values/themes.xml
# المتوقع: 2 نتائج على الأقل (في .Headline و .Title)

grep -n 'fontFamily">@font/inter' app/src/main/res/values/themes.xml
# المتوقع: 2 نتائج على الأقل (في .Body و .Button)

grep -rn 'SectionLabel' app/src/main/res/layout/ | wc -l
# المتوقع: 0 (يجب إزالة كل الاستخدامات في layer G2-G7)

grep -rn 'dm_serif_display' app/src/main/res/values/themes.xml
# المتوقع: 0 (DM Serif مهجور)
```

### 3.3 — G0.3: Shape System الجديد

**ملف جديد:** `app/src/main/res/values/shapes.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- بطاقات عادية -->
    <style name="ShapeAppearance.PlantCare.Card" parent="">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">24dp</item>
    </style>

    <!-- Pills كاملة (radius = height/2 ⇒ نضع رقم كبير) -->
    <style name="ShapeAppearance.PlantCare.Pill" parent="">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">50%</item>
    </style>

    <!-- Photo thumbnails صغيرة -->
    <style name="ShapeAppearance.PlantCare.Thumbnail" parent="">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">16dp</item>
    </style>

    <!-- Photo thumbnails كبيرة -->
    <style name="ShapeAppearance.PlantCare.ThumbnailLarge" parent="">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">22dp</item>
    </style>

    <!-- Input fields -->
    <style name="ShapeAppearance.PlantCare.Input" parent="">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">18dp</item>
    </style>
</resources>
```

**تعديل `themes.xml`:** إضافة `shapeAppearanceMediumComponent` و `shapeAppearanceLargeComponent` للـ theme:

```xml
<item name="shapeAppearanceMediumComponent">@style/ShapeAppearance.PlantCare.Card</item>
<item name="shapeAppearanceLargeComponent">@style/ShapeAppearance.PlantCare.Card</item>
```

**تعديل `PlantCare.Card` style:** `cardCornerRadius` من 20dp إلى 24dp.

**تعديل `PlantCare.Button.Filled` و `.Outlined` و `.Tonal`:** `cornerRadius` من 22dp إلى **100dp** (سيُقطع بـ minHeight=48dp ⇒ pill كامل تلقائياً).

**معيار قبول G0.3:**

```bash
grep -n 'ShapeAppearance.PlantCare.Card' app/src/main/res/values/shapes.xml
# المتوقع: سطر واحد

grep -n 'cardCornerRadius">24dp' app/src/main/res/values/themes.xml
# المتوقع: سطر واحد على الأقل

grep -n 'cornerRadius">100dp' app/src/main/res/values/themes.xml
# المتوقع: 4 على الأقل (Filled, Outlined, Tonal, Text)
```

### 3.4 — G0.4: Leaf Pattern Background

**ملف جديد:** `app/src/main/res/drawable/bg_leaf_pattern.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    PlantCare — Decorative leaf texture overlay (Phase G0.4).
    Used inside bg_screen_root layer-list at 6% opacity.
    Pure vector — no raster, no GPU load.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="360dp"
    android:height="640dp"
    android:viewportWidth="360"
    android:viewportHeight="640"
    android:alpha="0.06">

    <!-- ورقة 1: أعلى يمين -->
    <path
        android:fillColor="#1B3A2A"
        android:pathData="M280,40 C310,30 340,50 335,90 C330,120 290,140 270,110 C260,90 265,55 280,40 Z" />

    <!-- ورقة 2: يسار وسط -->
    <path
        android:fillColor="#1B3A2A"
        android:pathData="M20,300 C50,290 80,310 75,350 C70,380 30,400 10,370 C0,350 5,315 20,300 Z" />

    <!-- ورقة 3: أسفل يسار -->
    <path
        android:fillColor="#1B3A2A"
        android:pathData="M40,580 C70,570 100,590 95,630 C90,660 50,680 30,650 C20,630 25,595 40,580 Z" />

    <!-- ورقة 4: أسفل يمين -->
    <path
        android:fillColor="#1B3A2A"
        android:pathData="M300,540 C330,530 360,550 355,590 C350,620 310,640 290,610 C280,590 285,555 300,540 Z" />
</vector>
```

**ملف جديد:** `app/src/main/res/drawable/bg_screen_root.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@color/pc_background" />
    <item
        android:drawable="@drawable/bg_leaf_pattern"
        android:gravity="fill" />
</layer-list>
```

**تعديل `themes.xml`:** `android:windowBackground` من `@color/pc_background` إلى `@drawable/bg_screen_root`.

**معيار قبول G0.4:**

```bash
grep -n 'bg_screen_root' app/src/main/res/values/themes.xml
# المتوقع: سطر واحد على الأقل (في windowBackground)

ls app/src/main/res/drawable/bg_leaf_pattern.xml app/src/main/res/drawable/bg_screen_root.xml
# المتوقع: الملفّان موجودان
```

### 3.5 — G0 Build Verification (إلزامي)

```bash
gradlew.bat :app:assembleProdRelease --console=plain
# يجب أن ينتج: BUILD SUCCESSFUL
# عدد warnings ≤ baseline (19 من Round 30)
```

### 3.6 — G0 PROGRESS.md Entry

كتابة دخول جديد في PROGRESS.md (في الأعلى):

```markdown
## Session: 2026-MM-DD (Phase G0 — Design System Rewrite)
### Task Completed: G0.1 + G0.2 + G0.3 + G0.4 — Palette + Typography + Shape + Leaf pattern.
### Layer: Phase G (نقض Phase F2 typography + palette).
### Files changed:
  - app/src/main/res/values/colors.xml (rewrite)
  - app/src/main/res/values/themes.xml (rewrite headline/title/body)
  - app/src/main/res/values/shapes.xml (NEW)
  - app/src/main/res/font/manrope.xml (NEW)
  - app/src/main/res/font/inter.xml (NEW)
  - app/src/main/res/font/manrope_extra_bold.ttf (NEW asset)
  - app/src/main/res/font/inter_regular.ttf (NEW asset)
  - app/src/main/res/font/inter_semibold.ttf (NEW asset)
  - app/src/main/res/drawable/bg_leaf_pattern.xml (NEW)
  - app/src/main/res/drawable/bg_screen_root.xml (NEW)
### Evidence:
  - grep '#3FA856' colors.xml → 1 line
  - grep 'manrope' themes.xml → ≥ 2 lines
  - grep '#6B9080' res/ (excl colors.xml) → 0
### Build Status: ✅ assembleProdRelease passed (<مدة الـbuild>)
### Regressions: <none / describe>
### Next Task: Phase G1 — Bottom Navigation rebuild
```

---

## 4 — Layer G1: Bottom Navigation (4 tabs + Pill Indicator + Bigger FAB)

> **الهدف:** إعادة بناء Bottom Nav بـ4 tabs (Home / Garden / Diagnose / Settings) + إضافة Pill indicator خلف العنصر النشط + تكبير FAB.

### 4.1 — G1.1: تحديث Bottom Nav Menu

**الملف:** `app/src/main/res/menu/bottom_nav_menu.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/nav_today"
          android:icon="@drawable/ic_nav_home"
          android:title="@string/nav_home" />

    <item android:id="@+id/nav_garden"
          android:icon="@drawable/ic_nav_garden"
          android:title="@string/nav_garden" />

    <!-- CAMERA FAB CENTER (ليس menu item) -->

    <item android:id="@+id/nav_diagnose"
          android:icon="@drawable/ic_nav_diagnose"
          android:title="@string/nav_diagnose" />

    <item android:id="@+id/nav_settings"
          android:icon="@drawable/ic_nav_settings"
          android:title="@string/nav_settings" />
</menu>
```

**ملاحظات:**
- `nav_today` يبقى الـ id (لا تكسير لـ KEY_SELECTED_TAB persistence).
- `nav_calendar` يُحذَف بالكامل ⇒ Calendar مدمج في Home.
- `nav_diagnose` جديد ⇒ يحتاج destination في `MainActivity.selectTab(index)`.

### 4.2 — G1.2: Drawable للأيقونات

**ملفات جديدة:**

- `app/src/main/res/drawable/ic_nav_home.xml` — منزل/تطبيق نباتي outline.
- `app/src/main/res/drawable/ic_nav_diagnose.xml` — حقيبة طبية أو دائرة فحص (مثل المرجع).
- `app/src/main/res/drawable/ic_nav_garden.xml` — يبقى (إن كان جيداً) أو يُحدَّث.
- `app/src/main/res/drawable/ic_nav_settings.xml` — سداسي/ترس outline (مثل المرجع).
- `app/src/main/res/drawable/ic_nav_camera_fab.xml` — يبقى.

**كل أيقونة:** 24dp viewport، fillColor=#000، stroke 2dp، style موحّد.

### 4.3 — G1.3: Pill Indicator Style

**تعديل `themes.xml`:**

```xml
<style name="PlantCare.BottomNav.ActiveIndicator" parent="">
    <item name="android:width">64dp</item>
    <item name="android:height">36dp</item>
    <item name="android:color">@color/pc_primary_container</item>
    <item name="shapeAppearance">@style/ShapeAppearance.PlantCare.Pill</item>
</style>
```

**تعديل `activity_main.xml`:**

```xml
<com.google.android.material.bottomnavigation.BottomNavigationView
    android:id="@+id/bottomNav"
    ...
    app:itemActiveIndicatorStyle="@style/PlantCare.BottomNav.ActiveIndicator"
    app:itemIconTint="@color/bottom_nav_item_tint"
    app:itemTextColor="@color/bottom_nav_item_tint"
    ... />
```

### 4.4 — G1.4: FAB أكبر وأبرز

**تعديل `activity_main.xml`:**

```xml
<com.google.android.material.floatingactionbutton.FloatingActionButton
    android:id="@+id/fabCamera"
    android:layout_width="64dp"      <!-- كان wrap_content (=56dp) -->
    android:layout_height="64dp"
    android:translationY="36dp"      <!-- كان 28dp -->
    app:fabSize="auto"
    app:fabCustomSize="64dp"
    app:borderWidth="0dp"
    app:backgroundTint="@color/pc_primary"
    app:tint="@color/pc_on_primary"
    app:elevation="12dp"             <!-- كان 10dp -->
    ... />
```

**إضافة حلقة بيضاء حول الـFAB (دونات effect):**

`app/src/main/res/drawable/bg_fab_camera_donut.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="oval">
            <solid android:color="@color/pc_background" />
        </shape>
    </item>
    <item
        android:left="4dp" android:top="4dp"
        android:right="4dp" android:bottom="4dp">
        <shape android:shape="oval">
            <solid android:color="@color/pc_primary" />
        </shape>
    </item>
</layer-list>
```

تطبيق هذا الـ drawable على FAB بـ `android:src="@drawable/ic_nav_camera_fab"` و `app:backgroundTint` فارغ.

### 4.5 — G1.5: MainActivity routing updates

**ملف:** `app/src/main/java/com/example/plantcare/MainActivity.java`

**التعديلات الإلزامية:**

1. تحديث `selectTab(int index)`:
   - `0` → Home (TodayFragment)
   - `1` → Garden (MyPlantsFragment)
   - `2` → Diagnose (DiagnoseFragment جديد)
   - `3` → Settings (SettingsFragment)
2. حذف كل إشارة لـ `nav_calendar` و `CalendarScreenBridge` كـ tab. (الـCalendar يُفتح من Home كـbottom sheet في G4).
3. تحديث `KEY_SELECTED_TAB` mapping comments.
4. تحديث `launchTab` Intent extra documentation.

### 4.6 — G1 معيار قبول grep

```bash
# 4 menu items (Home, Garden, Diagnose, Settings)
grep -c '<item' app/src/main/res/menu/bottom_nav_menu.xml
# المتوقع: 4

# Diagnose موجود
grep -n 'nav_diagnose' app/src/main/res/menu/bottom_nav_menu.xml
# المتوقع: سطر واحد

# Calendar item محذوف
grep -n 'nav_calendar' app/src/main/res/menu/bottom_nav_menu.xml
# المتوقع: 0

# Pill indicator مفعّل
grep -n 'itemActiveIndicatorStyle' app/src/main/res/layout/activity_main.xml
# المتوقع: سطر واحد

# FAB size = 64dp
grep -n 'fabCustomSize="64dp"' app/src/main/res/layout/activity_main.xml
# المتوقع: سطر واحد
```

### 4.7 — G1 Build + PROGRESS

`./gradlew assembleProdRelease` ⇒ يجب أن يُبنى نظيفاً + entry في PROGRESS.

---

## 5 — Layer G2: Home Screen Hero Redesign

> **الهدف:** Home جديدة كلياً تطابق "Plant Identifier" في المرجع: عنوان ثابت + Pro banner + search bar + week strip + reminders + empty state بصور 3D.

### 5.1 — G2.1: حذف العنوان المُكرَّر

**ملف:** `app/src/main/res/layout/fragment_today.xml`

**الحذف:**
- `greetingTitle` "Guten Abend" (يتحوّل إلى subtitle صغير في AppBar فقط).
- العنوان الرئيسي في `activity_main.xml#toolbarTitle` يصبح ثابتاً = "PlantCare" أو "Pflanzenpflege".

**التغيير في `activity_main.xml`:**

```xml
<TextView
    android:id="@+id/toolbarTitle"
    android:text="@string/app_name"   <!-- ثابت دائماً، لا setText() ديناميكي -->
    android:textAppearance="@style/TextAppearance.PlantCare.Headline"
    android:textSize="32sp"
    ... />
```

**تعديل `MainActivity.java`:** حذف `toolbarTitle.setText(...)` من `onTabSelected`.

### 5.2 — G2.2: Hero Pro Banner

**استبدال أعلى `fragment_today.xml` بالكامل:**

```xml
<com.google.android.material.card.MaterialCardView
    android:id="@+id/heroProBanner"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="12dp"
    app:cardCornerRadius="24dp"
    app:cardElevation="0dp"
    app:cardBackgroundColor="@color/pc_primary_container">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:minHeight="148dp"
        android:padding="20dp">

        <TextView
            android:id="@+id/heroTitle"
            android:text="@string/hero_unlock_all_features"
            android:textAppearance="@style/TextAppearance.PlantCare.Title"
            android:textSize="20sp"
            android:textColor="@color/pc_on_primary_container"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toStartOf="@id/heroImage"
            app:layout_constraintWidth_default="spread"
            android:layout_width="0dp"
            android:layout_height="wrap_content" />

        <TextView
            android:id="@+id/heroSubtitle"
            android:text="@string/hero_unlock_subtitle"
            android:textAppearance="@style/TextAppearance.PlantCare.Body"
            android:textColor="@color/pc_on_primary_container"
            android:textSize="13sp"
            android:layout_marginTop="6dp"
            app:layout_constraintTop_toBottomOf="@id/heroTitle"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toStartOf="@id/heroImage"
            android:layout_width="0dp"
            android:layout_height="wrap_content" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/heroCta"
            style="@style/PlantCare.Button.Filled"
            android:text="@string/hero_go_pro"
            android:backgroundTint="@color/pc_surface"
            android:textColor="@color/pc_on_surface_strong"
            android:layout_marginTop="14dp"
            app:layout_constraintTop_toBottomOf="@id/heroSubtitle"
            app:layout_constraintStart_toStartOf="parent"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />

        <ImageView
            android:id="@+id/heroImage"
            android:src="@drawable/hero_pots_3d"
            android:layout_width="140dp"
            android:layout_height="148dp"
            android:scaleType="fitEnd"
            android:contentDescription="@null"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintBottom_toBottomOf="parent" />
    </androidx.constraintlayout.widget.ConstraintLayout>
</com.google.android.material.card.MaterialCardView>
```

**Strings جديدة في `res/values/strings.xml`:**

```xml
<string name="hero_unlock_all_features">Unlock all features</string>
<string name="hero_unlock_subtitle">Discover plant names and get instant details anytime!</string>
<string name="hero_go_pro">Go Pro</string>
```

### 5.3 — G2.3: Search Bar Pill

```xml
<com.google.android.material.card.MaterialCardView
    android:id="@+id/searchPill"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:layout_marginTop="14dp"
    app:cardCornerRadius="28dp"
    app:cardElevation="0dp"
    app:cardBackgroundColor="@color/pc_surface_translucent">

    <LinearLayout
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:padding="14dp"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <ImageView
            android:src="@drawable/ic_search_premium"
            app:tint="@color/pc_on_surface_secondary"
            android:layout_width="22dp"
            android:layout_height="22dp"
            android:layout_marginEnd="10dp" />

        <TextView
            android:text="@string/search_plant_placeholder"
            android:textAppearance="@style/TextAppearance.PlantCare.Body"
            android:textColor="@color/pc_on_surface_secondary"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

**onClick:** يفتح `AllPlantsFragment` بـ`Intent → CatalogActivity` مع focus على البحث.

### 5.4 — G2.4: Week Strip في Home (دمج Calendar)

```xml
<LinearLayout
    android:id="@+id/weekStrip"
    android:orientation="horizontal"
    android:weightSum="7"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="20dp">

    <!-- 7 أعمدة، كل عمود نفس البنية: -->
    <LinearLayout
        android:orientation="vertical"
        android:gravity="center"
        android:layout_weight="1"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:padding="6dp">
        <TextView android:text="Mo" android:textSize="11sp" .../>
        <FrameLayout
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:layout_marginTop="4dp"
            android:background="@drawable/bg_weekday_today">
            <TextView android:text="11" android:gravity="center" .../>
        </FrameLayout>
    </LinearLayout>
    ...
</LinearLayout>
```

**drawable جديد:** `bg_weekday_today.xml` — دائرة خضراء صلبة `pc_primary` لليوم الحالي، شفافة للأيام الأخرى.

**Java code:** TodayFragment يبني الأعمدة برمجياً من `LocalDate.now()` (لا hardcoded text).

### 5.5 — G2.5: Empty State بصور 3D + Brackets

```xml
<LinearLayout
    android:id="@+id/emptyStateContainer"
    android:orientation="vertical"
    android:gravity="center_horizontal"
    android:visibility="gone"
    android:layout_marginTop="60dp"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <FrameLayout
        android:layout_width="220dp"
        android:layout_height="220dp">

        <!-- 4 brackets -->
        <ImageView android:src="@drawable/ic_brackets_tl"
                   android:layout_gravity="top|start" ... />
        <ImageView android:src="@drawable/ic_brackets_tr"
                   android:layout_gravity="top|end" ... />
        <ImageView android:src="@drawable/ic_brackets_bl"
                   android:layout_gravity="bottom|start" ... />
        <ImageView android:src="@drawable/ic_brackets_br"
                   android:layout_gravity="bottom|end" ... />

        <!-- صورة 3D للنبتة -->
        <ImageView
            android:src="@drawable/empty_plant_pot_3d"
            android:layout_gravity="center"
            android:layout_width="160dp"
            android:layout_height="160dp"
            android:scaleType="fitCenter" />
    </FrameLayout>

    <TextView
        android:text="@string/empty_history_title"
        android:textAppearance="@style/TextAppearance.PlantCare.Title"
        android:layout_marginTop="20dp" />

    <TextView
        android:text="@string/empty_history_subtitle"
        android:textAppearance="@style/TextAppearance.PlantCare.Body"
        android:textColor="@color/pc_on_surface_secondary"
        android:gravity="center"
        android:layout_marginTop="6dp"
        android:layout_marginHorizontal="40dp" />
</LinearLayout>
```

### 5.6 — G2 معيار قبول grep

```bash
grep -n 'heroProBanner' app/src/main/res/layout/fragment_today.xml
# المتوقع: سطر واحد

grep -n 'searchPill\|hero_search' app/src/main/res/layout/fragment_today.xml
# المتوقع: ≥ سطر واحد

grep -n 'weekStrip' app/src/main/res/layout/fragment_today.xml
# المتوقع: سطر واحد

grep -n 'empty_plant_pot_3d' app/src/main/res/layout/fragment_today.xml
# المتوقع: سطر واحد

grep -n 'greetingTitle\|greetingSubtitle' app/src/main/res/layout/fragment_today.xml
# المتوقع: 0 (محذوفان)
```

---

## 6 — Layer G3: Streak Card Rebuild

> **الهدف:** بطاقة streak موحّدة بدون divider داخلي، مع progress bar للتحديات.

### 6.1 — G3.1: استبدال streakCard

**في `fragment_today.xml`:** استبدال الـ MaterialCardView الكامل بـ:

```xml
<com.google.android.material.card.MaterialCardView
    android:id="@+id/streakCard"
    app:cardCornerRadius="24dp"
    app:cardElevation="0dp"
    app:cardBackgroundColor="@color/pc_surface"
    app:strokeWidth="1dp"
    app:strokeColor="@color/pc_outline_variant"
    android:layout_marginTop="20dp"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <LinearLayout
        android:orientation="vertical"
        android:padding="20dp"
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <!-- Header row: icon + count -->
        <LinearLayout
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:layout_width="match_parent"
            android:layout_height="wrap_content">

            <FrameLayout
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:layout_marginEnd="14dp"
                android:background="@drawable/bg_chip_circle_primary">
                <ImageView
                    android:src="@drawable/ic_water"
                    app:tint="@color/pc_on_primary"
                    android:layout_gravity="center"
                    android:layout_width="24dp"
                    android:layout_height="24dp" />
            </FrameLayout>

            <LinearLayout
                android:orientation="vertical"
                android:layout_weight="1"
                android:layout_width="0dp"
                android:layout_height="wrap_content">

                <TextView
                    android:id="@+id/streakDaysText"
                    android:text="@string/streak_zero"
                    android:textAppearance="@style/TextAppearance.PlantCare.Title"
                    android:textSize="22sp" />

                <TextView
                    android:id="@+id/streakBestText"
                    android:textAppearance="@style/TextAppearance.PlantCare.Body"
                    android:textSize="13sp"
                    android:textColor="@color/pc_on_surface_secondary"
                    android:visibility="gone" />
            </LinearLayout>
        </LinearLayout>

        <!-- Challenges (لا divider قبلها — مسافة فقط) -->
        <LinearLayout
            android:id="@+id/challengesContainer"
            android:orientation="vertical"
            android:layout_marginTop="20dp"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

**تعديل `TodayFragment.java#renderChallenges`:** كل challenge يصبح:

```
┌──────────────────────────────────────┐
│ Water for 7 days                3/7  │
│ ▓▓▓░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  │
└──────────────────────────────────────┘
```

استخدام `LinearProgressIndicator` (Material Components) بـ `app:indicatorColor="pc_primary"` و `app:trackColor="pc_background_dim"`.

### 6.2 — G3 معيار قبول grep

```bash
grep -n 'View' app/src/main/res/layout/fragment_today.xml | grep '1dp' | grep -i 'background.*outline'
# المتوقع: 0 (لا divider لـ 1dp داخل streakCard)

grep -n 'LinearProgressIndicator' app/src/main/java/com/example/plantcare/TodayFragment.java
# المتوقع: ≥ سطر واحد (استخدام جديد)

grep -n 'pc_outlineVariant\|pc_outline_variant' app/src/main/res/layout/fragment_today.xml
# المتوقع: 1 (stroke فقط، ليس divider)
```

---

## 7 — Layer G4: Calendar Merge into Home

> **الهدف:** Calendar tab تختفي من Bottom Nav، والـCompose Calendar يُفتَح كـbottom sheet من زرّ في Home.

### 7.1 — G4.1: حذف Calendar tab

(تمّ في G1.1 — `nav_calendar` غير موجود في menu).

### 7.2 — G4.2: زرّ "Full Calendar" في Home

إضافة في `fragment_today.xml` تحت `weekStrip`:

```xml
<com.google.android.material.button.MaterialButton
    android:id="@+id/openFullCalendar"
    style="@style/PlantCare.Button.Text"
    android:text="@string/view_full_calendar"
    android:textColor="@color/pc_primary"
    app:icon="@drawable/ic_calendar_arrow"
    app:iconGravity="end"
    android:layout_gravity="end"
    android:layout_marginEnd="8dp"
    android:layout_marginTop="4dp"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content" />
```

### 7.3 — G4.3: BottomSheetDialogFragment للـCalendar

**ملف جديد:** `app/src/main/java/com/example/plantcare/ui/calendar/FullCalendarBottomSheet.kt`

```kotlin
class FullCalendarBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(...): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    // إعادة استخدام الـ Compose Calendar الموجود
                    MainScreenComposeBridge()
                }
            }
        }
    }

    override fun getTheme() = R.style.BottomSheetDialog_Modern
}
```

**في `TodayFragment.java`:**

```java
openFullCalendar.setOnClickListener(v ->
    new FullCalendarBottomSheet().show(getParentFragmentManager(), "calendar")
);
```

### 7.4 — G4.4: Calendar Empty State جديد

تعديل في `MainScreenCompose.kt` (داخل Compose):

```kotlin
@Composable
fun CalendarEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.empty_plant_pot_3d),
            contentDescription = null,
            modifier = Modifier.size(160.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.calendar_empty_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { /* add reminder */ },
            colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.pc_primary))
        ) {
            Text(stringResource(R.string.calendar_add_reminder))
        }
    }
}
```

### 7.5 — G4 معيار قبول grep

```bash
grep -n 'nav_calendar' app/src/main/res/menu/bottom_nav_menu.xml
# المتوقع: 0

grep -n 'FullCalendarBottomSheet' app/src/main/java/com/example/plantcare/
# المتوقع: ≥ 2 (تعريف + استخدام)

grep -n 'empty_plant_pot_3d' app/src/main/java/com/example/plantcare/weekbar/
# المتوقع: ≥ سطر واحد
```

---

## 8 — Layer G5: My Garden Redesign

> **الهدف:** بطاقات نباتات عمودية بصور كبيرة 16:9، حالة سقاية مرئية، empty state بصور 3D.

### 8.1 — G5.1: Item layout جديد

**استبدال `item_plant.xml` بـ:**

```xml
<com.google.android.material.card.MaterialCardView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="14dp"
    app:cardCornerRadius="24dp"
    app:cardElevation="0dp"
    app:strokeWidth="1dp"
    app:strokeColor="@color/pc_outline_variant"
    app:cardBackgroundColor="@color/pc_surface">

    <LinearLayout android:orientation="vertical" ...>

        <!-- Photo 16:9 -->
        <com.google.android.material.imageview.ShapeableImageView
            android:id="@+id/plantImage"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            app:layout_constraintDimensionRatio="16:9"
            android:scaleType="centerCrop"
            app:shapeAppearance="@style/ShapeAppearance.PlantCare.ThumbnailLarge" />

        <!-- Info row -->
        <LinearLayout android:orientation="horizontal" android:padding="16dp" ...>

            <LinearLayout android:orientation="vertical" android:layout_weight="1" ...>
                <TextView
                    android:id="@+id/plantName"
                    android:textAppearance="@style/TextAppearance.PlantCare.Title"
                    android:textSize="18sp" />
                <TextView
                    android:id="@+id/lastWatered"
                    android:textAppearance="@style/TextAppearance.PlantCare.Body"
                    android:textSize="13sp"
                    android:textColor="@color/pc_on_surface_secondary"
                    android:layout_marginTop="2dp" />
            </LinearLayout>

            <!-- Overdue badge -->
            <FrameLayout
                android:id="@+id/overdueBadge"
                android:visibility="gone"
                android:background="@drawable/bg_overdue_pill" ...>
                <TextView
                    android:id="@+id/overdueCount"
                    android:textColor="@color/pc_on_primary" .../>
            </FrameLayout>
        </LinearLayout>
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

### 8.2 — G5.2: Empty State

```xml
<LinearLayout
    android:id="@+id/emptyGardenState"
    android:visibility="gone"
    android:orientation="vertical"
    android:gravity="center"
    android:padding="40dp"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <ImageView
        android:src="@drawable/empty_plant_pot_3d"
        android:layout_width="180dp"
        android:layout_height="180dp" />

    <TextView
        android:text="@string/garden_empty_title"
        android:textAppearance="@style/TextAppearance.PlantCare.Title" />

    <TextView
        android:text="@string/garden_empty_subtitle"
        android:textAppearance="@style/TextAppearance.PlantCare.Body"
        android:textColor="@color/pc_on_surface_secondary"
        android:gravity="center" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/addFirstPlantBtn"
        style="@style/PlantCare.Button.Filled"
        android:text="@string/garden_add_first_plant"
        android:layout_marginTop="20dp" />
</LinearLayout>
```

### 8.3 — G5 معيار قبول grep

```bash
grep -n 'layout_constraintDimensionRatio="16:9"' app/src/main/res/layout/item_plant.xml
# المتوقع: سطر واحد

grep -n 'empty_plant_pot_3d' app/src/main/res/layout/fragment_my_plants.xml
# المتوقع: سطر واحد على الأقل
```

---

## 9 — Layer G6: Diagnose Tab Creation

> **الهدف:** Tab جديدة كلياً تعرض شاشة landing لميزة التشخيص، تفتح `DiseaseDiagnosisActivity` الموجودة، وتعرض تاريخ التشخيصات.

### 9.1 — G6.1: DiagnoseFragment

**ملف جديد:** `app/src/main/java/com/example/plantcare/ui/diagnose/DiagnoseFragment.kt`

```kotlin
class DiagnoseFragment : Fragment() {
    override fun onCreateView(...): View {
        return inflater.inflate(R.layout.fragment_diagnose, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<MaterialButton>(R.id.startDiagnoseBtn).setOnClickListener {
            startActivity(Intent(requireContext(), DiseaseDiagnosisActivity::class.java))
        }
        // load history into RecyclerView
        ...
    }
}
```

### 9.2 — G6.2: fragment_diagnose.xml

```xml
<androidx.core.widget.NestedScrollView ...>
    <LinearLayout android:orientation="vertical" android:padding="16dp">

        <!-- Hero card -->
        <com.google.android.material.card.MaterialCardView
            app:cardCornerRadius="24dp"
            app:cardElevation="0dp"
            app:cardBackgroundColor="@color/pc_primary_container">

            <ConstraintLayout android:padding="20dp" ...>
                <TextView
                    android:text="@string/diagnose_hero_title"
                    android:textAppearance="@style/TextAppearance.PlantCare.Title" />
                <TextView
                    android:text="@string/diagnose_hero_subtitle"
                    android:textAppearance="@style/TextAppearance.PlantCare.Body" />
                <MaterialButton
                    android:id="@+id/startDiagnoseBtn"
                    style="@style/PlantCare.Button.Filled"
                    android:text="@string/diagnose_start" />
                <ImageView android:src="@drawable/diagnose_3d_microscope" ... />
            </ConstraintLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- Recent diagnoses -->
        <TextView
            android:text="@string/diagnose_recent"
            android:textAppearance="@style/TextAppearance.PlantCare.Title"
            android:textSize="18sp"
            android:layout_marginTop="24dp" />

        <RecyclerView android:id="@+id/recentDiagnoses" ... />

        <!-- Empty state -->
        <LinearLayout android:id="@+id/emptyDiagnoses" ...>
            <ImageView android:src="@drawable/empty_diagnose_3d" ... />
            <TextView android:text="@string/diagnose_empty" ... />
        </LinearLayout>
    </LinearLayout>
</androidx.core.widget.NestedScrollView>
```

### 9.3 — G6.3: MainActivity routing

في `MainActivity.java#selectTab`:

```java
case 2: // Diagnose
    fragment = new DiagnoseFragment();
    break;
```

### 9.4 — G6 معيار قبول grep

```bash
grep -n 'DiagnoseFragment' app/src/main/java/com/example/plantcare/MainActivity.java
# المتوقع: ≥ سطر واحد

ls app/src/main/res/layout/fragment_diagnose.xml
# المتوقع: موجود

grep -n 'startActivity.*DiseaseDiagnosisActivity' app/src/main/java/com/example/plantcare/ui/diagnose/DiagnoseFragment.kt
# المتوقع: ≥ سطر واحد
```

---

## 10 — Layer G7: Settings Premium Redesign

> **الهدف:** قائمة Pills مفردة بـglassmorphism، بدون sections مجمَّعة (نقض F2.6).

### 10.1 — G7.1: Hero Pro Banner (نسخة من Home)

نفس مكوّن G2.2 (component shared).

### 10.2 — G7.2: حذف SettingsCard و SettingsRow

**استبدال `fragment_settings.xml` بالكامل بـ:**

```xml
<NestedScrollView ...>
    <LinearLayout android:orientation="vertical" android:padding="16dp">

        <!-- Hero Banner (مطابق Home) -->
        <include layout="@layout/include_hero_pro_banner" android:id="@+id/proHeroCard" />

        <!-- قائمة Pills مسطّحة (لا cards مجمَّعة) -->

        <include layout="@layout/include_settings_pill" android:id="@+id/rowContact" />
        <include layout="@layout/include_settings_pill" android:id="@+id/rowRateUs" />
        <include layout="@layout/include_settings_pill" android:id="@+id/rowShareApp" />

        <View android:layout_height="14dp" />

        <include layout="@layout/include_settings_pill" android:id="@+id/rowName" />
        <include layout="@layout/include_settings_pill" android:id="@+id/rowEmail" />
        <include layout="@layout/include_settings_pill" android:id="@+id/rowLogout" />

        <View android:layout_height="14dp" />

        <include layout="@layout/include_settings_pill" android:id="@+id/rowTerms" />
        <include layout="@layout/include_settings_pill" android:id="@+id/rowPrivacy" />
        <include layout="@layout/include_settings_pill" android:id="@+id/rowRestore" />
        <include layout="@layout/include_settings_pill" android:id="@+id/rowDataExport" />

        <View android:layout_height="14dp" />

        <include layout="@layout/include_settings_pill" android:id="@+id/rowAdvanced" />
        <include layout="@layout/include_settings_pill" android:id="@+id/rowDeleteAccount" />
    </LinearLayout>
</NestedScrollView>
```

### 10.3 — G7.3: include_settings_pill.xml (مكوّن جديد)

```xml
<com.google.android.material.card.MaterialCardView xmlns:android="..."
    android:layout_width="match_parent"
    android:layout_height="64dp"
    android:layout_marginBottom="10dp"
    app:cardCornerRadius="32dp"
    app:cardElevation="0dp"
    app:cardBackgroundColor="@color/pc_surface_translucent"
    android:foreground="?attr/selectableItemBackground"
    android:clickable="true"
    android:focusable="true">

    <LinearLayout
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingStart="16dp"
        android:paddingEnd="20dp"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <com.google.android.material.imageview.ShapeableImageView
            android:id="@+id/pillIcon"
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:padding="10dp"
            android:background="@drawable/bg_pill_icon_circle"
            app:tint="@color/pc_on_surface" />

        <TextView
            android:id="@+id/pillTitle"
            android:textAppearance="@style/TextAppearance.PlantCare.Body"
            android:textSize="16sp"
            android:textColor="@color/pc_on_surface"
            android:layout_marginStart="16dp"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

### 10.4 — G7.4: SettingsFragment.kt updates

تعديل لـ binding كل pill بـ `findViewById<View>(R.id.rowContact).findViewById<TextView>(R.id.pillTitle).text = ...`. أو الأفضل: تعريف helper:

```kotlin
private fun bindPill(rowId: Int, iconRes: Int, title: String, onClick: () -> Unit) {
    val row = view.findViewById<MaterialCardView>(rowId)
    row.findViewById<ShapeableImageView>(R.id.pillIcon).setImageResource(iconRes)
    row.findViewById<TextView>(R.id.pillTitle).text = title
    row.setOnClickListener { onClick() }
}
```

### 10.5 — G7 معيار قبول grep

```bash
grep -n 'SettingsCard\|SettingsRow\|SettingsCardInner' app/src/main/res/layout/fragment_settings.xml
# المتوقع: 0

grep -n 'SettingsSectionLabel' app/src/main/res/layout/fragment_settings.xml
# المتوقع: 0

grep -c 'include_settings_pill' app/src/main/res/layout/fragment_settings.xml
# المتوقع: ≥ 10
```

---

## 11 — Layer G8: Dialogs Modernization

> **الهدف:** Add Reminder dialog (المكسور) + Month picker + بقية الـdialogs بـTextInputLayout صحيح، بدون حقول مكشوفة.

### 11.1 — G8.1: Add Reminder Dialog

**استبدال `dialog_add_reminder.xml` بحقول TextInputLayout صريحة:**

```xml
<com.google.android.material.textfield.TextInputLayout
    style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
    app:boxCornerRadiusTopStart="18dp"
    app:boxCornerRadiusTopEnd="18dp"
    app:boxCornerRadiusBottomStart="18dp"
    app:boxCornerRadiusBottomEnd="18dp"
    app:boxStrokeColor="@color/pc_outline_variant"
    android:hint="@string/reminder_date_hint"
    android:layout_marginTop="14dp"
    ...>

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/dateField"
        android:focusable="false"
        android:clickable="true"
        android:inputType="none"
        app:drawableEndCompat="@drawable/ic_calendar_picker"
        ... />
</com.google.android.material.textfield.TextInputLayout>
```

نفس النمط لـ Description، Title، Repeat، Foto aufnehmen.

### 11.2 — G8.2: Month Picker

تحسين Dialog الموجود:
- يوم اليوم: دائرة `pc_primary` صلبة مع نص أبيض، **لا** circle خافت.
- زر Close يصبح Filled Tonal بدل Outlined.

### 11.3 — G8 معيار قبول grep

```bash
grep -c 'TextInputLayout' app/src/main/res/layout/dialog_add_reminder.xml
# المتوقع: ≥ 3 (Title + Description + Date)

grep -n 'boxStrokeColor' app/src/main/res/layout/dialog_add_reminder.xml
# المتوقع: ≥ 3
```

---

## 12 — Layer G9: Asset Pipeline (5 صور 3D + 6 vector drawables)

> **الهدف:** توفير كل الأصول البصرية الجديدة، توضيح المصادر، التراخيص، الأحجام.

### 12.1 — G9.1: قائمة الصور المطلوبة من Fady

| # | اسم الملف | الاستخدام | الأبعاد المنصوحة | المصدر المقترح |
|---|---|---|---|---|
| 1 | `hero_pots_3d.webp` | Pro banner في Home و Settings | 560×600px PNG ⇒ WebP 85% | freepik.com (queries: "3d plants pots", "potted plants illustration") |
| 2 | `empty_plant_pot_3d.webp` | Empty state في Home + Calendar + Garden | 600×600px PNG ⇒ WebP 85% | storyset.com (search: "houseplant", "plant pot") |
| 3 | `diagnose_3d_microscope.webp` | Diagnose tab hero | 400×400px | freepik.com ("plant disease", "leaf check") |
| 4 | `empty_diagnose_3d.webp` | Diagnose empty state | 400×400px | storyset.com ("healthcare", "checkup") |
| 5 | `streak_icon_3d.webp` | Streak card (اختياري) | 200×200px | storyset.com ("flame", "achievement") |

**الحجم المُستهدَف:** كل صورة < 80KB، المجموع < 400KB.

**توثيق المصادر:** `app/src/main/assets/CREDITS.md` يجب أن يُحدَّث:

```markdown
## Phase G — 3D Illustrations

| File | Source | Author | License | URL |
|---|---|---|---|---|
| hero_pots_3d.webp | Freepik | ... | Free | https://... |
| empty_plant_pot_3d.webp | Storyset | ... | CC0 | https://... |
| ...
```

### 12.2 — G9.2: Vector Drawables الإضافية

**6 ملفات يصنعها Claude:**

1. `ic_brackets_tl.xml` — قوس L علوي-يسار، stroke 5dp `pc_primary`.
2. `ic_brackets_tr.xml` — علوي-يمين.
3. `ic_brackets_bl.xml` — سفلي-يسار.
4. `ic_brackets_br.xml` — سفلي-يمين.
5. `ic_search_premium.xml` — أيقونة عدسة محدّثة (24dp).
6. `bg_overdue_pill.xml` — Pill مرجاني للـoverdue badges.

### 12.3 — G9.3: WebP Conversion

بعد رفع Fady للصور PNG/JPG:

```bash
# على Windows مع Android Studio image asset tool أو cwebp.exe
cwebp.exe -q 85 hero_pots.png -o hero_pots_3d.webp
```

أو من Android Studio: Right-click on PNG → Convert to WebP → quality 85.

### 12.4 — G9 معيار قبول

```bash
ls app/src/main/res/drawable-nodpi/hero_pots_3d.webp \
   app/src/main/res/drawable-nodpi/empty_plant_pot_3d.webp \
   app/src/main/res/drawable-nodpi/diagnose_3d_microscope.webp \
   app/src/main/res/drawable-nodpi/empty_diagnose_3d.webp
# المتوقع: كل الملفات موجودة

# مجموع حجم الـassets
du -ksh app/src/main/res/drawable-nodpi/*.webp | awk '{s+=$1} END {print s "KB"}'
# المتوقع: ≤ 400 KB

grep -c 'CREDITS' app/src/main/assets/CREDITS.md
# المتوقع: ≥ 1
```

---

## 13 — Layer G10: Polish النهائي

### 13.1 — G10.1: Animations

- إضافة fade-in 300ms للـHero banner عند فتح Home (`MaterialContainerTransform`).
- Tab switching: `FragmentTransaction.setReorderingAllowed(true)` + slide animations.

### 13.2 — G10.2: Haptics

في كل CTA و FAB:

```kotlin
view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
```

### 13.3 — G10.3: System UI

في `themes.xml`:

```xml
<item name="android:statusBarColor">@color/pc_background</item>
<item name="android:windowLightStatusBar">true</item>
<item name="android:navigationBarColor">@color/pc_surface</item>
<item name="android:windowLightNavigationBar">true</item>
```

### 13.4 — G10.4: Dark mode parity

تحديث `values-night/colors.xml` بنفس الـtokens لكن مع inversions:
- `pc_background` → `#0E2218` (Dark forest)
- `pc_surface` → `#1F3D2A`
- `pc_primary` يبقى `#3FA856`
- إلخ.

---

## 14 — Asset Inventory (مرجع شامل)

### 14.1 — ملفات جديدة (سيُنشئها Claude)

```
app/src/main/res/
├── values/
│   ├── shapes.xml                                (NEW)
│   └── colors_phase_g_backup_of_f.xml            (NEW - backup فقط)
├── font/
│   ├── manrope.xml                               (NEW)
│   ├── inter.xml                                 (NEW)
│   ├── manrope_extra_bold.ttf                    (NEW - Fady ينزّل)
│   ├── inter_regular.ttf                         (NEW - Fady ينزّل)
│   └── inter_semibold.ttf                        (NEW - Fady ينزّل)
├── drawable/
│   ├── bg_leaf_pattern.xml                       (NEW)
│   ├── bg_screen_root.xml                        (NEW)
│   ├── bg_fab_camera_donut.xml                   (NEW)
│   ├── bg_weekday_today.xml                      (NEW)
│   ├── bg_pill_icon_circle.xml                   (NEW)
│   ├── bg_overdue_pill.xml                       (NEW)
│   ├── ic_brackets_tl.xml                        (NEW)
│   ├── ic_brackets_tr.xml                        (NEW)
│   ├── ic_brackets_bl.xml                        (NEW)
│   ├── ic_brackets_br.xml                        (NEW)
│   ├── ic_search_premium.xml                     (NEW)
│   ├── ic_nav_home.xml                           (NEW)
│   ├── ic_nav_diagnose.xml                       (NEW)
│   └── ic_calendar_arrow.xml                     (NEW)
├── drawable-nodpi/
│   ├── hero_pots_3d.webp                         (NEW - Fady يرفع)
│   ├── empty_plant_pot_3d.webp                   (NEW - Fady يرفع)
│   ├── diagnose_3d_microscope.webp               (NEW - Fady يرفع)
│   ├── empty_diagnose_3d.webp                    (NEW - Fady يرفع)
│   └── streak_icon_3d.webp                       (OPTIONAL)
└── layout/
    ├── fragment_diagnose.xml                     (NEW)
    └── include_settings_pill.xml                 (NEW)
    └── include_hero_pro_banner.xml               (NEW)

app/src/main/java/com/example/plantcare/ui/
├── diagnose/
│   └── DiagnoseFragment.kt                       (NEW)
└── calendar/
    └── FullCalendarBottomSheet.kt                (NEW)

app/src/main/assets/
└── CREDITS.md                                    (UPDATED)
```

### 14.2 — ملفات معدَّلة جذرياً

```
app/src/main/res/
├── values/
│   ├── colors.xml                                (REWRITE - palette جديدة)
│   ├── themes.xml                                (REWRITE - typography + shapes)
│   └── styles.xml                                (UPDATE - حذف SettingsRow/Card legacy)
├── menu/
│   └── bottom_nav_menu.xml                       (REWRITE - 4 tabs ⇒ 4 tabs مختلفة)
├── layout/
│   ├── activity_main.xml                         (UPDATE - FAB حجم + indicator)
│   ├── fragment_today.xml                        (REWRITE)
│   ├── fragment_my_plants.xml                    (REWRITE)
│   ├── fragment_settings.xml                     (REWRITE)
│   ├── item_plant.xml                            (REWRITE - بطاقات عمودية)
│   ├── dialog_add_reminder.xml                   (REWRITE - TextInputLayout)
│   ├── dialog_add_manual_reminder.xml            (UPDATE - نفس النمط)
│   └── … (~ 15 dialog files آخرى)               (UPDATE - تطبيق Pill buttons)
└── color/
    └── bottom_nav_item_tint.xml                  (UPDATE - colors)

app/src/main/java/com/example/plantcare/
├── MainActivity.java                             (UPDATE - tab routing)
├── TodayFragment.java                            (UPDATE - week strip + new layout binds)
└── ui/settings/SettingsFragment.kt               (UPDATE - bind pills)
```

### 14.3 — ملفات محذوفة

```
app/src/main/res/font/
├── dm_serif_display.ttf                          (DELETE - بعد G2)
├── nunito.ttf                                    (DELETE - بعد G7)
└── playfair_display.ttf                          (DELETE - dead code)
```

---

## 15 — معايير القبول الإجمالية (Acceptance Criteria Master List)

كل Layer لا يُعدّ مكتملاً إلا إذا:

1. ✅ كل أوامر grep المذكورة في الـ Layer تُعطي الناتج المتوقَّع.
2. ✅ `gradlew.bat assembleProdRelease` ينجح.
3. ✅ عدد warnings ≤ baseline.
4. ✅ entry جديد في `PROGRESS.md` بصيغة CLAUDE.md §5.
5. ✅ لقطة شاشة من جهاز Fady تُظهر الشاشة المعنيّة بشكلها الجديد، يقارنها Fady مع المرجع.
6. ✅ لا regression في الميزات الوظيفية (التطبيق يبدأ، التبويبات تتنقّل، Add reminder يحفظ، إلخ).

---

## 16 — Risk Register (مخاطر معروفة + تخفيف)

| # | المخاطرة | احتمال | أثر | استراتيجية التخفيف |
|---|---|---|---|---|
| R1 | Phase F قرارات نُقضت ⇒ مراجعة Code Review طويلة | عالي | متوسط | كل layer = commit مستقل، tag منفصل، PHASE_G_REVERT.md جاهز |
| R2 | الصور 3D غير مرخّصة لـ commercial use | متوسط | عالي | استخدام Storyset (CC0) أو Freepik Free فقط؛ توثيق في CREDITS.md |
| R3 | حجم AAB > 25MB | منخفض | عالي | WebP 85%، باسطة بـ baseline قبل البدء، gate في G9 |
| R4 | Manrope/Inter لا يدعمان حروف ألمانية خاصة (ä ö ü ß) | منخفض | متوسط | اختبار manual باللغة الألمانية قبل اعتماد كل خط (Google Fonts تدعم) |
| R5 | Compose Calendar يستخدم colors قديمة | منخفض | متوسط | F9.3 يؤكّد أن Compose يستخدم `colorResource()` ⇒ يتكيّف تلقائياً |
| R6 | Bottom Nav 4 tabs مختلفة يكسر `KEY_SELECTED_TAB` | متوسط | عالي | تحديث indices في MainActivity + اختبار manual للـpersistence |
| R7 | TextInputLayout box يقاوم theme override | منخفض | منخفض | استخدام `Widget.Material3.TextInputLayout.OutlinedBox` صراحة |
| R8 | Material Components version لا يدعم itemActiveIndicatorStyle | متوسط | عالي | فحص `app/build.gradle` قبل البدء؛ upgrade إلى 1.10+ إن لزم |
| R9 | Cradle FAB يصطدم بـAdMob banner | منخفض | منخفض | DEFERRED_ISSUES #28 موجود، حلّ في G10 |
| R10 | الـ 60 ملف layout الذي عدّلته Phase F يحتاج إعادة فحص | عالي | متوسط | grep -rn pc_outlineVariant بعد G0 يكشف الشاشات المتأثّرة |

---

## 17 — Rollback Strategy

### 17.1 — Tags بعد كل Layer

```bash
git tag phase-g-0-design-system
git tag phase-g-1-bottom-nav
git tag phase-g-2-home
...
git tag phase-g-10-polish
```

### 17.2 — Revert كامل Phase G

```bash
git checkout phase-f-final-baseline
# أو
git revert phase-g-0-design-system..HEAD
```

### 17.3 — Revert layer واحد فقط

```bash
git revert phase-g-<n>~..phase-g-<n>
```

### 17.4 — PHASE_G_REVERT.md (يُكتب بعد G10)

ملف يوثّق:
- ما الذي يُلغى بكل revert.
- ما الذي يبقى (مثلاً: حذف dm_serif_display irreversible).
- خطوات الاستعادة.

---

## 18 — Timeline تقديري

| Layer | الوقت المقدَّر | اعتماديات |
|---|---|---|
| G0 — Design System | 3-4 ساعات | لا شيء |
| G1 — Bottom Nav | 2-3 ساعات | G0 |
| G2 — Home Hero | 4-6 ساعات | G0, G1, G9.1 (hero_pots image) |
| G3 — Streak Card | 1-2 ساعة | G0, G2 |
| G4 — Calendar Merge | 3-4 ساعات | G0, G1, G2 |
| G5 — My Garden | 3-4 ساعات | G0, G9.1 (empty_plant image) |
| G6 — Diagnose Tab | 3-4 ساعات | G0, G1, G9.1 (diagnose images) |
| G7 — Settings | 4-5 ساعات | G0, G9.1 (hero_pots) |
| G8 — Dialogs | 4-5 ساعات | G0 |
| G9 — Assets | 2-3 ساعات | يبدأ بالتوازي من G0 — Fady يرفع |
| G10 — Polish | 3-4 ساعات | كل ما سبق |
| **المجموع** | **32-44 ساعة** | — |

**جلسات مقترحة:** 8-12 جلسة بمعدّل 4 ساعات لكل جلسة.

---

## 19 — Post-Launch Verification (بعد G10)

### 19.1 — Visual QA Matrix

اختبار manual لكل شاشة مقابل المرجع، تعبئة جدول:

| Surface | المظهر يطابق المرجع؟ | الملاحظات |
|---|---|---|
| Home — Hero banner | ✅/❌ | … |
| Home — Search bar | ✅/❌ | … |
| Home — Week strip | ✅/❌ | … |
| Home — Empty state | ✅/❌ | … |
| Home — Streak card | ✅/❌ | … |
| My Garden — list | ✅/❌ | … |
| My Garden — empty | ✅/❌ | … |
| Diagnose — landing | ✅/❌ | … |
| Settings — Pills | ✅/❌ | … |
| Calendar bottom sheet | ✅/❌ | … |
| Add reminder dialog | ✅/❌ | … |
| Month picker | ✅/❌ | … |

### 19.2 — Accessibility Pass

- TalkBack: كل CTA يقرأ بوضوح.
- Touch targets ≥ 48dp.
- Contrast ratio ≥ 4.5:1 للنص العادي.

### 19.3 — Final AAB Size Check

```bash
gradlew.bat :app:bundleProdRelease
dir app\build\outputs\bundle\prodRelease\*.aab
# يجب أن يبقى < 25 MB
```

### 19.4 — اللقطات النهائية

التقاط 5 لقطات (Home, Garden, Diagnose, Settings, Add reminder) على نفس الجهاز الذي التُقطت عليه لقطات Phase F، ومقارنتها جنباً إلى جنب.

---

## 20 — Phase G Completion Checklist (للإغلاق الرسمي)

عند إكمال كل ما سبق:

- [ ] كل أوامر grep لكل Layer تُعطي الناتج المتوقَّع.
- [ ] `gradlew.bat assembleProdRelease` ينجح بلا warnings جديدة.
- [ ] AAB < 25 MB.
- [ ] Visual QA Matrix كلها ✅.
- [ ] TalkBack pass.
- [ ] PROGRESS.md فيه 11 entries (G0-G10) موثَّقة.
- [ ] PlantCare_Features_Inventory.md محدَّث بـ"Phase G complete — visual overhaul".
- [ ] PHASE_G_REVERT.md مكتمل.
- [ ] CREDITS.md يحوي كل الصور.
- [ ] Fady يضع علامة "Phase G ✅" في commit نهائي.

---

## 21 — ما بعد Phase G

عند انتهاء Phase G، التطبيق يكون بصرياً جاهزاً. الخطوات التالية حسب CLAUDE.md §2:

```
Phase G (Visual Overhaul) ✅
  → Phase A متبقّي (Security finalization)
    → Phase B (i18n shipping — DE + EN)
      → Phase C (Architecture - DAO out of UI)
        → Phase D (QA)
          → Phase E (Release to Play Store)
```

---

**نهاية الخطة.**

*مرّ على هذه الخطة قبل البدء، علّق على أي layer، وأكّد على القرارات في القسم 1.4 (Branch strategy). بعد التأكيد، أبدأ تنفيذ G0 فوراً.*
