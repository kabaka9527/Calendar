package com.shiftcalendar.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shiftcalendar.data.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 闹钟触发接收器。
 *
 * 系统在闹钟时间到达时通过 [android.app.AlarmManager] 投递广播。本接收器使用
 * [goAsync] 延长进程生命周期，确保异步查库 + 发通知完成后再放行，避免进程被
 * 系统提前回收导致闹钟丢失。
 *
 * 全程通过传入的 [Context] 获取数据库实例（[AppDatabase.getInstance]），不依赖
 * Application 单例，保证在冷启动场景下也能稳定工作。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER) return

        // goAsync() 让 BroadcastReceiver 在 onReceive 返回后仍保持存活，
        // 直到 finish() 被调用（限时约 10 秒），保证协程有时间完成工作。
        val pendingResult = goAsync()
        val shiftTypeId = intent.getLongExtra(EXTRA_SHIFT_TYPE_ID, 0L)
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(appContext)
                val type = db.shiftTypeDao().getById(shiftTypeId)
                if (type != null) {
                    NotificationHelper.showAlarmNotification(
                        appContext, type.name, type.startTime, type.endTime
                    )
                }
                // 触发后重新调度下一个闹钟
                AlarmScheduler.scheduleNext(appContext)
            } catch (_: Throwable) {
            } finally {
                try {
                    pendingResult.finish()
                } catch (_: Throwable) {
                }
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER = "com.shiftcalendar.alarm.TRIGGER"
        const val EXTRA_TYPE = "alarm_type"
        const val EXTRA_SHIFT_DAY_ID = "shift_day_id"
        const val EXTRA_SHIFT_DATE = "shift_date"
        const val EXTRA_SHIFT_TYPE_ID = "shift_type_id"
    }
}
