package com.example.plantcare

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PlantRepositoryTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private lateinit var dao: PlantDao

    @Before
    fun setup() {
        dao = mock()
    }

    // ── getAllUserPlantsForUser ──────────────────────────────────

    @Test
    fun `plant list is empty when dao returns nothing`() = runTest {
        whenever(dao.getAllUserPlantsForUser("test@test.com")).thenReturn(emptyList())
        val result = dao.getAllUserPlantsForUser("test@test.com")
        assertThat(result).isEmpty()
    }

    @Test
    fun `plant list has correct size`() = runTest {
        val plants = listOf(Plant().apply { name = "A" }, Plant().apply { name = "B" })
        whenever(dao.getAllUserPlantsForUser("user@test.com")).thenReturn(plants)
        val result = dao.getAllUserPlantsForUser("user@test.com")
        assertThat(result).hasSize(2)
    }

    @Test
    fun `plants are filtered by userEmail`() = runTest {
        val plant = Plant().apply { name = "Rose"; userEmail = "alice@test.com" }
        whenever(dao.getAllUserPlantsForUser("alice@test.com")).thenReturn(listOf(plant))
        whenever(dao.getAllUserPlantsForUser("bob@test.com")).thenReturn(emptyList())

        assertThat(dao.getAllUserPlantsForUser("alice@test.com")).hasSize(1)
        assertThat(dao.getAllUserPlantsForUser("bob@test.com")).isEmpty()
    }

    // ── findById / findByName ───────────────────────────────────

    @Test
    fun `findById returns correct plant`() = runTest {
        val plant = Plant().apply { id = 42; name = "Fern" }
        whenever(dao.findById(42)).thenReturn(plant)
        val result = dao.findById(42)
        assertThat(result?.name).isEqualTo("Fern")
    }

    @Test
    fun `findById returns null for unknown id`() = runTest {
        whenever(dao.findById(999)).thenReturn(null)
        assertThat(dao.findById(999)).isNull()
    }

    @Test
    fun `findByName returns matching plant`() = runTest {
        val plant = Plant().apply { name = "Cactus" }
        whenever(dao.findByName("Cactus")).thenReturn(plant)
        assertThat(dao.findByName("Cactus")?.name).isEqualTo("Cactus")
    }

    // ── insert / update / delete ────────────────────────────────

    @Test
    fun `insert calls dao insert and returns id`() = runTest {
        val plant = Plant().apply { name = "Tulip" }
        whenever(dao.insert(plant)).thenReturn(5L)
        val id = dao.insert(plant)
        assertThat(id).isEqualTo(5L)
        verify(dao).insert(plant)
    }

    @Test
    fun `update calls dao update`() = runTest {
        val plant = Plant().apply { name = "Updated" }
        dao.update(plant)
        verify(dao).update(plant)
    }

    @Test
    fun `delete calls dao delete`() = runTest {
        val plant = Plant().apply { name = "ToDelete" }
        dao.delete(plant)
        verify(dao).delete(plant)
    }

    // ── room / catalog ──────────────────────────────────────────

    @Test
    fun `countPlantsByRoom returns correct count`() = runTest {
        whenever(dao.countPlantsByRoom(1, "user@test.com")).thenReturn(3)
        assertThat(dao.countPlantsByRoom(1, "user@test.com")).isEqualTo(3)
    }

    @Test
    fun `getAllNonUserPlants returns catalog plants`() = runTest {
        val catalog = listOf(Plant().apply { isUserPlant = false; name = "CatalogPlant" })
        whenever(dao.getAllNonUserPlants()).thenReturn(catalog)
        val result = dao.getAllNonUserPlants()
        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo("CatalogPlant")
    }

    @Test
    fun `getPlantsByIds returns list for given ids`() = runTest {
        val plants = listOf(Plant().apply { id = 1 }, Plant().apply { id = 2 })
        whenever(dao.getPlantsByIds(listOf(1, 2))).thenReturn(plants)
        assertThat(dao.getPlantsByIds(listOf(1, 2))).hasSize(2)
    }

    // ── Plant.toString ──────────────────────────────────────────

    @Test
    fun `plant toString returns nickname if set`() {
        val plant = Plant().apply {
            name     = "Aloe Vera"
            nickname = "Alöchen"
        }
        assertThat(plant.toString()).isEqualTo("Alöchen")
    }

    @Test
    fun `plant toString falls back to name when no nickname`() {
        val plant = Plant().apply { name = "Ficus" }
        assertThat(plant.toString()).isEqualTo("Ficus")
    }
}
