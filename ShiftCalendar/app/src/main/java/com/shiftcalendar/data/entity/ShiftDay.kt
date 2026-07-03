package com.shiftcalendar.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 每日班次实体
 */
@Entity(
    tableName = "shift_days",
    indices = [Index(value = ["date"], unique = true)]
)
data class ShiftDay(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long,               // 日期 epoch day (毫秒, 当天 00:00:00)
    val shiftTypeId: Long,        // 关联班次类型 ID
    val isGenerated: Boolean = true,  // 是否自动生成
    val note: String = ""         // 备注
)