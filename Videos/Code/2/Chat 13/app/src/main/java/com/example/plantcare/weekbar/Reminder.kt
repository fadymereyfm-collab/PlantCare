package com.example.plantcare.weekbar

import java.time.LocalDate

data class Reminder(
    val id: String,
    val title: String,
    val time: String?,
    val date: LocalDate,
    val plantName: String? = null,
    val plantId: Long = 0L
)