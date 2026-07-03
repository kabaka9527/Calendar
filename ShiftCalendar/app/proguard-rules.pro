# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class com.shiftcalendar.data.entity.** { *; }
-dontwarn com.miui.x.**

# Alarm system
-keep class com.shiftcalendar.alarm.** { *; }
-keep class com.shiftcalendar.ui.settings.** { *; }
-keep class com.shiftcalendar.ui.shifttype.** { *; }
-keep class com.shiftcalendar.ui.shiftrule.** { *; }
-keep class com.shiftcalendar.domain.** { *; }
-keep class com.shiftcalendar.data.database.** { *; }
-keep class com.shiftcalendar.ui.calendar.CalendarViewModel { *; }
-keep class com.shiftcalendar.ui.calendar.CalendarViewModel$Factory { *; }
-keep class com.shiftcalendar.ui.calendar.CalendarData { *; }
-keep class com.shiftcalendar.ui.calendar.ShiftDayDetail { *; }
-keep class com.shiftcalendar.ui.calendar.CalendarFragment { *; }
-keepclassmembers class com.shiftcalendar.alarm.AlarmReceiver { *; }
-keepclassmembers class com.shiftcalendar.alarm.AlarmBootReceiver { *; }