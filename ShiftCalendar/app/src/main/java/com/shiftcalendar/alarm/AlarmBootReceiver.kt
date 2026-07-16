package com.shiftcalendar.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机 / 应用升级后重新注册闹钟。
 *
 * 系统 [android.app.AlarmManager] 闹钟不会在设备重启或应用升级后保留，需要在这
 * 些事件发生后重新调度，否则用户重启手机或更新 App 后闹钟将全部丢失。
 */
class AlarmBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val appContext = context.applicationContext
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        AlarmScheduler.scheduleNext(appContext)
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }
}
