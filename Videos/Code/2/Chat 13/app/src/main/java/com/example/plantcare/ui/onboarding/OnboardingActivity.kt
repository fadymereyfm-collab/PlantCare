package com.example.plantcare.ui.onboarding

import android.content.Context
import android.content.Intent
import com.example.plantcare.EmailContext
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.plantcare.AuthStartDialogFragment
import com.example.plantcare.ConsentManager
import com.example.plantcare.MainActivity
import com.example.plantcare.R
import com.example.plantcare.ui.viewmodel.OnboardingViewModel

/**
 * Onboarding screen shown on first app launch.
 * Pages: Welcome -> Watering Reminders -> Catalog & Archive -> Plant Selection
 * Options: Create account / Login / Try without account (Guest mode)
 * A DSGVO consent dialog is shown before the user leaves onboarding.
 */
class OnboardingActivity : AppCompatActivity() {

    private val viewModel: OnboardingViewModel by viewModels()

    private lateinit var viewPager: ViewPager2
    private lateinit var dotsContainer: LinearLayout
    private lateinit var btnNext: Button
    private lateinit var btnSkip: Button
    private lateinit var btnCreateAccount: Button
    private lateinit var btnLogin: Button
    private lateinit var btnGuestMode: Button
    private lateinit var authButtonsContainer: LinearLayout

    companion object {
        private const val PREF_ONBOARDING = "prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val GUEST_EMAIL = "guest@local"
        private const val TOTAL_PAGES = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREF_ONBOARDING, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)) {
            goToMainActivity()
            return
        }

        setContentView(R.layout.activity_onboarding)
        initializeViews()
        setupViewPager()
        setupButtons()
        setupDotsIndicator()

        viewModel.currentPage.observe(this) { page ->
            updateDotsIndicator(page)
            updateButtonVisibility(page)
        }
    }

    private fun initializeViews() {
        viewPager = findViewById(R.id.viewPager)
        dotsContainer = findViewById(R.id.dotsContainer)
        btnNext = findViewById(R.id.btnNext)
        btnSkip = findViewById(R.id.btnSkip)
        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        btnLogin = findViewById(R.id.btnLogin)
        btnGuestMode = findViewById(R.id.btnGuestMode)
        authButtonsContainer = findViewById(R.id.authButtonsContainer)
    }

    private fun setupViewPager() {
        val adapter = OnboardingPagerAdapter(this)
        viewPager.adapter = adapter
        viewPager.isUserInputEnabled = true

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.goToPage(position)
            }
        })
    }

    private fun setupButtons() {
        btnNext.setOnClickListener {
            val currentPage = viewModel.currentPage.value ?: 0
            if (currentPage < TOTAL_PAGES - 1) {
                viewPager.currentItem = currentPage + 1
            }
        }

        btnSkip.setOnClickListener {
            viewPager.currentItem = TOTAL_PAGES - 1
        }

        btnCreateAccount.setOnClickListener {
            showConsentThenProceed {
                markOnboardingComplete()
                startActivity(Intent(this, MainActivity::class.java).putExtra("show_auth", true))
                finish()
            }
        }

        btnLogin.setOnClickListener {
            showConsentThenProceed {
                markOnboardingComplete()
                startActivity(Intent(this, MainActivity::class.java).putExtra("show_auth", true))
                finish()
            }
        }

        btnGuestMode.setOnClickListener {
            showConsentThenProceed {
                EmailContext.setCurrent(this@OnboardingActivity, GUEST_EMAIL, true)
                markOnboardingComplete()
                goToMainActivity()
            }
        }
    }

    private fun showConsentThenProceed(onDone: () -> Unit) {
        if (ConsentManager.hasConsentBeenAsked(this)) {
            onDone()
            return
        }
        val dialog = ConsentDialogFragment()
        dialog.onConsentResult = { _ -> onDone() }
        dialog.show(supportFragmentManager, "consent")
    }

    private fun markOnboardingComplete() {
        val prefs = getSharedPreferences(PREF_ONBOARDING, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
        viewModel.completeOnboarding()
    }

    private fun setupDotsIndicator() {
        dotsContainer.removeAllViews()
        for (i in 0 until TOTAL_PAGES) {
            val dot = View(this).apply {
                val dotSize = 12
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    marginStart = 8
                    marginEnd = 8
                }
                background = ContextCompat.getDrawable(
                    this@OnboardingActivity,
                    R.drawable.dot_indicator_inactive
                )
            }
            dotsContainer.addView(dot)
        }
        updateDotsIndicator(0)
    }

    private fun updateDotsIndicator(currentPage: Int) {
        for (i in 0 until dotsContainer.childCount) {
            val dot = dotsContainer.getChildAt(i)
            dot.background = ContextCompat.getDrawable(
                this,
                if (i == currentPage) R.drawable.dot_indicator_active
                else R.drawable.dot_indicator_inactive
            )
        }
    }

    private fun updateButtonVisibility(currentPage: Int) {
        if (currentPage == TOTAL_PAGES - 1) {
            btnNext.visibility = View.GONE
            btnSkip.visibility = View.GONE
            authButtonsContainer.visibility = View.VISIBLE
        } else {
            btnNext.visibility = View.VISIBLE
            btnSkip.visibility = View.VISIBLE
            authButtonsContainer.visibility = View.GONE
        }
    }

    private fun goToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private inner class OnboardingPagerAdapter(
        activity: AppCompatActivity
    ) : FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = TOTAL_PAGES

        override fun createFragment(position: Int) = when (position) {
            0 -> OnboardingPageFragment.newInstance(
                title = getString(R.string.onboarding_welcome_title),
                description = getString(R.string.onboarding_welcome_desc),
                drawableRes = R.drawable.ic_plant_placeholder
            )
            1 -> OnboardingPageFragment.newInstance(
                title = getString(R.string.onboarding_watering_title),
                description = getString(R.string.onboarding_watering_desc),
                drawableRes = R.drawable.ic_water_drop
            )
            2 -> OnboardingPageFragment.newInstance(
                title = getString(R.string.onboarding_catalog_title),
                description = getString(R.string.onboarding_catalog_desc),
                drawableRes = R.drawable.ic_plant_placeholder
            )
            3 -> PlantSelectionFragment()
            else -> OnboardingPageFragment()
        }
    }
}
