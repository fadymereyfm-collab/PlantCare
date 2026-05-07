# ============================================================
# PlantCare – ProGuard / R8 Rules
# ============================================================

# ── General ──────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable   # readable crash stacks

-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

# ── Room (SQLite ORM) ───────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# ── Firebase ─────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Firebase Crashlytics – readable stack-traces
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# ── Glide ────────────────────────────────────────────────────
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-dontwarn com.bumptech.glide.load.resource.bitmap.VideoDecoder

# ── BCrypt ───────────────────────────────────────────────────
-keep class at.favre.lib.crypto.** { *; }

# ── Google Play Billing ──────────────────────────────────────
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**
# Billing Fragment instantiated via reflection in fragment transactions
-keep class com.example.plantcare.billing.** { *; }

# ── Google Mobile Ads (AdMob) ────────────────────────────────
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# ── Kotlinx Serialization / DateTime ─────────────────────────
-dontwarn kotlinx.datetime.**
-keep class kotlinx.datetime.** { *; }

# ── Compose ──────────────────────────────────────────────────
-dontwarn androidx.compose.**

# ── Coil ─────────────────────────────────────────────────────
-dontwarn coil.**

# ── WorkManager ──────────────────────────────────────────────
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# ── Navigation Component ────────────────────────────────────
-keep class * extends androidx.navigation.Navigator { *; }

# ── Data classes used in Firestore serialisation ─────────────
-keep class com.example.plantcare.Plant { *; }
-keep class com.example.plantcare.PlantPhoto { *; }
-keep class com.example.plantcare.WateringReminder { *; }
-keep class com.example.plantcare.RoomCategory { *; }
-keep class com.example.plantcare.User { *; }
# Plant Journal (F10.3): JournalMemo is mirrored to Firestore at
# users/{uid}/memos/{id}. R8 must not rename its fields/getters or
# `doc.toObject(JournalMemo.class)` returns an object full of defaults
# on cloud restore — the user sees zero memos despite having them in
# Firestore. Same reason every other Firestore entity is kept above.
-keep class com.example.plantcare.data.journal.JournalMemo { *; }
# Vacation cloud doc — same reasoning as JournalMemo.
-keep class com.example.plantcare.feature.vacation.VacationDoc { *; }
# Gamification cloud docs (streak + challenges) — same reasoning.
# Without these R8 renames the fields, doc.toObject returns a defaults
# shell, and a user's 100-day streak silently disappears on cloud
# restore in prodRelease.
-keep class com.example.plantcare.feature.streak.StreakDoc { *; }
-keep class com.example.plantcare.feature.streak.ChallengesDoc { *; }
-keep class com.example.plantcare.feature.streak.ChallengeProgressDto { *; }
# Pro status cloud doc — same reasoning. R8 must not rename
# isPro/lastUpdatedMs or doc.toObject returns a defaults shell and
# the cross-device Pro restore silently does nothing.
-keep class com.example.plantcare.billing.ProStatusDoc { *; }

# ── Disease Diagnosis (Room Entity + Gemini Gson DTOs) ───────
# data.disease.**  : Room entity DiseaseDiagnosis + DiseaseResult (UI model)
# data.gemini.**   : Gson-serialized Gemini API request/response shapes.
#                    Without this rule R8 may rename or strip fields and
#                    silently break JSON parsing in release builds.
-keep class com.example.plantcare.data.disease.** { *; }
-keep class com.example.plantcare.data.gemini.** { *; }

# ── Retrofit + OkHttp ───────────────────────────────────────
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keepattributes Exceptions
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Gson ────────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keep class com.example.plantcare.data.weather.** { *; }
-keep class com.example.plantcare.data.plantnet.** { *; }
-keep class com.example.plantcare.data.repository.WateringAdvice { *; }

# ── Google Play Services Location ───────────────────────────
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.location.**

# ── CalendarView (JitPack) ──────────────────────────────────
-dontwarn com.prolificinteractive.materialcalendarview.**
-keep class com.prolificinteractive.materialcalendarview.** { *; }

# ── EncryptedSharedPreferences (security-crypto) ─────────────
# Library ships its own consumer rules; dontwarn for internal deps
-dontwarn com.google.crypto.tink.**

# ── Google Sign-In (play-services-auth) ─────────────────────
# Already covered by -keep com.google.android.gms.** but be explicit
-keep class com.google.android.gms.auth.** { *; }

# ── Reflection-based archive lookup (ArchivePhotosDialogFragment) ─
# Keeps PlantPhotoDao methods accessible via reflection
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public *;
}
-keepclassmembers @androidx.room.Dao interface * { *; }
