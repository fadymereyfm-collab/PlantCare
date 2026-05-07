package com.example.plantcare.billing

import android.content.Context

/**
 * Cloud-synced Pro state DTO. `var` fields + defaults so Firestore's
 * JavaBeans-style reflection can deserialize via setter calls into
 * the no-arg constructor — same lesson as JournalMemo / VacationDoc /
 * StreakDoc / ChallengesDoc.
 *
 * `isPro` carries the boolean state. `lastUpdatedMs` lets us pick
 * the most recent write when local + cloud disagree on cold restore
 * (e.g. user purchased on phone, signed in on tablet 5 minutes later
 * — tablet should immediately reflect Pro).
 */
data class ProStatusDoc(
    var isPro: Boolean = false,
    var lastUpdatedMs: Long = 0L
)

/** Persists the user's Pro status locally so the paywall doesn't flicker on cold start. */
object ProStatusManager {
    private const val PREFS = "pro_status"
    private const val KEY_IS_PRO = "is_pro"
    private const val KEY_LAST_UPDATED = "is_pro_updated_ms"

    /** Plant limit for free users. */
    const val FREE_PLANT_LIMIT = 8

    @JvmStatic
    fun isPro(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_PRO, false)

    @JvmStatic
    fun lastUpdatedMs(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_UPDATED, 0L)

    @JvmStatic
    fun setPro(context: Context, pro: Boolean) {
        // ZZ2: BillingManager.connect() / refreshPurchases() runs on
        // every app start and on every reconnect — each path calls
        // grantOrRevokePro → setPro. Pre-fix every one of those calls
        // wrote a fresh lastUpdatedMs and triggered a Firestore write,
        // even when the value hadn't changed. For a daily-active user
        // that's ~30 cloud writes per month per device for zero
        // information gain. Skip the no-op early.
        val current = isPro(context)
        if (current == pro) return
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_PRO, pro)
            .putLong(KEY_LAST_UPDATED, now)
            .apply()
        // B7: mirror to Firestore so a user who purchased Pro on
        // device A sees the banner disappear on device B as soon as
        // B signs in, without waiting for a manual restorePurchases
        // tap. Best-effort: a sync failure must not unwind the local
        // write — Google Play remains the canonical source of truth
        // and the next BillingManager.connect() will reconcile.
        try {
            com.example.plantcare.FirebaseSyncManager.get().syncProStatus(
                ProStatusDoc(isPro = pro, lastUpdatedMs = now)
            )
        } catch (t: Throwable) {
            com.example.plantcare.CrashReporter.log(t)
        }
    }

    /**
     * Restore from cloud on sign-in. Picks the most recent write —
     * if the local state already happens to be more recent (the
     * sign-in race where the user purchased on this same device
     * just before signing in), the cloud doc is ignored.
     */
    @JvmStatic
    fun restoreFromCloud(context: Context, doc: ProStatusDoc) {
        val localTs = lastUpdatedMs(context)
        if (doc.lastUpdatedMs <= localTs) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_PRO, doc.isPro)
            .putLong(KEY_LAST_UPDATED, doc.lastUpdatedMs)
            .apply()
    }
}
