package com.example.plantcare

import android.content.Context

object EmailContext {

    @JvmStatic
    fun current(context: Context): String? =
        SecurePrefsHelper.get(context).getString(SecurePrefsHelper.KEY_USER_EMAIL, null)

    @JvmStatic
    @JvmOverloads
    fun setCurrent(context: Context, email: String?, isGuest: Boolean = false) {
        SecurePrefsHelper.get(context).edit()
            .putString(SecurePrefsHelper.KEY_USER_EMAIL, email)
            .putBoolean(SecurePrefsHelper.KEY_IS_GUEST, isGuest)
            .apply()
    }

    @JvmStatic
    fun isGuest(context: Context): Boolean =
        SecurePrefsHelper.get(context).getBoolean(SecurePrefsHelper.KEY_IS_GUEST, false)
}
