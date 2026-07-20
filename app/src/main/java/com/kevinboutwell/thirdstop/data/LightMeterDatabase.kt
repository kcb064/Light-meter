package com.kevinboutwell.thirdstop.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Schema JSON is exported to app/schemas (see build.gradle.kts) so future
// migrations can be written and tested against the real v1 shape.
@Database(entities = [ReadingEntity::class], version = 1, exportSchema = true)
abstract class LightMeterDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao

    companion object {
        fun build(context: Context): LightMeterDatabase =
            Room.databaseBuilder(context, LightMeterDatabase::class.java, "lightmeter.db")
                .build()
    }
}
