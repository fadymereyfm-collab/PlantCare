package com.example.plantcare.format

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Wraps an Activity's base context with two PlantCare-wide overrides:
 *
 * 1. **Locale = German.** PlantCare ships only to the German market
 *    (CLAUDE.md §6, build.gradle resConfigs "de","en"). The previous
 *    `AppCompatDelegate.setApplicationLocales("de")` call in
 *    `App.applyAppLocale()` set the per-app preference but didn't
 *    consistently make first-launch activities resolve resources via
 *    `values/` — on an English-locale device the user still saw EN
 *    strings (top tabs "All plants/My plants", auth dialogs, plant
 *    detail labels, etc.). Wrapping the base context here pins the
 *    Configuration's locale to DE so every `getString()` lookup hits
 *    the German `values/strings.xml` regardless of device locale.
 *
 * 2. **Font scale multiplier (the original purpose).** Layered on top
 *    of the system font scale via `AppearancePrefs.FontScale`.
 *
 * Used in `AppCompatActivity.attachBaseContext` for every Activity in
 * the app (MainActivity, OnboardingActivity, PlantsInRoomActivity,
 * PlantIdentifyActivity, DiseaseDiagnosisActivity, ...). Activity must
 * call `recreate()` after the user changes the font-scale pref so a
 * fresh baseContext is wrapped — that's what SettingsDialogFragment
 * does on toggle.
 */
object FontScaleHelper {

    private val GERMAN: Locale = Locale("de")

    @JvmStatic
    fun wrap(base: Context): Context {
        val baseConfig = base.resources.configuration
        val newConfig = Configuration(baseConfig)

        // 1) Force German UI locale so every R.string.* lookup resolves
        //    via values/ rather than the device-locale variant
        //    (values-en/ in particular).
        Locale.setDefault(GERMAN)
        newConfig.setLocale(GERMAN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            newConfig.setLocales(LocaleList(GERMAN))
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
