package com.example.plantcare.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.plantcare.data.journal.JournalEntry
import com.example.plantcare.data.journal.JournalFilter
import com.example.plantcare.data.journal.JournalSummary
import com.example.plantcare.data.repository.PlantJournalRepository
import com.example.plantcare.data.repository.ReminderRepository
import kotlinx.coroutines.launch

/**
 * Drives the Plant Journal UI. Reads from [PlantJournalRepository] (single
 * round-trip per refresh) and exposes:
 *  - [filteredEntries]: timeline filtered by current [filter] selection
 *  - [summary]: header counters
 *  - [filter]: current filter state
 *
 * Refresh is explicit ([load] / [refresh]) so the screen stays responsive under
 * orientation changes; the repository does the I/O on Dispatchers.IO.
 */
class PlantJournalViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PlantJournalRepository.getInstance(application)
    private val reminderRepo = ReminderRepository.getInstance(application)

    private val _allEntries = MutableLiveData<List<JournalEntry>>(emptyList())
    private val _summary = MutableLiveData<JournalSummary?>(null)
    private val _filter = MutableLiveData(JournalFilter.ALL)

    /**
     * One-shot error event for memo CRUD failures. Set to true when an
     * insert/update/delete throws (Room SQL constraint, transient I/O), so
     * the dialog can toast the user instead of silently swallowing the
     * exception inside `viewModelScope.launch`. Consumed via [consumeMemoError]
     * so a config change doesn't replay the toast.
     */
    private val _memoError = MutableLiveData<Boolean>()

    val summary: LiveData<JournalSummary?> = _summary
    val filter: LiveData<JournalFilter> = _filter
    val memoError: LiveData<Boolean> = _memoError

    fun consumeMemoError() { _memoError.value = false }

    val filteredEntries: LiveData<List<JournalEntry>> = MediatorLiveData<List<JournalEntry>>().apply {
        val recompute = {
            val list = _allEntries.value.orEmpty()
            value = when (_filter.value ?: JournalFilter.ALL) {
                JournalFilter.ALL -> list
                JournalFilter.WATERING -> list.filterIsInstance<JournalEntry.WateringEvent>()
                JournalFilter.PHOTOS -> list.filterIsInstance<JournalEntry.PhotoEntry>()
                JournalFilter.DIAGNOSES -> list.filterIsInstance<JournalEntry.DiagnosisEntry>()
                JournalFilter.MEMOS -> list.filterIsInstance<JournalEntry.MemoEntry>()
            }
        }
        addSource(_allEntries) { recompute() }
        addSource(_filter) { recompute() }
    }

    private var lastPlantId: Int = 0
    private var lastUserEmail: String? = null

    fun load(plantId: Int, userEmail: String?) {
        lastPlantId = plantId
        lastUserEmail = userEmail
        viewModelScope.launch {
            val snapshot = repo.getJournalForPlant(plantId, userEmail)
            _allEntries.value = snapshot.entries
            _summary.value = snapshot.summary
        }
    }

    fun refresh() {
        if (lastPlantId > 0) load(lastPlantId, lastUserEmail)
    }

    fun setFilter(newFilter: JournalFilter) {
        if (_filter.value != newFilter) _filter.value = newFilter
    }

    /**
     * Plant Journal write-side: persist a note on a completed watering, then
     * reload the snapshot so the new note appears immediately. Empty / blank
     * input clears the note (column → NULL) — the user shouldn't have to delete
     * the entry to remove a note.
     */
    fun saveNoteForReminder(reminderId: Int, note: String?) {
        viewModelScope.launch {
            reminderRepo.setNoteForReminder(reminderId, note)
            refresh()
        }
    }

    /**
     * Sprint-1 Task 1.2: persist a brand-new free-text memo for the currently
     * loaded plant, then reload the snapshot so it appears at the top of the
     * timeline. Blank input is rejected at the UI layer; we treat it as a
     * no-op here as a defensive guard.
     */
    fun addMemo(text: String) {
        val plantId = lastPlantId
        if (plantId <= 0) return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                repo.addMemo(plantId, lastUserEmail, trimmed)
                refresh()
            } catch (c: kotlinx.coroutines.CancellationException) {
                // #7 fix: lifecycle cancellation must not flip
                // _memoError. Pre-fix the user saw a fake "save
                // failed" toast on the next screen for an operation
                // that had simply not finished before they navigated
                // away. Cooperate with structured concurrency.
                throw c
            } catch (t: Throwable) {
                // Room may throw SQLiteConstraintException on the ABORT
                // strategy or arbitrary IO errors. Without surfacing this
                // the user taps Save and just sees nothing happen.
                com.example.plantcare.CrashReporter.log(t)
                _memoError.postValue(true)
            }
        }
    }

    /**
     * Sprint-1 Task 1.3: edit an existing memo. Bumps `updatedAt` via the
     * repo so the edit floats the memo back to the top — same convention as
     * a Notes app. Blank input is treated as a no-op (the caller offers
     * delete as the explicit removal path).
     */
    fun updateMemo(memoId: Int, newText: String) {
        if (memoId <= 0) return
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                repo.updateMemo(memoId, trimmed)
                refresh()
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                com.example.plantcare.CrashReporter.log(t)
                _memoError.postValue(true)
            }
        }
    }

    /**
     * Sprint-1 Task 1.3: hard-delete a memo by id, then refresh so the
     * timeline drops the row immediately.
     */
    fun deleteMemo(memoId: Int) {
        if (memoId <= 0) return
        viewModelScope.launch {
            try {
                repo.deleteMemo(memoId)
                refresh()
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                com.example.plantcare.CrashReporter.log(t)
                _memoError.postValue(true)
            }
        }
    }
}
