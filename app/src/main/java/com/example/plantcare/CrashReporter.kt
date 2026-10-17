package com.example.plantcare

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics

object CrashReporter {

    fun log(context: Context, e: Throwable) {
        if (!ConsentManager.isAnalyticsEnabled(context)) return
        try {
            FirebaseCrashlytics.getInstance().recordException(e)
        } catch (_: Throwable) {
            // Crashlytics not yet initialized (e.g. during App.onCreate before Firebase.initializeApp)
        }
    }

    /** Overload without context — only logs if Crashlytics collection is already enabled. */
    fun log(e: Throwable) {
        try {
            FirebaseCrashlytics.getInstance().recordException(e)
        } catch (_: Throwable) {}
    }
}
