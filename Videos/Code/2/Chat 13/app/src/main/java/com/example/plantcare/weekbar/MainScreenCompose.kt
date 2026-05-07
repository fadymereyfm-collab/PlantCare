package com.example.plantcare.weekbar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Today
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
// AppCompat AlertDialog used via fully qualified name below
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

// Project imports
import com.example.plantcare.DataChangeNotifier
import com.example.plantcare.EditManualReminderDialogFragment
import com.example.plantcare.FirebaseSyncManager
import com.example.plantcare.FullScreenImageDialogFragment
import com.example.plantcare.data.repository.PlantPhotoRepository
import com.example.plantcare.data.repository.ReminderRepository
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.plantcare.R

@Composable
fun WeatherTipCard(weatherTip: WeatherTip) {
    if (weatherTip.tip.isEmpty()) return

    val cardBg = colorResource(R.color.pc_secondaryContainer)
    val textColor = colorResource(R.color.pc_onSurface)
    val subtextColor = colorResource(R.color.pc_onSurfaceSecondary)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = cardBg,
        elevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Weather emoji
            Text(
                text = weatherTip.emoji,
                style = MaterialTheme.typography.h4,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                // City + temp
                if (weatherTip.city.isNotEmpty()) {
                    Text(
                        text = "${weatherTip.city} · ${weatherTip.temp.toInt()}°C",
                        color = textColor,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.body1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Tip message
                Text(
                    text = weatherTip.tip,
                    color = subtextColor,
                    style = MaterialTheme.typography.body2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: ReminderViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onAddReminderClick: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onGoToToday: () -> Unit,
    onEditReminder: (Reminder) -> Unit,
    onDeleteReminder: (Reminder) -> Unit,
    showAppBarAndTabs: Boolean = false
) {
    var showMonthDialog by remember { mutableStateOf(false) }

    val selectedDate by viewModel.selectedDate.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    val daysWithRemindersThisMonth by viewModel.daysWithRemindersThisMonth.collectAsState()
    val remindersByDateThisMonth by viewModel.remindersByDateThisMonth.collectAsState()
    val weatherTip by viewModel.weatherTip.collectAsState()
    // NEW: collect day photos
    val dayPhotos by viewModel.photosForSelectedDate.collectAsState()

    val context = LocalContext.current
    val activity = remember(context) { context as? FragmentActivity }
    val scope = rememberCoroutineScope()

    // Refresh when data changes anywhere in the app
    val refreshRunnable = remember { Runnable { viewModel.selectDate(viewModel.selectedDate.value) } }
    DisposableEffect(Unit) {
        DataChangeNotifier.addListener(refreshRunnable)
        onDispose { DataChangeNotifier.removeListener(refreshRunnable) }
    }

    // Delete confirmation state
    var reminderPendingDelete by remember { mutableStateOf<Reminder?>(null) }

    // Back button dismisses month overlay
    BackHandler(enabled = showMonthDialog) { showMonthDialog = false }

    Box(Modifier.fillMaxSize()) {

        val bgColor = colorResource(R.color.pc_background)
        val iconBgColor = colorResource(R.color.pc_primary)
        val iconTintOnPrimary = colorResource(R.color.pc_onPrimary)

        // Main content
        Column(
            Modifier
                .fillMaxSize()
                .background(bgColor)
                .zIndex(0f)
        ) {
            WeekBarWithMonthPicker(
                viewModel = viewModel,
                showMonthSheet = showMonthDialog,
                onShowMonthSheet = { showMonthDialog = it },
                onSelectDate = { viewModel.selectDate(it) },
                onGoToToday = {
                    viewModel.goToToday()
                    if (showMonthDialog) viewModel.setDisplayedMonthToToday()
                },
            )

            // Month dialog toggle button
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = { showMonthDialog = !showMonthDialog },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(iconBgColor)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarToday,
                        contentDescription = "اختر الشهر",
                        tint = iconTintOnPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Weather tip card
            WeatherTipCard(weatherTip = weatherTip)

            // Always render content so it's visible behind the semi-transparent dialog
            RemindersList(
                reminders = reminders,
                onEdit = { reminder ->
                    if (!showMonthDialog) {
                        val id = reminder.id.toIntOrNull()
                        if (id != null && id > 0 && activity != null) {
                            scope.launch(Dispatchers.IO) {
                                val wateringReminder = ReminderRepository
                                    .getInstance(context).getReminderById(id)
                                launch(Dispatchers.Main) {
                                    if (wateringReminder != null) {
                                        val dialog = EditManualReminderDialogFragment.newInstance(wateringReminder)
                                        dialog.show(activity.supportFragmentManager, "edit_manual_reminder")
                                    }
                                }
                            }
                        }
                        onEditReminder(reminder)
                    }
                },
                onDelete = { reminder ->
                    if (!showMonthDialog) {
                        reminderPendingDelete = reminder
                    }
                }
            )

            // صور اليوم (شبكة صور مربعة + اسم النبتة)
            if (dayPhotos.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                CalendarPhotoGrid(
                    photos = dayPhotos,
                    modifier = Modifier.fillMaxWidth(),
                    onPhotoClick = { photo ->
                        if (!showMonthDialog) {
                            val path = photo.imagePath
                            if (path != null && !path.startsWith("PENDING_DOC:") && activity != null) {
                                val dialog = FullScreenImageDialogFragment.newInstance(path)
                                dialog.show(activity.supportFragmentManager, "image_fullscreen")
                            } else if (path != null && path.startsWith("PENDING_DOC:")) {
                                Toast.makeText(context, R.string.photo_still_uploading, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onPhotoLongClick = { photo ->
                        if (showMonthDialog) return@CalendarPhotoGrid
                        if (activity == null) return@CalendarPhotoGrid
                        val optionDelete = context.getString(R.string.action_delete)
                        val optionChangeDate = context.getString(R.string.action_change_date)
                        val options = arrayOf(optionDelete, optionChangeDate)
                        androidx.appcompat.app.AlertDialog.Builder(context)
                            .setTitle(R.string.photo_options_title)
                            .setItems(options) { _, which ->
                                if (which == 0) {
                                    androidx.appcompat.app.AlertDialog.Builder(context)
                                        .setMessage(R.string.confirm_delete_photo_message)
                                        .setPositiveButton(R.string.action_yes) { _, _ ->
                                            scope.launch(Dispatchers.IO) {
                                                val plantPhoto = PlantPhotoRepository
                                                    .getInstance(context).getPhotoById(photo.photoId)
                                                if (plantPhoto != null) {
                                                    FirebaseSyncManager.get().deletePhotoSmart(plantPhoto, context)
                                                }
                                                launch(Dispatchers.Main) {
                                                    DataChangeNotifier.notifyChange()
                                                    viewModel.selectDate(viewModel.selectedDate.value)
                                                }
                                            }
                                        }
                                        .setNegativeButton(R.string.action_no, null)
                                        .show()
                                } else if (which == 1) {
                                    val cal = java.util.Calendar.getInstance()
                                    cal.set(photo.date.year, photo.date.monthValue - 1, photo.date.dayOfMonth)
                                    android.app.DatePickerDialog(context, { _, y, m, d ->
                                        val newDate = String.format(java.util.Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                                        scope.launch(Dispatchers.IO) {
                                            val photoRepo = PlantPhotoRepository.getInstance(context)
                                            val plantPhoto = photoRepo.getPhotoById(photo.photoId)
                                            if (plantPhoto != null) {
                                                plantPhoto.dateTaken = newDate
                                                photoRepo.update(plantPhoto)
                                            }
                                            launch(Dispatchers.Main) {
                                                DataChangeNotifier.notifyChange()
                                                viewModel.selectDate(viewModel.selectedDate.value)
                                            }
                                        }
                                    }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
                                }
                            }
                            .setNegativeButton(R.string.action_cancel, null)
                            .show()
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        // Bottom actions bar
        BottomActionsBar(
            onAddReminderClick = onAddReminderClick,
            onCameraClick = onCameraClick,
            onGalleryClick = onGalleryClick,
            onGoToToday = {
                viewModel.goToToday()
                if (showMonthDialog) viewModel.setDisplayedMonthToToday()
            },
            showTodayButton = (selectedDate != LocalDate.now()) ||
                    (showMonthDialog && viewModel.displayedMonthState.value != YearMonth.from(LocalDate.now())),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .zIndex(3f)
        )

        // Month picker overlay
        if (showMonthDialog) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .clickable(enabled = true) { showMonthDialog = false }
                    .zIndex(1f)
            )
            val dialogBg = colorResource(R.color.pc_surface)
            val dialogTextColor = colorResource(R.color.pc_onSurface)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .zIndex(2f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(dialogBg)
            ) {
                Column(
                    Modifier
                        .padding(20.dp)
                        .widthIn(min = 300.dp, max = 480.dp)
                ) {
                    Text(
                        text = "Monat auswählen",
                        color = dialogTextColor,
                        style = MaterialTheme.typography.h6
                    )
                    Spacer(Modifier.height(8.dp))

                    MonthPicker(
                        currentDate = selectedDate,
                        onSelectDate = { date -> viewModel.goToDate(date) },
                        daysWithReminders = daysWithRemindersThisMonth,
                        remindersByDate = remindersByDateThisMonth,
                        onConfirmDate = { date ->
                            viewModel.goToDate(date)
                            showMonthDialog = false
                        },
                        displayedMonthState = viewModel.displayedMonthState,
                        onMonthChanged = { ym -> viewModel.onDisplayedMonthChanged(ym) }
                    )

                    Spacer(Modifier.height(12.dp))

                    // Outlined button on dialog
                    val borderColor = colorResource(R.color.pc_outlineVariant)
                    OutlinedButton(
                        onClick = { showMonthDialog = false },
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.5.dp, borderColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = dialogTextColor)
                    ) {
                        Text("Schließen", color = dialogTextColor)
                    }
                }
            }
        }

        // Delete confirmation dialog
        reminderPendingDelete?.let { pending ->
            AlertDialog(
                onDismissRequest = { reminderPendingDelete = null },
                title = { Text("Löschen bestätigen") },
                text = { Text("Soll dieser Eintrag wirklich gelöscht werden?") },
                confirmButton = {
                    androidx.compose.material.TextButton(onClick = {
                        reminderPendingDelete = null
                        val id = pending.id.toIntOrNull()
                        if (id != null && id > 0) {
                            scope.launch(Dispatchers.IO) {
                                val reminderRepo = ReminderRepository.getInstance(context)
                                val wr = reminderRepo.getReminderById(id)
                                if (wr != null) reminderRepo.deleteReminder(wr)
                                launch(Dispatchers.Main) {
                                    DataChangeNotifier.notifyChange()
                                    viewModel.selectDate(viewModel.selectedDate.value)
                                }
                            }
                        }
                        onDeleteReminder(pending)
                    }) { Text("Ja") }
                },
                dismissButton = {
                    androidx.compose.material.TextButton(onClick = { reminderPendingDelete = null }) {
                        Text("Nein")
                    }
                }
            )
        }
    }
}

@Composable
private fun BottomActionsBar(
    onAddReminderClick: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onGoToToday: () -> Unit,
    showTodayButton: Boolean,
    modifier: Modifier = Modifier
) {
    val barColor = colorResource(R.color.pc_surface)
    val iconBg = colorResource(R.color.pc_secondaryContainer)
    val iconTint = colorResource(R.color.pc_primary)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        elevation = 4.dp,
        color = barColor
    ) {
        Row(
            Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onAddReminderClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(iconBg, shape = CircleShape)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Erinnerung hinzufügen",
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(
                onClick = onCameraClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(iconBg, shape = CircleShape)
            ) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = "Kamera",
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(
                onClick = onGalleryClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(iconBg, shape = CircleShape)
            ) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = "Galerie",
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }
            if (showTodayButton) {
                IconButton(
                    onClick = onGoToToday,
                    modifier = Modifier
                        .size(48.dp)
                        .background(iconBg, shape = CircleShape)
                ) {
                    Icon(
                        Icons.Filled.Today,
                        contentDescription = "Heute",
                        tint = iconTint,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}