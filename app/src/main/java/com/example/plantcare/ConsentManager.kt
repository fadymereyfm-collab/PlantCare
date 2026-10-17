package com.example.plantcare

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * GDPR consent gate. Two independent toggles since Wave 1:
 *  - analytics_enabled  → Firebase Analytics collection
 *  - crashreports_enabled → Crashlytics collection
 *
 * On the first ever consent prompt both toggles are written together via
 * [setConsent]. After that the user can flip each one independently from the
 * Settings dialog via [setAnalyticsEnabled] / [setCrashReportsEnabled].
 *
 * Backwards compat: a previously-stored single `analytics_enabled` value is
 * the source of truth for both toggles until the user touches either switch.
 * That keeps existing users on the exact behavior they consented to.
 */
object ConsentManager {

    private const val PREF_NAME = "consent_prefs"
    private const val KEY_CONSENT_ASKED = "consent_asked"
    private const val KEY_ANALYTICS_ENABLED = "analytics_enabled"
    private const val KEY_CRASHREPORTS_ENABLED = "crashreports_enabled"

    fun hasConsentBeenAsked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CONSENT_ASKED, false)

    fun isAnalyticsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ANALYTICS_ENABLED, false)

    /** Falls noch nie separat gesetzt: Folgewert von analytics. */
    fun isCrashReportsEnabled(context: Context): Boolean {
        val p = prefs(context)
        return if (p.contains(KEY_CRASHREPORTS_ENABLED)) {
            p.getBoolean(KEY_CRASHREPORTS_ENABLED, false)
        } else {
            p.getBoolean(KEY_ANALYTICS_ENABLED, false)
        }
    }

    /** Initial-Einwilligung: setzt beide Schalter auf den gleichen Wert. */
    fun setConsent(context: Context, analyticsEnabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_CONSENT_ASKED, true)
            .putBoolean(KEY_ANALYTICS_ENABLED, analyticsEnabled)
            .putBoolean(KEY_CRASHREPORTS_ENABLED, analyticsEnabled)
            .apply()
        applyAnalytics(context.applicationContext, analyticsEnabled)
        applyCrashlytics(context.applicationContext, analyticsEnabled)
    }

    fun setAnalyticsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_CONSENT_ASKED, true)
            .putBoolean(KEY_ANALYTICS_ENABLED, enabled)
            .apply()
        applyAnalytics(context.applicationContext, enabled)
    }

    fun setCrashReportsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_CONSENT_ASKED, true)
            .putBoolean(KEY_CRASHREPORTS_ENABLED, enabled)
            .apply()
        applyCrashlytics(context.applicationContext, enabled)
    }

    /** Restore previously saved consent state — call once in App.onCreate(). */
    fun applyStoredConsent(context: Context) {
        applyAnalytics(context.applicationContext, isAnalyticsEnabled(context))
        applyCrashlytics(context.applicationContext, isCrashReportsEnabled(context))
    }

    private fun applyAnalytics(context: Context, enabled: Boolean) {
        // GDPR-relevant code path — silent swallow gives us zero
        // observability if Firebase init fails (Play Services missing,
        // app bundle stripped, etc.). Route via CrashReporter so
        // Crashlytics surfaces the failure even though Crashlytics
        // itself is the thing that just refused to enable.
        try {
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
        } catch (t: Throwable) { CrashReporter.log(t) }
    }

    private fun applyCrashlytics(context: Context, enabled: Boolean) {
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        } catch (t: Throwable) { CrashReporter.log(t) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
