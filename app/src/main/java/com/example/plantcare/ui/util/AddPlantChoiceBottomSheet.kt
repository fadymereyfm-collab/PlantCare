package com.example.plantcare.ui.util

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.plantcare.R
import com.example.plantcare.ui.camera.UnifiedCameraActivity
import com.example.plantcare.ui.catalog.CatalogActivity
import com.example.plantcare.ui.util.ClickGuard
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView

/**
 * AddPlantChoiceBottomSheet (F2.4)
 *
 * Bottom-sheet picker shown when the user taps the "+" Extended FAB
 * inside Mein Garten. Two routes:
 *
 *   1. **Aus Katalog wählen** → starts [CatalogActivity] which hosts
 *      AllPlantsFragment (the curated 500+ plants list).
 *   2. **Mit Kamera erkennen** → starts [UnifiedCameraActivity] with
 *      `EXTRA_INITIAL_MODE = MODE_IDENTIFY` so the segmented slider
 *      defaults to "Pflanze erkennen" without an extra tap.
 *
 * The sheet dismisses itself after either choice; the downstream
 * activity owns the rest of the flow. No state to persist.
 *
 * Show via:
 * ```
 * AddPlantChoiceBottomSheet().show(parentFragmentManager, TAG)
 * ```
 */
class AddPlantChoiceBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "AddPlantChoiceBottomSheet"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_add_plant_choice, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 2026-05-19 click-guard: منع double-tap من فتح Catalog/Camera مرّتين
        view.findViewById<MaterialCardView>(R.id.optionCatalog).setOnClickListener(
            ClickGuard.wrap {
                startActivity(Intent(requireContext(), CatalogActivity::class.java))
                dismissAllowingStateLoss()
            }
        )

        view.findViewById<MaterialCardView>(R.id.optionCamera).setOnClickListener(
            ClickGuard.wrap {
                val intent = Intent(requireContext(), UnifiedCameraActivity::class.java)
                    .putExtra(
                        UnifiedCameraActivity.EXTRA_INITIAL_MODE,
                        UnifiedCameraActivity.MODE_IDENTIFY
                    )
                    startActivity(intent)
                dismissAllowingStateLoss()
            }
        )
    }
}
