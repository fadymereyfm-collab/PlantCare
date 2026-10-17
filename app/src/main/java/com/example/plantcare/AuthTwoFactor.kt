package com.example.plantcare

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.MultiFactorAssertion
import com.google.firebase.auth.MultiFactorResolver
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneMultiFactorGenerator

/**
 * Phase 6.3 / Auth hardening (2026-05-05): SMS-based second factor
 * (TOTP-style apps require Firebase Identity Platform, which is paid;
 * SMS multi-factor is on the free tier).
 *
 * Activation steps (Firebase Console — out-of-app):
 *   1. Authentication → Sign-in method → Multi-factor → Enable.
 *   2. Authentication → Settings → SMS region policy → allow target regions.
 *   3. (Optional) Configure quotas; default is fine for ≤1k MAU.
 *
 * Why this is sealed off in its own file with a deliberately minimal
 * surface: enrolment + verification require a `PhoneAuthProvider`
 * verification step that needs an Activity context + an inline UI to
 * collect the SMS code. The app doesn't ship a Phone-Auth UI yet, so we
 * only surface a state checker + a stub for future enrolment. When the
 * UI lands, the helpers below already point to the right Firebase calls.
 *
 * Status today (v1.0): wiring exists, UI is deferred to v1.1.
 */
object AuthTwoFactor {

    /** Whether the currently-signed-in user has at least one second factor
     *  enrolled. The Settings screen reads this to render
     *  "Aktiv ✓" vs "Inaktiv". */
    @JvmStatic
    fun isEnrolled(): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        return user.multiFactor.enrolledFactors.isNotEmpty()
    }

    /** Used by the sign-in flow when Firebase responds with a
     *  multi-factor required exception. Hosts cast `task.exception` to
     *  `FirebaseAuthMultiFactorException` and pass `getResolver()` here.
     *
     *  In v1.0 the host can call this to defer to the UI built later.
     *  Currently a no-op stub; replace with the proper resolver flow when
     *  the SMS UI ships. */
    @JvmStatic
    fun resumeWithSmsCode(
        resolver: MultiFactorResolver,
        verificationId: String,
        code: String,
        onComplete: (success: Boolean, message: String?) -> Unit
    ) {
        val cred = PhoneAuthProvider.getCredential(verificationId, code)
        val assertion: MultiFactorAssertion = PhoneMultiFactorGenerator.getAssertion(cred)
        resolver.resolveSignIn(assertion)
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { e -> onComplete(false, e.message) }
    }
}
