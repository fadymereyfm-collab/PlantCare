package com.example.plantcare

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Audit pass (2026-05-06): unit-test coverage for AuthRateLimiter.
 * Uses Robolectric so we can talk to a real (in-memory) SharedPreferences
 * — the real one, not a mock — which catches edit/apply ordering bugs
 * better than mocks would.
 */
@RunWith(RobolectricTestRunner::class)
class AuthRateLimiterTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun resetState() {
        // Clear the prefs file between tests so they're independent.
        context.getSharedPreferences("auth_rate_limit", android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    @Test
    fun `freshly installed app is unlocked`() {
        assertThat(AuthRateLimiter.secondsUntilUnlocked(context)).isEqualTo(0)
    }

    @Test
    fun `4 failures still allow attempts`() {
        repeat(4) { AuthRateLimiter.onFailure(context) }
        assertThat(AuthRateLimiter.secondsUntilUnlocked(context)).isEqualTo(0)
    }

    @Test
    fun `5 failures lock the user out`() {
        repeat(5) { AuthRateLimiter.onFailure(context) }
        val wait = AuthRateLimiter.secondsUntilUnlocked(context)
        assertThat(wait).isGreaterThan(0)
        assertThat(wait).isAtMost(31) // 30 s ceiling + rounding
    }

    @Test
    fun `success during lockout still reports zero wait`() {
        repeat(5) { AuthRateLimiter.onFailure(context) }
        AuthRateLimiter.onSuccess(context)
        assertThat(AuthRateLimiter.secondsUntilUnlocked(context)).isEqualTo(0)
    }

    @Test
    fun `counter resets after success`() {
        repeat(4) { AuthRateLimiter.onFailure(context) }
        AuthRateLimiter.onSuccess(context)
        // Now we should be able to fail 4 more times without lockout.
        repeat(4) { AuthRateLimiter.onFailure(context) }
        assertThat(AuthRateLimiter.secondsUntilUnlocked(context)).isEqualTo(0)
    }

    @Test
    fun `lockout activates only after the 5th failure, not earlier`() {
        // Failures 1-4 must not lock.
        for (i in 1..4) {
            AuthRateLimiter.onFailure(context)
            assertThat(AuthRateLimiter.secondsUntilUnlocked(context)).isEqualTo(0)
        }
        AuthRateLimiter.onFailure(context) // 5th
        assertThat(AuthRateLimiter.secondsUntilUnlocked(context)).isGreaterThan(0)
    }
}
