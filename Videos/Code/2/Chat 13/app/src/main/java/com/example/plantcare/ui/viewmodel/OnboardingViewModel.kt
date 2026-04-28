package com.example.plantcare.ui.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.plantcare.Plant
import com.example.plantcare.data.repository.PlantRepository
import kotlinx.coroutines.launch

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val plantRepo = PlantRepository.getInstance(application)
    private val sharedPreferences: SharedPreferences =
        application.getSharedPreferences("prefs", 0)

    private val _currentPage = MutableLiveData(0)
    val currentPage: LiveData<Int> = _currentPage

    private val _selectedPlants = MutableLiveData<List<Plant>>(emptyList())
    val selectedPlants: LiveData<List<Plant>> = _selectedPlants

    private val _selectedPlantIds = MutableLiveData<Set<Int>>(emptySet())
    val selectedPlantIds: LiveData<Set<Int>> = _selectedPlantIds

    private val _availablePlants = MutableLiveData<List<Plant>>(emptyList())
    val availablePlants: LiveData<List<Plant>> = _availablePlants

    init {
        loadAvailablePlants()
    }

    fun loadAvailablePlants() {
        viewModelScope.launch {
            try {
                _availablePlants.value = plantRepo.getAllCatalogPlantsList()
            } catch (e: Exception) {
                com.example.plantcare.CrashReporter.log(e)
                _availablePlants.value = emptyList()
            }
        }
    }

    fun togglePlantSelection(plant: Plant) {
        val currentSelected = _selectedPlants.value.orEmpty()
        val isCurrentlySelected = currentSelected.any { it.id == plant.id }

        val updated = if (isCurrentlySelected) {
            currentSelected.filter { it.id != plant.id }
        } else {
            currentSelected + plant
        }
        _selectedPlants.value = updated
        _selectedPlantIds.value = updated.map { it.id }.toSet()
    }

    fun goToPage(pageNumber: Int) { _currentPage.value = pageNumber }
    fun nextPage() { _currentPage.value = (_currentPage.value ?: 0) + 1 }
    fun previousPage() {
        val current = _currentPage.value ?: 0
        if (current > 0) _currentPage.value = current - 1
    }

    fun completeOnboarding() {
        sharedPreferences.edit().putBoolean("onboarding_completed", true).apply()
    }

    fun isOnboardingCompleted(): Boolean =
        sharedPreferences.getBoolean("onboarding_completed", false)

    fun resetOnboarding() {
        sharedPreferences.edit().putBoolean("onboarding_completed", false).apply()
        _currentPage.value = 0
        _selectedPlants.value = emptyList()
        _selectedPlantIds.value = emptySet()
    }

    fun getSelectedPlantIds(): List<Int> =
        _selectedPlants.value?.map { it.id } ?: emptyList()
}
