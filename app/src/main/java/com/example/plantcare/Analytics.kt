package com.example.plantcare

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

object Analytics {

    private fun fa(context: Context) = FirebaseAnalytics.getInstance(context)

    private fun log(context: Context, event: String, params: Bundle? = null) {
        if (ConsentManager.isAnalyticsEnabled(context)) {
            fa(context).logEvent(event, params)
        }
    }

    // ── Plants ─────────────────────────────────────────────────

    fun logPlantAdded(context: Context) {
        log(context, "plant_added")
    }

    /**
     * Log which top-3 candidate the user selected after plant identification.
     * @param rank 1/2/3 for the selected result, 0 for "none correct"
     * @param confidencePct 0-100
     */
    fun logPlantIdentified(context: Context, rank: Int, confidencePct: Int) {
        log(context, "plant_identified", Bundle().apply {
            putInt("result_rank", rank)
            putInt("confidence_pct", confidencePct)
            putBoolean("success", rank > 0)
        })
    }

    // ── Reminders ──────────────────────────────────────────────

    fun logReminderAdded(context: Context) {
        log(context, "reminder_added")
    }

    fun logReminderCompleted(context: Context) {
        log(context, "reminder_completed")
    }

    // ── Auth ───────────────────────────────────────────────────

    fun logLogin(context: Context, method: String) {
        log(context, FirebaseAnalytics.Event.LOGIN, Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, method)
        })
    }

    fun logSignUp(context: Context, method: String) {
        log(context, FirebaseAnalytics.Event.SIGN_UP, Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, method)
        })
    }

    fun logLogout(context: Context) {
        log(context, "logout")
    }

    fun logAccountDeleted(context: Context) {
        log(context, "account_deleted")
    }
}
