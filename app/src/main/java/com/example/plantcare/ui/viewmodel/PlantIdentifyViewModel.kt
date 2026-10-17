package com.example.plantcare.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.plantcare.data.plantnet.IdentificationResult
import com.example.plantcare.data.plantnet.PlantNetError
import com.example.plantcare.data.repository.PlantIdentificationRepository
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for plant identification screen. Holds the PlantNet result list,
 * the current UI state, and the selected image path. Plant insertion goes
 * through `AddToMyPlantsDialogFragment` (driven by
 * `PlantIdentifyActivity.enrichAndOpenDialog`) — this ViewModel intentionally
 * does NOT own that flow; an earlier `addPlantToMyPlants` method existed
 * but was never wired to any UI and shipped with a hardcoded
 * `wateringInterval = 3` plus empty care fields, so removing it eliminates
 * a footgun for future contributors.
 */
class PlantIdentifyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlantIdentificationRepository.getInstance(application)

    /** Current UI state */
    private val _uiState = MutableLiveData<IdentifyUiState>(IdentifyUiState.Idle)
    val uiState: LiveData<IdentifyUiState> = _uiState

    /** Identification results */
    private val _results = MutableLiveData<List<IdentificationResult>>(emptyList())
    val results: LiveData<List<IdentificationResult>> = _results

    /** Selected image file path for display */
    private val _selectedImagePath = MutableLiveData<String?>()
    val selectedImagePath: LiveData<String?> = _selectedImagePath

    /**
     * Set the selected image file path (for preview).
     */
    fun setImagePath(path: String) {
        _selectedImagePath.value = path
    }

    /**
     * Identify the plant from the given image file.
     *
     * @param imageFile Image file of the plant
     * @param organ The plant organ visible (leaf, flower, fruit, bark, auto)
     */
    fun identifyPlant(imageFile: File, organ: String = "auto") {
        _uiState.value = IdentifyUiState.Loading

        viewModelScope.launch {
            try {
                when (val outcome = repository.identifyPlant(imageFile, organ)) {
                    is PlantIdentificationRepository.IdentifyResult.Found -> {
                        _results.postValue(outcome.results)
                        _uiState.postValue(IdentifyUiState.Success)
                    }
                    is PlantIdentificationRepository.IdentifyResult.NoMatch -> {
                        _results.postValue(emptyList())
                        _uiState.postValue(IdentifyUiState.NoResults)
                    }
                    is PlantIdentificationRepository.IdentifyResult.Error -> {
                        _results.postValue(emptyList())
                        _uiState.postValue(IdentifyUiState.Error(outcome.type))
                    }
                }
            } catch (e: Exception) {
                // Sicherheitsnetz — der Repository‑Pfad fängt normalerweise bereits alles ab.
                com.example.plantcare.CrashReporter.log(e)
                _uiState.postValue(IdentifyUiState.Error(PlantNetError.UNKNOWN))
            }
        }
    }

    /**
     * Reset the state for a new identification.
     */
    fun reset() {
        _uiState.value = IdentifyUiState.Idle
        _results.value = emptyList()
        _selectedImagePath.value = null
    }
}

/**
 * Sealed class representing the UI states for plant identification.
 */
sealed class IdentifyUiState {
    /** Initial state – waiting for user to take/pick a photo */
    data object Idle : IdentifyUiState()

    /** Identification in progress */
    data object Loading : IdentifyUiState()

    /** Results received successfully */
    data object Success : IdentifyUiState()

    /** No matching plant found */
    data object NoResults : IdentifyUiState()

    /**
     * Ein klassifizierter Fehler (Schlüssel, Quote, Netz, Timeout, Server, Unbekannt).
     * Der Aktivitäts‑Code übersetzt den Typ in eine menschliche Meldung aus strings.xml.
     */
    data class Error(val type: PlantNetError) : IdentifyUiState()
}
