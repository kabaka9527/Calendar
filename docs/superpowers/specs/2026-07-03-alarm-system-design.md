# 闹钟系统设计

日期：2026-07-03
状态：已确认，待写实现计划

## 1. 目标

为倒班日历增加闹钟系统：根据班次时间自动设置闹钟，支持声音、震动，避免重复造轮子（全部使用 Android 系统 API）。

不做的事（YAGNI）：贪睡、自定义录音、闹钟 UI 动画、跨设备同步。

## 2. 触发时机

- 班次开始前提前提醒（提前量全局可调，默认 0 分钟）
- 班次结束时提醒

两类提醒独立调度，均走同一个 AlarmReceiver，通过 `Notification` 的 action 区分。

## 3. 配置粒度（全局 + 班次覆盖）

| 层级 | 字段 | 取值 |
|---|---|---|
| 全局（SharedPreferences） | `alarm_enabled: Boolean` | 总开关 |
| 全局 | `alarm_lead_minutes: Int` | 提前分钟数，默认 0 |
| 全局 | `alarm_ringtone_uri: String` | 铃声 URI，空=系统默认 |
| 全局 | `alarm_vibrate: Boolean` | 是否震动，默认 true |
| 班次级（ShiftType） | `alarmEnabled: Boolean?` | null=跟随全局；true=强制开；false=强制关 |

班次级覆盖规则：取班次的 `alarmEnabled`，若为 null 则用全局 `alarm_enabled`。休息班次默认 `alarmEnabled=false`。

## 4. 技术选型（避免重复造轮子）

| 能力 | 用什么 | 不自建的轮子 |
|---|---|---|
| 定时触发 | `AlarmManager.setAlarmClock` | Doze 下精确触发，系统级 |
| 声音/震动 | `NotificationChannel` | channel 自带 sound/vibration/importance |
| 铃声选择 | `RingtoneManager.ACTION_RINGTONE_PICKER` | 系统铃声选择器 |
| 闹钟呈现 | `Notification` + `setFullScreenIntent` | 锁屏全屏，标准 API |
| 关闭闹钟 | 通知 dismiss / 点击 | 系统通知交互 |
| 设备重启恢复 | `BOOT_COMPLETED` receiver | 重新调度 |
| 权限请求 | `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`（API 31+） | 系统权限对话框 |

## 5. 架构

```
┌─ SettingsFragment（新）──── 全局开关/提前量/铃声/震动
│        │
│        ▼
├─ AlarmSettings（SharedPreferences 包装）
│        │
│        ▼
├─ AlarmScheduler ──► AlarmManager.setAlarmClock
│        ▲                │
│        │                ▼
│   班次生成/修改     ┌─ AlarmReceiver（BroadcastReceiver）
│   设备重启 ──────────►  │  发通知（带声音/震动/全屏 intent）
│                       │  调度下一个闹钟
└─ AlarmBootReceiver ───►┘
```

### 调度策略：只调度最近的下一个

每次只调度"最近的下一个闹钟"（开始提醒或结束提醒中更近的那个），而非一次性注册 90 天的闹钟。

理由：
- 省内存、省电量（90 个 PendingIntent 没必要）
- 设备重启后只需重调一个
- 触发后由 receiver 调度再下一个（已在 receiver 内实现）

代价：每次触发后要重调下一个 —— 已包含在 AlarmReceiver 逻辑里，无额外用户感知。

### 触发点（何时调用 AlarmScheduler.scheduleNext）

1. `ShiftGenerator.generate()` 末尾 —— 班次重新生成后
2. `CalendarViewModel.changeShiftType()` —— 手动改班次后
3. `AlarmBootReceiver` —— 设备重启后
4. `SettingsFragment` 改完全局设置 —— 用户改配置后
5. `ShiftTypeEditDialogFragment` 保存班次后 —— 班次级覆盖变化
6. app 启动（`MainActivity.onCreate`）—— 兜底，防止调度丢失

所有触发点都调用同一个 `scheduleNext()`，内部幂等（先 cancel 再 set）。

## 6. 数据流：调度下一个闹钟

```
AlarmScheduler.scheduleNext():
  1. 读全局设置；若总开关关 → cancel 现有闹钟，return
  2. 查询未来 N 天内（N=90）的所有 ShiftDay + 关联 ShiftType
  3. 对每一天，计算两个候选时刻：
     - 开始提醒 = 班次日期 00:00 + startMinutes - leadMinutes
     - 结束提醒 = 班次日期 00:00 + endMinutes
     （若 endMinutes < startMinutes 表示跨天，结束提醒加一天）
  4. 按班次 alarmEnabled 覆盖规则过滤掉"关闭"的
  5. 取所有候选中 > now 的最早一个
  6. 若无候选 → cancel 现有闹钟，return
  7. cancel 旧 PendingIntent，建新 PendingIntent（带 type=START/END, shiftDayId）
  8. AlarmManager.setAlarmClock(triggerAt, pi)
```

## 7. 数据流：闹钟触发

```
AlarmReceiver.onReceive():
  1. 从 intent 取 type/shiftDayId
  2. 查 ShiftDay + ShiftType（若查不到/已被改 → 直接 scheduleNext() return）
  3. 构建通知：
     - channel: "shift_alarm"（IMPORTANCE_HIGH，带 sound/vibration）
     - title: 班次名 +（开始/结束）
     - fullScreenIntent → MainActivity（携带日期参数，打开当日详情）
  4. NotificationManager.notify(tag=shiftDayId, id=type, notification)
  5. scheduleNext()  // 调度下一个
```

## 8. NotificationChannel 管理

- channel id: `shift_alarm`
- 创建时从 AlarmSettings 读 sound/vibration/importance
- 用户在 SettingsFragment 改了铃声/震动 → 删除旧 channel（`deleteNotificationChannel`）→ 用新配置创建同 id channel
  - 注意：Android 对已存在 channel 的 sound/vibration 不可变，必须删除重建
  - 删除后旧通知会失效，但用户是在改设置时主动操作，可接受
- importance 固定 `IMPORTANCE_HIGH`（保证横幅 + 声音）

## 9. 数据库迁移

`AppDatabase` version 1 → 2

`ShiftType` 新增字段：
```kotlin
val alarmEnabled: Boolean? = null  // null=跟随全局
```

Migration:
```sql
ALTER TABLE shift_types ADD COLUMN alarmEnabled INTEGER  -- Room 用 INTEGER 存 Boolean?
```

`AppDatabase` 当前用 `fallbackToDestructiveMigration()`，按需保留或改为正式 Migration。本设计采用正式 Migration（保留用户已配置的班次数据）。

## 10. 权限

Manifest 增加：
```xml
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

- `SCHEDULE_EXACT_ALARM` + `USE_EXACT_ALARM`：API 31+ setAlarmClock 需要（USE_EXACT_ALARM 在 API 33+ 可免用户授权）
- `POST_NOTIFICATIONS`：API 33+ 通知运行时权限，SettingsFragment 首次启用闹钟时请求
- `RECEIVE_BOOT_COMPLETED`：开机重启恢复闹钟

## 11. 新增/修改文件清单

### 新增
- `app/src/main/java/com/shiftcalendar/alarm/AlarmSettings.kt`
- `app/src/main/java/com/shiftcalendar/alarm/AlarmScheduler.kt`
- `app/src/main/java/com/shiftcalendar/alarm/AlarmReceiver.kt`
- `app/src/main/java/com/shiftcalendar/alarm/AlarmBootReceiver.kt`
- `app/src/main/java/com/shiftcalendar/ui/settings/SettingsFragment.kt`
- `app/src/main/java/com/shiftcalendar/ui/settings/SettingsViewModel.kt`
- `app/src/main/res/layout/fragment_settings.xml`
- `app/src/main/res/drawable/ic_settings.xml`

### 修改
- `app/src/main/java/com/shiftcalendar/data/entity/ShiftType.kt` —— 加 `alarmEnabled`
- `app/src/main/java/com/shiftcalendar/data/database/AppDatabase.kt` —— version 2 + Migration + 去掉 fallbackToDestructiveMigration
- `app/src/main/java/com/shiftcalendar/ui/shifttype/ShiftTypeEditDialogFragment.kt` —— 加"启用闹钟"三态开关
- `app/src/main/res/layout/dialog_shift_type_edit.xml` —— 加开关
- `app/src/main/java/com/shiftcalendar/domain/generator/ShiftGenerator.kt` —— generate() 末尾调 scheduleNext
- `app/src/main/java/com/shiftcalendar/ui/calendar/CalendarViewModel.kt` —— changeShiftType 后调 scheduleNext
- `app/src/main/java/com/shiftcalendar/MainActivity.kt` —— onCreate 兜底调 scheduleNext
- `app/src/main/res/menu/bottom_nav_menu.xml` —— 加"设置"项
- `app/src/main/res/values/strings.xml` —— 闹钟相关文案
- `app/src/main/AndroidManifest.xml` —— 注册 receiver + 权限 + MainActivity intent-filter（全屏 intent）

## 12. 测试策略

- **单元测试**：AlarmScheduler.scheduleNext 的候选计算逻辑（纯函数部分抽出可测）
  - 给定班次列表 + 当前时间，返回下一个应触发的时刻与 type
- **instrumented test**：AlarmSettings 读写、数据库 migration（version 1→2 数据保留）
- 闹钟触发与通知（需真机/模拟器，不强求 CI）
