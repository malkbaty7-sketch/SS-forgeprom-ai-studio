package com.forgeprom.studio.core.jobs

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.forgeprom.studio.MainActivity
import com.forgeprom.studio.R

/** إشعار اختياري عند اكتمال مهمة مشهد في الخلفية. لا يمنع الإشعار عمل التطبيق. */
object SceneNotifier {

    private const val CHANNEL_ID = "scene_jobs"
    private const val CHANNEL_NAME = "مهام المشاهد"

    fun notifyCompleted(context: Context, promptId: Long, title: String) =
        notify(context, promptId, "اكتمل توليد المشهد", title.ifBlank { "المشهد جاهز للإدراج في المحرر." })

    fun notifyFailed(context: Context, promptId: Long) =
        notify(context, promptId, "تعذّر إكمال مهمة المشهد", "افتح المحرر لإعادة المحاولة.")

    private fun notify(context: Context, promptId: Long, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply{
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("promptId", promptId)
        }
        val pending = PendingIntent.getActivity(
            context, promptId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { manager.notify(promptId.toInt(), notification) }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "إشعار عند اكتمال مهمة توليد مشهد أُرسلت إلى طابور البوابة."
            }
        )
    }
}
