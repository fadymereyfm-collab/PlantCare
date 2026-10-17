package com.example.plantcare.feature.streak

/**
 * Cloud DTOs for streak + challenge state. Stored at:
 *   users/{uid}/gamification/streak     — single doc, fixed id
 *   users/{uid}/gamification/challenges — single doc, fixed id
 *
 * `var` (not `val`) is required so Firestore's JavaBeans-style
 * reflection can call setters during `doc.toObject(...)` —
 * Kotlin `val` lowers to `private final` with no setter and the
 * deserialised object would just be a defaults shell. Defaults on
 * every field provide the no-arg constructor Firestore uses.
 *
 * Lessons carried over from JournalMemo / VacationDoc.
 */

data class StreakDoc(
    var currentStreak: Int = 0,
    var bestStreak: Int = 0,
    /** ISO yyyy-MM-dd of the last day the user marked any reminder done. */
    var lastDay: String? = null
)

data class ChallengeProgressDto(
    var progress: Int = 0,
    var completedAtEpochMs: Long = 0L,
    /**
     * Month tag for monthly-resetting challenges (e.g. MONTHLY_PHOTO).
     * Empty string for challenges that don't reset.
     */
    var monthKey: String = ""
)

/**
 * The challenges doc is a flat container of named entries, one per
 * known challenge id. Adding a new challenge = adding a new field
 * (Firestore happily ignores unknown fields on either side, so
 * forward and backward compat both work).
 */
data class ChallengesDoc(
    var waterStreak7: ChallengeProgressDto? = null,
    var addFivePlants: ChallengeProgressDto? = null,
    var monthlyPhoto: ChallengeProgressDto? = null
)
