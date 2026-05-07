package com.example.plantcare.weekbar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.plantcare.Plant
import com.example.plantcare.PlantDetailDialogFragment
import com.example.plantcare.EmailContext
import com.example.plantcare.R
import com.example.plantcare.data.repository.PlantRepository
import com.example.plantcare.ui.theme.TextPrimary
import com.example.plantcare.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RemindersList(
    reminders: List<Reminder>,
    onEdit: (Reminder) -> Unit = {},
    onDelete: (Reminder) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cardColor = colorResource(R.color.pc_surface)
    val thumbBg = colorResource(R.color.pc_secondaryContainer)
    val iconBtnBg = colorResource(R.color.pc_secondaryContainer)
    val editIconTint = colorResource(R.color.pc_primary)
    val deleteIconTint = colorResource(R.color.pc_error)

    if (reminders.isEmpty()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Keine Erinnerungen für diesen Tag",
                color = TextSecondary,
                style = MaterialTheme.typography.body1
            )
        }
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            reminders.forEach { reminder ->
                val isAuto = reminder.title.isBlank() || reminder.title.isEmpty()

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp, horizontal = 2.dp)
                        .combinedClickable(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val plantRepo = PlantRepository.getInstance(context)
                                        val userEmail = EmailContext.current(context)
                                        var found: Plant? = null
                                        if (!reminder.plantName.isNullOrBlank() && userEmail != null) {
                                            val copies = plantRepo.findUserPlantsByName(reminder.plantName, userEmail)
                                            if (copies.isNotEmpty()) {
                                                found = plantRepo.findPlantById(copies[0].id)
                                            }
                                            if (found == null) {
                                                val byNick = plantRepo.findUserPlantsByNickname(reminder.plantName, userEmail)
                                                if (byNick.isNotEmpty()) {
                                                    found = plantRepo.findPlantById(byNick[0].id)
                                                }
                                            }
                                        }
                                        if (found == null && !reminder.plantName.isNullOrBlank()) {
                                            found = plantRepo.findAnyByNickname(reminder.plantName)
                                            if (found == null) found = plantRepo.findAnyByName(reminder.plantName)
                                        }
                                        if (found != null && context is FragmentActivity) {
                                            launch(Dispatchers.Main) {
                                                val dialog = PlantDetailDialogFragment.newInstance(found!!, true)
                                                dialog.setReadOnlyMode(true)
                                                dialog.show(context.supportFragmentManager, "plant_detail_from_calendar")
                                            }
                                        }
                                    } catch (_: Throwable) {}
                                }
                            },
                            onLongClick = { /* no-op */ }
                        ),
                    shape = RoundedCornerShape(20.dp),
                    color = cardColor,
                    elevation = 2.dp
                ) {
                    Row(
                        Modifier
                            .padding(18.dp)
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // صورة موحّدة المصدر
                        PlantThumbnail(
                            plantId = reminder.plantId,
                            plantName = reminder.plantName,
                            modifier = Modifier
                                .size(48.dp)
                                .background(thumbBg, shape = CircleShape)
                        )

                        Spacer(Modifier.width(12.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (!reminder.plantName.isNullOrBlank() && !reminder.title.isNullOrBlank())
                                    "${reminder.plantName}: ${reminder.title}"
                                else if (!reminder.plantName.isNullOrBlank())
                                    reminder.plantName!!
                                else reminder.title,
                                style = MaterialTheme.typography.body1.copy(fontSize = 17.sp, color = TextPrimary)
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        if (isAuto) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_watering_can),
                                contentDescription = "Watering Can",
                                modifier = Modifier.size(36.dp)
                            )
                        } else {
                            IconButton(
                                onClick = { onEdit(reminder) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(iconBtnBg, shape = CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Edit",
                                    tint = editIconTint
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { onDelete(reminder) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(iconBtnBg, shape = CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = deleteIconTint
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}