# PlantCare - ic_watering_can to ic_water Migration

## Summary of Changes

This document summarizes the changes made to replace all references to the non-existent drawable `R.drawable.ic_watering_can` with the existing `R.drawable.ic_water`.

## Files Changed

### 1. RemindersListCompose.kt
**Location**: `app/src/main/java/com/plantcare/RemindersListCompose.kt`
**Change**: Line 35
```kotlin
// BEFORE:
painter = painterResource(R.drawable.ic_watering_can),

// AFTER:
painter = painterResource(R.drawable.ic_water),
```

### 2. DailyWateringAdapter.java
**Location**: `app/src/main/java/com/plantcare/DailyWateringAdapter.java`
**Change**: Line 32
```java
// BEFORE:
holder.typeIcon.setImageResource(R.drawable.ic_watering_can);

// AFTER:
holder.typeIcon.setImageResource(R.drawable.ic_water);
```

### 3. TodayAdapter.java
**Location**: `app/src/main/java/com/plantcare/TodayAdapter.java`
**Change**: Line 32
```java
// BEFORE:
holder.typeIcon.setImageResource(R.drawable.ic_watering_can);

// AFTER:
holder.typeIcon.setImageResource(R.drawable.ic_water);
```

### 4. item_daily_watering.xml
**Location**: `app/src/main/res/layout/item_daily_watering.xml`
**Change**: Line 15
```xml
<!-- BEFORE: -->
android:src="@drawable/ic_watering_can"

<!-- AFTER: -->
android:src="@drawable/ic_water"
```
**Note**: The `app:tint="@null"` attribute was preserved to maintain existing styling.

## Available Resources

### ic_water.xml
The replacement drawable is properly defined at:
`app/src/main/res/drawable/ic_water.xml`

This is a vector drawable representing water/hydration, making it semantically appropriate for watering-related functionality.

## Verification

✅ **Total references found and replaced**: 4
✅ **Remaining references to ic_watering_can**: 0
✅ **New references to ic_water**: 4
✅ **Existing attributes preserved**: Yes (app:tint="@null" maintained)
✅ **Both Compose and View systems updated**: Yes

## Impact

- Eliminates build/runtime errors caused by missing drawable reference
- Maintains visual consistency with water/hydration theme
- Preserves existing styling attributes
- Compatible with both Jetpack Compose and traditional View-based UI