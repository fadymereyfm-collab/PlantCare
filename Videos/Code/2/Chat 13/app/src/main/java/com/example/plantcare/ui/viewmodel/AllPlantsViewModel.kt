package com.example.plantcare.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.plantcare.Plant
import com.example.plantcare.data.repository.PlantRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for AllPlantsFragment.
 * Uses PlantRepository instead of touching AppDatabase directly.
 */
class AllPlantsViewModel(application: Application) : AndroidViewModel(application) {

    private val plantRepo = PlantRepository.getInstance(application)

    private val _catalogPlants = MutableLiveData<List<Plant>>()
    val catalogPlants: LiveData<List<Plant>> = _catalogPlants

    private val _filteredPlants = MutableLiveData<List<Plant>>()
    val filteredPlants: LiveData<List<Plant>> = _filteredPlants

    private val _searchQuery = MutableLiveData("")
    val searchQuery: LiveData<String> = _searchQuery

    init {
        loadCatalogPlants()
    }

    fun loadCatalogPlants() {
        viewModelScope.launch {
            val plants = plantRepo.getAllCatalogPlantsList()
            _catalogPlants.value = plants
            _filteredPlants.value = plants
        }
    }

    fun searchPlants(query: String) {
        _searchQuery.value = query
        val allPlants = _catalogPlants.value.orEmpty()

        _filteredPlants.value = if (query.isBlank()) {
            allPlants
        } else {
            allPlants.filter { plant ->
                plant.name?.contains(query, ignoreCase = true) == true ||
                plant.nickname?.contains(query, ignoreCase = true) == true
            }
        }
    }
}
