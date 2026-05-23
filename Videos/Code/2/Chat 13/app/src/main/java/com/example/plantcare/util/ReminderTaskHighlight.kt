package com.example.plantcare.util

import android.content.Context
import com.example.plantcare.Plant
import com.example.plantcare.R
import com.example.plantcare.data.plantnet.PlantCareDefaults

/**
 * Builds the bold "what to do today" header that PlantDetailDialogFragment
 * shows above the regular care fields when the dialog was opened from a
 * tap on a calendar / today reminder.
 *
 * Source priority — designed so the user always gets the MOST accurate
 * available text:
 *
 *  1. **Per-plant text** from `plants.csv`:
 *      - water     → `plant.watering`     ("Alle 3 Tage. Mäßig gießen.")
 *      - fertilize → `plant.fertilizing`  ("Alle 4 Wochen düngen…")
 *  2. **Per-family text** from [PlantCareDefaults]:
 *      - mist      → `CareTexts.mistingText`     (Marantaceae, ferns, …)
 *      - repot     → `CareTexts.repottingText`   (Cactaceae, Orchidaceae, …)
 *  3. **Generic fallback** for unknown families
 *      - mist      → `R.string.reminder_task_mist_generic`
 *      - repot     → `R.string.reminder_task_repot_generic`
 *
 * Honest accuracy note: levels (1) is curated per species. Level (2) is
 * curated per botanical family. Level (3) is generic plant-care guidance —
 * still botanically correct, but missing species-specific nuance. plants.csv
 * does not currently carry per-row mist/repot text; if a future content
 * pass extends the schema, the per-plant level here can be promoted ahead
 * of the family level.
 */
object ReminderTaskHighlight {

    /**
     * Title displayed at the top of the highlight card —
     * "Heute gießen" / "Heute düngen" / "Heute besprühen" / "Bald umtopfen".
     * NULL/blank/unknown reminder types are treated as "water" to match
     * the legacy default everywhere else in the app.
     */
    fun title(context: Context, type: String?): String {
        val res = when (type?.lowercase()) {
            "fertilize" -> R.string.reminder_task_fertilize_title
            "mist"      -> R.string.reminder_task_mist_title
            "repot"     -> R.string.reminder_task_repot_title
            else        -> R.string.reminder_task_water_title
        }
        return context.getString(res)
    }

    /**
     * Body text — the actual instructions. Returns null when there's no
     * meaningful per-plant or per-family text AND no generic fallback
     * makes sense. Caller should hide the body and just show the title
     * in that case (rare — only if the per-plant text is blank for water/
     * fertilize).
     */
    fun instructions(context: Context, plant: Plant?, type: String?): String? {
        if (plant == null) return null
        val care = PlantCareDefaults.forFamily(plant.family)

        return when (type?.lowercase()) {
            "fertilize" -> {
                val perPlant = plant.fertilizing?.takeIf { it.isNotBlank() }
                perPlant ?: care.fertilizing
            }
            "mist" -> {
                // No per-plant column; fall through family → generic.
                care.mistingText
                    ?: context.getString(R.string.reminder_task_mist_generic)
            }
            "repot" -> {
                // No per-plant column; fall through family → generic.
                care.repottingText
                    ?: context.getString(R.string.reminder_task_repot_generic)
            }
            else -> {
                // water (incl. NULL/blank/unknown — treated as water everywhere).
                val perPlant = plant.watering?.takeIf { it.isNotBlank() }
                perPlant ?: care.watering
            }
        }
    }

    /**
     * Drawable used for the icon in the highlight card. Mirrors
     * [ReminderTypeUi.iconFor] so the header visual matches the icon the
     * user just tapped on the calendar/today list.
     */
    @androidx.annotation.DrawableRes
    fun iconFor(type: String?): Int = ReminderTypeUi.iconFor(type)

    /**
     * Background tint colour resource for the highlight card. Same palette
     * as the per-row indicator on the calendar.
     */
    @androidx.annotation.ColorRes
    fun tintFor(type: String?): Int = ReminderTypeUi.tintFor(type)
}
