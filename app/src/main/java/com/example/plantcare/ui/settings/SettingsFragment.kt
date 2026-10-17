package com.example.plantcare.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import com.example.plantcare.BuildConfig
import com.example.plantcare.ConsentManager
import com.example.plantcare.CrashReporter
import com.example.plantcare.DataExportManager
import com.example.plantcare.EmailContext
import com.example.plantcare.R
import com.example.plantcare.SettingsDialogFragment
import com.example.plantcare.billing.BillingManager
import com.example.plantcare.billing.PaywallDialogFragment
import com.example.plantcare.billing.ProStatusManager
import com.example.plantcare.data.repository.AuthRepository
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth

/**
 * SettingsFragment (F2.6)
 *
 * Replaces the F2.2 placeholder. Owns the sectioned UI in
 * `fragment_settings.xml` and wires up the rows that are safe to inline:
 *
 *  - Pro hero card / Subscription status / Restore purchases
 *  - Theme radios (Light / Dark / System)
 *  - Language picker (DE / EN / System — AppCompatDelegate)
 *  - Data export (DataExportManager)
 *  - Analytics + Crash reports consent toggles (ConsentManager)
 *  - Privacy + Terms URLs (existing strings)
 *  - Contact (mailto:), Rate us (Play Store), Share app (ACTION_SEND)
 *  - Version + Open-source licenses (OssLicensesMenuActivity)
 *  - Logout (AuthRepository.logout)
 *  - Name + Email display (read from AuthRepository / EmailContext)
 *
 * Long-tail flows (vacation date pickers, change-password flow,
 * delete-account flow, avatar/bio editor, per-type notifications,
 * family share) are reached via the "Erweiterte Einstellungen" row
 * (last row of the ÜBER card) which opens the existing
 * [SettingsDialogFragment] unchanged. This keeps every F18.x feature
 * reachable while the surrounding shell follows the new design.
 *
 * Lifecycle: held by MainActivity in the NAV_INDEX_SETTINGS slot. No
 * private state — all values come from prefs / Firestore / BillingManager
 * each time the fragment is shown.
 */
class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindProHero(view)
        bindAccountSection(view)
        bindAppearanceSection(view)
        bindSubscriptionSection(view)
        bindDataPrivacySection(view)
        bindHelpSection(view)
        bindAboutSection(view)
    }

    override fun onResume() {
        super.onResume()
        // Re-read Pro state on resume so a purchase made in another
        // activity (Paywall, Restore) is reflected when the user returns.
        view?.let {
            bindProHero(it)
            bindSubscriptionSection(it)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 1) Pro hero
    // ─────────────────────────────────────────────────────────────────────
    private fun bindProHero(root: View) {
        val card = root.findViewById<MaterialCardView>(R.id.proHeroCard)
        val title = root.findViewById<TextView>(R.id.proHeroTitle)
        val subtitle = root.findViewById<TextView>(R.id.proHeroSubtitle)
        val cta = root.findViewById<View>(R.id.proHeroCta)

        val isPro = ProStatusManager.isPro(requireContext())
        if (isPro) {
            title.setText(R.string.settings_pro_hero_active_title)
            subtitle.setText(R.string.settings_pro_hero_active_subtitle)
            cta.visibility = View.GONE
        } else {
            title.setText(R.string.settings_pro_hero_title)
            subtitle.setText(R.string.settings_pro_hero_subtitle)
            cta.visibility = View.VISIBLE
            cta.setOnClickListener {
                PaywallDialogFragment().show(parentFragmentManager, PaywallDialogFragment.TAG)
            }
        }
        card.visibility = View.VISIBLE
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2) KONTO
    // ─────────────────────────────────────────────────────────────────────
    private fun bindAccountSection(root: View) {
        val ctx = requireContext()
        val email = EmailContext.current(ctx) ?: ""

        // Optimistic placeholder: render the email as the name immediately
        // so the user never sees an empty row. The IO load below overwrites
        // it with the real display name from the local users row.
        val nameView = root.findViewById<TextView>(R.id.rowNameValue)
        nameView.text = email.takeIf { it.isNotBlank() } ?: "—"
        root.findViewById<TextView>(R.id.rowEmailValue).text =
            email.ifBlank { "—" }

        // F2.6 — load the User row off the main thread (CLAUDE.md threading
        // rule). FragmentBg.runWithResult handles fragment-lifetime safety;
        // the UI callback only fires if the fragment is still added.
        com.example.plantcare.ui.util.FragmentBg.runWithResult<String?>(this,
            {
                if (email.isBlank()) null
                else try {
                    AuthRepository.getInstance(ctx)
                        .getUserByEmailBlocking(email)
                        ?.name
                        ?.takeIf { it.isNotBlank() }
                } catch (t: Throwable) {
                    CrashReporter.log(t)
                    null
                }
            },
            { resolved ->
                if (resolved != null) nameView.text = resolved
            }
        )

        // Tap on Name → open the legacy dialog so the user can edit it
        // (full validation + Firestore sync flow already lives there).
        root.findViewById<View>(R.id.rowName).setOnClickListener {
            openLegacyDialog()
        }

        root.findViewById<View>(R.id.rowLogout).setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.settings_row_logout)
                .setMessage(R.string.auth_logout_confirm_message)
                .setPositiveButton(R.string.settings_row_logout) { d, _ ->
                    d.dismiss()
                    try {
                        FirebaseAuth.getInstance().signOut()
                    } catch (t: Throwable) {
                        CrashReporter.log(t)
                    }
                    AuthRepository.getInstance(ctx).logout()
                    // Recreate the host activity to bounce back into Onboarding.
                    requireActivity().recreate()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        root.findViewById<View>(R.id.rowDeleteAccount).setOnClickListener {
            // The delete-account flow is non-trivial (Firebase reauth,
            // password prompt, Storage + Firestore cascade) — defer to
            // the existing Dialog implementation, which already passed
            // multiple Bug-#5 review rounds.
            openLegacyDialog()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 3) DARSTELLUNG
    // ─────────────────────────────────────────────────────────────────────
    private fun bindAppearanceSection(root: View) {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val themeGroup = root.findViewById<RadioGroup>(R.id.themeGroup)

        when (prefs.getString("theme_mode", "system")) {
            "light" -> themeGroup.check(R.id.themeLight)
            "dark"  -> themeGroup.check(R.id.themeDark)
            else    -> themeGroup.check(R.id.themeSystem)
        }
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.themeLight -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    "light"
                }
                R.id.themeDark -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    "dark"
                }
                else -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    "system"
                }
            }
            prefs.edit().putString("theme_mode", mode).apply()
        }

        val languageValue = root.findViewById<TextView>(R.id.rowLanguageValue)
        languageValue.text = currentLanguageLabel()
        root.findViewById<View>(R.id.rowLanguage).setOnClickListener {
            showLanguagePicker { languageValue.text = currentLanguageLabel() }
        }
    }

    private fun currentLanguageLabel(): String {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return when {
            tags.startsWith("en") -> getString(R.string.settings_language_en)
            tags.startsWith("de") -> getString(R.string.settings_language_de)
            else -> getString(R.string.settings_theme_system)
        }
    }

    private fun showLanguagePicker(onPicked: () -> Unit) {
        val labels = arrayOf(
            getString(R.string.settings_theme_system),
            getString(R.string.settings_language_de),
            getString(R.string.settings_language_en)
        )
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val initial = when {
            tags.isEmpty() -> 0
            tags.startsWith("de") -> 1
            tags.startsWith("en") -> 2
            else -> 0
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_row_language)
            .setSingleChoiceItems(labels, initial) { d, which ->
                val target = when (which) {
                    1 -> LocaleListCompat.forLanguageTags("de")
                    2 -> LocaleListCompat.forLanguageTags("en")
                    else -> LocaleListCompat.getEmptyLocaleList()
                }
                AppCompatDelegate.setApplicationLocales(target)
                onPicked()
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────
    // 4) ABONNEMENT
    // ─────────────────────────────────────────────────────────────────────
    private fun bindSubscriptionSection(root: View) {
        val ctx = requireContext()
        val isPro = ProStatusManager.isPro(ctx)
        root.findViewById<TextView>(R.id.rowSubscriptionValue).setText(
            if (isPro) R.string.settings_subscription_status_pro
            else R.string.settings_subscription_status_free
        )
        root.findViewById<View>(R.id.rowRestorePurchases).setOnClickListener {
            try {
                BillingManager.getInstance(ctx).restorePurchasesAsync()
                Toast.makeText(ctx, R.string.paywall_restore_done, Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                CrashReporter.log(t)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 5) DATEN & DATENSCHUTZ
    // ─────────────────────────────────────────────────────────────────────
    private fun bindDataPrivacySection(root: View) {
        val ctx = requireContext()

        val dataExportRow = root.findViewById<View>(R.id.rowDataExport)
        dataExportRow.setOnClickListener {
            dataExportRow.isEnabled = false
            DataExportManager.exportAndShare(ctx, EmailContext.current(ctx) ?: "") { intent ->
                if (!isAdded) return@exportAndShare
                dataExportRow.isEnabled = true
                if (intent != null) {
                    try {
                        startActivity(
                            Intent.createChooser(intent, getString(R.string.settings_row_data_export))
                        )
                    } catch (t: Throwable) {
                        CrashReporter.log(t)
                        Toast.makeText(ctx, R.string.settings_export_error, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(ctx, R.string.settings_export_error, Toast.LENGTH_SHORT).show()
                }
            }
        }

        val analytics = root.findViewById<SwitchMaterial>(R.id.switchAnalytics)
        analytics.isChecked = ConsentManager.isAnalyticsEnabled(ctx)
        analytics.setOnCheckedChangeListener { _, checked ->
            ConsentManager.setAnalyticsEnabled(ctx, checked)
        }

        val crash = root.findViewById<SwitchMaterial>(R.id.switchCrashReports)
        crash.isChecked = ConsentManager.isCrashReportsEnabled(ctx)
        crash.setOnCheckedChangeListener { _, checked ->
            ConsentManager.setCrashReportsEnabled(ctx, checked)
        }

        root.findViewById<View>(R.id.rowPrivacy).setOnClickListener {
            openUrl(getString(R.string.consent_privacy_url))
        }
        root.findViewById<View>(R.id.rowTerms).setOnClickListener {
            openUrl(getString(R.string.consent_terms_url))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 6) HILFE & FEEDBACK
    // ─────────────────────────────────────────────────────────────────────
    private fun bindHelpSection(root: View) {
        val ctx = requireContext()

        root.findViewById<View>(R.id.rowContact).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${getString(R.string.settings_contact_email)}")
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.settings_contact_subject))
            }
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(ctx, R.string.settings_no_email, Toast.LENGTH_SHORT).show()
            }
        }

        root.findViewById<View>(R.id.rowRateUs).setOnClickListener {
            val appId = ctx.packageName.removeSuffix(".dev")
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appId"))
            try {
                startActivity(market)
            } catch (e: ActivityNotFoundException) {
                // Fall back to the https Play Store URL — works in any browser.
                openUrl("https://play.google.com/store/apps/details?id=$appId")
            }
        }

        root.findViewById<View>(R.id.rowShareApp).setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, getString(R.string.settings_share_app_text))
            }
            try {
                startActivity(Intent.createChooser(intent, getString(R.string.settings_row_share_app)))
            } catch (t: Throwable) {
                CrashReporter.log(t)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 7) ÜBER
    // ─────────────────────────────────────────────────────────────────────
    private fun bindAboutSection(root: View) {
        root.findViewById<TextView>(R.id.rowVersionValue).text =
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        root.findViewById<View>(R.id.rowOssLicenses).setOnClickListener {
            // Mirrors SettingsDialogFragment.showOssLicenses — single
            // AlertDialog backed by curated strings, no Play OSS Licenses
            // plugin dependency.
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_help_licenses_title)
                .setMessage(R.string.settings_help_licenses_body)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        root.findViewById<View>(R.id.rowAdvanced).setOnClickListener {
            openLegacyDialog()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────
    private fun openLegacyDialog() {
        SettingsDialogFragment().show(parentFragmentManager, "settings_dialog")
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.settings_no_browser, Toast.LENGTH_SHORT).show()
        }
    }
}
