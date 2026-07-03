package com.shiftcalendar

import android.app.Application

class ShiftCalendarApp : Application() {

    lateinit var database: data.database.AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = data.database.AppDatabase.getInstance(this)
    }

    companion object {
        lateinit var instance: ShiftCalendarApp
            private set
    }
}