package com.example.plantcare

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SecurePrefsHelperTest {

    @Test
    fun `KEY_USER_EMAIL constant matches expected value`() {
        assertThat(SecurePrefsHelper.KEY_USER_EMAIL).isEqualTo("current_user_email")
    }

    @Test
    fun `KEY_IS_GUEST constant matches expected value`() {
        assertThat(SecurePrefsHelper.KEY_IS_GUEST).isEqualTo("is_guest")
    }
}
