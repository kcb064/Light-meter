package com.kevinboutwell.lightmeter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** How a metering mode's calibration offset was last set. */
enum class CalibrationSource { MANUAL, REFERENCE_METER, SUNNY_16 }

data class ModeCalibration(
    val offsetEv: Double = 0.0,
    val calibratedAtEpochMs: Long? = null,
    val source: CalibrationSource? = null,
)

data class DialState(
    val isoNominal: String = "400",
    val priority: String = "APERTURE",
    val apertureNominal: String = "5.6",
    val shutterNominal: String = "1/125",
    val ecThirds: Int = 0,
    val filmId: String = "none",
    val mode: String = "REFLECTIVE",
    val spot: Boolean = true,
    /** 35mm-equivalent zoom, null = camera default (main lens, 1x). */
    val zoomMm: Double? = null,
    /** Focal length of the user's real lens to match, null = off. */
    val lensPresetMm: Int? = null,
)

data class AppSettings(
    val reflective: ModeCalibration = ModeCalibration(),
    val incident: ModeCalibration = ModeCalibration(),
    val dials: DialState = DialState(),
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val reflectiveOffset = doublePreferencesKey("reflective_offset_ev")
        val reflectiveCalibratedAt = longPreferencesKey("reflective_calibrated_at")
        val reflectiveSource = stringPreferencesKey("reflective_calibration_source")
        val incidentOffset = doublePreferencesKey("incident_offset_ev")
        val incidentCalibratedAt = longPreferencesKey("incident_calibrated_at")
        val incidentSource = stringPreferencesKey("incident_calibration_source")
        val iso = stringPreferencesKey("last_iso")
        val priority = stringPreferencesKey("last_priority")
        val aperture = stringPreferencesKey("last_aperture")
        val shutter = stringPreferencesKey("last_shutter")
        val ecThirds = intPreferencesKey("last_ec_thirds")
        val filmId = stringPreferencesKey("last_film_id")
        val mode = stringPreferencesKey("last_mode")
        val spot = booleanPreferencesKey("last_spot")
        val zoomMm = doublePreferencesKey("last_zoom_mm")
        val lensPresetMm = intPreferencesKey("last_lens_preset_mm")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            reflective = ModeCalibration(
                offsetEv = p[Keys.reflectiveOffset] ?: 0.0,
                calibratedAtEpochMs = p[Keys.reflectiveCalibratedAt],
                source = p[Keys.reflectiveSource]?.let { runCatching { CalibrationSource.valueOf(it) }.getOrNull() },
            ),
            incident = ModeCalibration(
                offsetEv = p[Keys.incidentOffset] ?: 0.0,
                calibratedAtEpochMs = p[Keys.incidentCalibratedAt],
                source = p[Keys.incidentSource]?.let { runCatching { CalibrationSource.valueOf(it) }.getOrNull() },
            ),
            dials = DialState(
                isoNominal = p[Keys.iso] ?: "400",
                priority = p[Keys.priority] ?: "APERTURE",
                apertureNominal = p[Keys.aperture] ?: "5.6",
                shutterNominal = p[Keys.shutter] ?: "1/125",
                ecThirds = p[Keys.ecThirds] ?: 0,
                filmId = p[Keys.filmId] ?: "none",
                mode = p[Keys.mode] ?: "REFLECTIVE",
                spot = p[Keys.spot] ?: true,
                zoomMm = p[Keys.zoomMm],
                lensPresetMm = p[Keys.lensPresetMm],
            ),
        )
    }

    suspend fun saveDialState(dials: DialState) {
        context.settingsStore.edit { p ->
            p[Keys.iso] = dials.isoNominal
            p[Keys.priority] = dials.priority
            p[Keys.aperture] = dials.apertureNominal
            p[Keys.shutter] = dials.shutterNominal
            p[Keys.ecThirds] = dials.ecThirds
            p[Keys.filmId] = dials.filmId
            p[Keys.mode] = dials.mode
            p[Keys.spot] = dials.spot
            dials.zoomMm?.let { p[Keys.zoomMm] = it } ?: p.remove(Keys.zoomMm)
            dials.lensPresetMm?.let { p[Keys.lensPresetMm] = it } ?: p.remove(Keys.lensPresetMm)
        }
    }

    suspend fun setCalibration(
        reflectiveMode: Boolean,
        offsetEv: Double,
        source: CalibrationSource,
        atEpochMs: Long,
    ) {
        context.settingsStore.edit { p ->
            if (reflectiveMode) {
                p[Keys.reflectiveOffset] = offsetEv
                p[Keys.reflectiveCalibratedAt] = atEpochMs
                p[Keys.reflectiveSource] = source.name
            } else {
                p[Keys.incidentOffset] = offsetEv
                p[Keys.incidentCalibratedAt] = atEpochMs
                p[Keys.incidentSource] = source.name
            }
        }
    }
}
