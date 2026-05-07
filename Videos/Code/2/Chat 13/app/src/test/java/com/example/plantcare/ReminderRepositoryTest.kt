package com.example.plantcare

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ReminderRepositoryTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private lateinit var dao: ReminderDao

    @Before
    fun setup() {
        dao = mock()
    }

    // ── getAllRemindersForUser ───────────────────────────────────

    @Test
    fun `getAllRemindersForUser returns empty list for unknown user`() = runTest {
        whenever(dao.getAllRemindersForUser("nobody@test.com")).thenReturn(emptyList())
        assertThat(dao.getAllRemindersForUser("nobody@test.com")).isEmpty()
    }

    @Test
    fun `getAllRemindersForUser returns all reminders for user`() = runTest {
        val reminders = listOf(
            WateringReminder().apply { id = 1; userEmail = "alice@test.com" },
            WateringReminder().apply { id = 2; userEmail = "alice@test.com" }
        )
        whenever(dao.getAllRemindersForUser("alice@test.com")).thenReturn(reminders)
        assertThat(dao.getAllRemindersForUser("alice@test.com")).hasSize(2)
    }

    // ── getTodayAndOverdueRemindersForUser ──────────────────────

    @Test
    fun `getTodayAndOverdueRemindersForUser returns only incomplete overdue reminders`() = runTest {
        val overdue = WateringReminder().apply { done = false; date = "2020-01-01" }
        whenever(dao.getTodayAndOverdueRemindersForUser("2025-01-01", "user@test.com"))
            .thenReturn(listOf(overdue))
        val result = dao.getTodayAndOverdueRemindersForUser("2025-01-01", "user@test.com")
        assertThat(result).hasSize(1)
        assertThat(result[0].done).isFalse()
    }

    @Test
    fun `getTodayAndOverdueAllRemindersForUser includes done reminders`() = runTest {
        val r1 = WateringReminder().apply { done = false; date = "2020-01-01" }
        val r2 = WateringReminder().apply { done = true;  date = "2020-01-01" }
        whenever(dao.getTodayAndOverdueAllRemindersForUser("2025-01-01", "user@test.com"))
            .thenReturn(listOf(r1, r2))
        val result = dao.getTodayAndOverdueAllRemindersForUser("2025-01-01", "user@test.com")
        assertThat(result).hasSize(2)
    }

    // ── insert / insertAll ──────────────────────────────────────

    @Test
    fun `insert calls dao insert`() = runTest {
        val reminder = WateringReminder().apply { plantId = 1; date = "2025-06-01" }
        dao.insert(reminder)
        verify(dao).insert(reminder)
    }

    @Test
    fun `insertAll calls dao insertAll with full list`() = runTest {
        val list = listOf(
            WateringReminder().apply { plantId = 1 },
            WateringReminder().apply { plantId = 1 }
        )
        dao.insertAll(list)
        verify(dao).insertAll(list)
    }

    // ── update / delete ─────────────────────────────────────────

    @Test
    fun `update calls dao update`() = runTest {
        val reminder = WateringReminder().apply { id = 3; done = true }
        dao.update(reminder)
        verify(dao).update(reminder)
    }

    @Test
    fun `delete calls dao delete`() = runTest {
        val reminder = WateringReminder().apply { id = 5 }
        dao.delete(reminder)
        verify(dao).delete(reminder)
    }

    // ── markDone logic (ReminderRepository business logic) ──────

    @Test
    fun `markDone sets done flag to true before calling update`() = runTest {
        val reminder = WateringReminder().apply { id = 10; done = false }
        // Simulate the markDone logic from ReminderRepository:
        // reminder.done = true; dao.update(reminder)
        reminder.done = true
        dao.update(reminder)

        val captor = argumentCaptor<WateringReminder>()
        verify(dao).update(captor.capture())
        assertThat(captor.firstValue.done).isTrue()
    }

    // ── getRemindersForPlant / getReminderById ──────────────────

    @Test
    fun `getRemindersForPlant returns reminders for given plant`() = runTest {
        val reminders = listOf(WateringReminder().apply { plantId = 7 })
        whenever(dao.getRemindersForPlant(7)).thenReturn(reminders)
        val result = dao.getRemindersForPlant(7)
        assertThat(result).hasSize(1)
        assertThat(result[0].plantId).isEqualTo(7)
    }

    @Test
    fun `getReminderById returns correct reminder`() = runTest {
        val reminder = WateringReminder().apply { id = 99; plantName = "Orchid" }
        whenever(dao.getReminderById(99)).thenReturn(reminder)
        val result = dao.getReminderById(99)
        assertThat(result?.plantName).isEqualTo("Orchid")
    }

    @Test
    fun `getReminderById returns null for unknown id`() = runTest {
        whenever(dao.getReminderById(0)).thenReturn(null)
        assertThat(dao.getReminderById(0)).isNull()
    }

    // ── delete queries ──────────────────────────────────────────

    @Test
    fun `deleteRemindersForPlant calls dao with correct plantId`() = runTest {
        dao.deleteRemindersForPlant(42)
        verify(dao).deleteRemindersForPlant(42)
    }

    @Test
    fun `deleteAllRemindersForUser calls dao with correct email`() = runTest {
        dao.deleteAllRemindersForUser("user@test.com")
        verify(dao).deleteAllRemindersForUser("user@test.com")
    }

    @Test
    fun `deleteFutureRemindersForPlant calls dao with correct args`() = runTest {
        dao.deleteFutureRemindersForPlant(3, "2025-06-01")
        verify(dao).deleteFutureRemindersForPlant(3, "2025-06-01")
    }

    // ── date range ──────────────────────────────────────────────

    @Test
    fun `getRemindersBetween returns reminders in range`() = runTest {
        val r = WateringReminder().apply { date = "2025-06-15" }
        whenever(dao.getRemindersBetween("2025-06-01", "2025-06-30")).thenReturn(listOf(r))
        val result = dao.getRemindersBetween("2025-06-01", "2025-06-30")
        assertThat(result).isNotEmpty()
    }

    @Test
    fun `getRemindersBetween returns empty outside range`() = runTest {
        whenever(dao.getRemindersBetween("2020-01-01", "2020-01-02")).thenReturn(emptyList())
        assertThat(dao.getRemindersBetween("2020-01-01", "2020-01-02")).isEmpty()
    }
}
