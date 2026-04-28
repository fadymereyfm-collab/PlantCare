package com.example.plantcare.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.plantcare.Plant
import com.example.plantcare.data.plantnet.IdentificationResult
import com.example.plantcare.data.plantnet.PlantNetError
import com.example.plantcare.data.repository.PlantIdentificationRepository
import com.example.plantcare.data.repository.PlantRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

/**
 * ViewModel for plant identification screen.
 * Manages the identification state and adding identified plants to the user's collection.
 */
class PlantIdentifyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlantIdentificationRepository.getInstance(application)
    private val plantRepo = PlantRepository.getInstance(application)

    /** Current UI state */
    private val _uiState = MutableLiveData<IdentifyUiState>(IdentifyUiState.Idle)
    val uiState: LiveData<IdentifyUiState> = _uiState

    /** Identification results */
    private val _results = MutableLiveData<List<IdentificationResult>>(emptyList())
    val results: LiveData<List<IdentificationResult>> = _results

    /** Selected image file path for display */
    private val _selectedImagePath = MutableLiveData<String?>()
    val selectedImagePath: LiveData<String?> = _selectedImagePath

    /** Plant added event (one-shot) */
    private val _plantAdded = MutableLiveData<Boolean>()
    val plantAdded: LiveData<Boolean> = _plantAdded

    /** منع الإضافة المكررة: true أثناء تنفيذ addPlantToMyPlants */
    @Volatile private var isAdding: Boolean = false

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
                e.printStackTrace()
                _uiState.postValue(IdentifyUiState.Error(PlantNetError.UNKNOWN))
            }
        }
    }

    /**
     * Add the identified plant to the user's collection (MyPlants).
     *
     * @param result The identification result to add
     * @param userEmail Current user's email
     * @param imageUri Optional image URI to use as the plant's profile image
     * @param roomId Target room id (0 = nicht zugeordnet)
     */
    fun addPlantToMyPlants(
        result: IdentificationResult,
        userEmail: String,
        imageUri: String?,
        roomId: Int
    ) {
        // Guard: ignore duplicate taps while an insert is already in flight
        if (isAdding) return
        isAdding = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val plant = Plant().apply {
                    name = result.commonName ?: result.scientificName
                    nickname = ""
                    startDate = Date()
                    wateringInterval = 3 // Default: every 3 days
                    isUserPlant = true
                    lighting = ""
                    soil = ""
                    fertilizing = ""
                    watering = ""
                    this.imageUri = imageUri
                    isFavorite = false
                    personalNote = "Wissenschaftlicher Name: ${result.scientificName}" +
                            (result.family?.let { "\nFamilie: $it" } ?: "")
                    this.userEmail = userEmail
                    this.roomId = roomId
                }
                plantRepo.insertPlant(plant)
                _plantAdded.postValue(true)
            } finally {
                // لا تُعِد isAdding إلى false: بعد النجاح يُغلق النشاط على أي حال.
                // ولو فشل الإدراج في رمي استثناء، سيرفع Crash أو يُسجّل؛ نحافظ على قفل الحماية.
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
        _plantAdded.value = false
        isAdding = false
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
