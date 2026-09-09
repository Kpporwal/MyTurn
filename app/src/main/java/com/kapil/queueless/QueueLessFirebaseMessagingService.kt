package com.kapil.queueless

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class QueueLessFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "queueless_notifications"
        private const val CHANNEL_NAME = "QueueLess Notifications"
        private const val CHANNEL_DESCRIPTION =
            "Notifications about your queue and business updates"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "QueueLess"

        val message = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "You have a new QueueLess update."

        showNotification(title, message)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // Token will be saved to Firestore in the next step.
        android.util.Log.d("QueueLessFCM", "New FCM Token: $token")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
            }

            val notificationManager =
                getSystemService(NotificationManager::class.java)

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, message: String) {

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(this)
                .notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            android.util.Log.e(
                "QueueLessFCM",
                "Notification permission not granted",
                e
            )
        }
    }
}