# Add project specific ProGuard rules here.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Room entities (Serializable + 反射访问字段)
-keep class com.shiftcalendar.data.entity.** { *; }

-dontwarn com.miui.x.**

# Application / Activity / BroadcastReceiver —— 在 Manifest 中声明，R8 默认会保留，
# 这里显式 keep 以防个别 ROM 的 R8 配置差异导致类被重命名后反射失败。
-keep class com.shiftcalendar.ShiftCalendarApp { *; }
-keep class com.shiftcalendar.MainActivity { *; }

# Alarm system —— 全部保留，避免 R8 剥离 BroadcastReceiver 入口或 object 单例
-keep class com.shiftcalendar.alarm.** { *; }
-keepclassmembers class com.shiftcalendar.alarm.AlarmReceiver { *; }
-keepclassmembers class com.shiftcalendar.alarm.AlarmBootReceiver { *; }

# UI / domain / database
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

# Kotlin Metadata —— 部分反射库依赖
-keep class kotlin.Metadata { *; }
