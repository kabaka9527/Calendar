package com.shiftcalendar.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.shiftcalendar.ShiftCalendarApp
import com.shiftcalendar.data.entity.ShiftDay
import com.shiftcalendar.data.entity.ShiftType
import java.util.Calendar

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

    fun computeNextAlarm(
        shiftDays: List<ShiftDay>,
        typeMap: Map<Long, ShiftType>,
        now: Long,
        leadMinutes: Int
    ): NextAlarm? {
        val candidates = mutableListOf<NextAlarm>()
        for (day in shiftDays) {
            val type = typeMap[day.shiftTypeId] ?: continue
            if (type.alarmEnabled == false) continue
            val startMinutes = parseHHmm(type.startTime)
            val startTrigger = day.date + (startMinutes - leadMinutes) * 60 * 1000L
            if (startTrigger > now) {
                candidates.add(NextAlarm(startTrigger, AlarmType.START, day, type))
            }
        }
        return candidates.minByOrNull { it.triggerAt }
    }

    suspend fun scheduleNext(context: Context) {
        val settings = AlarmSettings.get(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!settings.enabled) {
            cancel(context, alarmManager)
            return
        }
        val db = ShiftCalendarApp.instance.database
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
            cancel(context, alarmManager)
            return
        }
        NotificationHelper.ensureChannel(context, settings)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER
            putExtra(AlarmReceiver.EXTRA_TYPE, next.type.name)
            putExtra(AlarmReceiver.EXTRA_SHIFT_DAY_ID, next.shiftDay.id)
            putExtra(AlarmReceiver.EXTRA_SHIFT_DATE, next.shiftDay.date)
            putExtra(AlarmReceiver.EXTRA_SHIFT_TYPE_ID, next.shiftType.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val info = AlarmManager.AlarmClockInfo(next.triggerAt, null)
        alarmManager.setAlarmClock(info, pendingIntent)
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancel(context, alarmManager)
    }

    private fun cancel(context: Context, alarmManager: AlarmManager) {
        val intent = Intent(context, AlarmReceiver::class.java)
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