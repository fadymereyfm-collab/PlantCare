package com.example.plantcare.format

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Wraps an Activity's base context with two PlantCare-wide overrides:
 *
 * 1. **Locale matching the per-app language picker (Phase B4).**
 *    Reads `AppCompatDelegate.getApplicationLocales()`; defaults to
 *    German when the user has not picked. Pinning the Configuration's
 *    locale here ensures every `getString()` lookup resolves via the
 *    correct `values/` variant (`values-en/` for English-picker users,
 *    `values/` for everyone else). Without this wrap, AppCompatDelegate
 *    alone is sometimes inconsistent on first-launch resource lookups.
 *
 * 2. **Font scale multiplier (the original purpose).** Layered on top
 *    of the system font scale via `AppearancePrefs.FontScale`.
 *
 * Used in `AppCompatActivity.attachBaseContext` for every Activity in
 * the app (MainActivity, OnboardingActivity, PlantsInRoomActivity,
 * PlantIdentifyActivity, DiseaseDiagnosisActivity, ...). Activity must
 * call `recreate()` after the user changes the font-scale pref OR the
 * language pref so a fresh baseContext is wrapped — that's what
 * SettingsDialogFragment / SettingsFragment do on toggle.
 *
 * 2026-05-18 audit fix: previously hard-coded `Locale("de")` here,
 * which overrode any explicit English choice from the B4 picker on
 * the next Activity launch (the picker call worked momentarily, then
 * every Activity wrap reset it to German). Now reads the picker's
 * choice via AppCompatDelegate.
 */
object FontScaleHelper {

    private val GERMAN: Locale = Locale("de")
    private val ENGLISH: Locale = Locale("en")

    @JvmStatic
    fun wrap(base: Context): Context {
        val baseConfig = base.resources.configuration
        val newConfig = Configuration(baseConfig)

        // 1) Honour the per-app language preference (Phase B4 picker).
        //    Empty list = user picked "System" or never picked → default
        //    to German per the original PlantCare market policy.
        val appLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        val target: Locale = if (!appLocales.isEmpty) {
            // pickerCompat may return en-US / en-GB / ... — collapse to the
            // 2-letter primary so we match values-en/ correctly.
            val tag = appLocales.toLanguageTags().lowercase()
            if (tag.startsWith("en")) ENGLISH else GERMAN
        } else {
            GERMAN
        }

        Locale.setDefault(target)
        newConfig.setLocale(target)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            newConfig.setLocales(LocaleList(target))
        }

        // 2) Layer the user-chosen font-scale multiplier on top of the
        //    system pref. NORMAL means no extra multiplier — but we
        //    still apply the locale, so the wrap is no longer a no-op
        //    for the NORMAL case.
        val pref = AppearancePrefs.getFontScale(base)
        if (pref != AppearancePrefs.FontScale.NORMAL) {
            newConfig.fontScale = baseConfig.fontScale * pref.multiplier
        }

        return base.createConfigurationContext(newConfig)
    }
}
