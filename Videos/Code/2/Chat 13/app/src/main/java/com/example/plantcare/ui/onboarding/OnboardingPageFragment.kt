package com.example.plantcare.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.plantcare.R

class OnboardingPageFragment : Fragment() {

    private var titleText: String = ""
    private var descriptionText: String = ""
    private var drawableRes: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            titleText = it.getString(ARG_TITLE, "")
            descriptionText = it.getString(ARG_DESCRIPTION, "")
            drawableRes = it.getInt(ARG_DRAWABLE, 0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_onboarding_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvDescription = view.findViewById<TextView>(R.id.tvDescription)
        val ivImage = view.findViewById<ImageView>(R.id.ivImage)

        tvTitle.text = titleText
        tvDescription.text = descriptionText

        if (drawableRes != 0) {
            ivImage.setImageResource(drawableRes)
        }
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_DRAWABLE = "drawable"

        fun newInstance(
            title: String,
            description: String,
            drawableRes: Int = 0
        ): OnboardingPageFragment {
            return OnboardingPageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_DESCRIPTION, description)
                    putInt(ARG_DRAWABLE, drawableRes)
                }
            }
        }
    }
}
