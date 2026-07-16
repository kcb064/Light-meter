package com.kevinboutwell.lightmeter

import android.app.Application

class LightMeterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
