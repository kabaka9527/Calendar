package com.shiftcalendar.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.shiftcalendar.data.entity.ShiftRule
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftRuleDao {

    @Query("SELECT * FROM shift_rules WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getAll(): Flow<List<ShiftRule>>

    @Query("SELECT * FROM shift_rules WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getAllLiveData(): LiveData<List<ShiftRule>>

    @Query("SELECT * FROM shift_rules WHERE id = :id")
    suspend fun getById(id: Long): ShiftRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shiftRule: ShiftRule): Long

    @Update
    suspend fun update(shiftRule: ShiftRule)

    @Query("UPDATE shift_rules SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)

    @Query("DELETE FROM shift_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM shift_rules WHERE isActive = 1")
    suspend fun count(): Int
}