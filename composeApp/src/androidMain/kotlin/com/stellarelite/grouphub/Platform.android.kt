package com.stellarelite.grouphub

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object AppContext {
    var context: Context? = null
        private set
    fun init(ctx: Context) { context = ctx.applicationContext }
}

actual fun showAppNotification(title: String, message: String) {
    val ctx = AppContext.context ?: return
    val channelId = "grouphub_alerts"
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (android.os.Build.VERSION.SDK_INT >= 26) {
        nm.createNotificationChannel(
            NotificationChannel(channelId, "经营提醒", NotificationManager.IMPORTANCE_HIGH)
        )
    }
    val n = NotificationCompat.Builder(ctx, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()
    try {
        NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % 100000).toInt(), n)
    } catch (_: Exception) { }
}
