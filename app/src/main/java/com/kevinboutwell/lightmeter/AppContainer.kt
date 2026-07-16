package com.kevinboutwell.lightmeter

import android.content.Context

/**
 * Manual dependency container — deliberately no DI framework for an app this
 * size. Screen ViewModels pull what they need from here via their factories.
 */
class AppContainer(private val appContext: Context) {
    // Database, DataStore and meters are added here as they are implemented.
}
