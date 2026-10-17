package com.example.plantcare.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.plantcare.MainActivity
import com.example.plantcare.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * PlantCare Home Screen Widget Provider
 * Displays today's watering tasks with plant thumbnails
 */
class PlantCareWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_plantcare)

        // Set up the header with today's date
        val todayDate = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
        remoteViews.setTextViewText(R.id.widget_date_text, todayDate)

        // Set up click listener for header to open MainActivity
        val mainActivityIntent = Intent(context, MainActivity::class.java)
        val mainActivityPendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            mainActivityIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        remoteViews.setOnClickPendingIntent(R.id.widget_header, mainActivityPendingIntent)

        // Set up the RemoteViews list adapter
        val serviceIntent = Intent(context, PlantCareWidgetService::class.java)
        remoteViews.setRemoteAdapter(R.id.widget_list, serviceIntent)

        // Set up click listener for list items to open MainActivity
        val itemClickIntent = Intent(context, MainActivity::class.java)
        val itemClickPendingIntent = android.app.PendingIntent.getActivity(
            context,
            1,
            itemClickIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        remoteViews.setPendingIntentTemplate(R.id.widget_list, itemClickPendingIntent)

        // Show empty state if no tasks
        remoteViews.setTextViewText(R.id.widget_empty_text, context.getString(R.string.widget_no_tasks))

        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
    }

    companion object {
        /**
         * Manually refresh the widget. Called from
         * [com.example.plantcare.DataChangeNotifier] on every data
         * change broadcast — which is many times per minute during
         * the import flow. Early-return when no widget is currently
         * placed on any home screen so the majority of users
         * (who never added the widget) don't pay the broadcast +
         * RemoteViews-rebuild cost on every CRUD. ZZ3 fix.
         */
        fun updateWidget(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, PlantCareWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isEmpty()) return

            val intent = Intent(context, PlantCareWidget::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            context.sendBroadcast(intent)
        }
    }
}
