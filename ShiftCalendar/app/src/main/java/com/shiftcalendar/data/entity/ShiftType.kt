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
    val sortOrder: Int = 0      // 排序权重
) : java.io.Serializable