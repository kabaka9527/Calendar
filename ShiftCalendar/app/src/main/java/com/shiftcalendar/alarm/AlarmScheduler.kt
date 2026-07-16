package com.shiftcalendar.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.shiftcalendar.data.database.AppDatabase
import com.shiftcalendar.data.entity.ShiftDay
import com.shiftcalendar.data.entity.ShiftType
import java.util.Calendar

/**
 * 闹钟调度器
 *
 * 负责计算并注册下一个班次闹钟到系统的 [AlarmManager]。
 *
 * 设计要点：
 * - 完全基于传入的 [Context] 工作，使用 [Context.getApplicationContext]，
 *   不依赖任何全局单例（如 Application.instance），确保在 BroadcastReceiver、
 *   BootReceiver 等任意进程入口都能安全调用。
 * - 所有公开方法自身不向上抛异常（内部已 try-catch），调用方无需额外包裹。
 * - [scheduleNext] 优先使用 [AlarmManager.setAlarmClock]（免 exact alarm 权限），
 *   失败时降级为 [AlarmManager.setAndAllowWhileIdle]。
 */
object AlarmScheduler {

    private const val ALARM_REQUEST_CODE = 1001
    private const val LOOKAHEAD_DAYS = 90

    enum class AlarmType { START }

    data class NextAlarm(
        val triggerAt: Long,
        val type: AlarmType,
        val shiftDay: ShiftDay,
        val shiftType: ShiftType
    )

    /**
     * 计算下一个应触发的闹钟（纯函数，便于测试）。
     *
     * @param leadMinutes 提前提醒的分钟数
     */
    fun computeNextAlarm(
        shiftDays: List<ShiftDay>,
        typeMap: Map<Long, ShiftType>,
        now: Long,
        leadMinutes: Int
    ): NextAlarm? {
        val candidates = mutableListOf<NextAlarm>()
        for (day in shiftDays) {
            val type = typeMap[day.shiftTypeId] ?: continue
            // alarmEnabled: null / true = 启用，false = 禁用
            if (type.alarmEnabled == false) continue
            val startMinutes = parseHHmm(type.startTime)
            val startTrigger = day.date + (startMinutes - leadMinutes) * 60_000L
            if (startTrigger > now) {
                candidates.add(NextAlarm(startTrigger, AlarmType.START, day, type))
            }
        }
        return candidates.minByOrNull { it.triggerAt }
    }

    /**
     * 计算并注册下一个班次闹钟。
     *
     * 若全局开关关闭或无候选班次，会取消已注册的闹钟。
     * 该方法自身捕获所有异常，不会向上抛出，可安全在任意协程中调用。
     */
    suspend fun scheduleNext(context: Context) {
        try {
            scheduleNextInternal(context)
        } catch (_: Throwable) {
            // 调度失败不应影响调用方流程
        }
    }

    private suspend fun scheduleNextInternal(context: Context) {
        val appContext = context.applicationContext
        val settings = AlarmSettings.get(appContext)
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return

        if (!settings.enabled) {
            cancelInternal(appContext, alarmManager)
            return
        }

        val db = AppDatabase.getInstance(appContext)
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startRange = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, LOOKAHEAD_DAYS)
        val endRange = cal.timeInMillis

        val shiftDays = db.shiftDayDao().getByDateRangeStatic(startRange, endRange)
        val types = db.shiftTypeDao().getAllStaticList()
        val typeMap = types.associateBy { it.id }

        val next = computeNextAlarm(shiftDays, typeMap, now, settings.leadMinutes)
        if (next == null) {
            cancelInternal(appContext, alarmManager)
            return
        }

        // 提前确保通知渠道存在，避免触发时才发现渠道缺失
        NotificationHelper.ensureChannel(appContext, settings)

        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER
            putExtra(AlarmReceiver.EXTRA_TYPE, next.type.name)
            putExtra(AlarmReceiver.EXTRA_SHIFT_DAY_ID, next.shiftDay.id)
            putExtra(AlarmReceiver.EXTRA_SHIFT_DATE, next.shiftDay.date)
            putExtra(AlarmReceiver.EXTRA_SHIFT_TYPE_ID, next.shiftType.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // setAlarmClock 在 Android 12+ 不需要 SCHEDULE_EXACT_ALARM 权限，
        // 且会被系统作为「用户设置的闹钟」对待（不受 Doze 限制）。
        try {
            val info = AlarmManager.AlarmClockInfo(next.triggerAt, null)
            alarmManager.setAlarmClock(info, pendingIntent)
        } catch (_: SecurityException) {
            // 某些 ROM 可能限制 exact alarm，降级为 inexact
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, next.triggerAt, pendingIntent
                )
            } catch (_: Throwable) {
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * 取消已注册的闹钟。自身捕获异常，不会向上抛出。
     */
    fun cancel(context: Context) {
        try {
            val appContext = context.applicationContext
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE)
                as? AlarmManager ?: return
            cancelInternal(appContext, alarmManager)
        } catch (_: Throwable) {
        }
    }

    private fun cancelInternal(context: Context, alarmManager: AlarmManager) {
        // Intent 必须带上 ACTION_TRIGGER，与 scheduleNext 注册时一致，
        // 否则 PendingIntent.filterEquals() 不匹配，无法取消旧闹钟。
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    private fun parseHHmm(time: String): Int {
        return try {
            val parts = time.split(":")
            if (parts.size == 2) parts[0].toInt() * 60 + parts[1].toInt() else 0
        } catch (_: Exception) {
            0
        }
    }
}
