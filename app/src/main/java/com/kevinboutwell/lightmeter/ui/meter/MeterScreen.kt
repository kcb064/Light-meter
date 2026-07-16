package com.kevinboutwell.lightmeter.ui.meter

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinboutwell.lightmeter.core.FilmStocks
import com.kevinboutwell.lightmeter.core.Priority
import com.kevinboutwell.lightmeter.core.Stops
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterScreen(
    onOpenLog: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MeterViewModel = viewModel(factory = MeterViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        permissionRequested = true
    }

    var showFilmSheet by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Light Meter", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenLog) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Reading log")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }

            TabRow(selectedTabIndex = if (state.mode == MeterMode.REFLECTIVE) 0 else 1) {
                Tab(
                    selected = state.mode == MeterMode.REFLECTIVE,
                    onClick = { viewModel.setMode(MeterMode.REFLECTIVE) },
                    text = { Text("Reflective") },
                )
                Tab(
                    selected = state.mode == MeterMode.INCIDENT,
                    onClick = { viewModel.setMode(MeterMode.INCIDENT) },
                    text = { Text("Incident") },
                    enabled = state.hasLightSensor,
                )
            }

            when (state.mode) {
                MeterMode.REFLECTIVE -> ReflectivePanel(
                    state = state,
                    hasPermission = hasCameraPermission,
                    permissionRequested = permissionRequested,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f),
                )
                MeterMode.INCIDENT -> IncidentPanel(state, modifier = Modifier.weight(1f))
            }

            EvReadout(state)
            SolutionPanel(state)

            DialsRow(modifier = Modifier.padding(top = 4.dp)) {
                StepperDial(
                    label = "ISO",
                    values = Stops.ISOS,
                    selected = state.iso,
                    onSelect = viewModel::setIso,
                )
                if (state.priority == Priority.APERTURE) {
                    StepperDial(
                        label = "APERTURE",
                        values = Stops.APERTURES,
                        selected = state.aperture,
                        onSelect = viewModel::setAperture,
                        valuePrefix = "f/",
                    )
                } else {
                    StepperDial(
                        label = "SHUTTER",
                        values = Stops.SHUTTERS,
                        selected = state.shutter,
                        onSelect = viewModel::setShutter,
                    )
                }
                EcDial(ecThirds = state.ecThirds, onChange = viewModel::setEcThirds)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.priority == Priority.APERTURE,
                    onClick = { viewModel.setPriority(Priority.APERTURE) },
                    label = { Text("A priority") },
                )
                FilterChip(
                    selected = state.priority == Priority.SHUTTER,
                    onClick = { viewModel.setPriority(Priority.SHUTTER) },
                    label = { Text("S priority") },
                )
                Spacer(Modifier.weight(1f))
                FilterChip(
                    selected = state.film.id != FilmStocks.NONE.id,
                    onClick = { showFilmSheet = true },
                    label = {
                        Text(
                            if (state.film.id == FilmStocks.NONE.id) "Film…"
                            else state.film.name,
                        )
                    },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::toggleHold,
                    modifier = Modifier.weight(1f),
                    enabled = state.ev100 != null || state.isHeld,
                ) {
                    Text(if (state.isHeld) "Resume" else "Hold")
                }
                Button(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier.weight(1f),
                    enabled = state.ev100 != null,
                ) {
                    Text("Save")
                }
            }
        }
    }

    if (showFilmSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFilmSheet = false },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                FilmStocks.ALL.forEach { film ->
                    TextButton(
                        onClick = {
                            viewModel.setFilm(film)
                            showFilmSheet = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (film.maker.isEmpty()) film.name else film.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (film.id == state.film.id) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            film.note?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save reading") },
            text = {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveReading(note)
                    showSaveDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ReflectivePanel(
    state: MeterUiState,
    hasPermission: Boolean,
    permissionRequested: Boolean,
    onRequestPermission: () -> Unit,
    viewModel: MeterViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                !hasPermission -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (permissionRequested) {
                            "Camera permission denied. Grant it in system settings, or use incident mode."
                        } else {
                            "Reflective metering uses the camera to read the light in the scene."
                        },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRequestPermission) { Text("Allow camera") }
                }
                state.cameraUnsupportedReason != null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        state.cameraUnsupportedReason,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                else -> ReflectiveViewfinder(
                    spotEnabled = state.spot && state.supportsSpot,
                    onBind = viewModel::bindCamera,
                    onMeterAt = viewModel::meterAt,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (hasPermission && state.cameraUnsupportedReason == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.spot,
                    onClick = { viewModel.setSpot(true) },
                    label = { Text("Spot (tap to meter)") },
                    enabled = state.supportsSpot,
                )
                FilterChip(
                    selected = !state.spot,
                    onClick = { viewModel.setSpot(false) },
                    label = { Text("Average") },
                )
            }
        }
    }
}

@Composable
private fun IncidentPanel(state: MeterUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!state.hasLightSensor) {
            Text(
                "This device has no ambient light sensor — incident metering isn't available.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Text(
                state.lux?.let { String.format(Locale.US, "%,.0f lux", it) } ?: "— lux",
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Point the sensor (top front of the phone) toward the camera, from the subject's position.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EvReadout(state: MeterUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            state.ev100?.let { String.format(Locale.US, "EV %.1f", it) } ?: "EV —",
            style = MaterialTheme.typography.headlineMedium,
            color = if (state.isHeld) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
        val status = when {
            state.isHeld -> "HELD"
            state.isSettling && state.mode == MeterMode.REFLECTIVE -> "settling…"
            state.notConverged -> "unsteady light — reading may be off"
            state.apertureIsFallback -> "lens aperture unreported — calibrate for accuracy"
            else -> null
        }
        if (status != null) {
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SolutionPanel(state: MeterUiState) {
    val solution = state.solution
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val big = when {
            solution == null -> "—"
            state.priority == Priority.APERTURE -> solution.shutter.nominal
            else -> "f/${solution.aperture.nominal}"
        }
        Text(big, style = MaterialTheme.typography.displayMedium)
        Text(
            solution?.let {
                "f/${it.aperture.nominal}  ·  ${it.shutter.nominal}  ·  ISO ${state.iso.nominal}"
            } ?: "waiting for a reading",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (solution != null) {
            if (solution.outOfRange) {
                Text(
                    "beyond the standard range — clamped",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            val corrected = solution.correctedSeconds
            if (corrected != null) {
                Text(
                    "${state.film.name} reciprocity: " +
                        "${Stops.formatSeconds(solution.meteredSeconds)} → " +
                        Stops.formatSeconds(corrected),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
