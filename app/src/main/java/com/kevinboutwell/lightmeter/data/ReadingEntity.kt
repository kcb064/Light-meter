package com.kevinboutwell.lightmeter.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "readings")
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpochMs: Long,
    /** "REFLECTIVE_SPOT" | "REFLECTIVE_AVG" | "INCIDENT" */
    val mode: String,
    val ev100: Double,
    /** Incident readings only. */
    val lux: Double?,
    val isoNominal: String,
    val apertureNominal: String,
    val shutterNominal: String,
    val ecThirds: Int,
    val filmId: String?,
    val correctedShutterSec: Double?,
    val note: String?,
)
