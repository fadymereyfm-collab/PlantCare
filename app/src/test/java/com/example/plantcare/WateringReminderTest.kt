package com.example.plantcare

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

class WateringReminderTest {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    @Test
    fun `getDaysOverdue returns 0 when reminder is done`() {
        val r = WateringReminder().apply {
            done = true
            date = "2020-01-01"
        }
        assertThat(r.getDaysOverdue()).isEqualTo(0)
    }

    @Test
    fun `getDaysOverdue returns 0 when date is null`() {
        val r = WateringReminder().apply { done = false; date = null }
        assertThat(r.getDaysOverdue()).isEqualTo(0)
    }

    @Test
    fun `getDaysOverdue returns 0 for future date`() {
        val future = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 5) }
        val r = WateringReminder().apply {
            done = false
            date = sdf.format(future.time)
        }
        assertThat(r.getDaysOverdue()).isEqualTo(0)
    }

    @Test
    fun `getDaysOverdue returns positive number for past date`() {
        val r = WateringReminder().apply {
            done = false
            date = "2020-01-01"
        }
        assertThat(r.getDaysOverdue()).isGreaterThan(0)
    }

    @Test
    fun `generateRecurringDates returns empty list when repeat is null`() {
        val r = WateringReminder().apply {
            date   = sdf.format(Date())
            repeat = null
        }
        assertThat(r.generateRecurringDates(30)).isEmpty()
    }

    @Test
    fun `generateRecurringDates returns dates for valid interval`() {
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val r = WateringReminder().apply {
            date   = sdf.format(tomorrow.time)
            repeat = "7"
        }
        val dates = r.generateRecurringDates(30)
        assertThat(dates).isNotEmpty()
    }
}
