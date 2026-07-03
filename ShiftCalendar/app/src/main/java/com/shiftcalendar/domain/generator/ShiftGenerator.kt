package com.shiftcalendar.domain.generator

import com.shiftcalendar.data.database.AppDatabase
import com.shiftcalendar.data.entity.ShiftDay
import com.shiftcalendar.data.entity.ShiftRule
import java.util.Calendar

/**
 * 倒班批量生成引擎
 *
 * 根据规律配置自动计算并批量生成班次安排。
 *
 * 算法：
 * 1. 解析 shiftSequence 字符串（逗号分隔的 shiftTypeId 列表）
 * 2. 从 startDate 开始，按 cycleDays 周期循环 shiftSequence
 * 3. 生成指定天数范围内的 ShiftDay 记录
 *
 * 多规律冲突解决：后生成的覆盖先生成的（OnConflictStrategy.REPLACE）
 */
open class ShiftGenerator(private val database: AppDatabase) {

    private val shiftDayDao get() = database.shiftDayDao()

    /**
     * 清除指定日期及之后的旧班次数据
     */
    open suspend fun clearFromDate(fromDate: Long) {
        shiftDayDao.deleteFrom(fromDate)
    }

    /**
     * 根据规律生成指定天数的班次
     *
     * @param rule 倒班规律
     * @param days 生成天数（默认 90 天）
     */
    open suspend fun generate(rule: ShiftRule, days: Int = 90) {
        val sequence = parseSequence(rule.shiftSequence)
        if (sequence.isEmpty()) return

        val calendar = Calendar.getInstance().apply {
            timeInMillis = rule.startDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val shiftDays = mutableListOf<ShiftDay>()

        for (dayOffset in 0 until days) {
            val date = calendar.timeInMillis
            // 计算当前日期在周期中的位置
            val cycleIndex = dayOffset % rule.cycleDays
            // 获取对应的班次类型 ID
            val shiftTypeId = if (cycleIndex < sequence.size) {
                sequence[cycleIndex]
            } else {
                sequence[0] // fallback
            }

            shiftDays.add(
                ShiftDay(
                    date = date,
                    shiftTypeId = shiftTypeId,
                    isGenerated = true
                )
            )

            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        // 批量插入（OnConflictStrategy.REPLACE 确保冲突时覆盖）
        shiftDayDao.insertAll(shiftDays)
    }

    /**
     * 清除所有生成的班次数据
     */
    suspend fun clearAll() {
        shiftDayDao.deleteAll()
    }

    /**
     * 解析班次序列字符串
     * 格式: "0,1,2,3" -> [0, 1, 2, 3]
     */
    private fun parseSequence(sequence: String): List<Long> {
        return try {
            sequence.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { it.toLong() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}