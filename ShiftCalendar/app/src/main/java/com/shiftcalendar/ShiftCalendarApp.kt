package com.shiftcalendar

import android.app.Application
import com.shiftcalendar.data.database.AppDatabase

class ShiftCalendarApp : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
    }

    companion object {
        lateinit var instance: ShiftCalendarApp
            private set
    }
}