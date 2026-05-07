package com.example.plantcare

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

object ConsentManager {

    private const val PREF_NAME = "consent_prefs"
    private const val KEY_CONSENT_ASKED = "consent_asked"
    private const val KEY_ANALYTICS_ENABLED = "analytics_enabled"

    fun hasConsentBeenAsked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CONSENT_ASKED, false)

    fun isAnalyticsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ANALYTICS_ENABLED, false)

    fun setConsent(context: Context, analyticsEnabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_CONSENT_ASKED, true)
            .putBoolean(KEY_ANALYTICS_ENABLED, analyticsEnabled)
            .apply()
        applyToFirebase(context.applicationContext, analyticsEnabled)
    }

    /** Restore previously saved consent state — call once in App.onCreate(). */
    fun applyStoredConsent(context: Context) {
        val enabled = isAnalyticsEnabled(context)
        applyToFirebase(context.applicationContext, enabled)
    }

    private fun applyToFirebase(context: Context, enabled: Boolean) {
        // GDPR-relevant code path — silent swallow gives us zero
        // observability if Firebase init fails (Play Services missing,
        // app bundle stripped, etc.). Route via CrashReporter so
        // Crashlytics surfaces the failure even though Crashlytics
        // itself is the thing that just refused to enable.
        try {
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
        } catch (t: Throwable) { CrashReporter.log(t) }
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        } catch (t: Throwable) { CrashReporter.log(t) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
