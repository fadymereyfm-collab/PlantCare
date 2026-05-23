package com.example.plantcare.util

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.example.plantcare.R
import com.example.plantcare.WateringReminder

/**
 * v16 — single source of truth for the per-reminder-type visual treatment
 * (icon + tint) used across the Today list, the Calendar grid, and any
 * future per-type filter chip.
 *
 * Mapping:
 *   "water"     → blue water-drop  (default; matches the legacy Gießen icon)
 *   "fertilize" → green leaf
 *   "mist"      → light-blue droplet fan
 *   "repot"     → brown pot
 *
 * Treats NULL/blank/unknown as "water" so reminders created before the v15
 * type column was added (and any future unknown values from a newer
 * client version syncing back) render with the historical icon.
 */
object ReminderTypeUi {

    @DrawableRes
    fun iconFor(type: String?): Int = when (type?.lowercase()) {
        "fertilize" -> R.drawable.ic_reminder_fertilize
        "mist"      -> R.drawable.ic_reminder_mist
        "repot"     -> R.drawable.ic_reminder_repot
        // "water" + null + unknown share the historical watering-can asset.
        else        -> R.drawable.ic_watering_can
    }

    @ColorRes
    fun tintFor(type: String?): Int = when (type?.lowercase()) {
        "fertilize" -> R.color.reminder_type_fertilize
        "mist"      -> R.color.reminder_type_mist
        "repot"     -> R.color.reminder_type_repot
        else        -> R.color.reminder_type_water
    }

    /**
     * v16 close-out — true when the user has the per-type notification
     * toggle enabled in Settings. Treats NULL/blank/unknown as "water"
     * to match PlantReminderWorker's NULL→water default.
     *
     * Used by every UI surface that lists reminders (Today list, Calendar
     * grid, MonthPicker popup) so a user who muted Düngen in Settings
     * doesn't keep seeing fertilizer rows in their day view.
     * Pre-fix the toggles only suppressed the morning summary
     * notification — the rows themselves stayed visible everywhere.
     */
    @JvmStatic
    fun isTypeEnabled(context: Context, type: String?): Boolean {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val key = when (type?.lowercase()) {
            "fertilize" -> "notif_type_fertilize"
            "mist"      -> "notif_type_mist"
            "repot"     -> "notif_type_repot"
            else        -> "notif_type_water"  // NULL/blank/unknown → water
        }
        // Default true so a fresh install (or pre-v15 reminder) doesn't
        // disappear silently because the pref hasn't been written yet.
        return prefs.getBoolean(key, true)
    }

    /**
     * v16 close-out — convenience filter for any list of reminders that
     * a UI surface is about to render. Drops rows whose type toggle is
     * off in Settings.
     */
    @JvmStatic
    fun filterByEnabledTypes(
        context: Context,
        reminders: List<WateringReminder>
    ): List<WateringReminder> {
        // Snapshot all four toggles once instead of hitting SharedPreferences
        // per-row — for a 100-reminder list this drops 300 redundant
        // pref reads down to 4.
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val waterOn = prefs.getBoolean("notif_type_water", true)
        val fertOn  = prefs.getBoolean("notif_type_fertilize", true)
        val mistOn  = prefs.getBoolean("notif_type_mist", true)
        val repotOn = prefs.getBoolean("notif_type_repot", true)
        return reminders.filter { r ->
            when (r.type?.lowercase()) {
                "fertilize" -> fertOn
                "mist"      -> mistOn
                "repot"     -> repotOn
                else        -> waterOn  // NULL/blank/unknown → water
            }
        }
    }
}
