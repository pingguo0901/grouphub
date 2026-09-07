package com.stellarelite.grouphub

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        val source = when (sbn.packageName) {
            "com.whatsapp" -> "WhatsApp"
            "com.tencent.mm" -> "WeChat"
            else -> return
        }

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return

        Thread {
            try {
                val url = URL("${Config.SUPABASE_URL}/functions/v1/sync-phone-msg")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer ${Config.SUPABASE_ANON_KEY}")
                conn.doOutput = true
                val body = JSONObject().apply {
                    put("source", source)
                    put("sender", title)
                    put("summary", text)
                    put("msg_time", System.currentTimeMillis())
                }.toString()
                OutputStreamWriter(conn.outputStream).use { it.write(body) }
                conn.inputStream.close()
                conn.disconnect()
            } catch (e: Exception) {
                // 忽略网络异常，通知采集不影响主流程
            }
        }.start()
    }
}
