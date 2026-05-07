package com.example.plantcare.feature.share

import android.content.Context
import android.util.Patterns
import com.example.plantcare.Plant
import com.example.plantcare.data.repository.PlantRepository

/**
 * Lokale Familien-Freigabe (MVP): Besitzer einer Pflanze können weitere
 * E-Mails eintragen, damit sichtbar ist, wer mit-gießt. Persistiert als
 * kommagetrennte Liste im neuen Feld [Plant.sharedWith] (DB v8).
 *
 * Das ist absichtlich klein: keine Einladungs-Emails, kein Firestore-Sync,
 * keine Berechtigungs­matrix. Server-Sync kann später via
 * FirebaseSyncManager additiv ergänzt werden, weil die Quelle der Wahrheit
 * lokal bleibt.
 *
 * Warum CSV statt JSON?  Das Feld ist reine Anzeige-Information; keine
 * Queries, keine Ordnung, keine Nested-Struktur nötig.  CSV hält die
 * Migration trivial (Default ist `null` → noch niemand geteilt) und
 * kostet keinen Migrations-Schritt.
 */
object FamilyShareManager {

    private const val SEP = ","

    /**
     * Liest die bereits freigegebenen E-Mails (ohne den Besitzer selbst).
     * Doppelte/leere Einträge werden gefiltert.
     */
    @JvmStatic
    fun getSharedEmails(plant: Plant?): List<String> {
        val raw = plant?.sharedWith ?: return emptyList()
        return raw.split(SEP)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /**
     * Fügt eine E-Mail zur Freigabe-Liste hinzu und speichert die Pflanze.
     * @return true, wenn die E-Mail valide war und nicht schon existierte.
     */
    @JvmStatic
    fun addEmail(context: Context, plant: Plant, email: String): Boolean {
        val normalized = email.trim().lowercase()
        if (!isValidEmail(normalized)) return false
        val current = getSharedEmails(plant).toMutableList()
        if (normalized in current.map { it.lowercase() }) return false
        current.add(normalized)
        plant.sharedWith = current.joinToString(SEP)
        persist(context, plant)
        return true
    }

    /**
     * Entfernt eine E-Mail aus der Freigabe-Liste. Rückgabe signalisiert,
     * ob sich etwas geändert hat (für Toast).
     */
    @JvmStatic
    fun removeEmail(context: Context, plant: Plant, email: String): Boolean {
        val norm = email.trim().lowercase()
        val current = getSharedEmails(plant).toMutableList()
        val before = current.size
        current.removeAll { it.equals(norm, ignoreCase = true) }
        if (current.size == before) return false
        plant.sharedWith = if (current.isEmpty()) null else current.joinToString(SEP)
        persist(context, plant)
        return true
    }

    /** Stumpfe Android-Regex-Prüfung — gut genug für MVP. */
    @JvmStatic
    fun isValidEmail(email: String): Boolean =
        email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun persist(context: Context, plant: Plant) {
        // Kleine Operation → synchron im aufrufenden Thread zulässig.
        // Aufrufer verwenden ohnehin bereits einen Hintergrund-Thread.
        PlantRepository.getInstance(context).updateBlocking(plant)
        // F1: mirror to Firestore so the sharedWith list survives a
        // reinstall and propagates to the user's other devices.
        // Without this the local CSV is the only source of truth and
        // a phone-swap silently wipes everyone the user shared with.
        // Best-effort: a sync failure must not unwind the local
        // update — same pattern as memos / vacation / streak.
        try {
            com.example.plantcare.FirebaseSyncManager.get().syncPlant(plant)
        } catch (t: Throwable) {
            com.example.plantcare.CrashReporter.log(t)
        }
    }
}
