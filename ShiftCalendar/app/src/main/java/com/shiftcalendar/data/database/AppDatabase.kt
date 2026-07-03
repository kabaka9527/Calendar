package com.shiftcalendar.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.shiftcalendar.data.dao.ShiftDayDao
import com.shiftcalendar.data.dao.ShiftRuleDao
import com.shiftcalendar.data.dao.ShiftTypeDao
import com.shiftcalendar.data.entity.ShiftDay
import com.shiftcalendar.data.entity.ShiftRule
import com.shiftcalendar.data.entity.ShiftType

@Database(
    entities = [
        ShiftType::class,
        ShiftRule::class,
        ShiftDay::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun shiftTypeDao(): ShiftTypeDao
    abstract fun shiftRuleDao(): ShiftRuleDao
    abstract fun shiftDayDao(): ShiftDayDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shift_calendar.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}