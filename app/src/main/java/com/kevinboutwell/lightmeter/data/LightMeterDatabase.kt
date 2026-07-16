package com.kevinboutwell.lightmeter.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ReadingEntity::class], version = 1, exportSchema = false)
abstract class LightMeterDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao

    companion object {
        fun build(context: Context): LightMeterDatabase =
            Room.databaseBuilder(context, LightMeterDatabase::class.java, "lightmeter.db")
                .build()
    }
}
