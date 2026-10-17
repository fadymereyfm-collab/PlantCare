# PlantCare Project Context

## Project Overview
PlantCare is an Android application designed for plant enthusiasts to manage their plant collections, track care routines, and document growth through photos. The project is currently a functional prototype transitioning toward a more robust production-ready state.

### Core Technologies
- **Platform:** Android (Min SDK 24, Target SDK 35)
- **Languages:** Java (Primary), Kotlin (Modern components, Workers, Compose)
- **Database:** Room (Local SQLite) with a unified `AppDatabase` (Version 5+)
- **Cloud Integration:** Firebase (Authentication, Firestore, Storage, Crashlytics, Analytics)
- **Background Processing:** WorkManager (Reminders, Weather adjustments)
- **Network/API:** Retrofit (Weather data), OkHttp
- **UI Frameworks:** XML-based Fragments/Activities with integration of Jetpack Compose
- **Third-Party Libraries:** Glide (Images), Material CalendarView, BCrypt (Security), Facebook SDK (Login)

### Architecture Note
The project follows a "flat" package structure currently, but is moving towards a tiered approach (`ui`, `data`, `media`). It uses a Singleton pattern for database access via `DatabaseClient` and `AppDatabase`.

---

## Building and Running

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17
- Firebase `google-services.json` (already present in `app/`)

### Key Commands
- **Build Debug APK:** `./gradlew assembleDebug`
- **Install to Device:** `./gradlew installDebug`
- **Run Unit Tests:** `./gradlew test`
- **Run Instrumentation Tests:** `./gradlew connectedAndroidTest`
- **Clean Project:** `./gradlew clean`

---

## Development Conventions

### 1. Data Persistence
- Use **Room** for all local data. All DAOs are accessible via `AppDatabase.getInstance(context)`.
- **Migration Policy:** Versions 1-4 are subject to destructive migration. From Version 5 onwards, use `DatabaseMigrations.ALL_MIGRATIONS`.

### 2. Cloud Sync
- Use `FirebaseSyncManager` for Firestore synchronization.
- `CoverCloudSync` handles profile/cover photo mirroring between local storage and Firebase Storage/Firestore.

### 3. Background Tasks
- All scheduled tasks (reminders, syncs, weather updates) must be implemented using **WorkManager**.
- Refer to `PlantReminderWorker` for notification logic (morning/evening windows).

### 4. Security
- Never store raw passwords. Use `PasswordUtils` (which employs **BCrypt**).
- Sensitive user state is stored in `SharedPreferences` (e.g., `current_user_email`).

### 5. UI/UX
- Most UI components are `DialogFragment` based for a modular (though sometimes constrained) experience.
- New features should prefer **Jetpack Compose** where possible.
- The app supports **German (de)** as a primary locale.

### 6. Code Style
- Java code should avoid raw threads where possible; prefer using the `AppDatabase`'s built-in threading or Kotlin Coroutines for new Kotlin code.
- Maintain the `com.example.plantcare` namespace for existing components to avoid breaking Room/Firebase configurations.

---

## Key Files
- `MainActivity.java`: Central navigation hub (Tab-based).
- `AppDatabase.java`: Unified Room database configuration.
- `FirebaseSyncManager.java`: Logic for cloud data synchronization.
- `PlantReminderWorker.java`: WorkManager implementation for care notifications.
- `PlantDetailDialogFragment.java`: Comprehensive view/edit for individual plants.
- `PlantCare_Review_Report.md`: External audit report (April 2026) detailing architectural debt and roadmap.
