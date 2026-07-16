package com.kevinboutwell.lightmeter.ui.calibration

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinboutwell.lightmeter.core.Stops
import com.kevinboutwell.lightmeter.data.CalibrationSource
import com.kevinboutwell.lightmeter.ui.meter.ReflectiveViewfinder
import com.kevinboutwell.lightmeter.ui.meter.StepperDial
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    isReflective: Boolean,
    onBack: () -> Unit,
    viewModel: CalibrationViewModel = viewModel(
        key = "calibration-$isReflective",
        factory = CalibrationViewModel.factory(isReflective),
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val hasCameraPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isReflective) "Calibrate reflective" else "Calibrate incident") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // --- Step 1: reference source ---
            Text(
                "1 · Reference",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.source == CalibrationSource.REFERENCE_METER,
                    onClick = { viewModel.setSource(CalibrationSource.REFERENCE_METER) },
                    label = { Text("Trusted meter") },
                )
                FilterChip(
                    selected = state.source == CalibrationSource.SUNNY_16,
                    onClick = { viewModel.setSource(CalibrationSource.SUNNY_16) },
                    label = { Text("Sunny 16") },
                )
            }

            when (state.source) {
                CalibrationSource.SUNNY_16 -> Text(
                    "On a clear day, mid-morning to mid-afternoon, meter a front-lit " +
                        "subject. The reference is EV 15 at ISO 100 — no equipment needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    Text(
                        "Meter the same evenly-lit scene (ideally a gray card) with a meter " +
                            "or camera you trust, and enter its reading.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.entryMode == ReferenceEntry.EV_DIRECT,
                            onClick = { viewModel.setEntryMode(ReferenceEntry.EV_DIRECT) },
                            label = { Text("EV") },
                        )
                        FilterChip(
                            selected = state.entryMode == ReferenceEntry.CAMERA_SETTINGS,
                            onClick = { viewModel.setEntryMode(ReferenceEntry.CAMERA_SETTINGS) },
                            label = { Text("Camera settings") },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (state.entryMode == ReferenceEntry.EV_DIRECT) {
                        OutlinedTextField(
                            value = state.evText,
                            onValueChange = viewModel::setEvText,
                            label = { Text("Trusted EV (at ISO 100)") },
                            singleLine = true,
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            StepperDial("ISO", Stops.ISOS, state.refIso, viewModel::setRefIso)
                            StepperDial("f/", Stops.APERTURES, state.refAperture, viewModel::setRefAperture)
                            StepperDial("SHUTTER", Stops.SHUTTERS, state.refShutter, viewModel::setRefShutter)
                        }
                        state.trustedEv100?.let {
                            Text(
                                String.format(Locale.US, "= EV %.1f at ISO 100", it),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Step 2: the app's own reading of the same scene ---
            Text(
                "2 · This phone's reading",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (isReflective) {
                if (hasCameraPermission.value) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    ) {
                        ReflectiveViewfinder(
                            spotEnabled = false,
                            onBind = viewModel::bindCamera,
                            onMeterAt = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    Text(
                        "Camera permission is needed — grant it from the meter screen first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Text(
                    "Point the light sensor at the light, from the subject's position.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                state.liveEvRaw?.let { String.format(Locale.US, "EV %.1f", it) } ?: "EV —",
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )

            // --- Step 3: samples ---
            Text(
                "3 · Samples",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Add a sample when both readings are of the same scene. More samples at " +
                    "different light levels give a better offset.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::addSample,
                    enabled = state.trustedEv100 != null && state.liveEvRaw != null,
                ) { Text("Add sample") }
                if (state.samples.isNotEmpty()) {
                    OutlinedButton(onClick = viewModel::clearSamples) { Text("Clear") }
                }
            }
            state.samples.forEachIndexed { i, sample ->
                Text(
                    String.format(Locale.US, "Sample %d: %+.1f EV", i + 1, sample),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            state.proposedOffset?.let { offset ->
                Spacer(Modifier.height(12.dp))
                Text(
                    String.format(
                        Locale.US,
                        "Offset: %+.1f EV — your phone reads %.1f EV %s",
                        offset,
                        kotlin.math.abs(offset),
                        if (offset > 0) "low (dark)" else "hot (bright)",
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.bigOffsetWarning) {
                    Text(
                        "That's a large offset — double-check the reference reading before applying.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.spreadWarning) {
                    Text(
                        "Samples disagree by more than ⅔ stop — this phone's meter may be " +
                            "non-linear, or one sample was off.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = viewModel::apply, enabled = !state.applied) {
                    Text(if (state.applied) "Applied ✓" else "Apply offset")
                }
            }
        }
    }
}
