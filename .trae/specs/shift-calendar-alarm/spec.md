# 闹钟系统 Spec

## Why
倒班工作者需要根据班次时间设置闹钟提醒。当前应用仅展示班次日历，用户需手动在系统闹钟中设置，繁琐且容易遗漏。本功能利用班次已有的 startTime 数据，自动调度闹钟，在班次开始前提醒用户。

## What Changes
- 新增全局闹钟设置页：提前分钟数、震动开关、铃声选择
- 新增班次级闹钟开关：每个 ShiftType 可独立启用/关闭闹钟
- 新增闹钟调度器：自动查询未来班次，计算并注册最近的下一个闹钟
- 新增闹钟广播接收器：触发时弹出通知，并接力调度下一个闹钟
- 新增开机广播接收器：设备重启后恢复闹钟调度
- 新增通知渠道：班次闹钟专用通知
- ShiftType 实体新增 `alarmEnabled` 字段（**BREAKING**：数据库版本 1→2，使用 destructive migration）
- 新增底部导航第四个 Tab「设置」
- ProGuard 规则补全，覆盖所有新增包

## Impact
- Affected specs: shift-calendar-app（新增闹钟子模块）
- Affected code: ShiftType 实体、AppDatabase、ShiftTypeEditDialog、MainActivity、AndroidManifest、proguard-rules.pro、build.gradle.kts

## ADDED Requirements

### Requirement: 全局闹钟设置
系统 SHALL 提供全局闹钟设置页面，包含总开关、提前分钟数、震动开关、铃声选择。

#### Scenario: 设置提前分钟数
- **WHEN** 用户在设置页选择提前分钟数（0/5/10/15/30/60 分钟）
- **THEN** 系统保存设置，并立即重新调度闹钟

#### Scenario: 切换震动
- **WHEN** 用户切换震动开关
- **THEN** 系统保存设置，闹钟触发时按新设置震动

#### Scenario: 选择铃声
- **WHEN** 用户点击铃声选择，系统调起系统铃声选择器
- **THEN** 用户选择铃声后保存 URI，闹钟触发时使用该铃声

#### Scenario: 关闭总开关
- **WHEN** 用户关闭总开关
- **THEN** 系统取消所有已注册的闹钟

### Requirement: 班次级闹钟开关
系统 SHALL 在每个班次类型编辑弹窗中提供闹钟开关，允许用户独立控制该班次是否需要闹钟。

#### Scenario: 关闭某班次的闹钟
- **WHEN** 用户在班次编辑弹窗中关闭"闹钟提醒"开关
- **THEN** 该班次类型不再参与闹钟计算，系统重新调度闹钟

#### Scenario: 默认行为
- **WHEN** 新创建班次类型或升级旧数据
- **THEN** 闹钟开关默认为开启（null 或 true 均视为开启）

### Requirement: 闹钟自动调度
系统 SHALL 自动查询未来 90 天的班次数据，计算出最近的下一个需要提醒的班次，并用 AlarmManager 注册精确闹钟。

#### Scenario: 计算下一个闹钟
- **WHEN** 系统触发调度（启动/设置变更/班次变更/设备重启）
- **THEN** 系统查询未来 90 天班次，过滤 alarmEnabled=false 的班次类型
- **AND** 对每个候选计算 `shiftDate + startTime - leadMinutes`
- **AND** 取 >now 的最早时间作为下一个闹钟，用 `setAlarmClock()` 注册

#### Scenario: 无候选闹钟
- **WHEN** 未来 90 天内无符合条件的班次
- **THEN** 系统取消所有已注册的闹钟，不注册新闹钟

#### Scenario: 调度幂等
- **WHEN** 系统多次触发调度
- **THEN** 每次先 cancel 旧闹钟，再 set 新闹钟，确保始终只有一个闹钟

### Requirement: 闹钟触发与通知
系统 SHALL 在闹钟触发时弹出通知，内容包含班次名称、班次时间，并接力调度下一个闹钟。

#### Scenario: 闹钟触发
- **WHEN** AlarmManager 触发闹钟广播
- **THEN** 系统弹出通知，显示班次名称和起止时间
- **AND** 通知使用用户设置的铃声和震动模式
- **AND** 系统立即调度下一个闹钟（接力）

#### Scenario: 通知点击
- **WHEN** 用户点击闹钟通知
- **THEN** 系统打开应用主页面

### Requirement: 设备重启恢复
系统 SHALL 在设备重启后自动恢复闹钟调度。

#### Scenario: 开机广播
- **WHEN** 设备完成开机
- **THEN** 系统接收 BOOT_COMPLETED 广播，重新调度闹钟

### Requirement: 防崩溃保护
系统 SHALL 确保闹钟调度失败不影响应用正常使用。

#### Scenario: 调度异常
- **WHEN** 闹钟调度过程中发生异常（数据库未就绪、权限不足等）
- **THEN** 系统静默捕获异常，不阻塞当前操作，不导致应用崩溃

### Requirement: 简约 UI 风格
系统 SHALL 保持设置页与其他页面一致的简约风格。

#### Scenario: 设置页 UI
- **WHEN** 用户进入设置页
- **THEN** 页面使用 SwitchCompat 开关、下拉菜单、简洁文字，无大面积色块，与整体风格一致