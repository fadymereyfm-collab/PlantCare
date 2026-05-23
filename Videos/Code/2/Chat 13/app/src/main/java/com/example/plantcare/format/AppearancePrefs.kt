package com.example.plantcare.format

import android.content.Context

/**
 * Single source of truth for the appearance prefs added in Wave 2:
 *  - Units (metric / imperial)
 *  - Date format (ISO / DE / US)
 *  - Font scale multiplier (S / M / L / XL)
 *
 * Stored in the same `prefs` SharedPreferences file the rest of the app uses
 * so SettingsDialogFragment can read/write without owning a separate file.
 *
 * Defaults preserve historical behaviour:
 *  - units = METRIC (the entire codebase already speaks Celsius/cm)
 *  - dateFormat = ISO (`yyyy-MM-dd` — what we use as wire format already)
 *  - fontScale = NORMAL (no override)
 */
object AppearancePrefs {

    private const val PREFS_NAME = "prefs"
    private const val KEY_UNITS = "appearance_units"
    private const val KEY_DATE_FORMAT = "appearance_date_format"
    private const val KEY_FONT_SCALE = "appearance_font_scale"

    enum class Units { METRIC, IMPERIAL }
    enum class DateFormat { ISO, DE, US }

    /** Multiplier applied to fontScale on the resource Configuration. */
    enum class FontScale(val multiplier: Float) {
        SMALL(0.85f),
        NORMAL(1.0f),
        LARGE(1.15f),
        EXTRA_LARGE(1.30f);

        companion object {
            fun fromName(name: String?): FontScale = when (name) {
                "small" -> SMALL
                "large" -> LARGE
                "xlarge" -> EXTRA_LARGE
                else -> NORMAL
            }
        }

        fun storageKey(): String = when (this) {
            SMALL -> "small"
            NORMAL -> "normal"
            LARGE -> "large"
            EXTRA_LARGE -> "xlarge"
        }
    }

    fun getUnits(context: Context): Units =
        if (prefs(context).getString(KEY_UNITS, "metric") == "imperial") Units.IMPERIAL
        else Units.METRIC

    fun setUnits(context: Context, units: Units) {
        prefs(context).edit()
            .putString(KEY_UNITS, if (units == Units.IMPERIAL) "imperial" else "metric")
            .apply()
    }

    fun getDateFormat(context: Context): DateFormat =
        when (prefs(context).getString(KEY_DATE_FORMAT, "iso")) {
            "de" -> DateFormat.DE
            "us" -> DateFormat.US
            else -> DateFormat.ISO
        }

    fun setDateFormat(context: Context, format: DateFormat) {
        val raw = when (format) {
            DateFormat.DE -> "de"
            DateFormat.US -> "us"
            DateFormat.ISO -> "iso"
        }
        prefs(context).edit().putString(KEY_DATE_FORMAT, raw).apply()
    }

    fun getFontScale(context: Context): FontScale =
        FontScale.fromName(prefs(context).getString(KEY_FONT_SCALE, null))

    fun setFontScale(context: Context, scale: FontScale) {
        prefs(context).edit().putString(KEY_FONT_SCALE, scale.storageKey()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
