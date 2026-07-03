package com.shiftcalendar.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 倒班规律实体
 * shiftSequence 存储为 JSON 数组，每个元素是 shiftTypeId 的列表
 * 例如: "[0, 1, 2, 3]" 表示 4 天周期: 休息→白班→中班→夜班
 */
@Entity(tableName = "shift_rules")
data class ShiftRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startDate: Long,          // 起始日期 epoch day (毫秒)
    val cycleDays: Int,           // 周期天数
    val shiftSequence: String,    // JSON 数组: shiftTypeId 列表
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)