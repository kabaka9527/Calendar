package com.shiftcalendar.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.shiftcalendar.data.entity.ShiftDay
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDayDao {

    @Query("SELECT * FROM shift_days WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getByDateRange(startDate: Long, endDate: Long): Flow<List<ShiftDay>>

    @Query("SELECT * FROM shift_days WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getByDateRangeLiveData(startDate: Long, endDate: Long): LiveData<List<ShiftDay>>

    @Query("SELECT * FROM shift_days WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: Long): ShiftDay?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(days: List<ShiftDay>)

    @Query("DELETE FROM shift_days WHERE date >= :fromDate")
    suspend fun deleteFrom(fromDate: Long)

    @Query("DELETE FROM shift_days")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM shift_days")
    suspend fun count(): Int
}