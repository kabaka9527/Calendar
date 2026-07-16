package com.shiftcalendar.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shiftcalendar.MainActivity
import com.shiftcalendar.R

/**
 * 通知渠道与通知构建。
 *
 * 关于通知权限：在 Android 13+ 上若 [android.Manifest.permission.POST_NOTIFICATIONS]
 * 未授权，[NotificationManagerCompat.notify] 会静默失败（不会抛异常），因此本类无
 * 需额外判空。权限申请由调用方（设置页）在用户启用闹钟时发起。
 */
object NotificationHelper {

    const val CHANNEL_ID = "shift_alarm"
    private const val CHANNEL_NAME = "班次闹钟"
    private const val NOTIFICATION_ID = 1001

    private fun defaultSoundUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    /**
     * 确保通知渠道已创建。可重复调用，系统会幂等处理。自身捕获异常。
     */
    fun ensureChannel(context: Context, settings: AlarmSettings) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(settings.vibrate)
                val sound = if (settings.ringtoneUri.isNotEmpty()) {
                    Uri.parse(settings.ringtoneUri)
                } else {
                    defaultSoundUri()
                }
                setSound(sound, null)
            }
            manager.createNotificationChannel(channel)
        } catch (_: Throwable) {
        }
    }

    /**
     * 显示闹钟通知。内部会先确保渠道存在。自身捕获异常，不会向上抛出。
     */
    fun showAlarmNotification(
        context: Context, shiftName: String, startTime: String, endTime: String
    ) {
        try {
            val appContext = context.applicationContext
            val settings = AlarmSettings.get(appContext)
            ensureChannel(appContext, settings)

            val intent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                appContext, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("班次提醒: $shiftName")
                .setContentText("$startTime — $endTime")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
        }
    }
}
