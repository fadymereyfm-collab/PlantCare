package com.example.plantcare.feature.share

import android.content.Context
import android.util.Patterns
import com.example.plantcare.Plant
import com.example.plantcare.data.repository.PlantRepository

/**
 * Familien-Freigabe — die lokale Sharee-Liste pro Pflanze, persistiert als
 * kommagetrennte Liste im Feld [Plant.sharedWith] (DB v8) und nach
 * Firestore gespiegelt ([persist]).
 *
 * WICHTIG (Stand Wave 2, 2026-05-14): Diese Klasse ist NICHT das ganze
 * Feature — sie hält nur noch die lokale Anzeige-Liste. Die echte
 * Einladung läuft über [ShareInviteManager.createInvite] → Cloud Function
 * `onShareInviteCreated` (FCM-Push an den Eingeladenen) →
 * `onShareInviteAccepted` (materialisiert den Empfänger-Pointer unter
 * users/{toUid}/sharedPlants, sichtbar in SharedPlantsActivity).
 * [removeEmail] kaskadiert über [ShareInviteManager.revokeInvite] zurück.
 * Der frühere Kommentar hier ("absichtlich klein: keine Einladungs-Emails,
 * kein Firestore-Sync, keine Berechtigungsmatrix") galt nur für das MVP
 * und ist seit Wave 2 überholt.
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
     *
     * Phase 7 (Wave 2 Family Share, 2026-05-14): also revokes any open
     * shareInvites doc(s) for this owner+plant+email, which triggers the
     * `onShareInviteRevoked` Cloud Function to delete the recipient's
     * pointer doc. The recipient's `SharedPlantsRepository` listener
     * will tick on that deletion (sub-second when online, on next
     * connect when offline) and the plant card disappears from their
     * "Mit mir geteilt" screen automatically. Without this revoke
     * cascade, "removing" someone left them with full read access
     * forever — i.e. the UI lied.
     *
     * The revoke runs in the background AFTER the local CSV update so
     * the UI feedback is immediate; cloud cleanup may take a second or
     * two. Best-effort: a Firestore failure does NOT roll back the local
     * change (recipient's pointer doc may linger; recipient gets stale
     * but read-only access until they re-open the app or the listener
     * reconciles on next pointer collection refresh).
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

        // Phase 7: cascade-revoke the cloud invite(s) for this email.
        // The lookup is async; we ignore the result because the local
        // remove already succeeded and the cascade is fire-and-forget.
        revokeOpenInvitesFor(plant, norm)
        return true
    }

    /**
     * Phase 7 helper: find every open invite from this owner for this
     * plant addressed to `email` and call [ShareInviteManager.revokeInvite]
     * on each. Multi-revoke is rare but possible if the owner re-invited
     * the same email after a previous decline.
     */
    private fun revokeOpenInvitesFor(plant: Plant, email: String) {
        val ownerUid = com.google.firebase.auth.FirebaseAuth
            .getInstance().currentUser?.uid ?: return
        ShareInviteManager.findOpenInvitesByOwnerForPlant(ownerUid, plant.id) { invites ->
            for ((inviteId, toEmail) in invites) {
                if (toEmail.equals(email, ignoreCase = true)) {
                    ShareInviteManager.revokeInvite(inviteId) { /* fire-and-forget */ }
                }
            }
        }
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
