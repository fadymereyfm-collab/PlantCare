package com.example.plantcare.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.plantcare.R
import com.example.plantcare.ui.theme.PlantCareTheme

/**
 * Sample data + previews for [HomeScreen]. Used by Android Studio previews
 * and by the Roborazzi screenshot test (HomeScreenScreenshotTest), so the
 * design can be checked visually without a device or database.
 */
internal object HomeSampleData {

    private fun task(
        id: Int,
        name: String,
        type: HomeTaskType = HomeTaskType.WATER,
        overdue: Int = 0,
        done: Boolean = false,
        note: String? = null
    ) = HomeTask(
        reminderId = id,
        plantId = id,
        plantName = name,
        type = type,
        overdueDays = overdue,
        done = done,
        isManual = note != null,
        note = note
    )

    private val week = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").mapIndexed { i, label ->
        HomeWeekDay(label = label, dayOfMonth = 19 + i, isToday = i == 0)
    }

    val busyDay = HomeUiState(
        dateLabel = "Montag, 19. Oktober",
        greeting = "Guten Morgen",
        weekDays = week,
        sections = listOf(
            HomeRoomSection(
                roomId = 1,
                roomName = "Wohnzimmer",
                tasks = listOf(
                    task(1, "Monstera", overdue = 2),
                    task(2, "Gummibaum", type = HomeTaskType.FERTILIZE),
                    task(3, "Efeutute", done = true)
                )
            ),
            HomeRoomSection(
                roomId = 2,
                roomName = "Schlafzimmer",
                tasks = listOf(
                    task(4, "Bogenhanf"),
                    task(5, "Calathea", type = HomeTaskType.MIST)
                )
            ),
            HomeRoomSection(
                roomId = 3,
                roomName = "Küche",
                tasks = listOf(task(6, "Aloe Vera", type = HomeTaskType.REPOT, done = true))
            )
        ),
        streakDays = 4,
        bestStreak = 9,
        showProCard = true,
        challenges = listOf(
            HomeChallenge("WATER_STREAK_7", "7 Tage nacheinander gießen", 4, 7, complete = false),
            HomeChallenge("ADD_FIVE_PLANTS", "5 Pflanzen hinzufügen", 5, 5, complete = true),
            HomeChallenge("MONTHLY_PHOTO", "Ein Monatsfoto pro Pflanze", 0, 1, complete = false)
        )
    )

    val empty = HomeUiState(
        dateLabel = "Montag, 19. Oktober",
        greeting = "Guten Tag",
        weekDays = week,
        streakDays = 0,
        bestStreak = 1,
        showProCard = true,
        challenges = busyDay.challenges
    )

    val vacation = empty.copy(
        vacationText = "Du bist im Urlaub bis 26. Oktober 2026 — Erinnerungen sind pausiert.",
        showProCard = false
    )

    private val photos = mapOf(
        "Monstera" to R.drawable.fensterblatt,
        "Gummibaum" to R.drawable.gummibaum,
        "Efeutute" to R.drawable.efeutute,
        "Bogenhanf" to R.drawable.bogenhanf,
        "Calathea" to R.drawable.calathea,
        "Aloe Vera" to R.drawable.aloe_vera
    )

    /** Static thumbnail for previews (the app uses Glide via PlantImageLoader). */
    val thumbnail: @Composable (HomeTask, Modifier) -> Unit = { task, modifier ->
        Image(
            painter = painterResource(photos[task.plantName] ?: R.drawable.ic_default_plant),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

@Composable
internal fun HomeScreenSample(state: HomeUiState, darkTheme: Boolean) {
    PlantCareTheme(darkTheme = darkTheme) {
        HomeScreen(
            state = state,
            callbacks = HomeCallbacks(),
            thumbnail = HomeSampleData.thumbnail
        )
    }
}

@Preview(name = "Home — busy", showBackground = true, heightDp = 1700)
@Composable
internal fun HomeBusyPreview() = HomeScreenSample(HomeSampleData.busyDay, darkTheme = false)

@Preview(
    name = "Home — busy (dark)",
    showBackground = true,
    heightDp = 1700,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
internal fun HomeBusyDarkPreview() = HomeScreenSample(HomeSampleData.busyDay, darkTheme = true)

@Preview(name = "Home — empty", showBackground = true, heightDp = 1200)
@Composable
internal fun HomeEmptyPreview() = HomeScreenSample(HomeSampleData.empty, darkTheme = false)
