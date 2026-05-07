package com.example.plantcare.feature.treatment

import android.content.Context
import com.example.plantcare.R
import com.example.plantcare.WateringReminder
import com.example.plantcare.data.repository.ReminderRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Erzeugt aus einem Krankheits­schlüssel (PlantVillage-Label) eine einfache
 * Pflegeplan-Sequenz aus WateringReminder-Einträgen mit `isTreatment = 1`.
 *
 * Warum so schlicht?  Das MVP liefert einen sichtbaren "Follow-up" statt einer
 * großen Behandlungs-UI.  Jeder Schritt ist ein ganz normaler Reminder, der
 * in TodayFragment und im Kalender auftaucht, und den der Nutzer abhaken kann.
 * Einzige Besonderheit: `isTreatment = 1` kennzeichnet den Eintrag visuell
 * (kommt später über Adapter-Filter) und erlaubt eine spätere "Alle Behandlungs­
 * erinnerungen löschen"-Aktion ohne Verwechslung mit normalen Gieß­intervallen.
 *
 * Drei grobe Kategorien, ausgewählt über Schlüssel­teile:
 *   • Pilz/Fäule: Blätter entfernen → Fungizid → Nachbehandlung → Kontrolle
 *   • Tierisch/viral: Isolieren → Insektizid → Kontrolle → Kontrolle
 *   • Sonstiges (z. B. Nährstoffmangel): Substrat-Wechsel-Check → zwei Kontrollen
 *
 * Für "healthy"-Klassen wird nichts erzeugt (Button ist ohnehin deaktiviert).
 */
object TreatmentPlanBuilder {

    private val FMT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /** Ein geplanter Schritt: Offset in Tagen + deutscher Text aus Strings.xml. */
    private data class Step(val dayOffset: Int, val labelRes: Int)

    /**
     * Schreibt die Plan-Schritte als normale Reminder in die Datenbank.
     * @return Anzahl der erzeugten Reminder. 0 heißt "healthy" oder unklar.
     */
    @JvmStatic
    fun build(
        context: Context,
        plantId: Int,
        plantName: String?,
        userEmail: String?,
        diseaseKey: String
    ): Int {
        if (diseaseKey.isBlank()) return 0
        val lc = diseaseKey.lowercase(Locale.US)
        if (lc.contains("healthy")) return 0
        // D8: "unclear" gets two follow-up checks instead of recommending
        // less watering / repot — the diagnosis itself was inconclusive, so
        // suggesting a substrate change would be misleading.
        if (lc.contains("unclear")) return 0

        val steps: List<Step> = when {
            // Pilzerkrankungen (häufigste Kategorie in PlantVillage)
            lc.containsAny(
                "scab", "blight", "rot", "rust", "mildew", "mold", "spot",
                "anthracnose", "leaf", "schimmel", "mehltau", "fäule", "rost"
            ) -> listOf(
                Step(0, R.string.treatment_step_prune),
                Step(1, R.string.treatment_step_fungicide),
                Step(7, R.string.treatment_step_fungicide),
                Step(14, R.string.treatment_step_check),
            )
            // Tierische oder virale Schädlinge — auch deutsche Schlüssel von Gemini
            lc.containsAny(
                "mite", "aphid", "virus", "mosaic", "bacteria", "bacterial",
                "mealybug", "scale", "thrip", "fungus_gnat",
                "milbe", "blattlaus", "schmierlaus", "trauermücke"
            ) -> listOf(
                Step(0, R.string.treatment_step_isolate),
                Step(1, R.string.treatment_step_insecticide),
                Step(4, R.string.treatment_step_check),
                Step(10, R.string.treatment_step_check),
            )
            // Fallback: generische Pflege-Korrektur (z. B. Nährstoffmangel,
            // Überwässerung — bewusst NICHT für "unclear", siehe Early-Return oben)
            else -> listOf(
                Step(0, R.string.treatment_step_water_less),
                Step(2, R.string.treatment_step_repot),
                Step(7, R.string.treatment_step_check),
                Step(14, R.string.treatment_step_check),
            )
        }

        val reminderRepo = ReminderRepository.getInstance(context)
        val baseCal = Calendar.getInstance()
        val reminders = steps.map { step ->
            val cal = baseCal.clone() as Calendar
            cal.add(Calendar.DAY_OF_YEAR, step.dayOffset)
            WateringReminder().apply {
                this.plantId = plantId
                this.plantName = plantName
                this.date = FMT.format(cal.time)
                this.done = false
                this.completedDate = null
                this.repeat = null
                // Description trägt den deutschen Text des Schritts.
                this.description = context.getString(step.labelRes)
                this.userEmail = userEmail
                this.isTreatment = 1
            }
        }
        reminderRepo.insertAllBlocking(reminders)
        return reminders.size
    }

    private fun String.containsAny(vararg terms: String): Boolean =
        terms.any { this.contains(it) }
}
