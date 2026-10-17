package com.example.plantcare.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.plantcare.data.disease.DiseaseDiagnosis
import com.example.plantcare.data.disease.DiseaseReferenceImage
import com.example.plantcare.data.disease.DiseaseResult
import com.example.plantcare.data.plantnet.PlantNetError
import com.example.plantcare.data.repository.DiseaseDiagnosisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel for the cloud-based disease diagnosis screen (Google Gemini 2.5 Flash).
 *
 * Two diagnosis strategies live behind one VM:
 *  1. **Initial analyze** — Gemini returns up to 3 differential candidates;
 *     the UI shows each as a card with reference images so the user can
 *     pick the visually-best match.
 *  2. **Re-analyze with exclusions** — when the user taps "Keine passt"
 *     after seeing the reference images, Gemini gets the rejected keys as
 *     an exclusion list and proposes alternatives.
 *
 * The error enum is still named [PlantNetError] for source compatibility
 * with the previous implementation — see PROGRESS.md F3-gemini.
 */
class DiseaseDiagnosisViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DiseaseDiagnosisRepository.getInstance(application)

    private val _uiState = MutableLiveData<DiseaseUiState>(DiseaseUiState.Idle)
    val uiState: LiveData<DiseaseUiState> = _uiState

    private val _results = MutableLiveData<List<DiseaseResult>>(emptyList())
    val results: LiveData<List<DiseaseResult>> = _results

    /**
     * Best-effort plant species identification produced by Gemini.
     * Displayed above the candidate cards, and compared against the
     * user's target plant name before save (conflict warning).
     */
    private val _plantSpecies = MutableLiveData<String?>(null)
    val plantSpecies: LiveData<String?> = _plantSpecies

    /**
     * Reference images keyed by disease key. Populated lazily as each card
     * scrolls into view (or right after results land — the activity decides).
     * Keeping these in a single LiveData keeps the adapter binding simple:
     * one observer drives all 3 carousels.
     */
    private val _referenceImages = MutableLiveData<Map<String, List<DiseaseReferenceImage>>>(emptyMap())
    val referenceImages: LiveData<Map<String, List<DiseaseReferenceImage>>> = _referenceImages

    /** User-selected candidate. Null until they tap "Passt zu meinem Foto". */
    private val _selectedCandidate = MutableLiveData<DiseaseResult?>(null)
    val selectedCandidate: LiveData<DiseaseResult?> = _selectedCandidate

    /**
     * Primary image path — kept for back-compat with observers that still
     * bind to a single ImageView. Always mirrors [_selectedImagePaths][0]
     * when the list is non-empty, null otherwise.
     */
    private val _selectedImagePath = MutableLiveData<String?>()
    val selectedImagePath: LiveData<String?> = _selectedImagePath

    /**
     * 2026-05-20 Feature 2 (Multi-image): 1-4 image paths for the current
     * diagnosis session. Index 0 is the "primary" photo (what gets saved
     * in [DiseaseDiagnosis.imagePath]). Subsequent indices are extra
     * angles/zoom levels sent to Gemini in the same request.
     */
    private val _selectedImagePaths = MutableLiveData<List<String>>(emptyList())
    val selectedImagePaths: LiveData<List<String>> = _selectedImagePaths

    private val _saved = MutableLiveData<Boolean>()
    val saved: LiveData<Boolean> = _saved

    /** Disease keys the user has visually rejected — drives re-prompt exclusion list. */
    private val rejectedKeys: MutableList<String> = mutableListOf()

    /**
     * Diagnose-Save-Event: bündelt die DB-id der neuen [DiseaseDiagnosis]-Zeile
     * mit der `plantId`, mit der gespeichert wurde (0 = "allgemein, keine
     * Pflanze"). Der Activity-Observer braucht beide:
     *  - `id` zum Verknüpfen der optionalen Archive-PlantPhoto-Zeile
     *  - `plantId` zum Entscheiden, ob der Archive-Prompt überhaupt sichtbar
     *    werden soll (Bug D28: vorher wurde `targetPlantId` aus dem Intent
     *    verwendet, was im "general → picker → save" Fluss immer 0 war und
     *    den Prompt unsichtbar machte).
     *
     * **Single-shot semantics**: der Activity muss [consumeSavedDiagnosisId]
     * aufrufen, sobald das Event verarbeitet wurde, sonst tauchen Prompt +
     * Toast bei jeder Rotation wieder auf.
     */
    data class SavedDiagnosisEvent(val id: Long, val plantId: Int)

    private val _savedDiagnosis = MutableLiveData<SavedDiagnosisEvent?>()
    val savedDiagnosis: LiveData<SavedDiagnosisEvent?> = _savedDiagnosis

    /** Clear the [savedDiagnosis] event so it doesn't replay on rotation. */
    fun consumeSavedDiagnosisId() {
        _savedDiagnosis.value = null
    }

    /**
     * Set the image path for the next analyze call. Replaces any
     * existing list with a single-element list. Resets result-related
     * state so a stale error / "no plant detected" message from a
     * previous attempt doesn't stick around once the user has picked a
     * new photo (Functional Report-style UX bug).
     */
    fun setImagePath(path: String) {
        _selectedImagePath.value = path
        _selectedImagePaths.value = listOf(path)
        resetDiagnosisState()
    }

    /**
     * 2026-05-20 Feature 2 (Multi-image): append an additional photo to
     * the current diagnosis session. No-op when already at [MAX_IMAGES].
     * Resets result state — adding a new photo invalidates any pending
     * results from a previous analyse call.
     */
    fun addImagePath(path: String) {
        val current = _selectedImagePaths.value.orEmpty()
        if (current.size >= MAX_IMAGES) return
        if (current.contains(path)) return
        val next = current + path
        _selectedImagePaths.value = next
        _selectedImagePath.value = next.firstOrNull()
        resetDiagnosisState()
    }

    /**
     * 2026-05-20 Feature 2 (Multi-image): remove the photo at [index].
     * Out-of-range index is a no-op. When the primary (index 0) is
     * removed, the second photo (if any) becomes the new primary.
     */
    fun removeImagePath(index: Int) {
        val current = _selectedImagePaths.value.orEmpty()
        if (index !in current.indices) return
        val next = current.toMutableList().apply { removeAt(index) }
        _selectedImagePaths.value = next
        _selectedImagePath.value = next.firstOrNull()
        resetDiagnosisState()
    }

    fun reset() {
        _selectedImagePath.value = null
        _selectedImagePaths.value = emptyList()
        resetDiagnosisState()
    }

    private fun resetDiagnosisState() {
        _uiState.value = DiseaseUiState.Idle
        _results.value = emptyList()
        _plantSpecies.value = null
        _referenceImages.value = emptyMap()
        _selectedCandidate.value = null
        _saved.value = false
        _savedDiagnosis.value = null
        rejectedKeys.clear()
    }

    /**
     * Send the image(s) to Gemini and update [uiState] / [results].
     * Back-compat: single-image callers can still pass one File.
     */
    fun analyze(imageFile: File) {
        rejectedKeys.clear()
        runAnalyze(listOf(imageFile))
    }

    /**
     * 2026-05-20 Feature 2 (Multi-image): analyze all images currently
     * in [_selectedImagePaths]. No-op when the list is empty.
     */
    fun analyzeAll() {
        val paths = _selectedImagePaths.value.orEmpty()
        if (paths.isEmpty()) return
        rejectedKeys.clear()
        runAnalyze(paths.map { File(it) })
    }

    /**
     * Re-prompt Gemini with the previously-shown disease keys flagged as
     * "rejected by visual inspection". The new candidates exclude all
     * already-rejected keys so the user sees fresh alternatives instead of
     * the same suggestions again. Re-uses the same image set.
     */
    fun rejectAllAndReanalyze(imageFile: File) {
        val current = _results.value.orEmpty().map { it.diseaseKey }
        rejectedKeys.addAll(current)
        // Honour the multi-image set if it has more than the single fallback,
        // otherwise fall back to the file the caller passed.
        val paths = _selectedImagePaths.value.orEmpty()
        val files = if (paths.size > 1) paths.map { File(it) } else listOf(imageFile)
        runAnalyze(files)
    }

    private fun runAnalyze(imageFiles: List<File>) {
        _uiState.value = DiseaseUiState.Loading
        _results.value = emptyList()
        _plantSpecies.value = null
        _referenceImages.value = emptyMap()
        _selectedCandidate.value = null
        viewModelScope.launch {
            try {
                val outcome = repository.analyze(
                    imageFiles = imageFiles,
                    excludeDiseaseKeys = rejectedKeys.toList()
                )
                when (outcome) {
                    is DiseaseDiagnosisRepository.DiagnoseResult.Found -> {
                        _results.postValue(outcome.results)
                        _plantSpecies.postValue(outcome.plantSpecies)
                        _uiState.postValue(DiseaseUiState.Success)
                        // Kick off reference-image fetches for every candidate
                        // in parallel — the cache hits return instantly, the
                        // rest stream in over the next few seconds.
                        outcome.results.forEach { result ->
                            launchReferenceFetch(result)
                        }
                    }
                    is DiseaseDiagnosisRepository.DiagnoseResult.NoMatch -> {
                        _results.postValue(emptyList())
                        _uiState.postValue(DiseaseUiState.NoResults(outcome.bodyPreview))
                    }
                    is DiseaseDiagnosisRepository.DiagnoseResult.PlantNotDetected -> {
                        _results.postValue(emptyList())
                        _uiState.postValue(DiseaseUiState.PlantNotDetected)
                    }
                    is DiseaseDiagnosisRepository.DiagnoseResult.Error -> {
                        _results.postValue(emptyList())
                        _uiState.postValue(DiseaseUiState.Error(outcome.type, outcome.rawMessage))
                    }
                }
            } catch (t: Throwable) {
                com.example.plantcare.CrashReporter.log(t)
                _uiState.postValue(DiseaseUiState.Error(PlantNetError.UNKNOWN, t.message))
            }
        }
    }

    private fun launchReferenceFetch(result: DiseaseResult) {
        viewModelScope.launch {
            val images = try {
                repository.fetchReferenceImages(result.diseaseKey, result.displayName)
            } catch (t: Throwable) {
                com.example.plantcare.CrashReporter.log(t)
                emptyList()
            }
            // ALWAYS commit the result — even an empty list — so the
            // carousel transitions from "loading" (null entry) to "empty
            // state" (empty entry). Without this, Gemini diagnoses that
            // don't exist in any of our 3 sources (e.g. physiological
            // states like "Wassermangel" that aren't pathologies) leave
            // the spinner running forever.
            //
            // Read-modify-write MUST happen on Main with setValue (NOT
            // postValue): three reference-fetches race here, and
            // postValue's async dispatch lets the second launch read a
            // stale `.value` before the first launch's post is applied,
            // producing a "last write wins" map that drops earlier entries.
            withContext(Dispatchers.Main.immediate) {
                val current = _referenceImages.value.orEmpty().toMutableMap()
                current[result.diseaseKey] = images
                _referenceImages.value = current
            }
        }
    }

    /** Mark a candidate as the user's chosen diagnosis (visual confirmation). */
    fun selectCandidate(result: DiseaseResult) {
        _selectedCandidate.value = result
    }

    /**
     * Persist the user-selected candidate. Falls back to the top result
     * for back-compat with callers that haven't selected explicitly
     * (e.g. older intents).
     */
    fun saveSelectedResult(userEmail: String?, plantId: Int = 0, note: String? = null) {
        val chosen = _selectedCandidate.value
            ?: _results.value?.firstOrNull()
            ?: return
        val path = _selectedImagePath.value ?: return
        viewModelScope.launch {
            val newId = repository.save(
                imagePath = path,
                result = chosen,
                plantId = plantId,
                userEmail = userEmail,
                note = note
            )
            _savedDiagnosis.postValue(SavedDiagnosisEvent(id = newId, plantId = plantId))
            _saved.postValue(true)
        }
    }

    /** Back-compat alias — older callers that always saved the top result. */
    fun saveTopResult(userEmail: String?, plantId: Int = 0, note: String? = null) {
        if (_selectedCandidate.value == null) {
            _selectedCandidate.value = _results.value?.firstOrNull()
        }
        saveSelectedResult(userEmail, plantId, note)
    }

    fun observeHistory(userEmail: String?): LiveData<List<DiseaseDiagnosis>> =
        repository.observeHistory(userEmail)

    fun deleteDiagnosis(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    companion object {
        /**
         * 2026-05-20 Feature 2 (Multi-image): hard cap on photos per
         * diagnosis. 4 was chosen from PictureThis's benchmark
         * (wide + close-up + underside + stem covers ~95% of useful
         * angles); higher counts inflate input tokens without measurable
         * accuracy gain.
         */
        const val MAX_IMAGES = 4
    }
}

sealed class DiseaseUiState {
    data object Idle : DiseaseUiState()
    data object Loading : DiseaseUiState()
    data object Success : DiseaseUiState()
    /** Gemini returned a 200 OK with `plant_detected=true` but an empty `results` array — bodyPreview helps distinguish parser bug from a model that genuinely couldn't decide. */
    data class NoResults(val bodyPreview: String? = null) : DiseaseUiState()
    /** Gemini returned `plant_detected=false` — the photo isn't a plant. */
    data object PlantNotDetected : DiseaseUiState()
    data class Error(val type: PlantNetError, val rawMessage: String? = null) : DiseaseUiState()
}
