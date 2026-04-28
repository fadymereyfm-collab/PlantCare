package com.example.plantcare.billing

import android.content.Context

/** Persists the user's Pro status locally so the paywall doesn't flicker on cold start. */
object ProStatusManager {
    private const val PREFS = "pro_status"
    private const val KEY_IS_PRO = "is_pro"

    /** Plant limit for free users. */
    const val FREE_PLANT_LIMIT = 8

    @JvmStatic
    fun isPro(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_PRO, false)

    @JvmStatic
    fun setPro(context: Context, pro: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_IS_PRO, pro).apply()
    }
}
