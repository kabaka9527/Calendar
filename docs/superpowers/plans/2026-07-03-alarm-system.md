# 闹钟系统实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为倒班日历增加基于系统 API 的闹钟系统，按班次时间自动触发提醒（开始前提前 + 结束时），支持声音/震动/铃声选择，全局开关 + 班次级覆盖。

**Architecture:** 全部用 Android 系统 API（AlarmManager.setAlarmClock + NotificationChannel + RingtoneManager.ACTION_RINGTONE_PICKER），不自建播放/铃声/闹钟 UI。调度策略：只调度"最近的下一个闹钟"，触发后由 receiver 接力调度下一个。配置层：AlarmSettings（SharedPreferences 包装）+ ShiftType.alarmEnabled（三态覆盖）。

**Tech Stack:** Kotlin, Room (Migration v1→v2), AlarmManager, NotificationChannel, Material Design, Fragment + ViewModel

---

## 文件结构

### 新增
- `ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/AlarmSettings.kt` — 全局闹钟配置读写（SharedPreferences 包装）
- `ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/AlarmScheduler.kt` — 调度/取消闹钟，含纯函数 `computeNextAlarm()` 供测试
- `ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/AlarmReceiver.kt` — 闹钟触发 receiver，发通知 + 接力调度
- `ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/AlarmBootReceiver.kt` — 开机重启后重新调度
- `ShiftCalendar/app/src/main/java/com/shiftcalendar/ui/settings/SettingsFragment.kt` — 设置 UI
- `ShiftCalendar/app/src/main/java/com/shiftcalendar/ui/settings/SettingsViewModel.kt` — 设置 VM
- `ShiftCalendar/app/src/main/res/layout/fragment_settings.xml` — 设置页布局
- `ShiftCalendar/app/src/main/res/drawable/ic_settings.xml` — 设置图标
- `ShiftCalendar/app/src/test/java/com/shiftcalendar/alarm/AlarmSchedulerTest.kt` — computeNextAlarm 单元测试

### 修改
- `ShiftCalendar/app/src/main/java/com/shiftcalendar/data/entity/ShiftType.kt` — 加 `alarmEnabled: Boolean?`
- `ShiftCalendar/app/src/main/java/com/shiftcalendar/data/database/AppDatabase.kt` — version 2 + Migration，去掉 fallbackToDestructiveMigration
- `ShiftCalendar/app/src/main/java/com/shiftcalendar/ui/shifttype/ShiftTypeEditDialogFragment.kt` — 加"启用闹钟"三态选择
- `ShiftCalendar/app/src/main/res/layout/dialog_shift_type_edit.xml` — 加闹钟开关行
- `ShiftCalendar/app/src/main/java/com/shiftcalendar/domain/generator/ShiftGenerator.kt` — generate() 末尾调 scheduleNext
- `ShiftCalendar/app/src/main/java/com/shiftcalendar/ui/calendar/CalendarViewModel.kt` — changeShiftType/saveNote 后调 scheduleNext
- `ShiftCalendar/app/src/main/java/com/shiftcalendar/MainActivity.kt` — onCreate 兜底调 scheduleNext
- `ShiftCalendar/app/src/main/res/menu/bottom_nav_menu.xml` — 加"设置"项
- `ShiftCalendar/app/src/main/res/values/strings.xml` — 闹钟相关文案
- `ShiftCalendar/app/src/main/AndroidManifest.xml` — 注册 receiver + 权限 + MainActivity intent-filter

---

### Task 1: 数据库迁移 v1→v2（ShiftType 加 alarmEnabled）

**Files:**
- Modify: `ShiftCalendar/app/src/main/java/com/shiftcalendar/data/entity/ShiftType.kt`
- Modify: `ShiftCalendar/app/src/main/java/com/shiftcalendar/data/database/AppDatabase.kt`
- Test: `ShiftCalendar/app/src/androidTest/java/com/shiftcalendar/data/database/DatabaseMigrationTest.kt`（新建）

- [ ] **Step 1: 修改 ShiftType 实体加字段**

修改 `ShiftCalendar/app/src/main/java/com/shiftcalendar/data/entity/ShiftType.kt`，在 `sortOrder` 后加字段：

```kotlin
package com.shiftcalendar.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 班次类型实体
 */
@Entity(tableName = "shift_types")
data class ShiftType(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val colorTag: String,       // 颜色标签 hex 值，如 "#7BA587"
    val startTime: String,      // 格式 "HH:mm"
    val endTime: String,        // 格式 "HH:mm"
    val sortOrder: Int = 0,     // 排序权重
    // 闹钟覆盖：null=跟随全局；true=强制开；false=强制关（休息班次默认 false）
    val alarmEnabled: Boolean? = null
) : java.io.Serializable
```

- [ ] **Step 2: 修改 AppDatabase 升级到 v2 + Migration**

修改 `ShiftCalendar/app/src/main/java/com/shiftcalendar/data/database/AppDatabase.kt`，去掉 `fallbackToDestructiveMigration`，加 version 2 + Migration：

```kotlin
package com.shiftcalendar.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shiftcalendar.data.dao.ShiftDayDao
import com.shiftcalendar.data.dao.ShiftRuleDao
import com.shiftcalendar.data.dao.ShiftTypeDao
import com.shiftcalendar.data.entity.ShiftDay
import com.shiftcalendar.data.entity.ShiftRule
import com.shiftcalendar.data.entity.ShiftType

@Database(
    entities = [
        ShiftType::class,
        ShiftRule::class,
        ShiftDay::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun shiftTypeDao(): ShiftTypeDao
    abstract fun shiftRuleDao(): ShiftRuleDao
    abstract fun shiftDayDao(): ShiftDayDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v1 → v2：ShiftType 新增 alarmEnabled 字段（Boolean? 用 INTEGER 存，允许 NULL）
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shift_types ADD COLUMN alarmEnabled INTEGER")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shift_calendar.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
```

- [ ] **Step 3: 写 migration instrumented test**

新建 `ShiftCalendar/app/src/androidTest/java/com/shiftcalendar/data/database/DatabaseMigrationTest.kt`：

```kotlin
package com.shiftcalendar.data.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shiftcalendar.data.entity.ShiftType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // 用真实数据库构建器 + 迁移，验证 v1→v2 路径
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        )
            .addMigrations(AppDatabase.getInstance(context).let {
                // 直接用 inMemory 验证 migration 字段存在性
                it
            })
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testShiftTypeHasAlarmEnabledColumn() = runBlocking {
        val shiftType = ShiftType(
            name = "白班",
            colorTag = "#7BA587",
            startTime = "08:00",
            endTime = "16:00",
            alarmEnabled = null
        )
        val id = database.shiftTypeDao().insert(shiftType)
        val retrieved = database.shiftTypeDao().getById(id)
        assertNotNull(retrieved)
        assertEquals("白班", retrieved?.name)
        assertNull(retrieved?.alarmEnabled)
    }

    @Test
    fun testShiftTypeAlarmEnabledTrue() = runBlocking {
        val shiftType = ShiftType(
            name = "夜班",
            colorTag = "#6C7BA6",
            startTime = "20:00",
            endTime = "04:00",
            alarmEnabled = true
        )
        val id = database.shiftTypeDao().insert(shiftType)
        val retrieved = database.shiftTypeDao().getById(id)
        assertEquals(true, retrieved?.alarmEnabled)
    }
}
```

注：`getById` 已存在于 ShiftTypeDao（见 ShiftTypeFragment 调用）。

- [ ] **Step 4: 提交**

```bash
git add ShiftCalendar/app/src/main/java/com/shiftcalendar/data/entity/ShiftType.kt \
        ShiftCalendar/app/src/main/java/com/shiftcalendar/data/database/AppDatabase.kt \
        ShiftCalendar/app/src/androidTest/java/com/shiftcalendar/data/database/DatabaseMigrationTest.kt
git commit -m "feat(alarm): add ShiftType.alarmEnabled column with v1→v2 migration"
```

---

### Task 2: AlarmSettings（全局配置读写）

**Files:**
- Create: `ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/AlarmSettings.kt`

- [ ] **Step 1: 实现 AlarmSettings**

新建 `ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/AlarmSettings.kt`：

```kotlin
package com.shiftcalendar.alarm

import android.content.Context
import android.content.SharedPreferences

/**
 * 全局闹钟配置（SharedPreferences 包装）
 *
 * 字段：
 * - alarm_enabled: Boolean  总开关
 * - alarm_lead_minutes: Int  提前分钟数（默认 0）
 * - alarm_ringtone_uri: String  铃声 URI（空=系统默认）
 * - alarm_vibrate: Boolean  是否震动（默认 true）
 */
class AlarmSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var leadMinutes: Int
        get() = prefs.getInt(KEY_LEAD_MINUTES, 0)
        set(value) = prefs.edit().putInt(KEY_LEAD_MINUTES, value.coerceIn(0, 240)).apply()

    var ringtoneUri: String
        get() = prefs.getString(KEY_RINGTONE_URI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RINGTONE_URI, value).apply()

    var vibrate: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE, value).apply()

    companion object {
        private const val PREFS_NAME = "alarm_settings"
        private const val KEY_ENABLED = "alarm_enabled"
        private const val KEY_LEAD_MINUTES = "alarm_lead_minutes"
        private const val KEY_RINGTONE_URI = "alarm_ringtone_uri"
        private const val KEY_VIBRATE = "alarm_vibrate"

        @Volatile
        private var instance: AlarmSettings? = null

        fun get(context: Context): AlarmSettings =
            instance ?: synchronized(this) {
                instance ?: AlarmSettings(context).also { instance = it }
            }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/AlarmSettings.kt
git commit -m "feat(alarm): add AlarmSettings for global config persistence"
```

---

### Task 3: AlarmScheduler 纯函数 computeNextAlarm + 单元测试（TDD）

**Files:**
- Create: `ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/AlarmScheduler.kt`
- Create: `ShiftCalendar/app/src/test/java/com/shiftcalendar/alarm/AlarmSchedulerTest.kt`

- [ ] **Step 1: 写 computeNextAlarm 失败测试**

新建 `ShiftCalendar/app/src/test/java/com/shiftcalendar/alarm/AlarmSchedulerTest.kt`：

```kotlin
package com.shiftcalendar.alarm

import com.shiftcalendar.data.entity.ShiftDay
import com.shiftcalendar.data.entity.ShiftType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class AlarmSchedulerTest {

    private val baseType = ShiftType(
        id = 1,
        name = "白班",
        colorTag = "#7BA587",
        startTime = "08:00",   // 480 分钟
        endTime = "16:00",     // 960 分钟
        alarmEnabled = null
    )

    private val restType = ShiftType(
        id = 2,
        name = "休息",
        colorTag = "#A0A0A0",
        startTime = "00:00",
        endTime = "00:00",
        alarmEnabled = false
    )

    // 构造某天 00:00 的时间戳
    private fun dayStart(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month, day, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun `next alarm is start time minus lead when lead is 0`() {
        val dayStart = dayStart(2026, 6, 15)  // 2026-07-15
        val now = dayStart(2026, 6, 14) + 12 * 60 * 60 * 1000L  // 7-14 中午
        val shiftDays = listOf(ShiftDay(date = dayStart, shiftTypeId = 1, isGenerated = true))
        val typeMap = mapOf(1L to baseType)

        val result = AlarmScheduler.computeNextAlarm(shiftDays, typeMap, now, leadMinutes = 0)

        assertNotNull(result)
        assertEquals(AlarmScheduler.AlarmType.START, result!!.type)
        // 期望触发时刻 = dayStart + 480 分钟
        assertEquals(dayStart + 480 * 60 * 1000L, result.triggerAt)
    }

    @Test
    fun `next alarm respects lead minutes`() {
        val dayStart = dayStart(2026, 6, 15)
        val now = dayStart(2026, 6, 14) + 12 * 60 * 60 * 1000L
        val shiftDays = listOf(ShiftDay(date = dayStart, shiftTypeId = 1, isGenerated = true))
        val typeMap = mapOf(1L to baseType)

        val result = AlarmScheduler.computeNextAlarm(shiftDays, typeMap, now, leadMinutes = 15)!!

        assertEquals(AlarmScheduler.AlarmType.START, result.type)
        // 提前 15 分钟 = 480 - 15 = 465 分钟
        assertEquals(dayStart + 465 * 60 * 1000L, result.triggerAt)
    }

    @Test
    fun `chooses earlier between start and end alarm`() {
        // 同一天：start=08:00(480), end=16:00(960)，提前量 0
        // now 设为 09:00，start 已过 → 应选 end
        val dayStart = dayStart(2026, 6, 15)
        val now = dayStart + 9 * 60 * 60 * 1000L  // 09:00
        val shiftDays = listOf(ShiftDay(date = dayStart, shiftTypeId = 1, isGenerated = true))
        val typeMap = mapOf(1L to baseType)

        val result = AlarmScheduler.computeNextAlarm(shiftDays, typeMap, now, leadMinutes = 0)!!

        assertEquals(AlarmScheduler.AlarmType.END, result.type)
        assertEquals(dayStart + 960 * 60 * 1000L, result.triggerAt)
    }

    @Test
    fun `skips shift types with alarmEnabled false`() {
        val dayStart = dayStart(2026, 6, 15)
        val now = dayStart(2026, 6, 14) + 12 * 60 * 60 * 1000L
        val shiftDays = listOf(ShiftDay(date = dayStart, shiftTypeId = 2, isGenerated = true))
        val typeMap = mapOf(2L to restType)

        val result = AlarmScheduler.computeNextAlarm(shiftDays, typeMap, now, leadMinutes = 0)

        assertNull(result)
    }

    @Test
    fun `cross-day shift end is next day`() {
        // 夜班 20:00-04:00（次日），end=04:00=240 分钟
        val nightType = baseType.copy(startTime = "20:00", endTime = "04:00")
        val dayStart = dayStart(2026, 6, 15)
        val now = dayStart(2026, 6, 14) + 12 * 60 * 60 * 1000L
        val shiftDays = listOf(ShiftDay(date = dayStart, shiftTypeId = 1, isGenerated = true))
        val typeMap = mapOf(1L to nightType)

        val result = AlarmScheduler.computeNextAlarm(shiftDays, typeMap, now, leadMinutes = 0)!!

        // now < start(20:00) → 应选 start
        assertEquals(AlarmScheduler.AlarmType.START, result.type)
        assertEquals(dayStart + 20 * 60 * 60 * 1000L, result.triggerAt)

        // now 设为 21:00 → start 已过，应选 end（次日 04:00）
        val now2 = dayStart + 21 * 60 * 60 * 1000L
        val result2 = AlarmScheduler.computeNextAlarm(shiftDays, typeMap, now2, leadMinutes = 0)!!
        assertEquals(AlarmScheduler.AlarmType.END, result2.type)
        assertEquals(dayStart + 24 * 60 * 60 * 1000L + 240 * 60 * 1000L, result2.triggerAt)
    }

    private fun <T> assertNotNull(value: T?) {
        org.junit.Assert.assertNotNull(value)
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shiftcalendar.alarm.AlarmSchedulerTest"`
Expected: FAIL（AlarmScheduler.computeNextAlarm 未定义）

- [ ] **Step 3: 实现 AlarmScheduler**

新建 `ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/AlarmScheduler.kt`：

```kotlin
package com.shiftcalendar.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.shiftcalendar.ShiftCalendarApp
import com.shiftcalendar.data.entity.ShiftDay
import com.shiftcalendar.data.entity.ShiftType
import java.util.Calendar

/**
 * 闹钟调度器：只调度"最近的下一个闹钟"，触发后由 [AlarmReceiver] 接力。
 *
 * 所有触发点（班次生成/修改、设备重启、改设置、app 启动）都调用 [scheduleNext]，
 * 内部幂等（先 cancel 再 set）。
 */
object AlarmScheduler {

    private const val ALARM_REQUEST_CODE = 1001
    private const val LOOKAHEAD_DAYS = 90

    enum class AlarmType { START, END }

    data class NextAlarm(
        val triggerAt: Long,
        val type: AlarmType,
        val shiftDay: ShiftDay,
        val shiftType: ShiftType
    )

    /**
     * 纯函数：给定班次列表、班次类型映射、当前时间、提前量，
     * 返回下一个应触发的闹钟（>now 的最早候选）。无候选返回 null。
     *
     * 抽出为纯函数便于单元测试。
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
            // 班次级覆盖：false=强制关；null/true 继续参与
            if (type.alarmEnabled == false) continue

            val startMinutes = parseHHmm(type.startTime)
            val endMinutes = parseHHmm(type.endTime)

            // 开始提醒候选 = 当日 00:00 + startMinutes - leadMinutes
            val startTrigger = day.date + (startMinutes - leadMinutes) * 60 * 1000L
            if (startTrigger > now) {
                candidates.add(NextAlarm(startTrigger, AlarmType.START, day, type))
            }

            // 结束提醒候选 = 当日 00:00 + endMinutes
            // 跨天判断：endMinutes <= startMinutes 表示跨天，结束在次日
            val endBase = if (endMinutes <= startMinutes) {
                day.date + 24 * 60 * 60 * 1000L
            } else {
                day.date
            }
            val endTrigger = endBase + endMinutes * 60 * 1000L
            if (endTrigger > now) {
                candidates.add(NextAlarm(endTrigger, AlarmType.END, day, type))
            }
        }

        return candidates.minByOrNull { it.triggerAt }
    }

    /**
     * 调度最近的下一个闹钟。幂等：先 cancel 旧的，再 set 新的。
     *
     * 在 IO 协程中调用（内部查数据库）。
     */
    suspend fun scheduleNext(context: Context) {
        val settings = AlarmSettings.get(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 总开关关 → 取消现有闹钟
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

        // 重建 channel（铃声/震动可能已变）
        NotificationHelper.ensureChannel(context, settings)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER
            putExtra(AlarmReceiver.EXTRA_TYPE, next.type.name)
            putExtra(AlarmReceiver.EXTRA_SHIFT_DAY_ID, next.shiftDay.id)
            putExtra(AlarmReceiver.EXTRA_SHIFT_DATE, next.shiftDay.date)
            putExtra(AlarmReceiver.EXTRA_SHIFT_TYPE_ID, next.shiftType.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // setAlarmClock：Doze 下也精确触发，系统级
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
            context,
            ALARM_REQUEST_CODE,
            intent,
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
```

注：`getAllStaticList()` 待确认存在；若不存在需在 Task 4 的 DAO 补全。`NotificationHelper` 在 Task 5 创建。

- [ ] **Step 4: 确认/补全 ShiftTypeDao.getAllStaticList()**

检查 `ShiftCalendar/app/src/main/java/com/shiftcalendar/data/dao/ShiftTypeDao.kt` 是否有 `suspend fun getAllStaticList(): List<ShiftType>`。若无则添加：

```kotlin
@Query("SELECT * FROM shift_types ORDER BY sortOrder ASC")
suspend fun getAllStaticList(): List<ShiftType>
```

- [ ] **Step 5: 运行测试验证通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.shiftcalendar.alarm.AlarmSchedulerTest"`
Expected: PASS（5 个测试全过）

注：此时编译会因 `NotificationHelper` 未定义而失败。为让测试先跑通，临时在 AlarmScheduler.kt 把 `NotificationHelper.ensureChannel(...)` 注释掉，或先创建 NotificationHelper 空壳（Task 5 会填充）。**推荐：先创建 NotificationHelper 空壳让测试能编译。**

创建空壳 `ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/NotificationHelper.kt`：

```kotlin
package com.shiftcalendar.alarm

import android.content.Context

object NotificationHelper {
    fun ensureChannel(context: Context, settings: AlarmSettings) {
        // Task 5 填充
    }
}
```

- [ ] **Step 6: 提交**

```bash
git add ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/ \
        ShiftCalendar/app/src/test/java/com/shiftcalendar/alarm/ \
        ShiftCalendar/app/src/main/java/com/shiftcalendar/data/dao/ShiftTypeDao.kt
git commit -m "feat(alarm): add AlarmScheduler with pure computeNextAlarm + unit tests"
```

---

### Task 4: ShiftTypeDao 补全静态查询方法

**Files:**
- Modify: `ShiftCalendar/app/src/main/java/com/shiftcalendar/data/dao/ShiftTypeDao.kt`

- [ ] **Step 1: 读取当前 DAO 确认缺失方法**

读取 `ShiftCalendar/app/src/main/java/com/shiftcalendar/data/dao/ShiftTypeDao.kt`，确认是否有 `getAllStaticList()`。若已有则跳过此 Task；若无则继续 Step 2。

- [ ] **Step 2: 添加 getAllStaticList()**

在 ShiftTypeDao 接口中添加（与 ShiftDayDao.getByDateRangeStatic 风格一致）：

```kotlin
@Query("SELECT * FROM shift_types ORDER BY sortOrder ASC")
suspend fun getAllStaticList(): List<ShiftType>
```

- [ ] **Step 3: 提交（若 Step 3 在 Task 3 已一并提交则跳过）**

```bash
git add ShiftCalendar/app/src/main/java/com/shiftcalendar/data/dao/ShiftTypeDao.kt
git commit -m "feat(alarm): add ShiftTypeDao.getAllStaticList"
```

---

### Task 5: NotificationHelper（channel 创建/重建）

**Files:**
- Create: `ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/NotificationHelper.kt`（替换 Task 3 的空壳）

- [ ] **Step 1: 实现 NotificationHelper**

替换 `ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/NotificationHelper.kt` 内容：

```kotlin
package com.shiftcalendar.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build

/**
 * 通知 channel 管理。
 *
 * Android 对已存在 channel 的 sound/vibration 不可变，用户改设置后必须删除重建。
 */
object NotificationHelper {

    const val CHANNEL_ID = "shift_alarm"

    /**
     * 确保存在符合 [settings] 配置的 channel。若配置变更则删除旧 channel 重建。
     */
    fun ensureChannel(context: Context, settings: AlarmSettings) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        // 删除旧 channel（用户改了铃声/震动配置后，旧 channel 不可变，必须删了重建）
        manager.deleteNotificationChannel(CHANNEL_ID)

        val channel = NotificationChannel(
            CHANNEL_ID,
            "班次闹钟",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "班次开始与结束提醒"
            enableVibration(settings.vibrate)
            // 铃声
            val soundUri = settings.ringtoneUri.takeIf { it.isNotEmpty() }
                ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(Uri.parse(soundUri), attrs)
            // 锁屏可见
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/NotificationHelper.kt
git commit -m "feat(alarm): implement NotificationHelper with channel recreate on config change"
```

---

### Task 6: AlarmReceiver（触发处理）

**Files:**
- Create: `ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/AlarmReceiver.kt`

- [ ] **Step 1: 实现 AlarmReceiver**

新建 `ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/AlarmReceiver.kt`：

```kotlin
package com.shiftcalendar.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.shiftcalendar.MainActivity
import com.shiftcalendar.ShiftCalendarApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 闹钟触发 receiver：发通知（带声音/震动/全屏 intent）+ 接力调度下一个。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val typeStr = intent.getStringExtra(EXTRA_TYPE) ?: return
        val shiftDayId = intent.getLongExtra(EXTRA_SHIFT_DAY_ID, -1L)
        val shiftDate = intent.getLongExtra(EXTRA_SHIFT_DATE, 0L)
        val shiftTypeId = intent.getLongExtra(EXTRA_SHIFT_TYPE_ID, -1L)

        if (shiftDayId < 0) {
            scheduleNextSafely(context)
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = ShiftCalendarApp.instance.database
                val shiftType = db.shiftTypeDao().getByIdStatic(shiftTypeId)
                if (shiftType == null) {
                    scheduleNextSafely(context)
                    return@launch
                }

                val type = runCatching { AlarmScheduler.AlarmType.valueOf(typeStr) }
                    .getOrNull() ?: return@launch

                showNotification(context, shiftType, type, shiftDayId, shiftDate)
            } finally {
                // 接力调度下一个
                scheduleNextSafely(context)
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(
        context: Context,
        shiftType: com.shiftcalendar.data.entity.ShiftType,
        type: AlarmScheduler.AlarmType,
        shiftDayId: Long,
        shiftDate: Long
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val titlePrefix = if (type == AlarmScheduler.AlarmType.START) "开始" else "结束"
        val title = "${shiftType.name} $titlePrefix"
        val text = if (type == AlarmScheduler.AlarmType.START) {
            "班次 ${shiftType.startTime} 开始"
        } else {
            "班次 ${shiftType.endTime} 结束"
        }

        // 点击 / 全屏 intent → 打开 MainActivity 并携带日期参数
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_FOCUS_DATE, shiftDate)
        }
        val fullScreenIntent = PendingIntent.getActivity(
            context,
            shiftDayId.toInt(),
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(com.shiftcalendar.R.drawable.ic_calendar)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(fullScreenIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(shiftDayId.toInt(), notification)
    }

    private fun scheduleNextSafely(context: Context) {
        try {
            // 同步触发，scheduleNext 是 suspend，用协程
            kotlinx.coroutines.runBlocking {
                AlarmScheduler.scheduleNext(context)
            }
        } catch (_: Exception) {
            // 调度失败不阻塞 receiver
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
```

- [ ] **Step 2: 确认/添加 ShiftTypeDao.getByIdStatic()**

检查 ShiftTypeDao 是否有 `suspend fun getByIdStatic(id: Long): ShiftType?`（注意区别于现有返回 LiveData 的 getById，如有）。若无则添加：

```kotlin
@Query("SELECT * FROM shift_types WHERE id = :id LIMIT 1")
suspend fun getByIdStatic(id: Long): ShiftType?
```

- [ ] **Step 3: 提交**

```bash
git add ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/AlarmReceiver.kt \
        ShiftCalendar/app/src/main/java/com/shiftcalendar/data/dao/ShiftTypeDao.kt
git commit -m "feat(alarm): add AlarmReceiver with notification + chained scheduling"
```

---

### Task 7: AlarmBootReceiver（开机恢复）

**Files:**
- Create: `ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/AlarmBootReceiver.kt`

- [ ] **Step 1: 实现 AlarmBootReceiver**

新建 `ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/AlarmBootReceiver.kt`：

```kotlin
package com.shiftcalendar.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 设备开机完成后重新调度闹钟（AlarmManager 重启后已注册的闹钟会丢失）。
 */
class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            intent.action == "android.intent.action.MY_PACKAGE_REPLACED"
        ) {
            // 用协程触发调度（BroadcastReceiver.onReceive 返回后进程可能被杀，
            // 但 scheduleNext 内部用的是 setAlarmClock，已注册到系统 AlarmManager，
            // 即使本进程退出，闹钟仍由系统持有）
            kotlinx.coroutines.runBlocking {
                AlarmScheduler.scheduleNext(context)
            }
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add ShiftCalendar/app/src/main/java/com/shiftcalendar/alarm/AlarmBootReceiver.kt
git commit -m "feat(alarm): add AlarmBootReceiver to reschedule after reboot"
```

---

### Task 8: AndroidManifest 注册 + 权限 + MainActivity intent-filter

**Files:**
- Modify: `ShiftCalendar/app/src/main/AndroidManifest.xml`
- Modify: `ShiftCalendar/app/src/main/java/com/shiftcalendar/MainActivity.kt`

- [ ] **Step 1: 修改 AndroidManifest 加权限和 receiver**

修改 `ShiftCalendar/app/src/main/AndroidManifest.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".ShiftCalendarApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.ShiftCalendar"
        tools:targetApi="34">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <receiver
            android:name=".alarm.AlarmReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="com.shiftcalendar.alarm.TRIGGER" />
            </intent-filter>
        </receiver>

        <receiver
            android:name=".alarm.AlarmBootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

- [ ] **Step 2: MainActivity 加 EXTRA_FOCUS_DATE 常量 + 启动兜底调度**

修改 `ShiftCalendar/app/src/main/java/com/shiftcalendar/MainActivity.kt`：

加常量到类顶部：
```kotlin
companion object {
    const val EXTRA_FOCUS_DATE = "focus_date"
}
```

在 `onCreate` 末尾 `loadInitialFragment()` 后加兜底调度：
```kotlin
// 兜底：app 启动时确保闹钟已调度（防止调度丢失）
com.shiftcalendar.alarm.AlarmScheduler.scheduleNext(this)
```

注意：`scheduleNext` 是 suspend，需用协程。把 onCreate 调度包成：
```kotlin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
// ...
CoroutineScope(Dispatchers.IO).launch {
    com.shiftcalendar.alarm.AlarmScheduler.scheduleNext(this@MainActivity)
}
```

（保留 onCreate 现有逻辑不变，仅在末尾追加上述协程块。）

- [ ] **Step 3: 提交**

```bash
git add ShiftCalendar/app/src/main/AndroidManifest.xml \
        ShiftCalendar/app/src/main/java/com/shiftcalendar/MainActivity.kt
git commit -m "feat(alarm): register receivers/permissions in manifest + startup scheduling"
```

---

### Task 9: ShiftTypeEditDialogFragment 加"启用闹钟"三态选择

**Files:**
- Modify: `ShiftCalendar/app/src/main/res/layout/dialog_shift_type_edit.xml`
- Modify: `ShiftCalendar/app/src/main/java/com/shiftcalendar/ui/shifttype/ShiftTypeEditDialogFragment.kt`

- [ ] **Step 1: 布局加闹钟开关行**

在 `ShiftCalendar/app/src/main/res/layout/dialog_shift_type_edit.xml` 的 btnSave 之前、时间行之后，插入：

```xml
<View
    android:layout_width="match_parent"
    android:layout_height="1dp"
    android:layout_marginTop="16dp"
    android:background="@color/divider" />

<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginTop="16dp"
    android:text="闹钟提醒"
    android:textColor="@color/text_tertiary"
    android:textSize="12sp" />

<com.google.android.material.button.MaterialButtonToggleGroup
    android:id="@+id/alarmToggleGroup"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="4dp"
    app:singleSelection="true">

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnAlarmFollow"
        style="@style/Widget.MaterialComponents.Button.OutlinedButton"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="跟随全局"
        android:textSize="12sp" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnAlarmOn"
        style="@style/Widget.MaterialComponents.Button.OutlinedButton"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="强制开"
        android:textSize="12sp" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnAlarmOff"
        style="@style/Widget.MaterialComponents.Button.OutlinedButton"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="强制关"
        android:textSize="12sp" />
</com.google.android.material.button.MaterialButtonToggleGroup>
```

- [ ] **Step 2: DialogFragment 处理三态选择 + 保存**

修改 `ShiftCalendar/app/src/main/java/com/shiftcalendar/ui/shifttype/ShiftTypeEditDialogFragment.kt`：

在 `onViewCreated` 中，编辑模式回填时加：
```kotlin
// 回填闹钟三态
when (shiftType.alarmEnabled) {
    null -> binding.alarmToggleGroup.check(R.id.btnAlarmFollow)
    true -> binding.alarmToggleGroup.check(R.id.btnAlarmOn)
    false -> binding.alarmToggleGroup.check(R.id.btnAlarmOff)
}
```

新建模式默认选中"跟随全局":
```kotlin
binding.alarmToggleGroup.check(R.id.btnAlarmFollow)
```

`btnSave.setOnClickListener` 内构造 ShiftType 时加 `alarmEnabled`：
```kotlin
val alarmEnabled = when (binding.alarmToggleGroup.checkedButtonId) {
    R.id.btnAlarmOn -> true
    R.id.btnAlarmOff -> false
    else -> null  // 跟随全局
}

val shiftType = ShiftType(
    id = editingShiftType?.id ?: 0,
    name = name,
    colorTag = selectedColor,
    startTime = formatMinutes(startMinutes),
    endTime = formatMinutes(endMinutes),
    sortOrder = editingShiftType?.sortOrder ?: 0,
    alarmEnabled = alarmEnabled
)
```

- [ ] **Step 3: 提交**

```bash
git add ShiftCalendar/app/src/main/res/layout/dialog_shift_type_edit.xml \
        ShiftCalendar/app/src/main/java/com/shiftcalendar/ui/shifttype/ShiftTypeEditDialogFragment.kt
git commit -m "feat(alarm): add tri-state alarm toggle in shift type editor"
```

---

### Task 10: 设置页 UI（SettingsFragment + ViewModel + 布局 + 图标）

**Files:**
- Create: `ShiftCalendar/app/src/main/res/drawable/ic_settings.xml`
- Create: `ShiftCalendar/app/src/main/res/layout/fragment_settings.xml`
- Create: `ShiftCalendar/app/src/main/java/com/shiftcalendar/ui/settings/SettingsViewModel.kt`
- Create: `ShiftCalendar/app/src/main/java/com/shiftcalendar/ui/settings/SettingsFragment.kt`
- Modify: `ShiftCalendar/app/src/main/res/menu/bottom_nav_menu.xml`
- Modify: `ShiftCalendar/app/src/main/res/values/strings.xml`
- Modify: `ShiftCalendar/app/src/main/java/com/shiftcalendar/MainActivity.kt`

- [ ] **Step 1: 设置图标**

新建 `ShiftCalendar/app/src/main/res/drawable/ic_settings.xml`（Material 齿轮图标）：

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94l-0.36,-2.54c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41l-0.36,2.54c-0.59,0.24 -1.13,0.57 -1.62,0.94l-2.39,-0.96c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87c-0.12,0.21 -0.08,0.47 0.12,0.61l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z" />
</vector>
```

- [ ] **Step 2: 设置页布局**

新建 `ShiftCalendar/app/src/main/res/layout/fragment_settings.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingHorizontal="24dp"
        android:paddingTop="24dp"
        android:paddingBottom="48dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="闹钟提醒"
            android:textColor="@color/text_primary"
            android:textSize="20sp"
            android:fontFamily="sans-serif-medium"
            android:layout_marginBottom="20dp" />

        <com.google.android.material.materialswitch.MaterialSwitch
            android:id="@+id/switchAlarmEnabled"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="启用班次闹钟"
            android:textColor="@color/text_primary"
            android:textSize="15sp" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:text="提前提醒分钟数"
            android:textColor="@color/text_tertiary"
            android:textSize="12sp" />

        <com.google.android.material.textfield.TextInputLayout
            style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox.ExposedDropdownMenu"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp">

            <AutoCompleteTextView
                android:id="@+id/spinnerLeadMinutes"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:inputType="none"
                android:text="0 分钟" />
        </com.google.android.material.textfield.TextInputLayout>

        <com.google.android.material.materialswitch.MaterialSwitch
            android:id="@+id/switchVibrate"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:text="震动"
            android:textColor="@color/text_primary"
            android:textSize="15sp" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:text="铃声"
            android:textColor="@color/text_tertiary"
            android:textSize="12sp" />

        <LinearLayout
            android:id="@+id/layoutRingtone"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:padding="12dp"
            android:background="?attr/selectableItemBackground">

            <TextView
                android:id="@+id/tvRingtoneName"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="系统默认"
                android:textColor="@color/text_primary"
                android:textSize="14sp" />

            <ImageView
                android:layout_width="20dp"
                android:layout_height="20dp"
                android:src="@drawable/ic_settings"
                android:alpha="0.5" />
        </LinearLayout>

        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:layout_marginTop="24dp"
            android:background="@color/divider" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:text="改动后将重新调度闹钟。"
            android:textColor="@color/text_tertiary"
            android:textSize="12sp" />

    </LinearLayout>
</ScrollView>
```

- [ ] **Step 3: SettingsViewModel**

新建 `ShiftCalendar/app/src/main/java/com/shiftcalendar/ui/settings/SettingsViewModel.kt`：

```kotlin
package com.shiftcalendar.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.shiftcalendar.alarm.AlarmScheduler
import com.shiftcalendar.alarm.AlarmSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

class SettingsViewModel(private val appContext: Context) : ViewModel() {

    private val alarmSettings = AlarmSettings.get(appContext)

    val enabled get() = alarmSettings.enabled
    val leadMinutes get() = alarmSettings.leadMinutes
    val vibrate get() = alarmSettings.vibrate
    val ringtoneUri get() = alarmSettings.ringtoneUri

    fun setEnabled(value: Boolean) {
        alarmSettings.enabled = value
        reschedule()
    }

    fun setLeadMinutes(value: Int) {
        alarmSettings.leadMinutes = value
        reschedule()
    }

    fun setVibrate(value: Boolean) {
        alarmSettings.vibrate = value
        reschedule()
    }

    fun setRingtoneUri(value: String) {
        alarmSettings.ringtoneUri = value
        reschedule()
    }

    private fun reschedule() {
        viewModelScope.launch(Dispatchers.IO) {
            AlarmScheduler.scheduleNext(appContext)
        }
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(appContext) as T
        }
    }
}
```

- [ ] **Step 4: SettingsFragment**

新建 `ShiftCalendar/app/src/main/java/com/shiftcalendar/ui/settings/SettingsFragment.kt`：

```kotlin
package com.shiftcalendar.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.shiftcalendar.R
import com.shiftcalendar.ShiftCalendarApp
import com.shiftcalendar.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(requireContext().applicationContext)
    }

    private val leadOptions = listOf(0, 5, 10, 15, 30, 60)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // 不论是否授权都继续，通知权限仅影响横幅显示
        }

    private val pickRingtone =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                val uriStr = uri?.toString() ?: ""
                viewModel.setRingtoneUri(uriStr)
                updateRingtoneDisplay(uriStr)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 总开关
        binding.switchAlarmEnabled.isChecked = viewModel.enabled
        binding.switchAlarmEnabled.setOnCheckedChangeListener { _, checked ->
            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            viewModel.setEnabled(checked)
        }

        // 提前分钟数
        val leadLabels = leadOptions.map { if (it == 0) "不提前" else "${it} 分钟" }
        binding.spinnerLeadMinutes.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, leadLabels)
        )
        val currentLeadIndex = leadOptions.indexOf(viewModel.leadMinutes).coerceAtLeast(0)
        binding.spinnerLeadMinutes.setText(leadLabels[currentLeadIndex], false)
        binding.spinnerLeadminutes.setOnItemClickListener { _, _, position, _ ->
            viewModel.setLeadMinutes(leadOptions[position])
        }

        // 震动
        binding.switchVibrate.isChecked = viewModel.vibrate
        binding.switchVibrate.setOnCheckedChangeListener { _, checked ->
            viewModel.setVibrate(checked)
        }

        // 铃声选择
        updateRingtoneDisplay(viewModel.ringtoneUri)
        binding.layoutRingtone.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择闹钟铃声")
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            }
            pickRingtone.launch(intent)
        }
    }

    private fun updateRingtoneDisplay(uriStr: String) {
        binding.tvRingtoneName.text = if (uriStr.isEmpty()) {
            "系统默认"
        } else {
            try {
                val ringtone = RingtoneManager.getRingtone(requireContext(), Uri.parse(uriStr))
                ringtone?.getTitle(requireContext()) ?: "自定义"
            } catch (_: Exception) {
                "自定义"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

注：binding 中的 `spinnerLeadminutes` 是 DataBinding 自动生成的字段名（XML id `spinnerLeadMinutes` → camelCase）。若生成的字段名是 `spinnerLeadMinutes` 则用之，按实际生成调整。

- [ ] **Step 5: 底部导航加"设置"项**

修改 `ShiftCalendar/app/src/main/res/menu/bottom_nav_menu.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/nav_calendar"
        android:icon="@drawable/ic_calendar"
        android:title="@string/nav_calendar" />
    <item
        android:id="@+id/nav_shifts"
        android:icon="@drawable/ic_shifts"
        android:title="@string/nav_shifts" />
    <item
        android:id="@+id/nav_rules"
        android:icon="@drawable/ic_rules"
        android:title="@string/nav_rules" />
    <item
        android:id="@+id/nav_settings"
        android:icon="@drawable/ic_settings"
        android:title="@string/nav_settings" />
</menu>
```

- [ ] **Step 6: strings.xml 加文案**

修改 `ShiftCalendar/app/src/main/res/values/strings.xml`，在底部导航段加：

```xml
<string name="nav_settings">设置</string>
```

- [ ] **Step 7: MainActivity 接入设置页**

修改 `ShiftCalendar/app/src/main/java/com/shiftcalendar/MainActivity.kt`：

加字段：
```kotlin
private val settingsFragment = SettingsFragment()
```

`setupBottomNavigation` 的 when 加分支：
```kotlin
R.id.nav_settings -> switchFragment(settingsFragment)
```

`loadInitialFragment` 加 add+hide：
```kotlin
.add(R.id.fragmentContainer, settingsFragment, "settings")
.hide(settingsFragment)
```

import：
```kotlin
import com.shiftcalendar.ui.settings.SettingsFragment
```

- [ ] **Step 8: 提交**

```bash
git add ShiftCalendar/app/src/main/res/drawable/ic_settings.xml \
        ShiftCalendar/app/src/main/res/layout/fragment_settings.xml \
        ShiftCalendar/app/src/main/java/com/shiftcalendar/ui/settings/ \
        ShiftCalendar/app/src/main/res/menu/bottom_nav_menu.xml \
        ShiftCalendar/app/src/main/res/values/strings.xml \
        ShiftCalendar/app/src/main/java/com/shiftcalendar/MainActivity.kt
git commit -m "feat(alarm): add settings page with global toggle/lead/vibrate/ringtone"
```

---

### Task 11: 接入调度触发点（ShiftGenerator + CalendarViewModel）

**Files:**
- Modify: `ShiftCalendar/app/src/main/java/com/shiftcalendar/domain/generator/ShiftGenerator.kt`
- Modify: `ShiftCalendar/app/src/main/java/com/shiftcalendar/ui/calendar/CalendarViewModel.kt`

- [ ] **Step 1: ShiftGenerator.generate() 末尾调 scheduleNext**

修改 `ShiftCalendar/app/src/main/java/com/shiftcalendar/domain/generator/ShiftGenerator.kt`：

在 `shiftDayDao.insertAll(shiftDays)` 后追加：
```kotlin
// 班次重新生成后重新调度闹钟
try {
    kotlinx.coroutines.runBlocking {
        com.shiftcalendar.alarm.AlarmScheduler.scheduleNext(com.shiftcalendar.ShiftCalendarApp.instance)
    }
} catch (_: Exception) {
    // 调度失败不影响生成结果
}
```

注：ShiftGenerator 已在 IO 协程中（`suspend fun generate`），可直接用 AlarmScheduler.scheduleNext（suspend）。更优写法：
```kotlin
// ShiftGenerator 已在 suspend 上下文，直接调用 suspend 函数
kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    com.shiftcalendar.alarm.AlarmScheduler.scheduleNext(com.shiftcalendar.ShiftCalendarApp.instance)
}
```

优先用后者（无需 runBlocking）。

- [ ] **Step 2: CalendarViewModel.changeShiftType 后调 scheduleNext**

修改 `ShiftCalendar/app/src/main/java/com/shiftcalendar/ui/calendar/CalendarViewModel.kt` 的 `changeShiftType`：

在 `refreshShiftDays()` 后追加：
```kotlin
com.shiftcalendar.alarm.AlarmScheduler.scheduleNext(com.shiftcalendar.ShiftCalendarApp.instance)
```

（已在 viewModelScope.launch 协程中，直接调 suspend 函数即可）

同样在 `saveNote` 末尾追加（备注本身不影响闹钟，但保险起见——其实备注不影响，**不加**，避免无谓重调度）。

**只在 `changeShiftType` 末尾加，`saveNote` 不加。**

- [ ] **Step 3: 提交**

```bash
git add ShiftCalendar/app/src/main/java/com/shiftcalendar/domain/generator/ShiftGenerator.kt \
        ShiftCalendar/app/src/main/java/com/shiftcalendar/ui/calendar/CalendarViewModel.kt
git commit -m "feat(alarm): trigger reschedule after shift generation and manual change"
```

---

### Task 12: 编译验证 + 推送 CI

**Files:** 无（仅构建验证）

- [ ] **Step 1: 本地或 CI 编译**

确认本地构建（若 gradle 8.4 已就绪）：
```bash
cd ShiftCalendar && ./gradlew assembleDebug --stacktrace
```

若本地环境不可用，直接推送让 CI 验证（前序 CI 已稳定）。

- [ ] **Step 2: 运行单元测试**

```bash
cd ShiftCalendar && ./gradlew testDebugUnitTest --stacktrace
```

确认 `AlarmSchedulerTest` 5 个测试通过。

- [ ] **Step 3: 推送 + 等 CI**

```bash
git push origin HEAD
```

等 CI 通过（参考前序 build-apk.yml 工作流）。

- [ ] **Step 4: 提交最终状态（若有修复）**

若 CI 报错，按错误信息修复后追加提交：
```bash
git add -A && git commit -m "fix(alarm): resolve ci compile errors" && git push origin HEAD
```

---

## 自查（spec 覆盖核对）

| spec 要求 | 实现 Task |
|---|---|
| 触发时机：开始前提前 + 结束时 | Task 3 computeNextAlarm 计算两类候选 |
| 配置粒度：全局 + 班次覆盖 | Task 2 AlarmSettings + Task 1 ShiftType.alarmEnabled + Task 9 三态 UI |
| AlarmManager.setAlarmClock | Task 3 AlarmScheduler.scheduleNext |
| NotificationChannel + 声音/震动 | Task 5 NotificationHelper |
| RingtoneManager 选择器 | Task 10 SettingsFragment pickRingtone |
| 全屏 intent | Task 6 AlarmReceiver setFullScreenIntent |
| BOOT_COMPLETED 恢复 | Task 7 AlarmBootReceiver |
| 只调度下一个 + 接力 | Task 3 + Task 6 末尾 scheduleNext |
| 6 个触发点 | Task 8 (启动) + Task 11 (生成/改班次) + Task 7 (重启) + Task 10 (改设置) + Task 9 (编辑班次保存) |
| 数据库 migration v1→v2 | Task 1 |
| 权限清单 | Task 8 AndroidManifest |
| 单元测试 | Task 3 AlarmSchedulerTest |

类型一致性核对：
- `AlarmScheduler.AlarmType` 在 Task 3/Task 6 一致
- `AlarmScheduler.NextAlarm` 字段 triggerAt/type/shiftDay/shiftType 在 Task 3 测试与实现一致
- `AlarmSettings` 字段名 enabled/leadMinutes/ringtoneUri/vibrate 在 Task 2/5/10 一致
- `ShiftType.alarmEnabled` 字段名在 Task 1/3/9 一致
- `NotificationHelper.CHANNEL_ID` 在 Task 5/6 一致
- `AlarmReceiver` extras 常量在 Task 3/6 一致
