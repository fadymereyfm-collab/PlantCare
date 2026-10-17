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
    private const val KEY_LAST_VERIFIED = "is_pro_last_verified_ms"

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

    /**
     * Bug #10 fix (b): timestamp of the most recent SUCCESSFUL server-side
     * purchase verification (the `verifyPurchase` Cloud Function returned
     * verified=true, or a CF-written cloud doc granted Pro). 0 means "this
     * install has never once had a server confirmation of Pro".
     * BillingManager uses this to bound its optimistic-grant fallback — an
     * attacker who forges a local purchase and blocks the Cloud Function
     * endpoint never earns a successful verification, so this stays 0 and
     * they get no provisional Pro.
     */
    @JvmStatic
    fun lastVerifiedMs(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_VERIFIED, 0L)

    /**
     * Bug #10 fix (b): stamp "a server verification just succeeded". Call
     * ONLY when the verifyPurchase Cloud Function actually returned
     * verified=true — never on the optimistic-grant path.
     */
    @JvmStatic
    fun markVerified(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_VERIFIED, System.currentTimeMillis())
            .apply()
    }

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

        // Bug #10 fix (2026-05-14, server-side Pro verification):
        //
        // Cloud mirror REMOVED from this path. Pre-fix this called
        // FirebaseSyncManager.syncProStatus(...) so cross-device sync
        // reflected the local Pro state immediately. Post-fix, the
        // Firestore rule on `users/{uid}/billing/proStatus` denies
        // ALL client writes — only the `verifyPurchase` Cloud
        // Function can mutate it (via admin SDK). The CF writes the
        // verified state directly when it validates a purchase, so
        // cross-device sync still works (other devices read the
        // CF-written doc on sign-in via importProStatusForCurrentUser).
        //
        // Side-effect: a local revoke (e.g., Billing SDK decides
        // isPro=false at app launch because the cached purchase no
        // longer reports PURCHASED) updates ONLY local prefs. Cloud
        // still reflects the last verified state. The next CF call
        // (on the next launch's verifyPurchases pass) will reconcile
        // — if Play Developer API also says "not entitled", the CF
        // writes false to cloud. Convergence within one launch cycle
        // — acceptable for a Pro-status anti-fraud mechanism.
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
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_PRO, doc.isPro)
            .putLong(KEY_LAST_UPDATED, doc.lastUpdatedMs)
        // Bug #10 fix (b): a cloud doc that grants Pro was written by the
        // verifyPurchase Cloud Function (the Firestore rule forbids any
        // other writer), so doc.lastUpdatedMs IS a server-verification
        // time. Stamping it means a user who restores Pro on a new device
        // counts as "previously verified" — a transient CF outage on that
        // device's first run won't wrongly revoke the restored Pro. Only
        // stamped on a Pro-granting doc: lastVerifiedMs means "last time
        // Pro was server-confirmed", so a not-pro doc has nothing to stamp.
        if (doc.isPro) {
            editor.putLong(KEY_LAST_VERIFIED, doc.lastUpdatedMs)
        }
        editor.apply()
    }
}
