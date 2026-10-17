package com.example.plantcare.feature.share

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.plantcare.CrashReporter
import com.example.plantcare.Plant
import com.example.plantcare.WateringReminder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Phase 2 (Wave 2 Family Share, 2026-05-14) — Recipient-side data layer.
 *
 * Live aggregator over the recipient's `users/{uid}/sharedPlants/{plantId}`
 * pointer collection. For each pointer doc we fetch the matching plant
 * metadata + the plant's reminder docs from the OWNER's subtree
 * (`users/{ownerUid}/plants/{plantId}` + `users/{ownerUid}/reminders
 * where plantId = X`). Firestore rules expanded in Phase 1A allow the
 * cross-user reads when the pointer doc exists with the correct
 * ownerUid.
 *
 * Public surface:
 *  - [observeSharedPlants] — LiveData<List<SharedPlantWithReminders>>
 *    that fires whenever the pointer collection or any subscribed
 *    plant/reminder doc changes. The UI can observe this exactly like
 *    a Room DAO LiveData.
 *
 *  - [start] / [stop] — explicit lifecycle management. Called from
 *    Activity onStart/onStop or App.onCreate (the Activity-scoped
 *    pattern is preferred to avoid background data costs).
 *
 *  - [findOwnedReminderId] — utility for the mark-watered code path
 *    so it knows whether a reminder belongs to the current user (use
 *    the local DAO write path) or to a shared owner (route through
 *    `markSharedReminderWatered` callable).
 *
 * Why no Room mirror: shared plants aren't user-owned data — they're
 * a live view of someone else's data. Caching them locally would
 * complicate ownership semantics (whose reminder is this? who can
 * edit?) and the recipient is online when interacting with them
 * anyway (mark-watered IS a network call).
 *
 * Threading: all listeners fire on the main thread by default
 * (Firestore default executor). The internal mutation of the cache
 * map happens on main → no synchronization needed for reads from the
 * UI thread.
 */
object SharedPlantsRepository {

    private const val TAG = "SharedPlantsRepo"

    /** Single combined LiveData the UI observes. */
    private val live = MutableLiveData<List<SharedPlantWithReminders>>(emptyList())

    /** Listener for the pointer collection itself. */
    private var pointerListener: ListenerRegistration? = null

    /** Per-shared-plant downstream listeners. Keyed by plantId. */
    private val plantListeners = mutableMapOf<Int, ListenerRegistration>()
    private val reminderListeners = mutableMapOf<Int, ListenerRegistration>()

    /**
     * In-memory aggregation cache. Updated on every listener tick;
     * republishes [live] after each mutation. Concurrent access is
     * single-threaded (Firestore default executor = main).
     */
    private val cache = mutableMapOf<Int, MutableSharedPlantWithReminders>()

    @JvmStatic
    fun observeSharedPlants(): LiveData<List<SharedPlantWithReminders>> = live

    /**
     * Start listening for the currently-signed-in user. Idempotent —
     * call again after sign-in to retarget; calling without a signed-in
     * user is a no-op.
     */
    @JvmStatic
    fun start() {
        stop()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val db = FirebaseFirestore.getInstance()
        pointerListener = db.collection("users")
            .document(uid)
            .collection("sharedPlants")
            .addSnapshotListener { snap, err ->
                if (err != null) { CrashReporter.log(err); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener

                val seenPlantIds = mutableSetOf<Int>()
                for (doc in snap.documents) {
                    val plantId = (doc.getLong("plantId") ?: doc.id.toIntOrNull()?.toLong())?.toInt()
                        ?: continue
                    val ownerUid = doc.getString("ownerUid") ?: continue
                    val ownerEmail = doc.getString("ownerEmail") ?: ""
                    val plantName = doc.getString("plantName") ?: ""
                    seenPlantIds.add(plantId)

                    val entry = cache.getOrPut(plantId) {
                        MutableSharedPlantWithReminders(
                            plantId = plantId,
                            ownerUid = ownerUid,
                            ownerEmail = ownerEmail,
                            plantName = plantName,
                            plant = null,
                            reminders = emptyList()
                        )
                    }
                    // Refresh the pointer-doc fields in case they changed
                    // (owner renamed the plant, ownerEmail updated, etc.).
                    entry.ownerUid = ownerUid
                    entry.ownerEmail = ownerEmail
                    entry.plantName = plantName

                    // Subscribe to the per-plant downstream listeners
                    // exactly once per plantId. The Firestore rules
                    // expanded in Phase 1A allow these reads because the
                    // pointer doc above exists.
                    if (plantId !in plantListeners) {
                        attachPlantListener(ownerUid, plantId)
                    }
                    if (plantId !in reminderListeners) {
                        attachReminderListener(ownerUid, plantId)
                    }
                }

                // Drop entries whose pointer disappeared (revoke / decline).
                val toDrop = cache.keys - seenPlantIds
                for (gonePlantId in toDrop) {
                    plantListeners.remove(gonePlantId)?.remove()
                    reminderListeners.remove(gonePlantId)?.remove()
                    cache.remove(gonePlantId)
                }

                publish()
            }
    }

    private fun attachPlantListener(ownerUid: String, plantId: Int) {
        val ref = FirebaseFirestore.getInstance()
            .collection("users").document(ownerUid)
            .collection("plants").document(plantId.toString())
        plantListeners[plantId] = ref.addSnapshotListener { snap, err ->
            if (err != null) { CrashReporter.log(err); return@addSnapshotListener }
            val entry = cache[plantId] ?: return@addSnapshotListener
            entry.plant = snap?.let { runCatching { it.toObject(Plant::class.java) }.getOrNull() }
            publish()
        }
    }

    private fun attachReminderListener(ownerUid: String, plantId: Int) {
        val q = FirebaseFirestore.getInstance()
            .collection("users").document(ownerUid)
            .collection("reminders")
            .whereEqualTo("plantId", plantId)
        reminderListeners[plantId] = q.addSnapshotListener { snap, err ->
            if (err != null) { CrashReporter.log(err); return@addSnapshotListener }
            val entry = cache[plantId] ?: return@addSnapshotListener
            entry.reminders = snap?.documents
                ?.mapNotNull { reminderFromDoc(it) }
                ?: emptyList()
            publish()
        }
    }

    /**
     * Maps a Firestore reminder doc to a [WateringReminder] entity. We
     * use the same Java class the local Room layer uses so downstream
     * UI code can render shared and owned reminders with the same
     * adapter — only the badge differs.
     */
    private fun reminderFromDoc(doc: DocumentSnapshot): WateringReminder? = try {
        doc.toObject(WateringReminder::class.java)?.also {
            // Firestore strips unknown fields but doesn't auto-populate
            // a stable id. Stamp the doc id onto a synthetic field so
            // mark-watered routing can locate it on the owner's side.
            // (`id` is the Room PK and is meaningless cross-user; we
            // overwrite it with the doc id parsed as int when possible
            // so existing UI logic keying on `id` still works.)
            doc.id.toIntOrNull()?.let { intId -> it.id = intId }
        }
    } catch (t: Throwable) {
        CrashReporter.log(t)
        null
    }

    private fun publish() {
        // Snapshot the cache into immutable view objects. Copying keeps
        // the published list stable for downstream observers — they
        // can equality-check items without worrying about mid-flight
        // mutations.
        val snapshot = cache.values
            .filter { it.plant != null } // only publish entries whose plant has loaded
            .sortedBy { it.plantName.lowercase() }
            .map {
                SharedPlantWithReminders(
                    plantId = it.plantId,
                    ownerUid = it.ownerUid,
                    ownerEmail = it.ownerEmail,
                    plantName = it.plantName,
                    plant = it.plant,
                    reminders = it.reminders.toList()
                )
            }
        live.postValue(snapshot)
    }

    /**
     * Stop all listeners + clear cache. Call from Activity.onStop or
     * during sign-out so background snapshot reads don't keep firing
     * for the previous user.
     */
    @JvmStatic
    fun stop() {
        pointerListener?.remove(); pointerListener = null
        plantListeners.values.forEach { it.remove() }
        plantListeners.clear()
        reminderListeners.values.forEach { it.remove() }
        reminderListeners.clear()
        cache.clear()
        live.postValue(emptyList())
    }

    /**
     * Phase 4 helper: given a reminder rendered in the UI, locate its
     * owner if it's a shared one. Returns null if the reminder isn't
     * in the shared cache (i.e. it's owned by the current user — caller
     * uses the normal local DAO path). Returns the owner's uid if the
     * reminder is shared — caller routes mark-watered through the
     * `markSharedReminderWatered` callable.
     *
     * Matching is by reminderId because reminder.id was stamped from
     * the Firestore doc id in [reminderFromDoc].
     */
    @JvmStatic
    fun findSharedOwnerForReminder(reminderId: Int): String? {
        for (entry in cache.values) {
            for (r in entry.reminders) {
                if (r.id == reminderId) return entry.ownerUid
            }
        }
        return null
    }

    /** Internal mutable view used inside the cache map. */
    private data class MutableSharedPlantWithReminders(
        val plantId: Int,
        var ownerUid: String,
        var ownerEmail: String,
        var plantName: String,
        var plant: Plant?,
        var reminders: List<WateringReminder>
    )
}

/**
 * Immutable DTO published to UI observers. Mirrors the in-memory
 * cache row but is safe to compare/equality-check without coupling to
 * the repository internals.
 */
data class SharedPlantWithReminders(
    val plantId: Int,
    val ownerUid: String,
    val ownerEmail: String,
    val plantName: String,
    val plant: Plant?,
    val reminders: List<WateringReminder>
)
