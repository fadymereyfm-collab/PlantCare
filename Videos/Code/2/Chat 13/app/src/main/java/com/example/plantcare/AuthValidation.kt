package com.example.plantcare

import android.content.Context
import java.util.regex.Pattern

/**
 * Phase 0 / Auth hardening (2026-05-05): single source of truth for the
 * pieces of the auth flow that used to disagree across three different
 * dialogs (LoginDialogFragment said password >= 6, the now-deleted
 * EmailEntryDialogFragment said >= 4, no shared email regex). Centralising
 * them here lets the password rules + the email parser change in exactly
 * one place.
 *
 * Also lives the `nameFromEmail` fallback so an empty name field doesn't
 * persist as `""` — instead we extract the local part of the email so the
 * settings screen has something to show.
 */
object AuthValidation {

    /** Minimum password length — same threshold for sign-up, sign-in,
     *  and any future "create password" / "change password" flows. */
    const val MIN_PASSWORD_LENGTH = 8

    /** RFC 5322 simplified — copied from android.util.Patterns.EMAIL_ADDRESS
     *  so the validation works in plain JUnit tests too (the Android one
     *  only resolves at runtime). */
    private val EMAIL_PATTERN: Pattern = Pattern.compile(
        "[a-zA-Z0-9+._%\\-]{1,256}" +
        "@" +
        "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
        "(?:\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+"
    )

    /** Strict-enough email check matching the platform regex. */
    @JvmStatic
    fun isEmailValid(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return EMAIL_PATTERN.matcher(email.trim()).matches()
    }

    /** Returns null when the password meets the length requirement,
     *  or a localised error message resource id otherwise. */
    @JvmStatic
    fun passwordTooShort(password: String?): Boolean {
        return password.isNullOrEmpty() || password.length < MIN_PASSWORD_LENGTH
    }

    /** Localised "min N chars" message for the inline error views. */
    @JvmStatic
    fun passwordTooShortMessage(context: Context): String =
        context.getString(R.string.auth_error_password_too_short, MIN_PASSWORD_LENGTH)

    /** Crude but effective password strength heuristic for the meter UI:
     *  0 = empty / weak (< 8), 1 = medium, 2 = strong (length + diversity).
     *  We don't try to match zxcvbn — overkill for a plant-care app. */
    @JvmStatic
    fun passwordStrength(password: String?): Int {
        if (password.isNullOrEmpty() || password.length < MIN_PASSWORD_LENGTH) return 0
        val hasLower = password.any { it.isLowerCase() }
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }
        val classes = listOf(hasLower, hasUpper, hasDigit, hasSymbol).count { it }
        // Strong if 3+ classes AND length >= 12, OR 4 classes AND length >= 8.
        if ((classes >= 3 && password.length >= 12) || classes == 4) return 2
        return 1
    }

    /** When the user signs up with an empty name, fall back to the local
     *  part of the email so the Settings screen + notifications can address
     *  them as "Hi, alice" instead of "Hi, ". Always returns non-blank. */
    @JvmStatic
    fun nameFromEmail(name: String?, email: String?): String {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isNotEmpty()) return trimmed
        val at = email?.indexOf('@') ?: -1
        if (at > 0) {
            val prefix = email!!.substring(0, at).trim()
            if (prefix.isNotEmpty()) return prefix.replaceFirstChar { it.titlecase() }
        }
        return "User"
    }
}
