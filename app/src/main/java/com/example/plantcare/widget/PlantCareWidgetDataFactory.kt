package com.example.plantcare.widget

import android.content.Context
import android.graphics.Bitmap
import com.example.plantcare.EmailContext
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.plantcare.Plant
import com.example.plantcare.WateringReminder
import com.example.plantcare.R
import com.example.plantcare.data.repository.PlantRepository
import com.example.plantcare.data.repository.ReminderRepository
import com.example.plantcare.data.repository.RoomCategoryRepository
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data factory for PlantCare widget list items
 * Queries the database for today's reminders and associated plant data
 */
class PlantCareWidgetDataFactory(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory {

    private val plantRepo = PlantRepository.getInstance(context)
    private val reminderRepo = ReminderRepository.getInstance(context)
    private val roomRepo = RoomCategoryRepository.getInstance(context)

    private var widgetItems: List<WidgetItem> = emptyList()

    data class WidgetItem(
        val reminderName: String,
        val isDone: Boolean,
        val imageUri: String?,
        val roomName: String?
    )

    override fun onCreate() {
        // Initialize is called when the factory is first created
    }

    override fun onDataSetChanged() {
        // Fetch reminders and plant data
        val userEmail = EmailContext.current(context) ?: return
        // Locale.US for the wire format that hits SQLite — on ar/fa
        // devices Locale.getDefault() emits Eastern-Arabic digits and
        // the WHERE date <= today comparison silently matches zero
        // rows, leaving the widget permanently empty. Same A2 root
        // cause as the worker layer + Today screen fixes.
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        // Get all reminders for today and overdue
        val reminders = reminderRepo
            .getTodayAndOverdueAllRemindersForUserBlocking(today, userEmail)

        // W4: build per-name plant + per-id room indexes once instead
        // of querying inside the per-reminder loop. Previous code did
        // O(N) plant + room lookups for N reminders — for a 20-reminder
        // user that's 40 SQL hits on every widget refresh, on the
        // binder thread the widget host owns. The all-plants read
        // costs one query and the in-memory map turns the rest into
        // hash lookups.
        val allPlants = plantRepo.getAllUserPlantsForUserBlocking(userEmail)
        val plantByName: Map<String, Plant> = HashMap<String, Plant>().apply {
            for (p in allPlants) {
                val n = p.name ?: continue
                if (n.isNotBlank()) putIfAbsent(n, p)
                val nick = p.nickname
                if (!nick.isNullOrBlank()) putIfAbsent(nick, p)
            }
        }
        val neededRoomIds = allPlants.mapNotNull { p ->
            p.roomId.takeIf { it > 0 }
        }.toSet()
        val roomNameById: Map<Int, String> = neededRoomIds.associateWith { id ->
            roomRepo.findByIdBlocking(id)?.name ?: ""
        }

        widgetItems = reminders.map { reminder ->
            val plant = reminder.plantName?.let { plantByName[it] }
            val roomName = plant?.takeIf { it.roomId > 0 }
                ?.let { roomNameById[it.roomId]?.takeIf { name -> name.isNotEmpty() } }

            WidgetItem(
                reminderName = reminder.plantName ?: "",
                isDone = reminder.done,
                imageUri = plant?.imageUri,
                roomName = roomName
            )
        }
    }

    override fun getCount(): Int = widgetItems.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position < 0 || position >= widgetItems.size) {
            return RemoteViews(context.packageName, R.layout.widget_item_reminder)
        }

        val item = widgetItems[position]
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_item_reminder)

        // Set plant name
        remoteViews.setTextViewText(R.id.widget_item_name, item.reminderName)

        // Set status icon (checkmark if done, water drop if not done)
        if (item.isDone) {
            remoteViews.setImageViewResource(R.id.widget_item_status_icon, R.drawable.ic_check)
            remoteViews.setInt(R.id.widget_item_status_icon, "setColorFilter", 0xFF4CAF50.toInt())
        } else {
            remoteViews.setImageViewResource(R.id.widget_item_status_icon, R.drawable.ic_water_drop)
            remoteViews.setInt(R.id.widget_item_status_icon, "setColorFilter", 0xFF2196F3.toInt())
        }

        // Load and set plant thumbnail
        if (item.imageUri != null) {
            try {
                val bitmap = loadBitmapFromUri(item.imageUri)
                if (bitmap != null) {
                    remoteViews.setImageViewBitmap(R.id.widget_item_thumbnail, bitmap)
                } else {
                    remoteViews.setImageViewResource(R.id.widget_item_thumbnail, R.drawable.ic_plant_placeholder)
                }
            } catch (e: Exception) {
                remoteViews.setImageViewResource(R.id.widget_item_thumbnail, R.drawable.ic_plant_placeholder)
            }
        } else {
            remoteViews.setImageViewResource(R.id.widget_item_thumbnail, R.drawable.ic_plant_placeholder)
        }

        return remoteViews
    }

    override fun getLoadingView(): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_item_reminder)
    }

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    override fun onDestroy() {
        widgetItems = emptyList()
    }

    /**
     * Load bitmap from URI with size optimization
     */
    private fun loadBitmapFromUri(imageUri: String): Bitmap? {
        return try {
            val uri = Uri.parse(imageUri)
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 2 // Reduce size for widget thumbnail
                }
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            com.example.plantcare.CrashReporter.log(e)
            null
        }
    }
}
