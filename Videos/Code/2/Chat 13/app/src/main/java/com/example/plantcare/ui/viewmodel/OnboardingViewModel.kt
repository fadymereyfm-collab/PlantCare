package com.example.plantcare.ui.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Tracks the current onboarding page and the once-only completion flag.
 *
 * The starter-plant picker (page 4 in the previous design) was removed —
 * along with its `selectedPlants` / `availablePlants` / `togglePlantSelection`
 * machinery — because the catalog browser inside MainActivity already covers
 * the same need without forcing it on first-launch users.
 */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences: SharedPreferences =
        application.getSharedPreferences("prefs", 0)

    private val _currentPage = MutableLiveData(0)
    val currentPage: LiveData<Int> = _currentPage

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
    }
}
