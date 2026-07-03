# Tasks

- [x] Task 1: 数据层变更 — ShiftType 新增 alarmEnabled 字段
  - [x] ShiftType 实体新增 `alarmEnabled: Boolean?` 字段（默认 null = 启用）
  - [x] AppDatabase 版本升级到 2，保留 destructive migration
  - [x] 更新 ShiftTypeEditDialog 布局，新增 `SwitchCompat` 闹钟开关
  - [x] 更新 ShiftTypeViewModel.saveShiftType 处理新字段
  - [x] 更新 ShiftTypeAdapter 布局，显示闹钟开关状态

- [x] Task 2: 全局闹钟设置存储 — AlarmSettings
  - [x] 创建 `AlarmSettings` 类，基于 SharedPreferences 存储
  - [x] 存储字段：enabled(总开关)、leadMinutes(提前分钟数)、vibrate(震动)、ringtoneUri(铃声URI)
  - [x] 提供默认值：enabled=true, leadMinutes=0, vibrate=true, ringtoneUri=""

- [x] Task 3: 通知渠道 — NotificationHelper
  - [x] 创建 `NotificationHelper` 对象，创建通知渠道（channelId、channelName）
  - [x] 实现 `showAlarmNotification()` 方法：显示班次闹钟通知
  - [x] 实现 `ensureChannel()` 方法：重建渠道（铃声/震动变更后）
  - [x] 通知内容包含班次名称、起止时间

- [x] Task 4: 闹钟调度器 — AlarmScheduler
  - [x] 创建 `AlarmScheduler` 对象
  - [x] 实现 `computeNextAlarm()` 纯函数：给定班次列表、类型映射、now、leadMinutes，返回下一个闹钟
  - [x] 实现 `scheduleNext()` suspend 函数：查库→计算→注册 AlarmManager
  - [x] 实现 `cancel()` 方法：取消已注册闹钟
  - [x] 使用 `setAlarmClock()` 确保 Doze 模式下精确触发
  - [x] 查询范围：未来 90 天

- [x] Task 5: 闹钟广播接收器 — AlarmReceiver + AlarmBootReceiver
  - [x] 创建 `AlarmReceiver`：接收闹钟触发广播 → 弹出通知 → 接力调度下一个闹钟
  - [x] 创建 `AlarmBootReceiver`：接收 BOOT_COMPLETED 广播 → 恢复闹钟调度
  - [x] 在 AndroidManifest 注册两个 Receiver 及所需权限

- [x] Task 6: 设置页面 UI — SettingsFragment + SettingsViewModel
  - [x] 创建 `fragment_settings.xml` 布局：总开关(SwitchCompat)、提前分钟数(下拉)、震动(SwitchCompat)、铃声选择
  - [x] 创建 `SettingsFragment`：绑定 UI 与 ViewModel
  - [x] 创建 `SettingsViewModel`：管理设置读写，变更后触发 reschedule
  - [x] 铃声选择使用系统 `RingtoneManager.ACTION_RINGTONE_PICKER`

- [x] Task 7: 主页面整合 — 底部导航新增设置 Tab
  - [x] 在 `MainActivity` 底部导航新增「设置」Tab（第四个）
  - [x] 在 `loadInitialFragment()` 中注册 SettingsFragment
  - [x] 新增导航图标资源 `ic_settings`
  - [x] 新增底部菜单项 `nav_settings`

- [x] Task 8: 调度触发点接入 — 所有变更点触发 reschedule
  - [x] MainActivity.onCreate()：启动时兜底调度（try-catch 保护）
  - [x] ShiftGenerator.generate()：班次生成后调度（try-catch 保护）
  - [x] CalendarViewModel.changeShiftType()：手动改班次后调度（try-catch 保护）
  - [x] ShiftTypeViewModel.saveShiftType/deleteShiftType()：班次类型变更后调度（try-catch 保护）
  - [x] SettingsViewModel：设置变更后调度（已有）

- [x] Task 9: ProGuard 规则补全
  - [x] 新增 keep 规则：alarm、settings、shifttype、shiftrule、domain、database 包
  - [x] 新增 keep 规则：CalendarViewModel、CalendarData、ShiftDayDetail、CalendarFragment
  - [x] 保留 BroadcastReceiver 成员：AlarmReceiver、AlarmBootReceiver

- [x] Task 10: 权限配置
  - [x] AndroidManifest 声明 POST_NOTIFICATIONS 权限（Android 13+）
  - [x] AndroidManifest 声明 RECEIVE_BOOT_COMPLETED 权限
  - [x] AndroidManifest 声明 SCHEDULE_EXACT_ALARM 权限（Android 12+）
  - [x] AndroidManifest 声明 USE_EXACT_ALARM 权限（Android 13+）

# Task Dependencies
- Task 2 依赖 Task 1（AlarmSettings 需要读取 ShiftType.alarmEnabled）
- Task 3 无依赖，可与 Task 1-2 并行
- Task 4 依赖 Task 1、Task 2（AlarmScheduler 需要读数据库和设置）
- Task 5 依赖 Task 3、Task 4（Receiver 需要 NotificationHelper 和 AlarmScheduler）
- Task 6 依赖 Task 2（SettingsViewModel 需要 AlarmSettings）
- Task 7 依赖 Task 6（MainActivity 需要 SettingsFragment）
- Task 8 依赖 Task 4、Task 6（接入点需要 AlarmScheduler 和 SettingsViewModel）
- Task 9 可与 Task 1-8 并行，最后统一验证
- Task 10 依赖 Task 5（Receiver 需要权限声明）