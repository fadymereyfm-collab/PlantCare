package com.example.plantcare.weekbar

import java.time.LocalDate

data class Reminder(
    val id: String,
    val title: String,
    val time: String?,
    val date: LocalDate,
    val plantName: String? = null,
    val plantId: Long = 0L,
    /**
     * v16 reminder type — "water" / "fertilize" / "mist" / "repot".
     * NULL or blank is treated as "water" (legacy default) by
     * `ReminderTypeUi`. Without this field the calendar's
     * RemindersList rendered every auto reminder with the same
     * watering-can icon, even after the writer plumbing started
     * persisting types correctly.
     */
    val type: String? = null
)