package com.example.plantcare.ui.util

import android.content.Context
import com.example.plantcare.R

/**
 * Single source of truth for the default room list shown to a freshly
 * registered user. Previously the same five strings were hard-coded in
 * AddToMyPlantsDialogFragment, MyPlantsFragment and QuickAddHelper, so a
 * locale change or a typo would only get applied to one of them.
 *
 * The list lives in res/values{,-en}/strings.xml as a `string-array`, so
 * the German + English builds stay in lock-step automatically.
 */
object DefaultRooms {
    @JvmStatic
    fun get(context: Context): List<String> =
        context.resources.getStringArray(R.array.default_rooms).toList()
}
