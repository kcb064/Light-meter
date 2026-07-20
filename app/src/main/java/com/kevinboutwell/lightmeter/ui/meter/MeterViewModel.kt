package com.kevinboutwell.lightmeter.ui.meter

import androidx.camera.core.MeteringPoint
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kevinboutwell.lightmeter.LightMeterApp
import com.kevinboutwell.lightmeter.camera.AeReading
import com.kevinboutwell.lightmeter.camera.CameraMeter
import com.kevinboutwell.lightmeter.camera.ZoomCaps
import com.kevinboutwell.lightmeter.core.Exposure
import com.kevinboutwell.lightmeter.core.ExposureSolver
import com.kevinboutwell.lightmeter.core.FilmStock
import com.kevinboutwell.lightmeter.core.FilmStocks
import com.kevinboutwell.lightmeter.core.Priority
import com.kevinboutwell.lightmeter.core.Solution
import com.kevinboutwell.lightmeter.core.StopValue
import com.kevinboutwell.lightmeter.core.Stops
import com.kevinboutwell.lightmeter.data.DialState
import com.kevinboutwell.lightmeter.data.ReadingDao
import com.kevinboutwell.lightmeter.data.ReadingEntity
import com.kevinboutwell.lightmeter.data.SettingsRepository
import com.kevinboutwell.lightmeter.sensor.AmbientLightMeter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MeterMode { REFLECTIVE, INCIDENT }

data class HeldReading(val ev100Raw: Double, val lux: Double?)

data class MeterUiState(
    val mode: MeterMode = MeterMode.REFLECTIVE,
    val spot: Boolean = true,
    /** Calibrated scene EV100, null until a reading lands. */
    val ev100: Double? = null,
    val lux: Double? = null,
    val isSettling: Boolean = true,
    val isHeld: Boolean = false,
    val iso: StopValue = Stops.ISOS.first { it.nominal == "400" },
    val priority: Priority = Priority.APERTURE,
    val aperture: StopValue = Stops.DEFAULT_APERTURE,
    val shutter: StopValue = Stops.DEFAULT_SHUTTER,
    val ecThirds: Int = 0,
    val film: FilmStock = FilmStocks.NONE,
    val solution: Solution? = null,
    val hasLightSensor: Boolean = true,
    val supportsSpot: Boolean = true,
    val cameraUnsupportedReason: String? = null,
    val apertureIsFallback: Boolean = false,
    val notConverged: Boolean = false,
    /** Null until the camera reports usable focal-length data. */
    val zoomCaps: ZoomCaps? = null,
    /** Current 35mm-equivalent framing, clamped; null while [zoomCaps] is null. */
    val zoomMm: Float? = null,
    val lensPresetMm: Int? = null,
)

class MeterViewModel(
    private val cameraMeter: CameraMeter,
    private val ambient: AmbientLightMeter,
    private val settingsRepo: SettingsRepository,
    private val readingDao: ReadingDao,
) : ViewModel() {

    private data class Inputs(
        val mode: MeterMode = MeterMode.REFLECTIVE,
        val spot: Boolean = true,
        val held: HeldReading? = null,
        val iso: StopValue = Stops.ISOS.first { it.nominal == "400" },
        val priority: Priority = Priority.APERTURE,
        val aperture: StopValue = Stops.DEFAULT_APERTURE,
        val shutter: StopValue = Stops.DEFAULT_SHUTTER,
        val ecThirds: Int = 0,
        val film: FilmStock = FilmStocks.NONE,
        /** Requested 35mm-equivalent zoom; null = camera default (main lens). */
        val zoomMm: Float? = null,
        val lensPresetMm: Int? = null,
    )

    private data class CameraState(
        val reading: AeReading?,
        val isSettling: Boolean,
        val supportsSpot: Boolean,
        val unsupportedReason: String?,
    )

    private val inputs = MutableStateFlow(Inputs())

    private val cameraState = combine(
        cameraMeter.reading,
        cameraMeter.isSettling,
        cameraMeter.supportsSpot,
        cameraMeter.unsupportedReason,
    ) { reading, settling, spot, unsupported ->
        CameraState(reading, settling, spot, unsupported)
    }

    private val ambientLux: kotlinx.coroutines.flow.Flow<Double?> =
        ambient.lux.map { it as Double? }.onStart { emit(null) }

    val uiState: StateFlow<MeterUiState> = combine(
        inputs, cameraState, ambientLux, settingsRepo.settings, cameraMeter.zoomCaps,
    ) { input, camera, lux, settings, zoomCaps ->
        val rawEv100: Double?
        val calibratedEv100: Double?
        val shownLux: Double?
        when (input.mode) {
            MeterMode.REFLECTIVE -> {
                rawEv100 = input.held?.ev100Raw ?: camera.reading?.ev100
                calibratedEv100 = rawEv100?.plus(settings.reflective.offsetEv)
                shownLux = null
            }
            MeterMode.INCIDENT -> {
                rawEv100 = input.held?.ev100Raw
                    ?: lux?.takeIf { it > 0 }?.let { Exposure.ev100FromLux(it) }
                calibratedEv100 = rawEv100?.plus(settings.incident.offsetEv)
                shownLux = input.held?.lux ?: lux
            }
        }

        val fixed = if (input.priority == Priority.APERTURE) input.aperture else input.shutter
        val solution: Solution? = calibratedEv100?.let { ev ->
            ExposureSolver.solve(ev, input.iso, input.ecThirds / 3.0, input.priority, fixed, input.film)
        }

        MeterUiState(
            mode = input.mode,
            spot = input.spot,
            ev100 = calibratedEv100,
            lux = shownLux,
            isSettling = input.mode == MeterMode.REFLECTIVE && input.held == null && camera.isSettling,
            isHeld = input.held != null,
            iso = input.iso,
            priority = input.priority,
            aperture = input.aperture,
            shutter = input.shutter,
            ecThirds = input.ecThirds,
            film = input.film,
            solution = solution,
            hasLightSensor = ambient.isAvailable,
            supportsSpot = camera.supportsSpot,
            cameraUnsupportedReason = camera.unsupportedReason,
            apertureIsFallback = input.mode == MeterMode.REFLECTIVE &&
                camera.reading?.apertureIsFallback == true,
            notConverged = input.mode == MeterMode.REFLECTIVE && input.held == null &&
                camera.reading?.converged == false,
            zoomCaps = zoomCaps,
            zoomMm = zoomCaps?.let {
                (input.zoomMm ?: it.mainEqMm).coerceIn(it.minMm, it.maxMm)
            },
            lensPresetMm = input.lensPresetMm,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeterUiState())

    init {
        viewModelScope.launch {
            val dials = settingsRepo.settings.first().dials
            inputs.update { current ->
                current.copy(
                    mode = runCatching { MeterMode.valueOf(dials.mode) }.getOrDefault(MeterMode.REFLECTIVE),
                    spot = dials.spot,
                    iso = Stops.ISOS.firstOrNull { it.nominal == dials.isoNominal } ?: current.iso,
                    priority = runCatching { Priority.valueOf(dials.priority) }.getOrDefault(Priority.APERTURE),
                    aperture = Stops.APERTURES.firstOrNull { it.nominal == dials.apertureNominal } ?: current.aperture,
                    shutter = Stops.SHUTTERS.firstOrNull { it.nominal == dials.shutterNominal } ?: current.shutter,
                    ecThirds = dials.ecThirds.coerceIn(-9, 9),
                    film = FilmStocks.byId(dials.filmId),
                    zoomMm = dials.zoomMm?.toFloat(),
                    lensPresetMm = dials.lensPresetMm,
                )
            }
            // CameraMeter remembers the framing and applies it on (re)bind.
            dials.zoomMm?.let { cameraMeter.setZoomMm(it.toFloat()) }
        }
    }

    private fun persistDials() {
        val i = inputs.value
        viewModelScope.launch {
            settingsRepo.saveDialState(
                DialState(
                    isoNominal = i.iso.nominal,
                    priority = i.priority.name,
                    apertureNominal = i.aperture.nominal,
                    shutterNominal = i.shutter.nominal,
                    ecThirds = i.ecThirds,
                    filmId = i.film.id,
                    mode = i.mode.name,
                    spot = i.spot,
                    zoomMm = i.zoomMm?.toDouble(),
                    lensPresetMm = i.lensPresetMm,
                ),
            )
        }
    }

    // ---- camera lifecycle ----

    /**
     * Binds the camera to the calling screen's lifecycle owner; CameraX
     * releases it automatically when that lifecycle stops, so there is no
     * explicit unbind.
     */
    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        viewModelScope.launch {
            runCatching { cameraMeter.bind(lifecycleOwner, previewView) }
        }
    }

    // ---- user actions ----

    fun setMode(mode: MeterMode) {
        inputs.update { it.copy(mode = mode, held = null) }
        persistDials()
    }

    fun setSpot(spot: Boolean) {
        inputs.update { it.copy(spot = spot, held = null) }
        if (!spot) cameraMeter.meterAverage()
        persistDials()
    }

    fun meterAt(point: MeteringPoint) {
        if (inputs.value.spot) {
            inputs.update { it.copy(held = null) }
            cameraMeter.meterAt(point)
        }
    }

    fun toggleHold() {
        inputs.update { current ->
            if (current.held != null) {
                current.copy(held = null)
            } else {
                val state = uiState.value
                // Hold stores the raw (uncalibrated) EV so calibration changes
                // still apply to a held reading.
                val raw = when (current.mode) {
                    MeterMode.REFLECTIVE -> cameraMeter.reading.value?.ev100
                    MeterMode.INCIDENT -> state.lux?.takeIf { it > 0 }?.let { Exposure.ev100FromLux(it) }
                }
                if (raw == null) current
                else current.copy(held = HeldReading(raw, state.lux))
            }
        }
    }

    fun setIso(value: StopValue) { inputs.update { it.copy(iso = value) }; persistDials() }

    fun setPriority(priority: Priority) { inputs.update { it.copy(priority = priority) }; persistDials() }

    fun setAperture(value: StopValue) { inputs.update { it.copy(aperture = value) }; persistDials() }

    fun setShutter(value: StopValue) { inputs.update { it.copy(shutter = value) }; persistDials() }

    fun setEcThirds(thirds: Int) { inputs.update { it.copy(ecThirds = thirds.coerceIn(-9, 9)) }; persistDials() }

    fun setFilm(film: FilmStock) { inputs.update { it.copy(film = film) }; persistDials() }

    // ---- zoom ----

    private var zoomPersistJob: Job? = null

    /** Jump exactly to a focal length (lens pill tap, preset selection). */
    fun setZoomMm(mm: Float) = applyZoom(mm, debouncePersist = false)

    /** Incremental pinch update: multiply the current framing by [factor]. */
    fun pinchZoomBy(factor: Float) {
        val caps = cameraMeter.zoomCaps.value ?: return
        val current = (inputs.value.zoomMm ?: caps.mainEqMm).coerceIn(caps.minMm, caps.maxMm)
        applyZoom(current * factor, debouncePersist = true)
    }

    fun setLensPreset(mm: Int?) {
        inputs.update { it.copy(lensPresetMm = mm) }
        persistDials()
        if (mm != null) applyZoom(mm.toFloat(), debouncePersist = false)
    }

    private fun applyZoom(mm: Float, debouncePersist: Boolean) {
        val caps = cameraMeter.zoomCaps.value ?: return
        val clamped = mm.coerceIn(caps.minMm, caps.maxMm)
        inputs.update { it.copy(zoomMm = clamped) }
        cameraMeter.setZoomMm(clamped)
        if (debouncePersist) {
            // A pinch emits per-frame updates; don't hit DataStore for each.
            zoomPersistJob?.cancel()
            zoomPersistJob = viewModelScope.launch {
                delay(400)
                persistDials()
            }
        } else {
            persistDials()
        }
    }

    /**
     * Re-applies a logged reading's setup to the meter: ISO, EC, film, and
     * both fixed dials. Priority isn't stored on readings, so the current
     * priority keeps whichever restored dial it fixes.
     */
    fun applyReading(reading: ReadingEntity) {
        inputs.update { current ->
            current.copy(
                iso = Stops.ISOS.firstOrNull { it.nominal == reading.isoNominal } ?: current.iso,
                aperture = Stops.APERTURES.firstOrNull { it.nominal == reading.apertureNominal }
                    ?: current.aperture,
                shutter = Stops.SHUTTERS.firstOrNull { it.nominal == reading.shutterNominal }
                    ?: current.shutter,
                ecThirds = reading.ecThirds.coerceIn(-9, 9),
                film = FilmStocks.byId(reading.filmId),
            )
        }
        persistDials()
    }

    fun saveReading(note: String?) {
        val state = uiState.value
        val ev = state.ev100 ?: return
        val solution = state.solution
        val entity = ReadingEntity(
            timestampEpochMs = System.currentTimeMillis(),
            mode = when {
                state.mode == MeterMode.INCIDENT -> "INCIDENT"
                state.spot -> "REFLECTIVE_SPOT"
                else -> "REFLECTIVE_AVG"
            },
            ev100 = ev,
            lux = state.lux.takeIf { state.mode == MeterMode.INCIDENT },
            isoNominal = state.iso.nominal,
            apertureNominal = solution?.aperture?.nominal ?: state.aperture.nominal,
            shutterNominal = solution?.shutter?.nominal ?: state.shutter.nominal,
            ecThirds = state.ecThirds,
            filmId = state.film.id.takeIf { it != FilmStocks.NONE.id },
            correctedShutterSec = solution?.correctedSeconds,
            note = note?.takeIf { it.isNotBlank() },
        )
        viewModelScope.launch { readingDao.insert(entity) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LightMeterApp
                MeterViewModel(
                    cameraMeter = app.container.cameraMeter,
                    ambient = app.container.ambientLightMeter,
                    settingsRepo = app.container.settings,
                    readingDao = app.container.readingDao,
                )
            }
        }
    }
}
