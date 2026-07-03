package com.shiftcalendar.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.shiftcalendar.data.entity.ShiftType
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftTypeDao {

    @Query("SELECT * FROM shift_types ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<ShiftType>>

    @Query("SELECT * FROM shift_types ORDER BY sortOrder ASC")
    suspend fun getAllStaticList(): List<ShiftType>

    @Query("SELECT * FROM shift_types ORDER BY sortOrder ASC")
    fun getAllLiveData(): LiveData<List<ShiftType>>

    @Query("SELECT * FROM shift_types WHERE id = :id")
    suspend fun getById(id: Long): ShiftType?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shiftType: ShiftType): Long

    @Update
    suspend fun update(shiftType: ShiftType)

    @Delete
    suspend fun delete(shiftType: ShiftType)

    @Query("DELETE FROM shift_types WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM shift_types")
    suspend fun count(): Int
}