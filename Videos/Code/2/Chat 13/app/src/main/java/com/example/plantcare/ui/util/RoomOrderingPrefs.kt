package com.example.plantcare.ui.util

import android.content.Context

/**
 * Tracks whether the user has manually reordered their rooms via the
 * long-press → "Reorder" → drag flow in MyPlantsFragment.
 *
 * **Why a flag, not the room.position field?** Each RoomCategory already
 * stores `position` for the user-chosen order, but on a fresh install
 * positions all default to 0 — there is no in-band signal that means
 * "user has never reordered". Without a separate flag we'd have to
 * either re-derive intent from the position values (fragile) or pick a
 * sentinel that collides with legitimate values. A SharedPreferences
 * boolean is cheap, per-user, and keeps the schema unchanged.
 *
 * Lifecycle:
 *  - Fresh user → flag absent → MyPlantsFragment auto-sorts rooms by
 *    descending plant count on every load.
 *  - User long-presses a room and chooses "Reorder", then drops the
 *    drag → flag flips to TRUE → subsequent loads honour the manual
 *    `position` order from Room.
 *  - User picks "Auto-Sortierung" from the long-press menu → flag is
 *    cleared → next load goes back to count-based sort.
 *
 * Stored under SharedPreferences("prefs") with one key per user email
 * so signed-in and guest users don't share state.
 */
object RoomOrderingPrefs {

    private const val PREFS = "prefs"
    private const val KEY_PREFIX = "rooms_manual_order:"

    private fun keyFor(email: String?): String =
        KEY_PREFIX + (email ?: "guest@local")

    @JvmStatic
    fun wasManuallyReordered(context: Context, email: String?): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(keyFor(email), false)

    @JvmStatic
    fun markManualReorder(context: Context, email: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(keyFor(email), true).apply()
    }

    @JvmStatic
    fun clearManualReorder(context: Context, email: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(keyFor(email)).apply()
    }
}
