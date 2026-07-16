package com.kevinboutwell.lightmeter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kevinboutwell.lightmeter.LightMeterApp
import com.kevinboutwell.lightmeter.data.AppSettings
import com.kevinboutwell.lightmeter.data.CalibrationSource
import com.kevinboutwell.lightmeter.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setManualOffset(reflective: Boolean, offsetEv: Double) {
        viewModelScope.launch {
            repo.setCalibration(
                reflectiveMode = reflective,
                offsetEv = offsetEv,
                source = CalibrationSource.MANUAL,
                atEpochMs = System.currentTimeMillis(),
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LightMeterApp
                SettingsViewModel(app.container.settings)
            }
        }
    }
}
