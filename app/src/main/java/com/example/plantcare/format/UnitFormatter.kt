package com.example.plantcare.format

import android.content.Context
import kotlin.math.roundToInt

/**
 * Formats values for display according to the user's [AppearancePrefs.Units]
 * choice. Conversion uses standard factors:
 *  - Celsius → Fahrenheit: F = C * 9/5 + 32
 *  - cm     → inches:      in = cm / 2.54
 *
 * All callers should pass canonical SI values (Celsius, cm) and let this
 * helper format them. The DB and wire formats stay metric forever — only
 * the UI text changes.
 */
object UnitFormatter {

    /** "23°C" or "73°F" depending on user prefs. Input is Celsius. */
    fun formatTemp(context: Context, celsius: Double): String =
        when (AppearancePrefs.getUnits(context)) {
            AppearancePrefs.Units.METRIC ->
                "${celsius.roundToInt()}°C"
            AppearancePrefs.Units.IMPERIAL ->
                "${(celsius * 9.0 / 5.0 + 32.0).roundToInt()}°F"
        }

    /** "12 cm" or "5 in" depending on user prefs. Input is cm. */
    fun formatLength(context: Context, cm: Double): String =
        when (AppearancePrefs.getUnits(context)) {
            AppearancePrefs.Units.METRIC ->
                "${cm.roundToInt()} cm"
            AppearancePrefs.Units.IMPERIAL ->
                "${(cm / 2.54).roundToInt()} in"
        }
}
