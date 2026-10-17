package com.example.plantcare

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefsHelper {

    private const val TAG = "SecurePrefsHelper"
    private const val SECURE_PREFS_NAME = "secure_prefs"
    private const val PLAIN_PREFS_NAME  = "prefs"

    // Keys that are sensitive and must live in encrypted prefs
    const val KEY_USER_EMAIL = "current_user_email"
    const val KEY_IS_GUEST   = "is_guest"
    private const val KEY_MIGRATED = "migrated_v1"

    /**
     * PERF1: cached EncryptedSharedPreferences instance. The
     * underlying Tink keyset is thread-safe and bound to the
     * application context, so a single instance for the process
     * lifetime is correct AND much cheaper than rebuilding the
     * MasterKey + Tink keystore on every accessor call. Pre-fix
     * `EmailContext.current(context)` (called from 24+ sites
     * including hot paths like every Worker run, every
     * Fragment onResume) re-derived the master key on each
     * invocation — measurable cold-start hit + steady-state CPU
     * for zero security gain.
     *
     * Volatile + double-checked synchronisation so the first
     * caller from a background thread doesn't race with the
     * App.onCreate caller.
     */
    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    fun get(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        synchronized(this) {
            cachedPrefs?.let { return it }
            val appCtx = context.applicationContext
            val masterKey = MasterKey.Builder(appCtx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val created = EncryptedSharedPreferences.create(
                appCtx,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            cachedPrefs = created
            return created
        }
    }

    /**
     * One-time migration: moves sensitive keys from plain "prefs" into
     * EncryptedSharedPreferences, then removes them from the plain file.
     * Safe to call on every app start — runs only once.
     */
    fun migrateIfNeeded(context: Context) {
        try {
            val secure = get(context)
            if (secure.getBoolean(KEY_MIGRATED, false)) return

            val plain = context.getSharedPreferences(PLAIN_PREFS_NAME, Context.MODE_PRIVATE)

            secure.edit().apply {
                val email = plain.getString(KEY_USER_EMAIL, null)
                if (email != null) putString(KEY_USER_EMAIL, email)
                putBoolean(KEY_IS_GUEST, plain.getBoolean(KEY_IS_GUEST, false))
                putBoolean(KEY_MIGRATED, true)
            }.apply()

            plain.edit()
                .remove(KEY_USER_EMAIL)
                .remove(KEY_IS_GUEST)
                .apply()

            Log.i(TAG, "Migrated sensitive prefs to EncryptedSharedPreferences")
        } catch (e: Exception) {
            Log.e(TAG, "Migration failed — sensitive prefs stay in plain file", e)
        }
    }
}
