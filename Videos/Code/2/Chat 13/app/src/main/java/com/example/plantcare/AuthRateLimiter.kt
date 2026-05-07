package com.example.plantcare

import android.content.Context

/**
 * Phase 2.3 / Auth hardening (2026-05-05): client-side throttle for the
 * email/password sign-in form. Firebase will rate-limit on the server
 * eventually, but a few seconds of brute-force on the same device should
 * still be locally blocked so the password field doesn't accept rapid
 * dictionary attempts.
 *
 * Rules:
 *  - 5 consecutive failures within FAILURE_WINDOW_MS → 30-second lockout.
 *  - A successful sign-in resets the counter.
 *  - State lives in plain SharedPreferences (not encrypted) — there's no
 *    secret here, just a counter + timestamp. A reinstall clears it,
 *    which is acceptable because Firebase's server-side rate-limiting
 *    catches anything that survives the client.
 */
object AuthRateLimiter {

    private const val PREFS_NAME = "auth_rate_limit"
    private const val KEY_FAIL_COUNT = "fail_count"
    private const val KEY_FIRST_FAIL_TS = "first_fail_ts"
    private const val KEY_LOCKOUT_UNTIL = "lockout_until"

    private const val MAX_FAILURES = 5
    private const val FAILURE_WINDOW_MS = 5 * 60 * 1000L      // 5 min
    private const val LOCKOUT_MS = 30 * 1000L                  // 30 s

    /** Returns the seconds the caller must wait before retrying, or 0 if
     *  the user is free to attempt now. */
    @JvmStatic
    fun secondsUntilUnlocked(context: Context): Int {
        val sp = prefs(context)
        val lockoutUntil = sp.getLong(KEY_LOCKOUT_UNTIL, 0L)
        val now = System.currentTimeMillis()
        if (lockoutUntil <= now) return 0
        return ((lockoutUntil - now) / 1000L + 1).toInt().coerceAtLeast(1)
    }

    /** Record one failed attempt. Activates the lockout when the count
     *  hits the threshold within the window. */
    @JvmStatic
    fun onFailure(context: Context) {
        val sp = prefs(context)
        val now = System.currentTimeMillis()
        val firstTs = sp.getLong(KEY_FIRST_FAIL_TS, 0L)

        // Reset window if the previous failure was too long ago.
        val countNow = if (firstTs == 0L || now - firstTs > FAILURE_WINDOW_MS) {
            sp.edit().putLong(KEY_FIRST_FAIL_TS, now).apply()
            1
        } else {
            sp.getInt(KEY_FAIL_COUNT, 0) + 1
        }

        val edit = sp.edit().putInt(KEY_FAIL_COUNT, countNow)
        if (countNow >= MAX_FAILURES) {
            edit.putLong(KEY_LOCKOUT_UNTIL, now + LOCKOUT_MS)
                .putInt(KEY_FAIL_COUNT, 0)
                .putLong(KEY_FIRST_FAIL_TS, 0L)
        }
        edit.apply()
    }

    /** A successful sign-in clears all counters. */
    @JvmStatic
    fun onSuccess(context: Context) {
        prefs(context).edit()
            .remove(KEY_FAIL_COUNT)
            .remove(KEY_FIRST_FAIL_TS)
            .remove(KEY_LOCKOUT_UNTIL)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
