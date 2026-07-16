package com.kevinboutwell.lightmeter

import android.content.Context
import com.kevinboutwell.lightmeter.camera.CameraMeter
import com.kevinboutwell.lightmeter.data.LightMeterDatabase
import com.kevinboutwell.lightmeter.data.ReadingDao
import com.kevinboutwell.lightmeter.data.SettingsRepository
import com.kevinboutwell.lightmeter.sensor.AmbientLightMeter

/**
 * Manual dependency container — deliberately no DI framework for an app this
 * size. Screen ViewModels pull what they need from here via their factories.
 */
class AppContainer(private val appContext: Context) {
    private val database by lazy { LightMeterDatabase.build(appContext) }

    val readingDao: ReadingDao get() = database.readingDao()
    val settings by lazy { SettingsRepository(appContext) }
    val cameraMeter by lazy { CameraMeter(appContext) }
    val ambientLightMeter by lazy { AmbientLightMeter(appContext) }
}
