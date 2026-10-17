package com.example.plantcare.format

import android.content.Context

/**
 * Wave 2 — last successful Firestore/Storage sync timestamp.
 *
 * Stored as a long (epoch millis) in `prefs` so SettingsDialogFragment can
 * render a "Last sync: 2 min ago" label without owning its own pref file.
 *
 * Updated by [FirebaseSyncManager] after every successful operation. Reads
 * are cheap (single SharedPreferences lookup), so the Settings dialog
 * refreshes the label every time it's shown without caching.
 */
object LastSyncTracker {

    private const val PREFS_NAME = "prefs"
    private const val KEY_LAST_SYNC_AT = "last_sync_at_millis"

    @JvmStatic
    fun markSynced(context: Context) {
        prefs(context).edit()
            .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
            .apply()
    }

    @JvmStatic
    fun lastSyncMillis(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SYNC_AT, 0L)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
