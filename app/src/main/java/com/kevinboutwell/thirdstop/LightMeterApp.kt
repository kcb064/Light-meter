package com.kevinboutwell.thirdstop

import android.app.Application

class LightMeterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
