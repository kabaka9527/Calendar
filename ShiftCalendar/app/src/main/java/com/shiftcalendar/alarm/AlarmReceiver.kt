package com.shiftcalendar.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER) return
        val shiftTypeId = intent.getLongExtra(EXTRA_SHIFT_TYPE_ID, 0)
        val db = com.shiftcalendar.ShiftCalendarApp.instance.database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val type = db.shiftTypeDao().getById(shiftTypeId)
                if (type != null) {
                    NotificationHelper.showAlarmNotification(
                        context, type.name, type.startTime, type.endTime
                    )
                }
                scheduleNextSafely(context)
            } catch (_: Exception) {
            }
        }
    }

    private fun scheduleNextSafely(context: Context) {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AlarmScheduler.scheduleNext(context)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
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