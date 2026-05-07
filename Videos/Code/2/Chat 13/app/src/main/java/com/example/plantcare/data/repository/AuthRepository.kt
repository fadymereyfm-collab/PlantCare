package com.example.plantcare.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.plantcare.AppDatabase
import com.example.plantcare.SecurePrefsHelper
import com.example.plantcare.User
import com.example.plantcare.UserDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for authentication and user management.
 * Sensitive keys (email, guest flag) are stored in EncryptedSharedPreferences.
 * Non-sensitive keys (theme, etc.) remain in plain "prefs".
 */
class AuthRepository private constructor(appContext: Context) {

    // Sprint-3 cleanup 2026-05-05: store applicationContext explicitly so
    // SharedPreferences lookups don't pin a transient Activity into the
    // singleton.
    private val context: Context = appContext.applicationContext
    private val userDao: UserDao = AppDatabase.getInstance(context).userDao()
    // PERF2: was a `get()` getter that re-built EncryptedSharedPreferences
    // on every property access — every isLoggedIn(), isGuestMode(),
    // getCurrentUserEmail() rebuilt the MasterKey + Tink-backed prefs.
    // Cache once at construction; the underlying SharedPreferences is
    // thread-safe and lifetime-bound to the application context the
    // singleton already pins.
    private val sharedPreferences: SharedPreferences = SecurePrefsHelper.get(context)

    /**
     * Get the current logged-in user's email.
     * Returns guest email if in guest mode, null if not logged in.
     */
    fun getCurrentUserEmail(): String? {
        return if (isGuestMode()) {
            GUEST_EMAIL
        } else {
            sharedPreferences.getString(CURRENT_USER_EMAIL, null)
        }
    }

    /**
     * Check if user is currently logged in (not in guest mode and email stored).
     */
    fun isLoggedIn(): Boolean {
        if (isGuestMode()) return false
        return !getCurrentUserEmail().isNullOrEmpty()
    }

    /**
     * Check if currently in guest mode.
     */
    fun isGuestMode(): Boolean {
        return sharedPreferences.getBoolean(IS_GUEST_MODE, false)
    }

    /**
     * Set or clear guest mode flag.
     * If true, guest mode is enabled and email is set to "guest@local".
     * If false, guest mode is disabled.
     */
    fun setGuestMode(enabled: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(IS_GUEST_MODE, enabled)
            if (enabled) {
                putString(CURRENT_USER_EMAIL, GUEST_EMAIL)
            } else {
                remove(CURRENT_USER_EMAIL)
            }
            apply()
        }
    }

    /**
     * Sign up a new user with email, name, and password.
     * Password should be hashed before calling this method.
     * Runs on IO dispatcher.
     */
    suspend fun signUp(email: String, name: String, passwordHash: String): Boolean =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val user = User(email, name, passwordHash)
                userDao.insert(user)
                sharedPreferences.edit().apply {
                    putString(CURRENT_USER_EMAIL, email)
                    putBoolean(IS_GUEST_MODE, false)
                    apply()
                }
                true
            } catch (e: Exception) {
                com.example.plantcare.CrashReporter.log(e)
                false
            }
        }

    /**
     * Sign in with email and password hash.
     * Returns true if sign-in is successful and user is authenticated.
     * Runs on IO dispatcher.
     */
    suspend fun signIn(email: String, passwordHash: String): Boolean =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val user = userDao.login(email, passwordHash)
                if (user != null) {
                    sharedPreferences.edit().apply {
                        putString(CURRENT_USER_EMAIL, email)
                        putBoolean(IS_GUEST_MODE, false)
                        apply()
                    }
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                com.example.plantcare.CrashReporter.log(e)
                false
            }
        }

    /**
     * Log out the current user.
     * Clears stored email and disables guest mode.
     */
    fun logout() {
        sharedPreferences.edit().apply {
            remove(CURRENT_USER_EMAIL)
            putBoolean(IS_GUEST_MODE, false)
            apply()
        }
    }

    /**
     * Get user by email.
     * Runs on IO dispatcher.
     */
    suspend fun getUserByEmail(email: String): User? = withContext(Dispatchers.IO) {
        userDao.getUserByEmail(email)
    }

    /**
     * Update user password.
     * Runs on IO dispatcher.
     */
    suspend fun updateUserPassword(email: String, newPasswordHash: String) =
        withContext(Dispatchers.IO) {
            userDao.updateUserPassword(email, newPasswordHash)
        }

    /**
     * Update user name.
     * Runs on IO dispatcher.
     */
    suspend fun updateUserName(email: String, newName: String) = withContext(Dispatchers.IO) {
        userDao.updateUserName(email, newName)
    }

    /**
     * Delete user account and all associated data.
     * Runs on IO dispatcher.
     */
    suspend fun deleteUser(email: String) = withContext(Dispatchers.IO) {
        userDao.deleteUserByEmail(email)
    }

    // ────────────────────────────────────────────────────────────────────
    // Sprint-3 Task 3.2b: blocking helpers for legacy Java callers.
    // ────────────────────────────────────────────────────────────────────

    fun getUserByEmailBlocking(email: String): User? = userDao.getUserByEmail(email)
    fun loginBlocking(email: String, passwordHash: String?): User? = userDao.login(email, passwordHash)
    fun insertUserBlocking(user: User) = userDao.insert(user)
    fun updateUserNameBlocking(email: String, newName: String?) =
        userDao.updateUserName(email, newName)
    fun updateUserPasswordBlocking(email: String, newHash: String?) =
        userDao.updateUserPassword(email, newHash)
    fun deleteUserByEmailBlocking(email: String) = userDao.deleteUserByEmail(email)

    companion object {
        private const val CURRENT_USER_EMAIL = SecurePrefsHelper.KEY_USER_EMAIL
        private const val IS_GUEST_MODE      = SecurePrefsHelper.KEY_IS_GUEST
        private const val GUEST_EMAIL        = "guest@local"

        @Volatile
        private var INSTANCE: AuthRepository? = null

        @JvmStatic
        fun getInstance(context: Context): AuthRepository {
            // #5 fix: inner recheck.
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthRepository(context.applicationContext)
                    .also { INSTANCE = it }
            }
        }
    }
}
