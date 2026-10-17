package com.example.plantcare.feature.vacation

/**
 * Cloud-side representation of a vacation window.
 *
 * Stored at `users/{uid}/vacation/current` (single doc, fixed id) — the
 * user can only have one vacation set at a time, so a collection +
 * timestamp would be over-engineered. The doc id is constant so the
 * setter is a plain id-keyed `set()` upsert and clearing is an
 * id-keyed `delete()`.
 *
 *  * `start` / `end` — ISO-8601 date strings (yyyy-MM-dd) so they round-
 *    trip through Firestore as plain strings, no timezone surprise.
 *  * `welcomeFired` — mirrors the local `welcome_fired_<email>` boolean
 *    so a user signing in on a fresh device mid-vacation doesn't get a
 *    duplicate "welcome back" they've already seen on the original
 *    device.
 *
 * `var` (not `val`) is required for Firestore deserialisation — the
 * Java SDK uses JavaBeans setters and Kotlin `val` fields are
 * `private final` with no setter (same lesson learned from
 * JournalMemo).
 */
data class VacationDoc(
    var start: String? = null,
    var end: String? = null,
    var welcomeFired: Boolean = false
)
