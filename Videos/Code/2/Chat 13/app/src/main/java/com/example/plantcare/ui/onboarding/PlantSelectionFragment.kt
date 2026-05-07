package com.example.plantcare.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.plantcare.R
import com.example.plantcare.ui.viewmodel.OnboardingViewModel

/**
 * Fragment showing a grid of catalog plants the user can pick
 * as their "starter" plants during onboarding.
 */
class PlantSelectionFragment : Fragment() {

    private val viewModel: OnboardingViewModel by activityViewModels()
    private lateinit var adapter: PlantSelectionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_plant_selection, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerViewPlants = view.findViewById<RecyclerView>(R.id.recyclerViewPlants)

        // Adapter receives a plant ID on click → resolve it from the available list
        adapter = PlantSelectionAdapter { plantId ->
            val plant = viewModel.availablePlants.value?.find { it.id == plantId }
            if (plant != null) viewModel.togglePlantSelection(plant)
        }

        recyclerViewPlants.layoutManager = GridLayoutManager(requireContext(), 2)
        recyclerViewPlants.adapter = adapter

        viewModel.availablePlants.observe(viewLifecycleOwner) { plants ->
            adapter.submitList(plants)
        }

        viewModel.selectedPlantIds.observe(viewLifecycleOwner) { selectedIds ->
            adapter.updateSelectedIds(selectedIds)
        }
    }
}
