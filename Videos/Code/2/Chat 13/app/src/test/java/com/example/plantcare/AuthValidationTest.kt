package com.example.plantcare

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Phase 0 / Auth hardening (2026-05-05): unit tests for the centralised
 * validation helpers. Pure JVM — no Android dependencies — so this runs
 * in the standard `test` source set.
 */
class AuthValidationTest {

    // ── isEmailValid ────────────────────────────────────────────

    @Test
    fun `isEmailValid accepts standard address`() {
        assertThat(AuthValidation.isEmailValid("alice@example.com")).isTrue()
    }

    @Test
    fun `isEmailValid rejects null`() {
        assertThat(AuthValidation.isEmailValid(null)).isFalse()
    }

    @Test
    fun `isEmailValid rejects blank`() {
        assertThat(AuthValidation.isEmailValid("   ")).isFalse()
    }

    @Test
    fun `isEmailValid rejects missing at`() {
        assertThat(AuthValidation.isEmailValid("alice.example.com")).isFalse()
    }

    @Test
    fun `isEmailValid rejects spaces inside`() {
        assertThat(AuthValidation.isEmailValid("a lice@example.com")).isFalse()
    }

    // ── passwordTooShort ─────────────────────────────────────────

    @Test
    fun `passwordTooShort true for empty`() {
        assertThat(AuthValidation.passwordTooShort("")).isTrue()
    }

    @Test
    fun `passwordTooShort true for null`() {
        assertThat(AuthValidation.passwordTooShort(null)).isTrue()
    }

    @Test
    fun `passwordTooShort true for 7 chars`() {
        assertThat(AuthValidation.passwordTooShort("1234567")).isTrue()
    }

    @Test
    fun `passwordTooShort false for exactly 8`() {
        assertThat(AuthValidation.passwordTooShort("12345678")).isFalse()
    }

    @Test
    fun `passwordTooShort false for long password`() {
        assertThat(AuthValidation.passwordTooShort("supersecret123!")).isFalse()
    }

    // ── passwordStrength ─────────────────────────────────────────

    @Test
    fun `passwordStrength weak for empty`() {
        assertThat(AuthValidation.passwordStrength("")).isEqualTo(0)
    }

    @Test
    fun `passwordStrength weak for too short`() {
        assertThat(AuthValidation.passwordStrength("abc12")).isEqualTo(0)
    }

    @Test
    fun `passwordStrength medium for 8 lowercase only`() {
        assertThat(AuthValidation.passwordStrength("abcdefgh")).isEqualTo(1)
    }

    @Test
    fun `passwordStrength medium for 8 mixed case`() {
        assertThat(AuthValidation.passwordStrength("AbCdEfGh")).isEqualTo(1)
    }

    @Test
    fun `passwordStrength strong for 12 mixed case + digits`() {
        assertThat(AuthValidation.passwordStrength("Password1234")).isEqualTo(2)
    }

    @Test
    fun `passwordStrength strong for 8 with all 4 classes`() {
        assertThat(AuthValidation.passwordStrength("Ab1!cdef")).isEqualTo(2)
    }

    // ── nameFromEmail ────────────────────────────────────────────

    @Test
    fun `nameFromEmail returns trimmed name when present`() {
        assertThat(AuthValidation.nameFromEmail("  Alice  ", "anything@x.com"))
            .isEqualTo("Alice")
    }

    @Test
    fun `nameFromEmail extracts local part when name blank`() {
        assertThat(AuthValidation.nameFromEmail("", "alice@example.com"))
            .isEqualTo("Alice")
    }

    @Test
    fun `nameFromEmail extracts when name null`() {
        assertThat(AuthValidation.nameFromEmail(null, "bob.smith@example.com"))
            .isEqualTo("Bob.smith")
    }

    @Test
    fun `nameFromEmail falls back to User if email also missing`() {
        assertThat(AuthValidation.nameFromEmail(null, null)).isEqualTo("User")
        assertThat(AuthValidation.nameFromEmail("", "")).isEqualTo("User")
        assertThat(AuthValidation.nameFromEmail(null, "@example.com")).isEqualTo("User")
    }
}
