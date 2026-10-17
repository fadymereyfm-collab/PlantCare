package com.example.plantcare.feature.share

import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.plantcare.CrashReporter
import com.example.plantcare.MainActivity
import com.example.plantcare.PlantNotificationHelper
import com.example.plantcare.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Wave 2 — receives Family Share push notifications from the
 * `onShareInviteCreated` Cloud Function.
 *
 * Two payload `type` values are handled:
 *  - "share_invite"          → "Du wurdest eingeladen…" → tapping opens the
 *                              app to the Settings → Family Share view (or
 *                              MainActivity with an extra so MainActivity
 *                              can surface an Accept dialog).
 *  - "share_invite_accepted" → "X hat angenommen" → owner-side confirmation.
 *
 * Token rotation routed through [FcmTokenManager] so the Firestore mirror
 * stays current.
 */
class PlantCareMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        try {
            FcmTokenManager.onTokenRefreshed(token)
        } catch (t: Throwable) {
            CrashReporter.log(t)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        try {
            val data = message.data
            val type = data["type"] ?: return
            val title = message.notification?.title
                ?: data["title"]
                ?: getString(R.string.app_name)
            val body = message.notification?.body ?: data["body"] ?: ""

            // Carry the share metadata into MainActivity so it can surface
            // the accept-invite UI on next foreground.
            val intent = android.content.Intent(this, MainActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("share_push_type", type)
                data["inviteId"]?.let { putExtra("share_invite_id", it) }
                data["plantId"]?.let { putExtra("share_plant_id", it) }
                data["fromUid"]?.let { putExtra("share_from_uid", it) }
                data["fromEmail"]?.let { putExtra("share_from_email", it) }
            }

            val pi = PendingIntent.getActivity(
                this,
                /* requestCode */ type.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Reuse the existing reminder channel — same priority bracket and
            // the user already learned to allow it through system settings.
            val notif = NotificationCompat.Builder(this, PlantNotificationHelper.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_plant)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            // Distinct notification id per invite so multiple pushes don't
            // collapse into one.
            val notifId = (data["inviteId"] ?: type).hashCode()
            try {
                NotificationManagerCompat.from(this).notify(notifId, notif)
            } catch (se: SecurityException) {
                // POST_NOTIFICATIONS not granted — silently ignore.
            }
        } catch (t: Throwable) {
            CrashReporter.log(t)
        }
    }
}
