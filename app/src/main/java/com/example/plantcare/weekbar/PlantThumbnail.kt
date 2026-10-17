package com.example.plantcare.weekbar

import android.content.Context
import android.widget.ImageView
import com.example.plantcare.EmailContext
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun PlantThumbnail(
    plantId: Long,
    plantName: String?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context -> ImageView(context) },
        update = { imageView ->
            val context: Context = imageView.context
            val userEmail = EmailContext.current(context)
            PlantImageLoader.loadInto(context, imageView, plantId, plantName, userEmail)
        }
    )
}