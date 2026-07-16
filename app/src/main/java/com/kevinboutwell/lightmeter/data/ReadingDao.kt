package com.kevinboutwell.lightmeter.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    @Query("SELECT * FROM readings ORDER BY timestampEpochMs DESC")
    fun all(): Flow<List<ReadingEntity>>

    @Insert
    suspend fun insert(reading: ReadingEntity): Long

    @Delete
    suspend fun delete(reading: ReadingEntity)
}
