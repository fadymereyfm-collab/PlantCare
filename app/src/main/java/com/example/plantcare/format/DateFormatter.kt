package com.example.plantcare.format

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats dates for **display** according to the user's [AppearancePrefs.DateFormat].
 *
 * NOT used for wire formats — those stay `yyyy-MM-dd` Locale.US so SQLite
 * comparisons remain locale-invariant (see CLAUDE.md §4 + DEFERRED #1).
 */
object DateFormatter {

    private val ISO  = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val DE   = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)
    private val US   = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)

    fun format(context: Context, date: LocalDate): String =
        when (AppearancePrefs.getDateFormat(context)) {
            AppearancePrefs.DateFormat.ISO -> date.format(ISO)
            AppearancePrefs.DateFormat.DE  -> date.format(DE)
            AppearancePrefs.DateFormat.US  -> date.format(US)
        }

    /** For UI labels that show "today", "tomorrow", "in 3 days" — fallback to
     *  formatted date if outside the relative range. */
    fun formatRelativeOrAbsolute(context: Context, date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, date).toInt()
        return when (days) {
            0 -> "—"   // caller's responsibility to swap with localized "Heute"
            else -> format(context, date)
        }
    }
}
