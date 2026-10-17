package com.example.plantcare.data.journal

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.plantcare.Plant

/**
 * Plant Journal write-side (Sprint-1 Task 1.2): a free-text memo the user
 * attaches to a specific plant from the Journal screen. Distinct from
 * `WateringReminder.notes` (which is bound to a single watering event) — a
 * memo can stand on its own with no scheduling implication.
 *
 *  • [createdAt] / [updatedAt] are epoch millis. The repository uses [updatedAt]
 *    for timeline ordering so editing an old memo bumps it back to the top —
 *    same intuition as a Notes app.
 *  • Cascade-delete on plant removal: a deleted plant should never leave
 *    orphan memos that the journal screen would silently hide.
 *  • [userEmail] kept nullable to mirror every other table — guest mode rows
 *    have null email and the repository segregation handles them via filter.
 */
@Entity(
    tableName = "journal_memo",
    foreignKeys = [
        ForeignKey(
            entity = Plant::class,
            parentColumns = ["id"],
            childColumns = ["plantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("plantId"),
        Index("userEmail"),
        Index(value = ["plantId", "updatedAt"])
    ]
)
data class JournalMemo(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,

    var plantId: Int = 0,

    @ColumnInfo(typeAffinity = ColumnInfo.TEXT)
    var userEmail: String? = null,

    /** Free-text body; the UI enforces a soft 1000-char cap, the DB does not. */
    var text: String = "",

    var createdAt: Long = 0L,

    var updatedAt: Long = 0L
)
// `var` (not `val`) is required for the Firestore deserialisation path —
// Firestore's Java SDK uses JavaBeans reflection (setters), and Kotlin's
// `val` lowers to a `private final` field with no setter, so
// `doc.toObject(JournalMemo.class)` would silently produce an object full
// of defaults. Defaults on every field are also required so Firestore's
// reflection can use the generated no-arg constructor when deserialising
// `users/{uid}/memos/{id}` documents back into Kotlin objects on sign-in
// restore. The data-class `copy()` API (used in PlantJournalRepository)
// keeps working unchanged after the val→var switch.
