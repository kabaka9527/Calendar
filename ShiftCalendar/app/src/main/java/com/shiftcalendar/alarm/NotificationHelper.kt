package com.shiftcalendar.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.shiftcalendar.MainActivity
import com.shiftcalendar.R

object NotificationHelper {
    const val CHANNEL_ID = "shift_alarm"
    private const val CHANNEL_NAME = "班次闹钟"

    fun ensureChannel(context: Context, settings: AlarmSettings) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(settings.vibrate)
            if (settings.ringtoneUri.isNotEmpty()) {
                setSound(Uri.parse(settings.ringtoneUri), null)
            } else {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
            }
        }
        manager.createNotificationChannel(channel)
    }

    fun showAlarmNotification(context: Context, shiftName: String, startTime: String, endTime: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("班次提醒: $shiftName")
            .setContentText("${startTime} — ${endTime}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1001, notification)
    }
}