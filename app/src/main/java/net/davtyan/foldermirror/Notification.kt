package net.davtyan.foldermirror

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

private const val CHANNEL_ID = "FOLDER_MIRROR_CHANEL_1"

class Notification(appContext: Context) {

    private val context = appContext

    private var lastNotificationReportedMillis: Long = 0
    private var lastNotificationReportedTimeout: Long = 400

    // Create an explicit intent for an Activity in your app
    private val intent =
        Intent(context, MainActivity::class.java).addCategory(Intent.CATEGORY_LAUNCHER)
            .setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

    private val stopIntent = Intent(context, ReceiverStop::class.java)
    private val stopReceiverIntent: PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        stopIntent,
        0
    )

    private val builderButtons: NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setContentTitle("FolderMirror")
            setContentText("")
            setSmallIcon(R.drawable.ic_stat_folder)
            priority = NotificationCompat.PRIORITY_HIGH
            setContentIntent(pendingIntent)
            setAutoCancel(true)
            addAction(android.R.drawable.ic_menu_delete, "Cancel", stopReceiverIntent)
        }

    private val builderNoButtons: NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setContentTitle("FolderMirror")
            setContentText("")
            setSmallIcon(R.drawable.ic_stat_folder)
            priority = NotificationCompat.PRIORITY_HIGH
            setContentIntent(pendingIntent)
            setAutoCancel(true)
        }

    fun buildNotificationIndeterminate(contentTitle: String) {
        NotificationManagerCompat.from(context).apply {
            builderButtons.setContentTitle(contentTitle)
                .setContentText("")
                .setProgress(100, 0, true)
            notify(1, builderButtons.build())
        }
    }

    fun buildNotificationStatic(contentTitle: String) {
        NotificationManagerCompat.from(context).apply {
            builderNoButtons.setContentTitle(contentTitle)
                .setContentText("")
                .setProgress(100, 100, false)
            notify(1, builderNoButtons.build())
        }
    }

    fun buildNotificationProgress(contentTitle: String, contentText: String, progress: Int) {
        if ((System.currentTimeMillis() - lastNotificationReportedMillis) > lastNotificationReportedTimeout) {
            NotificationManagerCompat.from(context).apply {
                builderButtons.setContentTitle(contentTitle)
                    .setContentText(contentText)
                    .setProgress(100, progress, false)
                notify(1, builderButtons.build())
            }
            lastNotificationReportedMillis = System.currentTimeMillis()
        }
    }

    fun cancelAllNotifications() {
        NotificationManagerCompat.from(context).cancelAll()
    }

    fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "FolderMirror"
            val descriptionText = "Main Channel"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }


    }
}