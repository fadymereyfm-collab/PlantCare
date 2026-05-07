package com.example.plantcare.data.journal

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * DAO for [JournalMemo]. Synchronous methods only — `PlantJournalRepository`
 * already runs all journal queries on `Dispatchers.IO` and fans them in as a
 * single snapshot, so flowing this table separately would just complicate the
 * merge step.
 */
@Dao
interface JournalMemoDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(memo: JournalMemo): Long

    @Update
    fun update(memo: JournalMemo)

    @Delete
    fun delete(memo: JournalMemo)

    @Query("DELETE FROM journal_memo WHERE id = :id")
    fun deleteById(id: Int)

    /**
     * Wipes the user's memos before a cloud restore overwrites them. The
     * `userEmail IS :email` form (instead of `=`) is needed so that signing
     * in as a guest (`email == null`) still matches NULL rows — SQLite
     * `NULL = NULL` is false, but `NULL IS NULL` is true.
     */
    @Query("DELETE FROM journal_memo WHERE userEmail IS :email")
    fun deleteAllForUser(email: String?)

    @Query("SELECT * FROM journal_memo WHERE plantId = :plantId ORDER BY updatedAt DESC")
    fun getForPlant(plantId: Int): List<JournalMemo>

    @Query("SELECT * FROM journal_memo WHERE id = :id")
    fun findById(id: Int): JournalMemo?
}
