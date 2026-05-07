package com.example.plantcare.widget

import android.content.Intent
import android.widget.RemoteViewsService
import com.example.plantcare.AppDatabase

/**
 * RemoteViewsService for providing data to the PlantCare widget
 */
class PlantCareWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return PlantCareWidgetDataFactory(this.applicationContext)
    }
}
