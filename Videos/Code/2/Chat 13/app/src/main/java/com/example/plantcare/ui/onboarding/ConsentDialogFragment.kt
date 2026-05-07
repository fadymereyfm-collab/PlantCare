package com.example.plantcare.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.plantcare.ConsentManager
import com.example.plantcare.R
import com.google.android.material.button.MaterialButton

class ConsentDialogFragment : DialogFragment() {

    /** Called after the user makes a choice. True = analytics accepted. */
    var onConsentResult: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_consent, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<MaterialButton>(R.id.btnAccept).setOnClickListener {
            ConsentManager.setConsent(requireContext(), analyticsEnabled = true)
            onConsentResult?.invoke(true)
            dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btnDecline).setOnClickListener {
            ConsentManager.setConsent(requireContext(), analyticsEnabled = false)
            onConsentResult?.invoke(false)
            dismiss()
        }

        view.findViewById<TextView>(R.id.tvPrivacyLink).setOnClickListener {
            val url = getString(R.string.consent_privacy_url)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * 0.9).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }
}
