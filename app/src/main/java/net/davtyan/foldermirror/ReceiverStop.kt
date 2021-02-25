package net.davtyan.foldermirror

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager

class ReceiverStop : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        WorkManager.getInstance(context).cancelAllWork()
        NotificationManagerCompat.from(context).cancelAll()
    }
}