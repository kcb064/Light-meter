package com.kevinboutwell.thirdstop

import android.content.Context
import com.kevinboutwell.thirdstop.camera.CameraMeter
import com.kevinboutwell.thirdstop.data.LightMeterDatabase
import com.kevinboutwell.thirdstop.data.ReadingDao
import com.kevinboutwell.thirdstop.data.SettingsRepository
import com.kevinboutwell.thirdstop.sensor.AmbientLightMeter

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
