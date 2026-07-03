package com.shiftcalendar.alarm

import android.content.Context
import android.content.SharedPreferences

class AlarmSettings private constructor(private val prefs: SharedPreferences) {
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    var leadMinutes: Int
        get() = prefs.getInt(KEY_LEAD_MINUTES, 0)
        set(value) = prefs.edit().putInt(KEY_LEAD_MINUTES, value).apply()
    var vibrate: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE, value).apply()
    var ringtoneUri: String
        get() = prefs.getString(KEY_RINGTONE_URI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RINGTONE_URI, value).apply()

    companion object {
        private const val PREFS_NAME = "alarm_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LEAD_MINUTES = "lead_minutes"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_RINGTONE_URI = "ringtone_uri"

        fun get(context: Context): AlarmSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AlarmSettings(prefs)
        }
    }
}