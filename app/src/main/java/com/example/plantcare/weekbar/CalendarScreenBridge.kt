package com.example.plantcare.weekbar

import androidx.compose.ui.platform.ComposeView
import com.example.plantcare.ui.theme.PlantCareTheme

object CalendarScreenBridge {
    @JvmStatic
    fun showInView(
        composeView: ComposeView,
        onAddReminderClick: () -> Unit,
        onCameraClick: () -> Unit,
        onGalleryClick: () -> Unit,
        onGoToToday: (() -> Unit)? = null
    ) {
        composeView.setContent {
            PlantCareTheme {
                MainScreen(
                    onAddReminderClick = onAddReminderClick,
                    onCameraClick = onCameraClick,
                    onGalleryClick = onGalleryClick,
                    onGoToToday = onGoToToday ?: {},
                    onEditReminder = {},
                    onDeleteReminder = {},
                    showAppBarAndTabs = false
                )
            }
        }
    }
}