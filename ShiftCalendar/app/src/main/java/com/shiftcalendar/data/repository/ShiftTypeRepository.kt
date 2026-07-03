package com.shiftcalendar.data.repository

import com.shiftcalendar.data.dao.ShiftTypeDao
import com.shiftcalendar.data.entity.ShiftType
import kotlinx.coroutines.flow.Flow

class ShiftTypeRepository(private val dao: ShiftTypeDao) {

    fun getAll(): Flow<List<ShiftType>> = dao.getAll()

    suspend fun getById(id: Long): ShiftType? = dao.getById(id)

    suspend fun insert(shiftType: ShiftType): Long = dao.insert(shiftType)

    suspend fun update(shiftType: ShiftType) = dao.update(shiftType)

    suspend fun delete(shiftType: ShiftType) = dao.delete(shiftType)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun count(): Int = dao.count()
}