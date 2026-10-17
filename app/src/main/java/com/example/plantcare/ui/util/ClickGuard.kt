package com.example.plantcare.ui.util

import android.os.SystemClock
import android.view.View

/**
 * Three-layer double-tap defence for buttons that launch Activities,
 * Dialogs, or any other heavy operation.
 *
 *   Layer 1: debounce window — drops every tap that arrives within
 *            [DEFAULT_DEBOUNCE_MS] of the previous accepted tap.
 *   Layer 2: view disable — the View is set `isClickable = false`
 *            immediately when the tap is accepted, and re-enabled
 *            only after [DEFAULT_REARM_MS]. This blocks not just
 *            time-spaced taps but also genuine multi-touch and any
 *            touch event the framework would otherwise queue.
 *   Layer 3: singleTop / singleTask launchMode on the target
 *            Activity (set in AndroidManifest.xml). Even if the
 *            first two layers somehow fail, the OS itself refuses
 *            to put two instances of the same Activity on the back
 *            stack — the second tap just brings the existing one
 *            forward (no-op for the user).
 *
 * Usage:
 *
 * ```kotlin
 * // Kotlin
 * view.setOnClickListener(ClickGuard.wrap { startActivity(intent) })
 * ```
 *
 * ```java
 * // Java
 * view.setOnClickListener(ClickGuard.wrap(v -> startActivity(intent)));
 * ```
 *
 * Created 2026-05-19 (initial), hardened 2026-05-20 after a regression
 * report — the original 600ms debounce was too short for cold Activity
 * launches on slower devices, letting taps 2+ go through while the
 * first Activity was still resolving.
 */
object ClickGuard {

    /** Default debounce window — long enough to absorb a cold Activity
     *  launch on a mid-range device (a Samsung A52 takes ~700-900ms,
     *  add headroom for older devices and low-memory pauses). */
    const val DEFAULT_DEBOUNCE_MS: Long = 1500L

    /** How long the View stays `isClickable = false` after an accepted
     *  tap. Slightly longer than DEFAULT_DEBOUNCE_MS so the next
     *  Activity has time to draw its first frame before this one
     *  becomes clickable again. */
    const val DEFAULT_REARM_MS: Long = 2000L

    @JvmStatic
    fun wrap(listener: View.OnClickListener): View.OnClickListener =
        wrap(DEFAULT_DEBOUNCE_MS, DEFAULT_REARM_MS, listener)

    @JvmStatic
    fun wrap(debounceMs: Long, listener: View.OnClickListener): View.OnClickListener =
        wrap(debounceMs, DEFAULT_REARM_MS, listener)

    @JvmStatic
    fun wrap(
        debounceMs: Long,
        rearmMs: Long,
        listener: View.OnClickListener
    ): View.OnClickListener = object : View.OnClickListener {
        private var lastClickMs: Long = 0L

        override fun onClick(v: View) {
            val now = SystemClock.uptimeMillis()
            // Layer 1: time-based debounce.
            if (now - lastClickMs < debounceMs) return
            lastClickMs = now

            // Layer 2: disable the View so genuine multi-touch can't
            // sneak through while the next Activity is launching.
            v.isClickable = false
            v.isEnabled = false

            try {
                listener.onClick(v)
            } finally {
                // Re-arm after rearmMs — even if the launched Activity
                // is still on top, the user might back out and the
                // button must work again.
                v.postDelayed({
                    v.isClickable = true
                    v.isEnabled = true
                }, rearmMs)
            }
        }
    }

    /** Sugar for places that don't have a View handy — protect a code
     *  block from being entered twice within [debounceMs]. Returns true
     *  if the block ran, false if it was debounced. */
    @JvmStatic
    @JvmOverloads
    fun runOnce(
        state: GateState,
        block: Runnable,
        debounceMs: Long = DEFAULT_DEBOUNCE_MS
    ): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - state.lastMs < debounceMs) return false
        state.lastMs = now
        block.run()
        return true
    }

    /** Mutable holder for [runOnce]. Keep one per call-site. */
    class GateState {
        @JvmField
        var lastMs: Long = 0L
    }
}
