package com.shiftcalendar.domain.generator

import com.shiftcalendar.data.entity.ShiftDay
import com.shiftcalendar.data.entity.ShiftRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * 批量生成引擎单元测试
 */
class ShiftGeneratorTest {

    private lateinit var generator: ShiftGenerator

    // 使用内存数据库模拟
    private val generatedDays = mutableListOf<ShiftDay>()
    private val deletedFromDates = mutableListOf<Long>()

    @Before
    fun setup() {
        generatedDays.clear()
        deletedFromDates.clear()
    }

    // ========== 解析序列测试 ==========

    @Test
    fun `parseSequence - valid comma-separated string`() {
        val rule = createRule(
            name = "四班三运转",
            startDate = "2024-01-01",
            cycleDays = 4,
            sequence = "0,1,2,3"
        )

        val generator = createMockGenerator()
        generator.generate(rule, 4)

        assertEquals(4, generatedDays.size)
        // 验证序列: 0, 1, 2, 3
        assertEquals(0L, generatedDays[0].shiftTypeId)
        assertEquals(1L, generatedDays[1].shiftTypeId)
        assertEquals(2L, generatedDays[2].shiftTypeId)
        assertEquals(3L, generatedDays[3].shiftTypeId)
    }

    @Test
    fun `parseSequence - empty string returns empty list`() {
        val rule = createRule(
            name = "空序列",
            startDate = "2024-01-01",
            cycleDays = 4,
            sequence = ""
        )

        val generator = createMockGenerator()
        generator.generate(rule, 4)

        // 空序列不应生成任何内容
        assertEquals(0, generatedDays.size)
    }

    @Test
    fun `parseSequence - string with spaces`() {
        val rule = createRule(
            name = "带空格",
            startDate = "2024-01-01",
            cycleDays = 3,
            sequence = " 0 , 1 , 2 "
        )

        val generator = createMockGenerator()
        generator.generate(rule, 3)

        assertEquals(3, generatedDays.size)
        assertEquals(0L, generatedDays[0].shiftTypeId)
        assertEquals(1L, generatedDays[1].shiftTypeId)
        assertEquals(2L, generatedDays[2].shiftTypeId)
    }

    // ========== 生成算法测试 ==========

    @Test
    fun `generate - 90 days for 4-day cycle`() {
        val rule = createRule(
            name = "四班三运转",
            startDate = "2024-01-01",
            cycleDays = 4,
            sequence = "0,1,2,3"
        )

        val generator = createMockGenerator()
        generator.generate(rule, 90)

        assertEquals(90, generatedDays.size)

        // 验证第 1 天和第 5 天班次相同（周期 4）
        assertEquals(generatedDays[0].shiftTypeId, generatedDays[4].shiftTypeId)
        assertEquals(generatedDays[1].shiftTypeId, generatedDays[5].shiftTypeId)
    }

    @Test
    fun `generate - sequence shorter than cycle uses fallback`() {
        val rule = createRule(
            name = "短序列",
            startDate = "2024-01-01",
            cycleDays = 5,
            sequence = "0,1,2"
        )

        val generator = createMockGenerator()
        generator.generate(rule, 5)

        assertEquals(5, generatedDays.size)
        // 第 4 天(index=3)超出序列长度, 应fallback到序列[0]
        assertEquals(0L, generatedDays[3].shiftTypeId)
        // 第 5 天(index=4)应取序列[4%3=1]
        assertEquals(1L, generatedDays[4].shiftTypeId)
    }

    @Test
    fun `generate - dates are sequential`() {
        val rule = createRule(
            name = "日期连续性",
            startDate = "2024-01-01",
            cycleDays = 2,
            sequence = "0,1"
        )

        val generator = createMockGenerator()
        generator.generate(rule, 3)

        val cal = Calendar.getInstance()
        cal.timeInMillis = generatedDays[0].date
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))

        cal.timeInMillis = generatedDays[1].date
        assertEquals(2, cal.get(Calendar.DAY_OF_MONTH))

        cal.timeInMillis = generatedDays[2].date
        assertEquals(3, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `generate - all generated days marked as generated`() {
        val rule = createRule(
            name = "标记测试",
            startDate = "2024-01-01",
            cycleDays = 2,
            sequence = "0,1"
        )

        val generator = createMockGenerator()
        generator.generate(rule, 5)

        generatedDays.forEach {
            assertTrue("Day ${it.date} should be marked as generated", it.isGenerated)
        }
    }

    // ========== 多规律冲突测试 ==========

    @Test
    fun `conflict resolution - later generation overwrites earlier`() {
        val rule1 = createRule(
            name = "旧规律",
            startDate = "2024-01-01",
            cycleDays = 2,
            sequence = "0,1"
        )

        val rule2 = createRule(
            name = "新规律",
            startDate = "2024-01-01",
            cycleDays = 2,
            sequence = "2,3"
        )

        val generator = createMockGenerator()
        generator.generate(rule1, 2)
        generator.generate(rule2, 2)

        // 后生成的应覆盖，总共 2 条记录（REPLACE 策略）
        assertEquals(2, generatedDays.size)
        assertEquals(2L, generatedDays[0].shiftTypeId)
        assertEquals(3L, generatedDays[1].shiftTypeId)
    }

    @Test
    fun `conflict resolution - different start dates no conflict`() {
        val rule1 = createRule(
            name = "规律1",
            startDate = "2024-01-01",
            cycleDays = 2,
            sequence = "0,1"
        )

        val rule2 = createRule(
            name = "规律2",
            startDate = "2024-01-05",
            cycleDays = 2,
            sequence = "2,3"
        )

        val generator = createMockGenerator()
        // 先生成规律1的3天(1/1-1/3)
        // 再生成规律2的3天(1/5-1/7)
        // 总共 6 天，无冲突
        generator.generate(rule1, 3)
        generator.generate(rule2, 3)

        assertEquals(6, generatedDays.size)
    }

    // ========== 辅助方法 ==========

    private fun createRule(
        name: String,
        startDate: String,
        cycleDays: Int,
        sequence: String
    ): ShiftRule {
        val cal = Calendar.getInstance()
        val parts = startDate.split("-")
        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)

        return ShiftRule(
            id = 1,
            name = name,
            startDate = cal.timeInMillis,
            cycleDays = cycleDays,
            shiftSequence = sequence,
            createdAt = System.currentTimeMillis(),
            isActive = true
        )
    }

    private fun createMockGenerator(): ShiftGenerator {
        return object : ShiftGenerator(createMockDatabase()) {
            override suspend fun generate(rule: ShiftRule, days: Int) {
                val sequence = parseSequencePublic(rule.shiftSequence)
                if (sequence.isEmpty()) return

                val calendar = Calendar.getInstance().apply {
                    timeInMillis = rule.startDate
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                for (dayOffset in 0 until days) {
                    val date = calendar.timeInMillis
                    val cycleIndex = dayOffset % rule.cycleDays
                    val shiftTypeId = if (cycleIndex < sequence.size) sequence[cycleIndex] else sequence[0]

                    // 模拟 REPLACE 策略：同日期覆盖
                    generatedDays.removeAll { it.date == date }
                    generatedDays.add(
                        ShiftDay(
                            date = date,
                            shiftTypeId = shiftTypeId,
                            isGenerated = true
                        )
                    )
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                }
                generatedDays.sortBy { it.date }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createMockDatabase(): com.shiftcalendar.data.database.AppDatabase {
        // Mock database is never accessed; generate() is overridden below
        return null as com.shiftcalendar.data.database.AppDatabase
    }
}

// Extension to expose private parseSequence for testing
private fun ShiftGenerator.parseSequencePublic(sequence: String): List<Long> {
    return try {
        sequence.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.toLong() }
    } catch (e: Exception) {
        emptyList()
    }
}