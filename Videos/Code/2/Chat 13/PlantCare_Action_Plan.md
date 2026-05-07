# PlantCare — خطة العمل التنفيذية للوصول إلى النشر التجاري
### Action Plan مبنيّ من الأساس إلى القمة (Bottom-up)
**التاريخ: 23 أبريل 2026**
**المرجع:** `PlantCare_Review_Report.md` (الإصدار الثاني)
**صاحب التنفيذ:** Fady
**الهدف النهائي:** تطبيق جاهز للنشر التجاري في السوق الألماني مع نموذج Freemium مفعّل

---

## مبدأ البناء (اقرأ قبل البدء)

هذه الخطة مبنية على مبدأ صارم: **لا تبني شيئاً سيُهدم لاحقاً**. الطبقات مرتّبة بحيث كل طبقة تُشكّل أساساً صلباً للطبقات التي فوقها. مخالفة الترتيب = إعادة عمل.

**قاعدة ذهبية:**
> قبل أن تنتقل إلى الطبقة التالية، تأكّد أن **كل** "معايير الانتقال" (Exit Criteria) في الطبقة الحالية قد تحققت.

**رموز الحالة المستخدمة:**
- 🔴 حرج (blocker نشر)
- 🟠 عالية الأولوية
- 🟡 متوسطة
- 🟢 منخفضة / Nice-to-have
- ⏱️ الوقت التقديري

---

## نظرة عامة على الطبقات

| الطبقة | المحور | الوقت | الحالة |
|-------|--------|------|--------|
| **Layer 0** | الهوية والنطاق (Identity & Namespace) | 2-3 أيام | ⬜ لم يبدأ |
| **Layer 1** | أمن البيانات والـ Schema | 3-4 أيام | ⬜ لم يبدأ |
| **Layer 2** | أساس الاختبارات (Testing Foundation) | 4-5 أيام | ⬜ لم يبدأ |
| **Layer 3** | تصليب البنية المعمارية | 5-7 أيام | ⬜ لم يبدأ |
| **Layer 4** | التدويل (i18n) | 3-4 أيام | ⬜ لم يبدأ |
| **Layer 5** | تنظيف وتحسين | 2-3 أيام | ⬜ لم يبدأ |
| **Layer 6** | البنية التجارية (Billing + Store) | 5-7 أيام | ⬜ لم يبدأ |
| **Layer 7** | ضمان الجودة (QA) | 4-5 أيام | ⬜ لم يبدأ |
| **Layer 8** | الإطلاق (Soft Launch) | 7-10 أيام | ⬜ لم يبدأ |
| **Layer 9** | نمو ما بعد الإطلاق | مستمر | ⬜ لم يبدأ |

**المجموع حتى الإطلاق:** ~5-7 أسابيع عمل (مع التقاطع بين بعض المهام).

---

# LAYER 0 — الهوية والنطاق
## **لماذا أولاً؟** كل شيء يعتمد على `applicationId`. تغييره لاحقاً = إعادة كتابة اختبارات، إعادة بناء Firebase، إعادة إعداد Google Play.

### Task 0.1 — اختيار `applicationId` النهائي 🔴 ⏱️ 2 ساعة

**الخطوات:**
1. اختر نطاقاً تملكه أو ستُسجّله (مثال: `app.plantcare.de` أو `com.fadymerey.plantcare`)
2. تحقق من توفره على Google Play: `https://play.google.com/store/apps/details?id=<applicationId>`
3. تحقق من توفره على Firebase: أنشئ مشروعاً جديداً باسم يطابق التطبيق
4. **لا تختر `com.plantcare`** لأنه قد يكون محجوزاً لشركات أخرى

**معايير النجاح:**
- [ ] `applicationId` مكتوب على ورقة وسيُستخدم في كل الخطوات التالية دون تغيير
- [ ] لديك حساب Google Play Developer مُفعَّل (25$ لمرة واحدة)
- [ ] لديك حساب Firebase على نفس البريد الإلكتروني

---

### Task 0.2 — إنشاء مشروع Firebase جديد 🔴 ⏱️ 3 ساعات

**الخطوات:**
1. Firebase Console → Add Project → باسم `PlantCare-Prod`
2. فعّل: Authentication (Google + Email/Password)، Firestore، Storage، Analytics، Crashlytics
3. أضِف Android App بـ `applicationId` الجديد
4. نزّل `google-services.json` الجديد
5. **احتفظ بـ `google-services.json` القديم** في مجلد backup باسم `google-services-old.json`
6. ضع الملف الجديد في `app/google-services.json`
7. في Firebase Console → Project Settings → Service Accounts → Generate Private Key → احفظ آمناً (للاستخدام في Cloud Functions لاحقاً)

**ملاحظة مهمة:**
- إذا كان لديك مستخدمو إنتاج على المشروع القديم، **لا تحذفه**. أبقِه قيد التشغيل بالتوازي حتى migration البيانات.
- إذا كان المشروع القديم بيئة تطوير فقط، يمكن أرشفته بعد النقل.

**معايير النجاح:**
- [ ] `google-services.json` الجديد في `app/`
- [ ] Firebase Console يُظهر تسجيل دخول ناجح من جهاز اختبار
- [ ] Crashlytics يستقبل test crash

---

### Task 0.3 — تغيير `applicationId` في المشروع 🔴 ⏱️ 1 يوم

**الملفات المتأثرة:**
- `app/build.gradle`
- `app/src/main/AndroidManifest.xml`
- `app/proguard-rules.pro`
- `google-services.json`

**الخطوات:**
1. في `app/build.gradle`:
   ```gradle
   defaultConfig {
       applicationId "app.plantcare.de"  // الجديد
       namespace "com.example.plantcare"  // أبقِه مؤقتاً للتوافق مع Java packages
       // ...
   }
   ```
2. **ملاحظة معمارية:** `applicationId` و`namespace` يمكن أن يكونا مختلفين. `namespace` يحدد Java packages (`com.example.plantcare.*`)، بينما `applicationId` هو معرّف التطبيق على الجهاز والـ Play Store.
3. إذا أردت تنظيف Java packages أيضاً (Task 0.5 الاختياري)، افعل ذلك **الآن** قبل أي اختبار.
4. تحديث `versionCode = 1` و `versionName = "0.1.0"` (Pre-release)
5. Sync Gradle → Build → Install على جهاز جديد → تحقق من عدم تعارض مع أي نسخة قديمة مُثبّتة

**معايير النجاح:**
- [ ] البناء ينجح بدون أخطاء
- [ ] التطبيق يُثبَّت بـ `applicationId` الجديد
- [ ] تسجيل دخول يعمل مع Firebase الجديد
- [ ] `./gradlew :app:assembleDebug` ينجح

---

### Task 0.4 — إعداد ProductFlavors (Debug/Prod) 🟠 ⏱️ 3 ساعات

**لماذا؟** لتجنب تلويث بيانات الإنتاج أثناء التطوير.

**الخطوات:**
1. في `app/build.gradle`:
   ```gradle
   productFlavors {
       dev {
           applicationIdSuffix ".dev"
           versionNameSuffix "-dev"
           resValue "string", "app_name", "PlantCare Dev"
       }
       prod {
           resValue "string", "app_name", "PlantCare"
       }
   }
   ```
2. ضع `google-services.json` للـ Dev في `app/src/dev/` وللـ Prod في `app/src/prod/`
3. اختر الـ flavor الافتراضي `dev` للتطوير اليومي

**معايير النجاح:**
- [ ] يمكن تثبيت النسختين (Dev وProd) على نفس الجهاز دون تعارض
- [ ] كل نسخة تتصل بمشروع Firebase الخاص بها

---

### Task 0.5 — (اختياري) تنظيف Java Package من `com.example` 🟡 ⏱️ 1-2 يوم

**الخطر:** هذا يغيّر اسم الـ package لـ 116 ملف. قد يكسر serialization في Firestore إذا كانت الكلاسات مخزّنة بالاسم.

**فقط نفّذه إذا:**
- لديك اختبارات تمرّ حالياً (بعد Layer 2)
- لم تنشر بعد أي بيانات حقيقية في Firestore

**الخطوات:**
1. في Android Studio: Right-click على `com.example.plantcare` → Refactor → Rename → `app.plantcare.de`
2. تحديث `namespace` في `build.gradle` ليطابق
3. تحديث كل `@DocumentId` أو class references مستخدمة في Firestore
4. بناء + تشغيل كل الاختبارات

**معايير النجاح:**
- [ ] كل الاختبارات تمر
- [ ] قراءة/كتابة Firestore تعمل

**إن لم تنفّذ:** أبقِ `namespace = com.example.plantcare` والـ `applicationId = app.plantcare.de` مختلفين — هذا مقبول لكن غير مثالي.

---

### Task 0.6 — إعداد Privacy Policy و Terms of Service 🔴 ⏱️ 1 يوم

**متطلبات Google Play 2026:**
- Privacy Policy إلزامي لأي تطبيق يجمع بيانات
- Data Safety Declaration
- إذا كان التطبيق موجّه للأطفال أو مختلط، تصريح COPPA

**الخطوات:**
1. استخدم generator مثل `https://app-privacy-policy-generator.firebaseapp.com/` أو اكتب يدوياً
2. يجب أن تُذكر:
   - Firebase Analytics (بيانات الاستخدام)
   - Firebase Auth (بريد، اسم، صورة)
   - Firestore (بيانات النباتات والصور)
   - PlantNet API (إرسال صور للتعرّف)
   - Location (إن استُخدم — Weather)
3. استضفها على GitHub Pages أو خدمة مجانية
4. احفظ الرابط للاستخدام في Layer 6 (Google Play Console)
5. اكتب Terms of Service مبسّطة (يمكن في نفس الصفحة)

**معايير النجاح:**
- [ ] رابط Privacy Policy HTTPS يعمل
- [ ] يذكر كل الخدمات الخارجية المستخدمة
- [ ] متوفر بالألمانية (للسوق المستهدف) + الإنجليزية

---

## ✅ Exit Criteria — Layer 0

قبل الانتقال لـ Layer 1، تأكد من:

- [ ] `applicationId` نهائي ومثبّت (لن يتغير)
- [ ] Firebase Project جديد يعمل مع `google-services.json` الجديد
- [ ] Dev/Prod flavors منفصلان
- [ ] Privacy Policy متاحة على URL عام
- [ ] Google Play Developer account مُفعَّل

---

# LAYER 1 — أمن البيانات والـ Schema
## **لماذا ثانياً؟** تعديل Schema أو قواعد الأمان لاحقاً = migration معقد + إعادة كتابة اختبارات. افعلها الآن قبل الاختبارات.

### Task 1.1 — كتابة `firestore.rules` 🔴 ⏱️ 1 يوم

**الملف:** `firestore.rules` (في جذر المشروع)

**القواعد المقترحة:**
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // Helper: check user is authenticated and matches UID
    function isOwner(userId) {
      return request.auth != null && request.auth.uid == userId;
    }

    // Plants subcollection per user
    match /users/{userId}/plants/{plantId} {
      allow read, write: if isOwner(userId);
    }

    // Reminders per user
    match /users/{userId}/reminders/{reminderId} {
      allow read, write: if isOwner(userId);
    }

    // Rooms (categories) per user
    match /users/{userId}/rooms/{roomId} {
      allow read, write: if isOwner(userId);
    }

    // Photos metadata per user
    match /users/{userId}/photos/{photoId} {
      allow read, write: if isOwner(userId);
    }

    // Public plant catalog (read-only)
    match /plantCatalog/{plantId} {
      allow read: if request.auth != null;
      allow write: if false; // admin via Console only
    }

    // Deny everything else by default
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

**الخطوات:**
1. انشئ الملف في جذر المشروع
2. Firebase Console → Firestore → Rules → Paste → Publish
3. اختبر عبر Firebase Emulator: `firebase emulators:start --only firestore`
4. شغّل: `firebase firestore:rules:test --project=plantcare-prod firestore.rules`

**معايير النجاح:**
- [ ] محاولة قراءة `/users/other_user_id/plants` من حساب آخر تُرفَض
- [ ] المستخدم المصادَق يقرأ/يكتب بياناته فقط
- [ ] بدون مصادقة = رفض كامل

---

### Task 1.2 — إعادة هيكلة Firestore — UID بدل Email 🔴 ⏱️ 1-2 يوم

**المشكلة الحالية:** Document IDs مثل `email_plantId` تكشف PII.

**الهيكل الجديد:**
```
users/{uid}/
├── profile          # document with displayName, createdAt, etc
├── plants/{plantId}
├── reminders/{reminderId}
├── rooms/{roomId}
└── photos/{photoId}
```

**الخطوات:**
1. في `FirebaseSyncManager.java` أو Repository المعادل:
   - استبدل `user.getEmail()` بـ `FirebaseAuth.getInstance().getCurrentUser().getUid()`
   - استبدل كل `"users/" + email` بـ `"users/" + uid`
2. إذا كانت لديك بيانات إنتاج: اكتب **Cloud Function للـ migration** (Node.js + Firebase Admin SDK) تنقل من الهيكل القديم للجديد
3. بعد الـ migration، احذف الهيكل القديم
4. تحديث `firestore.rules` ليطابق الهيكل الجديد

**ملاحظة مهمة:**
- إن لم تكن لديك بيانات إنتاج: استخدم هيكل UID مباشرة، ولا حاجة لـ migration
- إن كانت لديك: خذ backup قبل الـ migration (Firebase Console → Firestore → Export)

**معايير النجاح:**
- [ ] لا يوجد Email في Document IDs
- [ ] كل البيانات الجديدة تُكتب تحت `users/{uid}/`
- [ ] التطبيق يقرأ/يكتب بشكل صحيح

---

### Task 1.3 — EncryptedSharedPreferences 🟠 ⏱️ 3 ساعات

**الملف المعني:** أينما تُستخدم `SharedPreferences` للبيانات الحساسة

**الخطوات:**
1. أضِف في `app/build.gradle`:
   ```gradle
   implementation "androidx.security:security-crypto:1.1.0-alpha06"
   ```
2. أنشئ `SecurePrefsHelper.kt`:
   ```kotlin
   object SecurePrefsHelper {
       fun get(context: Context): SharedPreferences {
           val masterKey = MasterKey.Builder(context)
               .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
               .build()
           return EncryptedSharedPreferences.create(
               context,
               "secure_prefs",
               masterKey,
               EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
               EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
           )
       }
   }
   ```
3. استبدل SharedPreferences للمفاتيح الحساسة: `current_user_email`, `is_guest`, أي `tokens`
4. أضِف migration من SharedPreferences العادية في أول تشغيل:
   ```kotlin
   fun migrateIfNeeded(context: Context) {
       val old = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
       val secure = SecurePrefsHelper.get(context)
       if (!secure.contains("migrated") && old.contains("current_user_email")) {
           secure.edit()
               .putString("current_user_email", old.getString("current_user_email", null))
               .putBoolean("is_guest", old.getBoolean("is_guest", false))
               .putBoolean("migrated", true)
               .apply()
           old.edit().remove("current_user_email").remove("is_guest").apply()
       }
   }
   ```
5. استدعِ `migrateIfNeeded()` في `App.onCreate()`

**معايير النجاح:**
- [ ] البريد والـ tokens لم تعد في SharedPreferences العادية
- [ ] التطبيق يعمل بعد تحديث (بدون إعادة تسجيل دخول)
- [ ] `adb shell run-as app.plantcare.de.dev cat /data/data/*/shared_prefs/prefs.xml` لا يُظهر بيانات حساسة

---

### Task 1.4 — إضافة Foreign Keys و Indexes في Room 🟠 ⏱️ 1 يوم

**Migration من v6 إلى v7.**

**الخطوات:**
1. في `Plant.java` / `WateringReminder.java` / `PlantPhoto.java` / `DiseaseDiagnosis`:
   ```java
   @Entity(
       tableName = "reminders",
       foreignKeys = @ForeignKey(
           entity = Plant.class,
           parentColumns = "id",
           childColumns = "plantId",
           onDelete = ForeignKey.CASCADE
       ),
       indices = {
           @Index("plantId"),
           @Index("userEmail"),
           @Index(value = {"userEmail", "completed"}) // compound
       }
   )
   public class WateringReminder { ... }
   ```
2. افعل نفس الشيء لـ `PlantPhoto` → `Plant`، و`DiseaseDiagnosis` → `Plant`
3. أضِف Migration 6→7 في `DatabaseMigrations.java`:
   ```java
   static final Migration MIGRATION_6_7 = new Migration(6, 7) {
       @Override
       public void migrate(@NonNull SupportSQLiteDatabase database) {
           // SQLite doesn't support adding FK to existing tables directly
           // Need to recreate tables. Example for reminders:
           database.execSQL("CREATE TABLE reminders_new (" +
               "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
               "plantId INTEGER NOT NULL, " +
               "userEmail TEXT, " +
               "completed INTEGER NOT NULL DEFAULT 0, " +
               // ... other columns
               "FOREIGN KEY(plantId) REFERENCES plants(id) ON DELETE CASCADE)");
           database.execSQL("INSERT INTO reminders_new SELECT * FROM reminders");
           database.execSQL("DROP TABLE reminders");
           database.execSQL("ALTER TABLE reminders_new RENAME TO reminders");
           database.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_plantId ON reminders(plantId)");
           database.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_userEmail ON reminders(userEmail)");
       }
   };
   ```
4. زِد `version = 7` في `@Database`
5. أضِف Migration في `ALL_MIGRATIONS` array

**معايير النجاح:**
- [ ] حذف نبات يحذف تذكيراته وصوره تلقائياً (CASCADE)
- [ ] `sqlite3` على DB ملف يُظهر `CREATE INDEX` statements
- [ ] Migration من v6 → v7 نجحت على جهاز عنده بيانات v6

---

### Task 1.5 — تفعيل `exportSchema = true` 🟡 ⏱️ 1 ساعة

**الخطوات:**
1. في `AppDatabase.java`:
   ```java
   @Database(entities = {...}, version = 7, exportSchema = true)
   ```
2. في `app/build.gradle`:
   ```gradle
   android {
       defaultConfig {
           javaCompileOptions {
               annotationProcessorOptions {
                   arguments += ["room.schemaLocation": "$projectDir/schemas".toString()]
               }
           }
       }
       sourceSets {
           androidTest.assets.srcDirs += files("$projectDir/schemas".toString())
       }
   }
   ```
3. أضف مجلد `app/schemas/` إلى Git (لا تضعه في `.gitignore`)
4. بناء المشروع — سيتم توليد JSON لكل Entity

**معايير النجاح:**
- [ ] ملف `app/schemas/com.example.plantcare.AppDatabase/7.json` موجود
- [ ] تمّ إضافته إلى git

---

## ✅ Exit Criteria — Layer 1

- [ ] `firestore.rules` منشور ويُرفض كل وصول غير مُصرَّح به
- [ ] هيكل Firestore يستخدم UID حصراً
- [ ] EncryptedSharedPreferences مُفعَّل للبيانات الحساسة
- [ ] Room v7 بـ Foreign Keys + Indexes + Migration 6→7 اختُبرت يدوياً
- [ ] Schema JSON مُصدَّر وفي git

---

# LAYER 2 — أساس الاختبارات (Testing Foundation)
## **لماذا ثالثاً؟** بدون اختبارات، كل refactor في Layer 3 مخاطرة. الاختبارات هي شبكة الأمان.

### Task 2.1 — إعداد dependencies للاختبار 🟠 ⏱️ 2 ساعة

**في `app/build.gradle`:**
```gradle
dependencies {
    // Unit tests
    testImplementation "junit:junit:4.13.2"
    testImplementation "org.mockito:mockito-core:5.11.0"
    testImplementation "org.mockito.kotlin:mockito-kotlin:5.2.1"
    testImplementation "androidx.arch.core:core-testing:2.2.0"
    testImplementation "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3"
    testImplementation "com.google.truth:truth:1.4.2"
    testImplementation "org.robolectric:robolectric:4.11.1"

    // Instrumentation tests
    androidTestImplementation "androidx.test.ext:junit:1.1.5"
    androidTestImplementation "androidx.test.espresso:espresso-core:3.5.1"
    androidTestImplementation "androidx.room:room-testing:2.6.1"
    androidTestImplementation "com.google.truth:truth:1.4.2"
    androidTestImplementation "androidx.test:runner:1.5.2"
    androidTestImplementation "androidx.test:rules:1.5.0"
}
```

**معايير النجاح:**
- [ ] `./gradlew test` يعمل (حتى لو صفر اختبار)
- [ ] `./gradlew connectedAndroidTest` يعمل على محاكي

---

### Task 2.2 — اختبارات Migration لـ Room 🔴 ⏱️ 1 يوم

**لماذا حرج؟** Migration خاطئ = مسح بيانات المستخدمين = تقييمات 1-star فورية.

**الملف:** `app/src/androidTest/java/com/example/plantcare/database/MigrationTest.java`

```java
@RunWith(AndroidJUnit4.class)
public class MigrationTest {

    private static final String TEST_DB = "migration-test";

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase.class.getCanonicalName(),
        new FrameworkSQLiteOpenHelperFactory()
    );

    @Test
    public void migrate5To6() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 5);
        // Insert test data matching v5 schema
        db.execSQL("INSERT INTO plants (id, name) VALUES (1, 'Aloe Vera')");
        db.close();

        // Run migration
        db = helper.runMigrationsAndValidate(TEST_DB, 6, true,
            DatabaseMigrations.MIGRATION_5_6);

        // Verify data survived
        Cursor cursor = db.query("SELECT * FROM plants");
        assertThat(cursor.moveToFirst()).isTrue();
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            .isEqualTo("Aloe Vera");
    }

    @Test
    public void migrate6To7() throws IOException {
        // similar test for v6 → v7 migration
    }

    @Test
    public void migrateAllVersions() throws IOException {
        helper.createDatabase(TEST_DB, 5).close();
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            AppDatabase.class, TEST_DB)
            .addMigrations(DatabaseMigrations.ALL_MIGRATIONS)
            .build()
            .getOpenHelper()
            .getWritableDatabase();
    }
}
```

**معايير النجاح:**
- [ ] اختبار لكل Migration موجود (5→6، 6→7)
- [ ] اختبار مشترك يُمرّر من v5 إلى الأحدث
- [ ] كل الاختبارات تمر على محاكي API 24 + API 34

---

### Task 2.3 — Unit Tests للـ Repositories 🟠 ⏱️ 2 يوم

**الأولوية:** PlantRepository, ReminderRepository, AuthRepository (الحساسة أمنياً)

**مثال:** `app/src/test/java/.../PlantRepositoryTest.kt`

```kotlin
class PlantRepositoryTest {

    private lateinit var dao: PlantDao
    private lateinit var repo: PlantRepository

    @Before
    fun setup() {
        dao = mock()
        repo = PlantRepository(dao)
    }

    @Test
    fun `getPlants returns dao data`() = runTest {
        val mockPlants = listOf(Plant(id=1, name="Aloe"))
        whenever(dao.getAllForUser("test@mail.com")).thenReturn(mockPlants)

        val result = repo.getPlants("test@mail.com")

        assertThat(result).containsExactly(Plant(id=1, name="Aloe"))
    }

    @Test
    fun `addPlant validates input`() = runTest {
        assertThrows<IllegalArgumentException> {
            repo.addPlant(Plant(id=0, name=""))
        }
    }

    // ... 10-15 tests per repository
}
```

**المستهدف:**
- PlantRepository: 8-10 اختبار
- ReminderRepository: 5-7 اختبار
- AuthRepository: 5-7 اختبار (login, logout, guest mode, register)
- PlantIdentificationRepository: 3-5 اختبار (mock PlantNetService)
- DiseaseDiagnosisRepository: 3-5 اختبار

**معايير النجاح:**
- [ ] 25+ اختبار يمر
- [ ] coverage > 40% للـ `data/repository/` package
- [ ] `./gradlew test` ينجح في <30 ثانية

---

### Task 2.4 — تفعيل Crashlytics فعلياً 🔴 ⏱️ 3 ساعات

**المشكلة:** Crashlytics مُسجَّل في Gradle لكن لا يُستخدم.

**الخطوات:**
1. تأكد من plugin في `app/build.gradle`:
   ```gradle
   apply plugin: 'com.google.firebase.crashlytics'
   ```
2. في `App.onCreate()`:
   ```java
   FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG);
   ```
3. **استبدل كل `catch (Throwable ignored) {}`** بـ:
   ```java
   catch (Throwable t) {
       FirebaseCrashlytics.getInstance().recordException(t);
       // log locally for dev
       if (BuildConfig.DEBUG) Log.e(TAG, "...", t);
   }
   ```
4. أضِف User Identifier (بدون PII):
   ```java
   FirebaseCrashlytics.getInstance().setUserId(currentUser.getUid());
   FirebaseCrashlytics.getInstance().setCustomKey("is_guest", isGuest);
   ```
5. اختبر: أضِف زر مؤقت يرمي `throw new RuntimeException("Test crash")` وتحقق من ظهوره في Console خلال 5 دقائق

**معايير النجاح:**
- [ ] test crash ظهر في Firebase Console
- [ ] صفر `catch (Throwable ignored)` في الكود (استخدم `grep -r "Throwable ignored" app/src/`)
- [ ] كل الاستثناءات المُلتقطة تصل لـ Crashlytics

---

### Task 2.5 — إعداد Firebase Analytics Events 🟠 ⏱️ 4 ساعة

**الأحداث الأساسية:**
```java
FirebaseAnalytics.getInstance(context).logEvent("plant_added", bundle);
FirebaseAnalytics.getInstance(context).logEvent("reminder_completed", bundle);
FirebaseAnalytics.getInstance(context).logEvent("plant_identified", bundle);
FirebaseAnalytics.getInstance(context).logEvent("disease_diagnosed", bundle);
FirebaseAnalytics.getInstance(context).logEvent("widget_added", bundle);
FirebaseAnalytics.getInstance(context).logEvent("onboarding_completed", bundle);
FirebaseAnalytics.getInstance(context).logEvent("guest_registered", bundle);  // مهم لحساب التحويل
```

**Custom Parameters:**
- `plant_added`: plantType, roomId
- `reminder_completed`: reminderType (watering/fertilizing), daysLate
- `plant_identified`: success (bool), confidence
- `disease_diagnosed`: diseaseKey, confidence

**الخطوات:**
1. أنشئ `AnalyticsHelper.kt` كـ wrapper
2. استدعِها من Repositories (ليس من UI — سبب: consistency)
3. في Firebase Console → Analytics → DebugView: فعّل عبر `adb shell setprop debug.firebase.analytics.app app.plantcare.de.dev`

**معايير النجاح:**
- [ ] كل الأحداث الرئيسية تظهر في DebugView عند تنفيذها
- [ ] لا بيانات PII في Parameters

---

## ✅ Exit Criteria — Layer 2

- [ ] 25+ Unit Test يمر
- [ ] اختبارات Migration تغطي v5→v7
- [ ] Crashlytics مفعّل ويستقبل أخطاء فعلية
- [ ] Analytics Events الأساسية تُسجَّل
- [ ] صفر `catch (Throwable ignored)` في الكود
- [ ] `./gradlew test` + `./gradlew connectedAndroidTest` ينجحان

---

# LAYER 3 — تصليب البنية المعمارية
## **لماذا رابعاً؟** الآن لديك اختبارات تحميك. يمكنك refactor بأمان.

### Task 3.1 — إزالة DAO المباشر من طبقة UI 🟠 ⏱️ 2-3 يوم

**الهدف:** لا `AppDatabase.getInstance()` ولا `fragmentDao = ...` في أي Fragment/Activity/ViewModel.

**الخطوات:**
1. ابحث عن كل استخدام: `grep -rn "AppDatabase.getInstance\|DatabaseClient\." app/src/main/java | grep -v "repository/"`
2. لكل استخدام خارج `data/repository/`:
   - أنشئ method جديد في Repository المناسب
   - استبدل الاستخدام المباشر بـ Repository
   - تأكد من وجود اختبار للـ method الجديد (يُكتب أثناء Task 2.3)
3. الحالات الشائعة:
   - `CalendarFragment` → أنشئ `CalendarViewModel` يستخدم Repositories
   - `MyPlantsViewModel` إن كان يتجاوز Repository → مرره عبر Repository

**معايير النجاح:**
- [ ] `grep -rn "AppDatabase.getInstance" app/src/main/java/com/example/plantcare/ui/` يُعطي 0 نتائج
- [ ] كل ViewModel يستقبل Repository في constructor
- [ ] كل الاختبارات لا تزال تمر

---

### Task 3.2 — إدخال Hilt للـ DI 🟠 ⏱️ 2 يوم

**لماذا؟** Singletons يدوية تصعّب الاختبار. Hilt يحلّ هذا جذرياً.

**الخطوات:**
1. في `build.gradle` (project):
   ```gradle
   plugins {
       id 'com.google.dagger.hilt.android' version '2.51' apply false
   }
   ```
2. في `app/build.gradle`:
   ```gradle
   apply plugin: 'dagger.hilt.android.plugin'
   apply plugin: 'kotlin-kapt'

   dependencies {
       implementation "com.google.dagger:hilt-android:2.51"
       kapt "com.google.dagger:hilt-compiler:2.51"
   }
   ```
3. أنشئ `@HiltAndroidApp class App` (أو عدّل الموجود)
4. أنشئ modules في `di/`:
   ```kotlin
   @Module
   @InstallIn(SingletonComponent::class)
   object DatabaseModule {
       @Provides @Singleton
       fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
           AppDatabase.getInstance(ctx)

       @Provides fun providePlantDao(db: AppDatabase) = db.plantDao()
       @Provides fun provideReminderDao(db: AppDatabase) = db.reminderDao()
       // ...
   }

   @Module
   @InstallIn(SingletonComponent::class)
   object RepositoryModule {
       @Provides @Singleton
       fun providePlantRepo(dao: PlantDao) = PlantRepository(dao)
       // ...
   }
   ```
5. علّم Activities بـ `@AndroidEntryPoint` وFragments
6. علّم ViewModels بـ `@HiltViewModel` و `@Inject constructor`
7. في الـ Fragments، استخدم `by viewModels()` بدل الـ factory اليدوي

**معايير النجاح:**
- [ ] لا `NewInstanceFactory` ولا factory يدوية في المشروع
- [ ] البناء يمر
- [ ] كل الاختبارات تمر (مع mock Repository بدل Hilt في tests)

---

### Task 3.3 — توحيد خيوط التشغيل (Threading) 🟠 ⏱️ 1 يوم

**المشكلة:** خليط من `new Thread()` و `AsyncTask` و `Coroutines`.

**المعيار الجديد:** كل I/O عبر Coroutines على `Dispatchers.IO`. لا `new Thread()` جديد.

**الخطوات:**
1. ابحث: `grep -rn "new Thread\|AsyncTask" app/src/main/java`
2. لكل استخدام:
   - إن كان في Kotlin: استخدم `viewModelScope.launch(Dispatchers.IO) { ... }`
   - إن كان في Java: استخدم `Executors.newSingleThreadExecutor()` أو تحوّل الملف لـ Kotlin
3. في WorkManager workers الـ Java الحالية، أبقِها كما هي (CoroutineWorker بديل ممتاز للكتابة الجديدة)

**معايير النجاح:**
- [ ] 0 استخدام لـ `new Thread` في طبقة UI/ViewModel
- [ ] `AsyncTask` مُزال كلياً (deprecated في API 30+)

---

### Task 3.4 — استخراج رسائل الإشعارات إلى موارد 🟠 ⏱️ 4 ساعة

**المشكلة:** `PlantNotificationHelper` يستخدم strings ثابتة بالألمانية.

**الخطوات:**
1. انقل كل النصوص إلى `res/values/strings.xml`:
   ```xml
   <string name="notif_channel_reminders">Pflegeerinnerungen</string>
   <string name="notif_title_morning_1">Guten Morgen!</string>
   <string name="notif_body_watering">Deine Pflanzen warten auf Wasser</string>
   <!-- 20 variations -->
   <string-array name="notif_morning_bodies">
       <item>@string/notif_body_morning_1</item>
       <item>@string/notif_body_morning_2</item>
       <!-- ... -->
   </string-array>
   ```
2. استبدل في Java:
   ```java
   String[] messages = context.getResources().getStringArray(R.array.notif_morning_bodies);
   String msg = messages[new Random().nextInt(messages.length)];
   ```
3. هذا يفتح الباب لترجمة الإشعارات لاحقاً في Layer 4

**معايير النجاح:**
- [ ] 0 strings ثابتة في `PlantNotificationHelper.java`
- [ ] الإشعارات تعمل بالضبط كما كانت

---

## ✅ Exit Criteria — Layer 3

- [ ] صفر DAO مباشر خارج Repository layer
- [ ] Hilt مفعّل ويعمل
- [ ] Threading موحّد (Coroutines فقط للجديد)
- [ ] كل النصوص في موارد (قابلة للترجمة)
- [ ] كل الاختبارات تمر (35+ اختبار الآن)

---

# LAYER 4 — التدويل (i18n)
## **لماذا خامساً؟** بعد استخراج النصوص، الترجمة سهلة. قبلها، مستحيلة.

### Task 4.1 — إزالة `resConfigs "de"` 🔴 ⏱️ 30 دقيقة

**في `app/build.gradle`:**
```gradle
defaultConfig {
    // resConfigs "de"  ← احذفها
    // أو
    resConfigs "de", "en"  ← إن أردت تقييد محدود
}
```

**التأثير:** الـ APK سيشمل الآن موارد لكل اللغات المتاحة (زيادة حجم ضئيلة).

---

### Task 4.2 — ترجمة إنجليزية كاملة 🟠 ⏱️ 1 يوم

**الخطوات:**
1. أنشئ `app/src/main/res/values-en/strings.xml` (هذا يصبح الافتراضي إن لم توجد لغة النظام)
2. ترجم كل string
3. استخدم Android Studio Translations Editor: `Right-click على strings.xml → Open Translations Editor`
4. تحقق من:
   - النباتات (Aloe Vera vs Aloe Vera — في الألمانية والإنجليزية بنفس الاسم غالباً)
   - الأزرار (Sign up, Log in)
   - رسائل الأخطاء
   - الإشعارات

**تحديث `plants.csv`:**
- الحالي: أسماء ووصف بالألمانية فقط
- المقترح: أضف أعمدة `name_en, lighting_en, soil_en, fertilizing_en, watering_en`
- أو: أنشئ `plants_en.csv` منفصل وحمّل الملف المناسب حسب اللغة

**معايير النجاح:**
- [ ] تغيير لغة النظام إلى English يُظهر التطبيق بالإنجليزية
- [ ] النباتات تظهر بالإنجليزية
- [ ] الإشعارات بالإنجليزية

---

### Task 4.3 — دعم لغة التطبيق منفصلة عن لغة النظام 🟡 ⏱️ 4 ساعة

**لماذا؟** مستخدمون كثر في ألمانيا لديهم هواتف بالإنجليزية لكن يفضلون التطبيق بالألمانية (أو العكس).

**الخطوات:**
1. استخدم AppCompat 1.7+ Per-App Language API:
   ```kotlin
   val localeList = LocaleListCompat.forLanguageTags("de")
   AppCompatDelegate.setApplicationLocales(localeList)
   ```
2. أضِف قائمة لغات في شاشة الإعدادات (`SettingsActivity`)
3. احفظ اللغة في EncryptedSharedPreferences

**معايير النجاح:**
- [ ] تغيير اللغة من داخل التطبيق يعمل فوراً
- [ ] اللغة تُحفظ بعد إعادة التشغيل

---

### Task 4.4 — (اختياري) ترجمة عربية 🟢 ⏱️ 1 يوم

إذا كنت تستهدف السوق العربي لاحقاً:
- أنشئ `values-ar/strings.xml`
- اختبر RTL layout (موجود أصلاً `supportsRtl="true"`)
- تحقق من الـ layouts باستخدام `ConstraintLayout` تعمل في RTL

**ملاحظة:** هذا يضيف ~8-10 أيام للصيانة المستقبلية. لا تنفّذها إلا إذا كنت مستعداً للصيانة.

---

## ✅ Exit Criteria — Layer 4

- [ ] `resConfigs "de"` مُزال
- [ ] الإنجليزية مترجمة كاملاً (strings + plants catalog + notifications)
- [ ] تبديل اللغة من داخل التطبيق يعمل
- [ ] اختبار يدوي: تغيير إلى English + dark mode + إشعار = كل شيء متسق

---

# LAYER 5 — تنظيف وتحسين
## **لماذا سادساً؟** الآن البنية صلبة. حان وقت القص والتلميع.

### Task 5.1 — إزالة Facebook SDK 🟢 ⏱️ 1 ساعة

**الخطوات:**
1. تحقق أولاً أنه غير مستخدم فعلاً: `grep -rn "Facebook\|FB" app/src/main/java`
2. احذف من `build.gradle`:
   ```gradle
   // implementation 'com.facebook.android:facebook-login:...'
   ```
3. احذف من Manifest أي `meta-data` أو permissions لـ Facebook
4. Sync + build + اختبار تسجيل الدخول

**التأثير:** توفير ~2-3 MB من APK + إزالة permissions غير مبررة.

---

### Task 5.2 — تحديث Firebase BoM 🟡 ⏱️ 2 ساعة

**الخطوات:**
1. في `build.gradle`:
   ```gradle
   implementation platform('com.google.firebase:firebase-bom:33.10.0')  // أحدث مستقر
   ```
2. اقرأ release notes لأي breaking changes
3. Sync + build + تشغيل سيناريوهات Firebase الأساسية (login, Firestore, Storage)

**معايير النجاح:**
- [ ] كل الميزات Firebase تعمل
- [ ] `./gradlew dependencies` يُظهر الإصدارات الجديدة

---

### Task 5.3 — تحليل حجم APK 🟡 ⏱️ 3 ساعة

**الخطوات:**
1. `./gradlew :app:analyzeReleaseBundle` أو Android Studio → Build → Analyze APK
2. راقب:
   - حجم `lib/` (TensorFlow Lite كبير عادة)
   - حجم `assets/` (CSV + TFLite model)
   - حجم `res/` (drawables كبيرة؟)
3. حلول:
   - **App Bundle (AAB) بدل APK** — Google Play يوزّع dynamic
   - **abiFilters** للحد من Native libraries:
     ```gradle
     ndk { abiFilters "armeabi-v7a", "arm64-v8a" }
     ```
   - **WebP بدل PNG** للصور الكبيرة
   - **Vector Drawables بدل PNG** للأيقونات

**الهدف:** APK < 30 MB (مثالي < 20 MB)

---

### Task 5.4 — Top-3 نتائج لتعرّف النبات 🟠 ⏱️ 3 ساعة

**المشكلة:** تعرّف خاطئ = شكوى #1 على تطبيقات النباتات.

**الخطوات:**
1. في `PlantIdentifyViewModel`:
   - استقبل top-3 results من PlantNet
2. في `PlantIdentifyActivity`:
   - اعرض 3 خيارات مع صورة كل واحد
   - زر "هذا هو!" + زر "لا أحد صحيح"
3. سجّل في Analytics أي نتيجة اختيرت (1st, 2nd, 3rd, none) — يساعد على قياس الدقة الفعلية

**معايير النجاح:**
- [ ] المستخدم يرى 3 خيارات مع نسبة ثقة لكل
- [ ] اختيار 2nd أو 3rd يُسجَّل في Analytics

---

### Task 5.5 — Caching لنتائج PlantNet 🟠 ⏱️ 4 ساعة

**لماذا؟** 500 طلب/يوم ينفد بسرعة. + نفس المستخدم يعرّف نفس النبات مرتين.

**الخطوات:**
1. أنشئ `IdentificationCache` entity في Room (v7→v8):
   ```kotlin
   @Entity(tableName = "identification_cache")
   data class CachedIdentification(
       @PrimaryKey val imageHash: String, // SHA-256 للصورة
       val response: String, // JSON response
       val timestamp: Long
   )
   ```
2. قبل استدعاء PlantNet: احسب hash للصورة → تحقق من cache → إن وُجد ولم يتجاوز 7 أيام، استخدمه
3. إن لم يوجد: استدعِ API واحفظ النتيجة
4. أضِف زر "اعرف نبتة جديدة" (force refresh) في UI

**معايير النجاح:**
- [ ] تعرّف مرتين لنفس الصورة = استدعاء API واحد فقط
- [ ] cache يُمسح أوتوماتيكياً بعد 30 يوم (cleanup في WorkManager)

---

### Task 5.6 — إعداد App Bundle للـ Release 🟠 ⏱️ 2 ساعة

**الخطوات:**
1. `./gradlew :app:bundleRelease` (بدل assembleRelease)
2. استخدم `bundletool` محلياً لاختبار قبل الرفع:
   ```
   bundletool build-apks --bundle=app-release.aab --output=app.apks
   bundletool install-apks --apks=app.apks
   ```
3. راقب حجم كل APK عند تقسيم Bundle

---

## ✅ Exit Criteria — Layer 5

- [ ] Facebook SDK مُزال
- [ ] Firebase BoM 33.x+ مستقر
- [ ] حجم APK < 30 MB
- [ ] Top-3 نتائج تعرّف تعمل
- [ ] Caching يوفّر استدعاءات PlantNet
- [ ] AAB يبنى بنجاح

---

# LAYER 6 — البنية التجارية (Billing + Store Setup)
## **لماذا سابعاً؟** الآن التطبيق مستقر. حان وقت ربطه بنقطة البيع.

### Task 6.1 — إعداد Google Play Console 🔴 ⏱️ 1 يوم

**الخطوات:**
1. Google Play Console → Create App
2. تعبئة:
   - App name: PlantCare
   - Default language: Deutsch (أو حسب أولويتك)
   - App or Game: App
   - Free or Paid: Free (مع in-app purchases)
3. **Store listing:**
   - Short description (80 حرف بالألمانية): "Halte deine Zimmerpflanzen gesund mit Erinnerungen, KI-Diagnose und Pflegetipps."
   - Full description (~4000 حرف)
   - Screenshots (8 صور 1080x1920): من التطبيق الفعلي
   - Feature graphic: 1024x500
   - Icon: 512x512
4. **Content rating:** PEGI 3 (Everyone) — استخدم questionnaire
5. **Target audience:** 13+
6. **Data safety:** أكمل الفورم (Firebase Analytics, Auth, Storage...)
7. **Privacy Policy URL:** (من Task 0.6)

**معايير النجاح:**
- [ ] التطبيق ظاهر في Play Console بحالة Draft
- [ ] كل الأقسام الإلزامية مكتملة (علامة ✓)

---

### Task 6.2 — إعداد Billing SKUs 🔴 ⏱️ 4 ساعة

**في Play Console → Monetize → Products → Subscriptions:**

**SKU 1: `monthly_pro`**
- Price: €2.99
- Billing period: 1 month
- Free trial: 7 days

**SKU 2: `yearly_pro`**
- Price: €19.99
- Billing period: 1 year
- Free trial: 7 days

**SKU 3: `lifetime_pro` (In-app product, not subscription)**
- Price: €39.99
- One-time

**معايير النجاح:**
- [ ] 3 SKUs في Play Console بحالة Active
- [ ] معرّفات SKUs مسجّلة في ملف config

---

### Task 6.3 — تكامل Google Play Billing Library 🔴 ⏱️ 2 يوم

**الخطوات:**
1. في `build.gradle`:
   ```gradle
   implementation "com.android.billingclient:billing-ktx:6.2.0"
   ```
2. أنشئ `BillingManager.kt`:
   ```kotlin
   @Singleton
   class BillingManager @Inject constructor(
       @ApplicationContext private val context: Context
   ) : PurchasesUpdatedListener {

       private val billingClient = BillingClient.newBuilder(context)
           .setListener(this)
           .enablePendingPurchases()
           .build()

       suspend fun connect() = suspendCoroutine<Boolean> { cont ->
           billingClient.startConnection(object : BillingClientStateListener {
               override fun onBillingSetupFinished(result: BillingResult) {
                   cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
               }
               override fun onBillingServiceDisconnected() {}
           })
       }

       suspend fun queryProducts(): List<ProductDetails> {
           val params = QueryProductDetailsParams.newBuilder()
               .setProductList(listOf(
                   Product.newBuilder().setProductId("monthly_pro").setProductType(SUBS).build(),
                   Product.newBuilder().setProductId("yearly_pro").setProductType(SUBS).build(),
                   Product.newBuilder().setProductId("lifetime_pro").setProductType(INAPP).build()
               ))
               .build()
           return billingClient.queryProductDetails(params).productDetailsList.orEmpty()
       }

       fun launchPurchase(activity: Activity, product: ProductDetails) {
           // flow...
       }

       override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
           // verify + grant
       }

       // Critical: server-side verification
       private suspend fun verifyPurchase(purchase: Purchase): Boolean {
           // Call Cloud Function that uses Google Play Developer API to verify
       }

       // Critical: Restore purchases
       suspend fun restorePurchases(): List<Purchase> {
           return billingClient.queryPurchasesAsync(
               QueryPurchasesParams.newBuilder().setProductType(SUBS).build()
           ).purchasesList
       }
   }
   ```
3. في ViewModel: استعلم عن حالة subscription واحفظها
4. **مهم جداً:** Server-side verification عبر Cloud Function — لا تثق بالـ client
5. Restore Purchases: زر واضح في شاشة Premium

**معايير النجاح:**
- [ ] شراء من حساب Test يمنح Premium
- [ ] إلغاء Subscription يسحب Premium بعد نهاية الفترة
- [ ] Restore Purchases يعمل
- [ ] لا crashes عند انقطاع الاتصال أثناء الشراء

---

### Task 6.4 — تصميم Paywall غير عدواني 🟠 ⏱️ 1 يوم

**مبادئ:**
1. **لا Paywall في أول 7 أيام** — المستخدم يختبر القيمة أولاً
2. **Paywall contextual** — عند محاولة إضافة نبات 9 (الحد المجاني 8)، اعرض Paywall مع "Du brauchst mehr Platz für deine grüne Familie!"
3. **Free Trial 7 days** واضح: "Starte kostenlos, dann €19.99/Jahr"
4. **زر Close واضح** في الأعلى يميناً — لا تُخفه
5. **Restore Purchases** ظاهر
6. **المقارنة بصرية** Free vs Premium في جدول

**معايير النجاح:**
- [ ] Paywall يُظهر 3 خيارات (Monthly, Yearly, Lifetime)
- [ ] زر Close يعيد للتطبيق بدون مشاكل
- [ ] "Restore Purchases" يعمل

---

### Task 6.5 — إعداد AdMob للمستوى المجاني 🟡 ⏱️ 4 ساعة

**الخطوات:**
1. AdMob Console → Create App → اربطه بـ `applicationId`
2. أنشئ Ad Unit: Banner
3. في `build.gradle`:
   ```gradle
   implementation 'com.google.android.gms:play-services-ads:23.0.0'
   ```
4. في `AndroidManifest`:
   ```xml
   <meta-data
       android:name="com.google.android.gms.ads.APPLICATION_ID"
       android:value="ca-app-pub-XXXXXXXXXXXXX~YYYYYYYYY"/>
   ```
5. أضف Banner في أسفل Main Screen فقط (لا interstitials — عدوانية)
6. إخفاء Banner للـ Premium users

**معايير النجاح:**
- [ ] Banner يظهر في Dev builds مع Test Ad IDs
- [ ] لا ads للـ Premium users
- [ ] لا performance impact محسوس

---

### Task 6.6 — إعداد ASO (App Store Optimization) 🟠 ⏱️ 1 يوم

**الخطوات:**
1. **Keyword research:**
   - Pflanzenpflege, Zimmerpflanzen, Pflanzen-Identifikation, Gießplan, Pflanzenkrankheiten
   - استخدم Google Keyword Planner أو Sensor Tower (trial)
2. **Title تحسين:** `PlantCare: Pflanzenpflege KI`
3. **Short description:** احتفظ بأهم كلمات مفتاحية في أول 30 حرف
4. **Screenshots نصية** مع text overlays: "Erkenne jede Pflanze", "KI-Diagnose für kranke Pflanzen"
5. **A/B testing** في Play Console: جرّب 2 icons + 2 screenshot orderings

---

## ✅ Exit Criteria — Layer 6

- [ ] Google Play Console جاهز (Draft كامل)
- [ ] 3 SKUs مُفعَّلة
- [ ] Billing integration تعمل (شراء + استعادة + إلغاء)
- [ ] Paywall غير عدواني
- [ ] AdMob Banner (للمجاني فقط)
- [ ] Screenshots + Description + Icons جاهزة

---

# LAYER 7 — ضمان الجودة (Quality Assurance)
## **لماذا ثامناً؟** الآن التطبيق "جاهز" تقنياً. اختبره فعلياً قبل إطلاقه.

### Task 7.1 — مصفوفة اختبار يدوي 🔴 ⏱️ 3 أيام

**الأجهزة المستهدفة (حد أدنى):**
- Android 7 (API 24) — Galaxy J4
- Android 10 (API 29) — Pixel 4a
- Android 13 (API 33) — Pixel 7
- Android 15 (API 35) — Pixel 8

**السيناريوهات:**
| # | السيناريو | API 24 | 29 | 33 | 35 |
|---|-----------|:------:|:--:|:--:|:--:|
| 1 | تثبيت + تسجيل جديد | | | | |
| 2 | تسجيل دخول Google | | | | |
| 3 | Guest mode → Registered migration | | | | |
| 4 | إضافة نبات + تذكير | | | | |
| 5 | تلقي إشعار صباحي | | | | |
| 6 | Kamera → تعرّف نبات | | | | |
| 7 | Kamera → تشخيص مرض (offline) | | | | |
| 8 | Widget إضافة + تحديث | | | | |
| 9 | Dark mode تبديل | | | | |
| 10 | تغيير اللغة DE→EN | | | | |
| 11 | شراء Premium (Test account) | | | | |
| 12 | Restore Purchases | | | | |
| 13 | إلغاء Subscription → فقدان Premium | | | | |
| 14 | Offline → إضافة نبات → Online → sync | | | | |
| 15 | Backup بعد uninstall → Reinstall | | | | |

**معايير النجاح:**
- [ ] كل خلية في المصفوفة = ✅ أو مُسجَّل كـ "يعمل لكن ملاحظة X"
- [ ] صفر Critical bugs (crashes, data loss)
- [ ] أقل من 5 High bugs

---

### Task 7.2 — اختبارات الأداء 🟠 ⏱️ 1 يوم

**الأدوات:**
- Android Profiler في Studio
- Firebase Performance Monitoring
- Macrobenchmark (اختياري)

**المقاييس المستهدفة:**
- App startup cold: < 2 ثانية
- Navigate to PlantList: < 500ms
- Plant identification (network): < 5 ثواني على 3G
- Disease diagnosis (local TFLite): < 2 ثانية
- Memory baseline: < 150 MB
- APK size: < 30 MB

**معايير النجاح:**
- [ ] كل المقاييس ضمن المستهدف
- [ ] لا memory leaks (LeakCanary clean)

---

### Task 7.3 — اختبار الإمكانية (Accessibility) 🟡 ⏱️ 4 ساعة

**الخطوات:**
1. TalkBack على جهاز: انتقل في التطبيق، تأكد كل العناصر لها `contentDescription`
2. Large font scale (200%): لا نصوص مقصوصة
3. Color contrast: استخدم Accessibility Scanner (Google)

**معايير النجاح:**
- [ ] Accessibility Scanner = 0 critical issues
- [ ] TalkBack يُكمل سيناريو "إضافة نبات" بدون مساعدة بصرية

---

### Task 7.4 — اختبار أمان نهائي 🔴 ⏱️ 4 ساعة

**Checklist:**
- [ ] `adb backup` للتطبيق → لا تظهر passwords أو tokens (عبر EncryptedPrefs)
- [ ] `adb shell run-as <app> cat shared_prefs/*.xml` → لا PII غير مشفرة
- [ ] Firestore rules: محاولة قراءة بيانات مستخدم آخر من حساب مخترق → رفض
- [ ] HTTPS فقط (no cleartext) — تحقق `android:usesCleartextTraffic="false"` في Manifest
- [ ] Certificate pinning لـ PlantNet API (اختياري لكن موصى به)
- [ ] Obfuscation (ProGuard) يعمل — `./gradlew assembleRelease` ثم `apktool d app-release.apk` → الكود مبهم

---

## ✅ Exit Criteria — Layer 7

- [ ] مصفوفة اختبار مكتملة، 0 Critical bugs
- [ ] أداء ضمن المستهدف
- [ ] Accessibility Pass
- [ ] Security Checklist كامل

---

# LAYER 8 — الإطلاق (Soft Launch)
## **لماذا تاسعاً؟** الآن آمن للعرض على مستخدمين حقيقيين.

### Task 8.1 — Closed Beta (Internal Testing) 🔴 ⏱️ 3 أيام

**الخطوات:**
1. Play Console → Testing → Internal testing → Create release
2. أضِف 5-10 أشخاص (عائلة، أصدقاء) عبر إيميلاتهم
3. شارك رابط Beta
4. اطلب feedback عبر:
   - Google Form بسيط
   - أو in-app feedback tool (Shake to report)
5. إصلاح أي Critical bug → رفع إصدار جديد

**معايير النجاح:**
- [ ] 5+ مستخدمين يستخدمون التطبيق لأسبوع
- [ ] Crashlytics يُظهر < 1% crash rate
- [ ] feedback مُسجَّل ومُحلَّل

---

### Task 8.2 — Open Beta (Public) في ألمانيا فقط 🟠 ⏱️ 2 أسبوع

**الخطوات:**
1. Play Console → Open testing → Countries: Germany only
2. ترويج محدود:
   - Reddit r/Plants (English community لكن كثيرون ألمان)
   - Reddit r/de (ألماني)
   - Instagram @plantcare_app حساب رسمي
   - صفحة Landing بسيطة `plantcare.de`
3. هدف: 100-500 مستخدم في أسبوعين

**KPIs مستهدفة:**
- D1 Retention > 40%
- D7 Retention > 20%
- Crash-free sessions > 99.5%
- Onboarding completion > 70%
- تقييم متوسط > 4.0

---

### Task 8.3 — التعلّم والتحسين 🟠 ⏱️ مستمر

**الأدوات:**
- Firebase Analytics: شاشة بشاشة conversion funnel
- Crashlytics: أعلى 10 crashes
- Play Console Reviews: كل تقييم يُقرأ ويُرد عليه

**أسبوعياً:**
- إصلاح 3 أعلى crashes
- الرد على كل تقييم < 24 ساعة
- نشر إصدار hotfix إن لزم

---

### Task 8.4 — الإطلاق العلني 🔴 ⏱️ 1 يوم

**الشروط للتقدم:**
- [ ] Open Beta 4+ أسابيع
- [ ] KPIs ضمن المستهدف
- [ ] Crash-free > 99.5%
- [ ] تقييم متوسط > 4.0

**الخطوات:**
1. Play Console → Production → Create release
2. Rollout 10% أولاً → راقب يومين → 50% → يومين → 100%
3. إعلان على social media
4. Press kit جاهز للمدونات التقنية الألمانية (t3n, heise, etc.)

---

## ✅ Exit Criteria — Layer 8

- [ ] التطبيق منشور علناً في Germany Play Store
- [ ] 500+ تحميل في الأسبوع الأول
- [ ] تقييم متوسط 4.0+
- [ ] أول subscription مدفوع مستلَم

---

# LAYER 9 — نمو ما بعد الإطلاق (مستمر)
## **لماذا أخيراً؟** البنية جاهزة. الآن تنافس على السوق.

### Roadmap ربع سنوي

#### Q3 2026 (يوليو-سبتمبر)
- ✨ **Time-lapse تلقائي** من الصور المؤرشفة — ميزة حصرية
- ✨ **QR Code لكل نبات** — للأسر والمكاتب
- 📈 توسع إلى النمسا وسويسرا (نفس اللغة الألمانية)

#### Q4 2026 (أكتوبر-ديسمبر)
- ✨ **AI Chat** للنباتات (Claude/OpenAI integration)
- ✨ **Community Feed** — تحديات أسبوعية
- 📈 إطلاق نسخة الإنجليزية (US/UK market entry)

#### Q1 2027 (يناير-مارس)
- ✨ **iOS Version** (Kotlin Multiplatform أو Swift)
- ✨ **Local Plant Swap** — geofencing
- 📈 5 دول أوروبية

#### Q2 2027 (أبريل-يونيو)
- ✨ **IoT Integration** — Xiaomi Mi Flora sensors
- ✨ **Plant Insurance** (subscription إضافي)
- 📈 20K+ MAU مستهدف

---

## ملاحظات ختامية

### قواعد حاكمة أثناء التنفيذ

1. **لا تقفز طبقة** — كل طبقة لها Exit Criteria. لا تنتقل قبل تحقيقها.
2. **اكتب اختبار قبل كل refactor** — حتى لو اختبار بسيط. يجنّبك ساعات debug.
3. **commit صغيرة ومتكررة** — كل task = commit منفصل. يسهّل rollback.
4. **لا تضف ميزات جديدة في Layers 0-7** — ركّز على الاستقرار. الميزات في Layer 9.
5. **قس كل شيء** — Analytics + Crashlytics يخبرانك بحقائق لا يخبرها المستخدمون.
6. **استمع للمستخدمين** — 3 reviews سلبية لنفس السبب = خلل حقيقي.

### إشارات التحذير (Red Flags)

أوقف العمل وراجع إذا:
- **Test coverage يهبط** أثناء refactoring
- **Crash rate يرتفع** بعد release
- **D1 Retention ينزل تحت 30%** — مشكلة onboarding
- **APK size يكبر > 10%** في release واحد
- **Billing errors > 1%** في subscriptions

### مصادر خارجية مفيدة

- Google Play Console: `https://play.google.com/console`
- Firebase Console: `https://console.firebase.google.com`
- AdMob Console: `https://apps.admob.com`
- PlantNet API docs: `https://my.plantnet.org/usage`
- Android 15 migration guide: في Android Developers

---

## جدول زمني إجمالي مقترح

```
الأسبوع 1:        Layer 0 + Layer 1 (بدء)
الأسبوع 2:        Layer 1 (إنهاء) + Layer 2 (بدء)
الأسبوع 3:        Layer 2 (إنهاء) + Layer 3 (بدء)
الأسبوع 4:        Layer 3 (إنهاء) + Layer 4
الأسبوع 5:        Layer 5 + Layer 6 (بدء)
الأسبوع 6:        Layer 6 (إنهاء) + Layer 7
الأسبوع 7:        Layer 7 (إنهاء) + Closed Beta
الأسبوع 8-9:      Open Beta
الأسبوع 10+:      Production Launch
```

**الهدف المُعقلَن:** تطبيق منشور تجارياً خلال **10 أسابيع** من بداية التنفيذ المكرّس.

---

## خلاصة

هذه الخطة ليست للقراءة — هي **contract مع نفسك**. كل مهمة بها معايير نجاح يمكن قياسها. لا "مكتمل نوعاً ما" ولا "سأعود إليها لاحقاً".

**التنفيذ الصارم لهذه الخطة = تطبيق قابل للنشر التجاري في 10 أسابيع.**
**التنفيذ المتقطع = مشروع آخر في مقبرة المشاريع الشخصية.**

الفرق ليس في الذكاء أو المهارة — هو في الانضباط.

Viel Erfolg. 🌱
