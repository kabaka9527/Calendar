package com.shiftcalendar.data.repository

import com.shiftcalendar.data.dao.ShiftRuleDao
import com.shiftcalendar.data.entity.ShiftRule
import kotlinx.coroutines.flow.Flow

class ShiftRuleRepository(private val dao: ShiftRuleDao) {

    fun getAll(): Flow<List<ShiftRule>> = dao.getAll()

    suspend fun getById(id: Long): ShiftRule? = dao.getById(id)

    suspend fun insert(rule: ShiftRule): Long = dao.insert(rule)

    suspend fun update(rule: ShiftRule) = dao.update(rule)

    suspend fun deactivate(id: Long) = dao.deactivate(id)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun count(): Int = dao.count()
}