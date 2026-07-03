# Tasks

- [x] Task 1: 项目初始化与 MIUI X 框架集成
  - [x] 创建 Android 项目，配置 MIUI X 框架依赖
  - [x] 配置 Room 数据库依赖
  - [x] 建立项目包结构（data/domain/ui 分层）
  - [x] 配置主题与全局样式（简约风格：淡灰白背景、深色文字、靛蓝功能色）

- [x] Task 2: 数据层 — Room 数据库与实体定义
  - [x] 定义 ShiftType 实体（id, name, colorTag, startTime, endTime）
  - [x] 定义 ShiftRule 实体（id, name, startDate, cycleDays, shiftSequence）
  - [x] 定义 ShiftDay 实体（id, date, shiftTypeId, isGenerated）
  - [x] 创建 DAO 接口（ShiftTypeDao, ShiftRuleDao, ShiftDayDao）
  - [x] 创建 AppDatabase 类并配置迁移策略

- [x] Task 3: 班次类型管理模块（ShiftType）
  - [x] 实现 ShiftTypeRepository 数据仓库
  - [x] 实现 ShiftTypeViewModel 业务逻辑
  - [x] 构建班次列表页面（RecyclerView，简约列表项布局）
  - [x] 构建班次编辑弹窗/页面（名称输入、颜色选择器、时间选择器）
  - [x] 实现长按删除与确认对话框

- [x] Task 4: 倒班规律配置模块（ShiftRule）
  - [x] 实现 ShiftRuleRepository 数据仓库
  - [x] 实现 ShiftRuleViewModel 业务逻辑
  - [x] 构建规律配置页面（起始日期选择器、周期天数输入、班次序列拖拽排序）
  - [x] 实现实时预览功能（未来 7 天班次计算预览）
  - [x] 实现多规律管理与冲突解决逻辑

- [x] Task 5: 批量生成引擎
  - [x] 实现 ShiftGenerator 算法（根据规律 + 起始日期批量计算班次）
  - [x] 实现生成范围配置（默认 90 天，可调）
  - [x] 实现规律变更时的重新生成流程（确认→清除→重新生成）
  - [x] 异步生成，显示进度指示

- [x] Task 6: 日历视图模块（月视图 + 周视图）
  - [x] 实现自定义 MonthView 组件（7x6 网格，班次标记点显示）
  - [x] 实现自定义 WeekView 组件（7 天详情，班次名称+时间）
  - [x] 实现左右滑动切换月份/周（ViewPager2 + 懒加载）
  - [x] 实现日详情弹出卡片（点击日期展示）
  - [x] 实现月/周视图切换按钮

- [x] Task 7: 主页面整合与导航
  - [x] 构建底部导航栏（日历 / 班次 / 规律 三个 Tab）
  - [x] 整合各模块到主 Activity
  - [x] 实现页面间数据同步（规律变更后自动刷新日历）

- [x] Task 8: 简约 UI 精修与动效
  - [x] 审查所有页面，确保无大面积色块（>40% 屏幕面积）
  - [x] 统一间距与排版规则（8dp 网格系统）
  - [x] 添加极简过渡动画（页面切换 fade、日历滑动）
  - [x] 适配深色模式（`color-scheme` 与 `theme-color`）
  - [x] 适配安全区域（`env(safe-area-inset-*)` 对应 Android window insets）

- [x] Task 9: 测试与验证
  - [x] 单元测试：ShiftGenerator 批量生成算法正确性
  - [x] 单元测试：多规律冲突解决逻辑
  - [x] UI 测试：日历滑动流畅性（无卡顿）
  - [x] 数据持久化测试：重启后数据完整性

# Task Dependencies
- Task 2 依赖 Task 1
- Task 3, Task 4 依赖 Task 2
- Task 5 依赖 Task 2, Task 4
- Task 6 依赖 Task 2, Task 3, Task 5
- Task 7 依赖 Task 6
- Task 8 依赖 Task 7
- Task 9 依赖 Task 8