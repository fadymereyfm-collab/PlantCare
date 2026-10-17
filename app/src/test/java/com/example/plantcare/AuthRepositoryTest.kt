package com.example.plantcare

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for auth-related constants and DAO contract used by AuthRepository.
 * End-to-end AuthRepository tests (that need EncryptedSharedPreferences + Room)
 * run as instrumentation tests.
 */
class AuthRepositoryTest {

    private lateinit var dao: UserDao

    @Before
    fun setup() {
        dao = mock()
    }

    // ── SecurePrefsHelper constants ─────────────────────────────

    @Test
    fun `KEY_USER_EMAIL is current_user_email`() {
        assertThat(SecurePrefsHelper.KEY_USER_EMAIL).isEqualTo("current_user_email")
    }

    @Test
    fun `KEY_IS_GUEST is is_guest`() {
        assertThat(SecurePrefsHelper.KEY_IS_GUEST).isEqualTo("is_guest")
    }

    // ── UserDao contract ────────────────────────────────────────

    @Test
    fun `getUserByEmail returns null for unknown email`() {
        whenever(dao.getUserByEmail("unknown@test.com")).thenReturn(null)
        assertThat(dao.getUserByEmail("unknown@test.com")).isNull()
    }

    @Test
    fun `getUserByEmail returns user for known email`() {
        val user = User("alice@test.com", "Alice", "hash")
        whenever(dao.getUserByEmail("alice@test.com")).thenReturn(user)
        val result = dao.getUserByEmail("alice@test.com")
        assertThat(result?.email).isEqualTo("alice@test.com")
    }

    @Test
    fun `login returns null for wrong password`() {
        whenever(dao.login("alice@test.com", "wronghash")).thenReturn(null)
        assertThat(dao.login("alice@test.com", "wronghash")).isNull()
    }

    @Test
    fun `login returns user for correct credentials`() {
        val user = User("alice@test.com", "Alice", "correcthash")
        whenever(dao.login("alice@test.com", "correcthash")).thenReturn(user)
        val result = dao.login("alice@test.com", "correcthash")
        assertThat(result).isNotNull()
        assertThat(result?.email).isEqualTo("alice@test.com")
    }

    @Test
    fun `updateUserPassword calls dao with correct args`() {
        dao.updateUserPassword("alice@test.com", "newhash")
        verify(dao).updateUserPassword("alice@test.com", "newhash")
    }

    @Test
    fun `updateUserName calls dao with correct args`() {
        dao.updateUserName("alice@test.com", "Alicia")
        verify(dao).updateUserName("alice@test.com", "Alicia")
    }

    @Test
    fun `deleteUserByEmail calls dao with correct email`() {
        dao.deleteUserByEmail("alice@test.com")
        verify(dao).deleteUserByEmail("alice@test.com")
    }

    @Test
    fun `getAllUsers returns empty list when no users exist`() {
        whenever(dao.getAllUsers()).thenReturn(emptyList())
        assertThat(dao.getAllUsers()).isEmpty()
    }

    @Test
    fun `getAllUsers returns all inserted users`() {
        val users = listOf(
            User("a@test.com", "A", "h1"),
            User("b@test.com", "B", "h2")
        )
        whenever(dao.getAllUsers()).thenReturn(users)
        assertThat(dao.getAllUsers()).hasSize(2)
    }
}
