package com.example.plantcare.ui.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.setFragmentResult
import com.example.plantcare.R
import com.example.plantcare.ui.util.ClickGuard
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * ImagePostCaptureBottomSheet (2026-05-20 Feature 1 — Crop)
 *
 * Shown after the user captures a photo or picks one from the gallery
 * inside [UnifiedCameraActivity]. Lets the user decide whether to crop
 * the image before it's routed to PlantIdentify / DiseaseDiagnosis.
 *
 * **Why this is a separate sheet** rather than always-on uCrop: many
 * captures are already well-framed (close-up of a single leaf) and a
 * mandatory crop step would feel like friction. The sheet preserves the
 * one-tap path for those cases while making the crop tool a single,
 * obvious tap away when the photo has clutter to remove (wall, pot,
 * unrelated foliage, ...).
 *
 * The sheet doesn't perform any image work itself — it returns a
 * choice via the [FragmentResult] API and lets the host Activity
 * (which already owns the file paths and the routing logic) carry
 * out the next step. Three possible outcomes:
 *
 *   - [RESULT_CHOICE_CROP]   → host should launch uCrop
 *   - [RESULT_CHOICE_SKIP]   → host should route the original file
 *   - [RESULT_CHOICE_CANCEL] → host should stay on the camera preview
 *
 * Show via:
 * ```
 * ImagePostCaptureBottomSheet.newInstance(filePath, mode)
 *     .show(supportFragmentManager, ImagePostCaptureBottomSheet.TAG)
 * ```
 *
 * The host registers a fragment-result listener on [REQUEST_KEY] in
 * onCreate so the answer survives configuration changes.
 */
class ImagePostCaptureBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "ImagePostCaptureBottomSheet"

        /** Fragment-result request key. Host listens on this. */
        const val REQUEST_KEY = "image_post_capture_request"

        /** Bundle keys inside the result. */
        const val RESULT_KEY_CHOICE = "choice"
        const val RESULT_KEY_FILE_PATH = "file_path"
        const val RESULT_KEY_MODE = "mode"

        /** Choice values stored at [RESULT_KEY_CHOICE]. */
        const val RESULT_CHOICE_CROP = "crop"
        const val RESULT_CHOICE_SKIP = "skip"
        const val RESULT_CHOICE_CANCEL = "cancel"

        private const val ARG_FILE_PATH = "arg_file_path"
        private const val ARG_MODE = "arg_mode"

        @JvmStatic
        fun newInstance(filePath: String, mode: String): ImagePostCaptureBottomSheet =
            ImagePostCaptureBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_FILE_PATH, filePath)
                    putString(ARG_MODE, mode)
                }
            }
    }

    private val filePath: String
        get() = arguments?.getString(ARG_FILE_PATH).orEmpty()

    private val mode: String
        get() = arguments?.getString(ARG_MODE).orEmpty()

    /** Tracks whether the user made an explicit choice; if they dismiss the
     *  sheet by swiping or tapping outside, [onDismiss] treats it as cancel. */
    private var choiceDispatched = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_image_post_capture, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 2026-05-20 click-guard: prevent double-tap from triggering uCrop twice
        view.findViewById<MaterialCardView>(R.id.optionCrop).setOnClickListener(
            ClickGuard.wrap {
                dispatchChoice(RESULT_CHOICE_CROP)
                dismissAllowingStateLoss()
            }
        )

        // 2026-05-20 click-guard: prevent double-tap from routing twice
        view.findViewById<MaterialCardView>(R.id.optionSkip).setOnClickListener(
            ClickGuard.wrap {
                dispatchChoice(RESULT_CHOICE_SKIP)
                dismissAllowingStateLoss()
            }
        )

        view.findViewById<MaterialButton>(R.id.buttonCancel).setOnClickListener {
            dispatchChoice(RESULT_CHOICE_CANCEL)
            dismissAllowingStateLoss()
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        // Swipe-down / tap-outside should behave like "Abbrechen" so the
        // host knows the user backed out and can re-enable the shutter.
        if (!choiceDispatched) {
            dispatchChoice(RESULT_CHOICE_CANCEL)
        }
        super.onDismiss(dialog)
    }

    private fun dispatchChoice(choice: String) {
        choiceDispatched = true
        setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putString(RESULT_KEY_CHOICE, choice)
                putString(RESULT_KEY_FILE_PATH, filePath)
                putString(RESULT_KEY_MODE, mode)
            }
        )
    }
}
