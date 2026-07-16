package com.kevinboutwell.lightmeter.ui.calibration

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kevinboutwell.lightmeter.LightMeterApp
import com.kevinboutwell.lightmeter.camera.CameraMeter
import com.kevinboutwell.lightmeter.core.Exposure
import com.kevinboutwell.lightmeter.core.StopValue
import com.kevinboutwell.lightmeter.core.Stops
import com.kevinboutwell.lightmeter.data.CalibrationSource
import com.kevinboutwell.lightmeter.data.SettingsRepository
import com.kevinboutwell.lightmeter.sensor.AmbientLightMeter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/** How the trusted reference value is entered. */
enum class ReferenceEntry { EV_DIRECT, CAMERA_SETTINGS }

data class CalibrationUiState(
    val isReflective: Boolean = true,
    val source: CalibrationSource = CalibrationSource.REFERENCE_METER,
    val entryMode: ReferenceEntry = ReferenceEntry.EV_DIRECT,
    /** Live, uncalibrated EV100 from the selected meter. */
    val liveEvRaw: Double? = null,
    val evText: String = "",
    val refIso: StopValue = Stops.ISO_100,
    val refAperture: StopValue = Stops.APERTURES.first { it.nominal == "16" },
    val refShutter: StopValue = Stops.SHUTTERS.first { it.nominal == "1/125" },
    /** Offset samples collected so far (trusted − app). */
    val samples: List<Double> = emptyList(),
    val applied: Boolean = false,
) {
    /** The trusted EV100 implied by the current source/entry, if parseable. */
    val trustedEv100: Double?
        get() = when (source) {
            CalibrationSource.SUNNY_16 -> SUNNY_16_EV100
            else -> when (entryMode) {
                ReferenceEntry.EV_DIRECT -> evText.trim().toDoubleOrNull()
                ReferenceEntry.CAMERA_SETTINGS -> Exposure.ev100FromExposure(
                    refAperture.exact, refShutter.exact, refIso.exact,
                )
            }
        }

    val proposedOffset: Double? get() = samples.takeIf { it.isNotEmpty() }?.average()

    /** Large per-sample spread suggests a non-linear phone meter (or a bad sample). */
    val spreadWarning: Boolean
        get() = samples.size >= 2 && (samples.max() - samples.min()) > 0.7

    /** A huge offset usually means a bad reference reading. */
    val bigOffsetWarning: Boolean get() = proposedOffset?.let { abs(it) > 1.5 } == true

    companion object {
        const val SUNNY_16_EV100 = 15.0
    }
}

class CalibrationViewModel(
    private val isReflective: Boolean,
    private val cameraMeter: CameraMeter,
    private val ambient: AmbientLightMeter,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val inputs = MutableStateFlow(CalibrationUiState(isReflective = isReflective))

    private val liveEvRaw = if (isReflective) {
        cameraMeter.reading.map { it?.ev100 }
    } else {
        ambient.lux
            .map { lux -> lux.takeIf { it > 0 }?.let { Exposure.ev100FromLux(it) } }
            .onStart { emit(null) }
    }

    val uiState: StateFlow<CalibrationUiState> =
        combine(inputs, liveEvRaw) { input, live -> input.copy(liveEvRaw = live) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                CalibrationUiState(isReflective = isReflective),
            )

    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        if (!isReflective) return
        viewModelScope.launch {
            runCatching { cameraMeter.bind(lifecycleOwner, previewView) }
        }
    }

    fun setSource(source: CalibrationSource) = inputs.update { it.copy(source = source) }

    fun setEntryMode(mode: ReferenceEntry) = inputs.update { it.copy(entryMode = mode) }

    fun setEvText(text: String) = inputs.update { it.copy(evText = text) }

    fun setRefIso(v: StopValue) = inputs.update { it.copy(refIso = v) }

    fun setRefAperture(v: StopValue) = inputs.update { it.copy(refAperture = v) }

    fun setRefShutter(v: StopValue) = inputs.update { it.copy(refShutter = v) }

    fun addSample() {
        val state = uiState.value
        val trusted = state.trustedEv100 ?: return
        val live = state.liveEvRaw ?: return
        inputs.update { it.copy(samples = it.samples + (trusted - live), applied = false) }
    }

    fun clearSamples() = inputs.update { it.copy(samples = emptyList(), applied = false) }

    fun apply() {
        val state = uiState.value
        val offset = state.proposedOffset ?: return
        viewModelScope.launch {
            settingsRepo.setCalibration(
                reflectiveMode = isReflective,
                offsetEv = offset,
                source = state.source,
                atEpochMs = System.currentTimeMillis(),
            )
            inputs.update { it.copy(applied = true) }
        }
    }

    companion object {
        fun factory(isReflective: Boolean) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LightMeterApp
                CalibrationViewModel(
                    isReflective = isReflective,
                    cameraMeter = app.container.cameraMeter,
                    ambient = app.container.ambientLightMeter,
                    settingsRepo = app.container.settings,
                )
            }
        }
    }
}
