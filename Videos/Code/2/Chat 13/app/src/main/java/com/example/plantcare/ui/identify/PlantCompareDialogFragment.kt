package com.example.plantcare.ui.identify

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.example.plantcare.R
import com.google.android.material.button.MaterialButton
import java.io.File

/**
 * Full-screen comparison dialog.
 *
 * Shows the PlantNet reference image (top half) alongside the user's
 * captured photo (bottom half) so they can visually confirm whether
 * the suggested plant matches their specimen.
 *
 * Usage:
 *   val dlg = PlantCompareDialogFragment.newInstance(
 *       candidateImageUrl = result.largeImageUrl ?: result.imageUrl,
 *       capturedImagePath = viewModel.selectedImagePath.value ?: "",
 *       plantName         = result.commonName ?: result.scientificName
 *   )
 *   dlg.setOnConfirm { enrichAndOpenDialog(result) }
 *   dlg.show(supportFragmentManager, PlantCompareDialogFragment.TAG)
 */
class PlantCompareDialogFragment : DialogFragment() {

    private var onConfirmCallback: (() -> Unit)? = null

    /** Called when the user taps "Das bin ich!" — use to trigger enrichment + add flow. */
    fun setOnConfirm(callback: () -> Unit) {
        onConfirmCallback = callback
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Full-screen, no title bar
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_plant_compare, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args          = requireArguments()
        val candidateUrl  = args.getString(ARG_CANDIDATE_URL)
        val capturedPath  = args.getString(ARG_CAPTURED_PATH) ?: ""
        val plantName     = args.getString(ARG_PLANT_NAME) ?: ""

        // ── Toolbar ──────────────────────────────────────────────────────────
        view.findViewById<TextView>(R.id.txtComparePlantName).text = plantName

        view.findViewById<ImageButton>(R.id.btnCompareClose).setOnClickListener {
            dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btnCompareConfirm).setOnClickListener {
            onConfirmCallback?.invoke()
            dismiss()
        }

        // ── Candidate image (top) ────────────────────────────────────────────
        val imgCandidate = view.findViewById<ImageView>(R.id.imgCandidatePlant)
        if (!candidateUrl.isNullOrBlank()) {
            Glide.with(this)
                .load(candidateUrl)
                .centerCrop()
                .placeholder(R.drawable.ic_plant_placeholder)
                .error(R.drawable.ic_plant_placeholder)
                .into(imgCandidate)
        } else {
            imgCandidate.setImageResource(R.drawable.ic_plant_placeholder)
        }

        // ── Captured photo (bottom) ──────────────────────────────────────────
        val imgCaptured = view.findViewById<ImageView>(R.id.imgCapturedPhoto)
        if (capturedPath.isNotBlank()) {
            Glide.with(this)
                .load(File(capturedPath))
                .centerCrop()
                .placeholder(R.drawable.ic_plant_placeholder)
                .error(R.drawable.ic_plant_placeholder)
                .into(imgCaptured)
        } else {
            imgCaptured.setImageResource(R.drawable.ic_plant_placeholder)
        }
    }

    override fun onStart() {
        super.onStart()
        // Ensure the window fills the entire screen
        dialog?.window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    // ── Factory ──────────────────────────────────────────────────────────────

    companion object {
        const val TAG = "plant_compare"

        private const val ARG_CANDIDATE_URL  = "candidate_url"
        private const val ARG_CAPTURED_PATH  = "captured_path"
        private const val ARG_PLANT_NAME     = "plant_name"

        fun newInstance(
            candidateImageUrl: String?,
            capturedImagePath: String,
            plantName: String
        ): PlantCompareDialogFragment = PlantCompareDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_CANDIDATE_URL, candidateImageUrl)
                putString(ARG_CAPTURED_PATH, capturedImagePath)
                putString(ARG_PLANT_NAME, plantName)
            }
        }
    }
}
