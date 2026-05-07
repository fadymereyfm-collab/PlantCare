package com.example.plantcare

import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

/**
 * Phase 2.4 / 3.1 — single home for the password-management dialogs.
 *
 * The Settings screen used to host empty placeholder buttons for "change
 * password" and "create password (Google users)" — they were declared in
 * the layout, the click listeners were `/* placeholder */`. This file
 * implements them properly by wrapping Firebase's `updatePassword` /
 * `linkWithCredential` flows in three reusable AlertDialogs.
 *
 * Why a separate file instead of inlining into SettingsDialogFragment?
 * The dialogs use nearly identical layout-building boilerplate; centralising
 * it keeps the Settings file focused on settings and lets us reuse the
 * same Builder for any future password-input flow.
 */
object AuthPasswordDialogs {

    /** Phase 2.4: existing email-password user wants a new password.
     *  Re-auths with the current password first to satisfy Firebase's
     *  "recent login" rule. */
    @JvmStatic
    fun showChangePassword(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email
        if (user == null || email == null) {
            Toast.makeText(context, R.string.auth_error_user_not_found, Toast.LENGTH_SHORT).show()
            return
        }

        val currentInput = passwordField(context, R.string.auth_change_password_current)
        val newInput = passwordField(context, R.string.auth_change_password_new)
        val confirmInput = passwordField(context, R.string.auth_change_password_confirm)
        val container = stack(context, currentInput, newInput, confirmInput)

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.auth_change_password_title)
            .setView(container)
            .setPositiveButton(R.string.auth_change_password_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val saveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveBtn.setOnClickListener {
                val cur = textOf(currentInput)
                val new1 = textOf(newInput)
                val new2 = textOf(confirmInput)

                if (AuthValidation.passwordTooShort(new1)) {
                    Toast.makeText(context,
                        AuthValidation.passwordTooShortMessage(context),
                        Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (new1 != new2) {
                    Toast.makeText(context, R.string.auth_error_password_mismatch,
                        Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Audit fix #8 (2026-05-06): disable Save while the round-trip
                // runs so a double-tap doesn't double-fire the reauth call.
                saveBtn.isEnabled = false
                val cred = EmailAuthProvider.getCredential(email, cur)
                user.reauthenticate(cred).addOnCompleteListener { reauth ->
                    if (!reauth.isSuccessful) {
                        saveBtn.isEnabled = true
                        Toast.makeText(context, R.string.auth_reauth_failed,
                            Toast.LENGTH_SHORT).show()
                        return@addOnCompleteListener
                    }
                    user.updatePassword(new1).addOnCompleteListener { update ->
                        if (update.isSuccessful) {
                            Toast.makeText(context, R.string.auth_change_password_success,
                                Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                        } else {
                            saveBtn.isEnabled = true
                            val msg = update.exception?.message ?: "?"
                            Toast.makeText(context,
                                context.getString(R.string.auth_change_password_failed, msg),
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    /** Phase 3.1: Google-only user wants to add an email/password method
     *  (so they can sign in without Google). Uses linkWithCredential which
     *  attaches the password provider to the existing UID. */
    @JvmStatic
    fun showSetPassword(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email
        if (user == null || email == null) {
            Toast.makeText(context, R.string.auth_error_user_not_found, Toast.LENGTH_SHORT).show()
            return
        }

        val newInput = passwordField(context, R.string.auth_change_password_new)
        val confirmInput = passwordField(context, R.string.auth_change_password_confirm)
        val container = stack(context, newInput, confirmInput)

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.auth_set_password_title)
            .setMessage(R.string.auth_set_password_message)
            .setView(container)
            .setPositiveButton(R.string.auth_set_password_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val saveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveBtn.setOnClickListener {
                val new1 = textOf(newInput)
                val new2 = textOf(confirmInput)
                if (AuthValidation.passwordTooShort(new1)) {
                    Toast.makeText(context,
                        AuthValidation.passwordTooShortMessage(context),
                        Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (new1 != new2) {
                    Toast.makeText(context, R.string.auth_error_password_mismatch,
                        Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // Audit fix #8: same double-tap guard as showChangePassword.
                saveBtn.isEnabled = false
                val cred = EmailAuthProvider.getCredential(email, new1)
                user.linkWithCredential(cred).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(context, R.string.auth_set_password_success,
                            Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    } else {
                        saveBtn.isEnabled = true
                        val msg = task.exception?.message ?: "?"
                        Toast.makeText(context,
                            context.getString(R.string.auth_set_password_failed, msg),
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun passwordField(context: Context, @androidx.annotation.StringRes hintRes: Int): TextInputEditText {
        val edit = TextInputEditText(context)
        edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        edit.setHint(hintRes)
        return edit
    }

    private fun stack(context: Context, vararg fields: TextInputEditText): LinearLayout {
        val ll = LinearLayout(context)
        ll.orientation = LinearLayout.VERTICAL
        val pad = (16 * context.resources.displayMetrics.density).toInt()
        ll.setPadding(pad, pad / 2, pad, 0)
        for (f in fields) {
            // Wrap each field so the Material outline + label render properly.
            val tl = TextInputLayout(context)
            tl.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = pad / 2
            tl.layoutParams = lp
            tl.addView(f)
            ll.addView(tl)
        }
        return ll
    }

    private fun textOf(t: TextInputEditText): String =
        t.text?.toString().orEmpty()
}
