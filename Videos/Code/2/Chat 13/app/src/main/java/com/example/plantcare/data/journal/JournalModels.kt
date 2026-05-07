package com.example.plantcare.data.journal

import com.example.plantcare.PlantPhoto
import com.example.plantcare.WateringReminder
import com.example.plantcare.data.disease.DiseaseDiagnosis

/**
 * Per-plant timeline entry. Plant Journal feature (Functional Report §4).
 *
 * Three concrete kinds — watering events the user actually completed, photos
 * the user took, and disease diagnoses — sorted in a single list by [timestamp]
 * (epoch millis, descending). The UI groups by date label and renders each kind
 * with its own item type.
 */
sealed class JournalEntry {

    /** Epoch millis used for unified sort across kinds. */
    abstract val timestamp: Long

    /** ISO yyyy-MM-dd string for date-bucket grouping in the UI. */
    abstract val dateString: String

    /** Stable ID per kind so DiffUtil can reuse views. */
    abstract val stableId: String

    data class WateringEvent(
        val reminder: WateringReminder,
        override val timestamp: Long,
        override val dateString: String
    ) : JournalEntry() {
        override val stableId: String = "watering-${reminder.id}"
    }

    data class PhotoEntry(
        val photo: PlantPhoto,
        override val timestamp: Long,
        override val dateString: String
    ) : JournalEntry() {
        override val stableId: String = "photo-${photo.id}"
    }

    data class DiagnosisEntry(
        val diagnosis: DiseaseDiagnosis,
        override val timestamp: Long,
        override val dateString: String
    ) : JournalEntry() {
        override val stableId: String = "diagnosis-${diagnosis.id}"
    }

    /**
     * Free-text memo the user wrote from the Journal screen — the only
     * write-side entry that doesn't shadow another action (waterings, photos
     * and diagnoses are all created elsewhere; memos are journal-native).
     *
     * Sorted by `updatedAt` (which equals `createdAt` on first save), so
     * editing an old memo bumps it back to the top.
     */
    data class MemoEntry(
        val memo: JournalMemo,
        override val timestamp: Long,
        override val dateString: String
    ) : JournalEntry() {
        override val stableId: String = "memo-${memo.id}"
    }
}

/**
 * Aggregate counters shown in the journal header card.
 */
data class JournalSummary(
    val plantId: Int,
    val plantDisplayName: String?,
    val roomName: String?,
    /** Days since [com.example.plantcare.Plant.startDate]; null if startDate is missing. */
    val daysSinceStart: Long?,
    /** ISO yyyy-MM-dd of the most recent completed watering, or null if none yet. */
    val lastWateringDate: String?,
    val completedWateringCount: Int,
    val photoCount: Int,
    val diagnosisCount: Int,
    val memoCount: Int = 0
)

/**
 * UI filter state — drives which kinds the journal list renders.
 */
enum class JournalFilter { ALL, WATERING, PHOTOS, DIAGNOSES, MEMOS }

/**
 * Single round-trip result from [PlantJournalRepository]. Returned as one
 * snapshot so the ViewModel doesn't have to coordinate three async sources.
 */
data class JournalSnapshot(
    val entries: List<JournalEntry>,
    val summary: JournalSummary
)
