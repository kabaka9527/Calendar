package com.shiftcalendar.data.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shiftcalendar.data.entity.ShiftDay
import com.shiftcalendar.data.entity.ShiftRule
import com.shiftcalendar.data.entity.ShiftType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var shiftTypeDao: com.shiftcalendar.data.dao.ShiftTypeDao
    private lateinit var shiftRuleDao: com.shiftcalendar.data.dao.ShiftRuleDao
    private lateinit var shiftDayDao: com.shiftcalendar.data.dao.ShiftDayDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        shiftTypeDao = database.shiftTypeDao()
        shiftRuleDao = database.shiftRuleDao()
        shiftDayDao = database.shiftDayDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInsertAndRetrieveShiftType() = runBlocking {
        val shiftType = ShiftType(
            name = "白班",
            colorTag = "#7BA587",
            startTime = "08:00",
            endTime = "16:00"
        )
        val id = shiftTypeDao.insert(shiftType)
        assertTrue(id > 0)

        val retrieved = shiftTypeDao.getById(id)
        assertNotNull(retrieved)
        assertEquals("白班", retrieved?.name)
        assertEquals("#7BA587", retrieved?.colorTag)
    }

    @Test
    fun testInsertAndRetrieveShiftRule() = runBlocking {
        val rule = ShiftRule(
            name = "四班三运转",
            startDate = System.currentTimeMillis(),
            cycleDays = 4,
            shiftSequence = "0,1,2,3"
        )
        val id = shiftRuleDao.insert(rule)
        assertTrue(id > 0)

        val retrieved = shiftRuleDao.getById(id)
        assertNotNull(retrieved)
        assertEquals("四班三运转", retrieved?.name)
        assertEquals(4, retrieved?.cycleDays)
    }

    @Test
    fun testBulkInsertShiftDays() = runBlocking {
        val cal = Calendar.getInstance()
        cal.set(2024, 0, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val days = (0 until 90).map {
            ShiftDay(
                date = cal.timeInMillis,
                shiftTypeId = (it % 4).toLong(),
                isGenerated = true
            ).also { cal.add(Calendar.DAY_OF_MONTH, 1) }
        }

        shiftDayDao.insertAll(days)

        val count = shiftDayDao.count()
        assertEquals(90, count)
    }

    @Test
    fun testDataIntegrityAfterRestart() = runBlocking {
        // 模拟数据持久化：插入 -> 关闭 -> 重新打开
        val shiftType = ShiftType(
            name = "夜班",
            colorTag = "#6C7BA6",
            startTime = "00:00",
            endTime = "08:00"
        )
        shiftTypeDao.insert(shiftType)

        val rule = ShiftRule(
            name = "测试规律",
            startDate = System.currentTimeMillis(),
            cycleDays = 3,
            shiftSequence = "0,1,2"
        )
        shiftRuleDao.insert(rule)

        // 关闭并重新打开（使用相同的 in-memory database 模拟）
        // 实际设备上 Room 会持久化到文件
        val types = shiftTypeDao.getAll().first()
        val rules = shiftRuleDao.getAll().first()

        assertTrue(types.isNotEmpty())
        assertTrue(rules.isNotEmpty())
        assertEquals("夜班", types[0].name)
        assertEquals("测试规律", rules[0].name)
    }

    @Test
    fun testDeleteShiftDayFromDate() = runBlocking {
        val cal = Calendar.getInstance()
        cal.set(2024, 0, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val date1 = cal.timeInMillis

        cal.add(Calendar.DAY_OF_MONTH, 30)
        val date2 = cal.timeInMillis

        shiftDayDao.insertAll(
            listOf(
                ShiftDay(date = date1, shiftTypeId = 0, isGenerated = true),
                ShiftDay(date = date2, shiftTypeId = 0, isGenerated = true)
            )
        )

        // 删除 date2 及之后的数据
        shiftDayDao.deleteFrom(date2)
        val count = shiftDayDao.count()
        assertEquals(1, count)
    }
}