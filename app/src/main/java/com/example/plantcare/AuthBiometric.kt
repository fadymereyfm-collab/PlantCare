package com.example.plantcare

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Phase 4.1 / Auth hardening (2026-05-05): optional fingerprint/face unlock
 * gate that runs **after** Firebase has restored the session, before the
 * MainActivity content is shown.
 *
 * Why this is local-only and not real authentication:
 *   PlantCare doesn't store anything that needs hardware-backed protection;
 *   Firebase already takes care of the actual identity check. Biometrics
 *   here are a UX feature — "I don't want my partner browsing my plants
 *   list when they pick up my phone" — so we use the simplest possible
 *   API: BiometricPrompt with no crypto object.
 *
 * Toggle state lives in plain SharedPreferences so it can be flipped from
 * Settings without round-trips. Safe to ship at API 23+; older devices fall
 * through to "feature unavailable" cleanly.
 */
object AuthBiometric {

    private const val PREFS = "auth_biometric"
    private const val KEY_ENABLED = "enabled"

    /** Whether the user enabled the toggle in Settings. */
    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    @JvmStatic
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Whether the device has biometric hardware that's set up by the user.
     *  Used by Settings to disable the toggle when the system reports no
     *  enrolled fingerprint/face. */
    @JvmStatic
    fun isAvailable(context: Context): Boolean {
        val mgr = BiometricManager.from(context)
        val result = mgr.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /** Show the biometric prompt and call [onSuccess] when the user
     *  authenticates, [onFallback] if they tap "Use password" — the host
     *  Activity then proceeds without further checks (the user is already
     *  signed in via Firebase). */
    @JvmStatic
    fun prompt(
        activity: FragmentActivity,
        onSuccess: Runnable,
        onFallback: Runnable
    ) {
        if (!isAvailable(activity)) {
            onFallback.run()
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess.run()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Negative button or system cancel both land here.
                    onFallback.run()
                }
                override fun onAuthenticationFailed() {
                    // Bad fingerprint — let the user try again. Silent.
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.auth_biometric_prompt_title))
            .setSubtitle(activity.getString(R.string.auth_biometric_prompt_subtitle))
            .setNegativeButtonText(activity.getString(R.string.auth_biometric_prompt_negative))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }
}
