package com.kevinboutwell.lightmeter.ui.meter

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinboutwell.lightmeter.core.FilmStocks
import com.kevinboutwell.lightmeter.core.Priority
import com.kevinboutwell.lightmeter.core.Stops
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MeterScreen(
    onOpenLog: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MeterViewModel = viewModel(factory = MeterViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            EvStatusChip(state)
            StatusLine(state)
            SolutionBlock(state)
            Spacer(Modifier.height(8.dp))
            ViewfinderCard(
                state = state,
                hasPermission = hasCameraPermission,
                permissionRequested = permissionRequested,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onToggleMode = {
                    viewModel.setMode(
                        if (state.mode == MeterMode.REFLECTIVE) MeterMode.INCIDENT
                        else MeterMode.REFLECTIVE,
                    )
                },
                onLongPressHold = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleHold()
                },
                viewModel = viewModel,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            ControlDeck(
                state = state,
                viewModel = viewModel,
                onOpenFilmSheet = { showFilmSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            ActionRow(
                state = state,
                onOpenLog = onOpenLog,
                onOpenSettings = onOpenSettings,
                onToggleHold = viewModel::toggleHold,
                onSave = {
                    viewModel.saveReading(null)
                    scope.launch { snackbarHostState.showSnackbar("Reading saved") }
                },
                onSaveWithNote = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showSaveDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
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
                    scope.launch { snackbarHostState.showSnackbar("Reading saved") }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// ---- top readout ----

@Composable
private fun EvStatusChip(state: MeterUiState) {
    val evText = state.ev100?.let { String.format(Locale.US, "EV %.1f", it) } ?: "EV —"
    val suffix = when {
        state.isHeld -> "held"
        state.ev100 != null -> "live"
        else -> "waiting"
    }
    val textColor = if (state.isHeld) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.height(28.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.ev100 != null) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
            Text(
                "$evText · $suffix",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = textColor,
            )
        }
    }
}

/** Fixed-height slot so status text appearing never shifts the layout. */
@Composable
private fun StatusLine(state: MeterUiState) {
    val (text, color) = when {
        state.solution?.outOfRange == true ->
            "beyond the standard range — clamped" to MaterialTheme.colorScheme.error
        state.isSettling && state.mode == MeterMode.REFLECTIVE ->
            "settling…" to MaterialTheme.colorScheme.onSurfaceVariant
        state.notConverged ->
            "unsteady light — reading may be off" to MaterialTheme.colorScheme.onSurfaceVariant
        state.apertureIsFallback ->
            "lens aperture unreported — calibrate for accuracy" to
                MaterialTheme.colorScheme.onSurfaceVariant
        else -> null to Color.Unspecified
    }
    Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) {
        if (text != null) {
            Text(text, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
private fun SolutionBlock(state: MeterUiState) {
    val solution = state.solution
    val glowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // Decorative amber glow behind the answer, ~260x120dp ellipse.
                val glowWidth = 260.dp.toPx()
                scale(scaleX = 1f, scaleY = 120.dp.toPx() / glowWidth) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(glowColor, Color.Transparent),
                            center = center,
                            radius = glowWidth / 2,
                        ),
                        radius = glowWidth / 2,
                        center = center,
                    )
                }
            },
    ) {
        val big = when {
            solution == null -> "—"
            state.priority == Priority.APERTURE -> solution.shutter.nominal
            else -> "f/${solution.aperture.nominal}"
        }
        Text(
            big,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            if (state.priority == Priority.APERTURE) "SHUTTER · A PRIORITY"
            else "APERTURE · S PRIORITY",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
            color = MaterialTheme.colorScheme.primary,
        )
        val corrected = solution?.correctedSeconds
        if (solution != null && corrected != null) {
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

// ---- viewfinder card ----

@Composable
private fun ViewfinderCard(
    state: MeterUiState,
    hasPermission: Boolean,
    permissionRequested: Boolean,
    onRequestPermission: () -> Unit,
    onToggleMode: () -> Unit,
    onLongPressHold: () -> Unit,
    viewModel: MeterViewModel,
    modifier: Modifier = Modifier,
) {
    val cameraReady = hasPermission && state.cameraUnsupportedReason == null
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        when {
            state.mode == MeterMode.INCIDENT -> IncidentGlass(
                state = state,
                onLongPressHold = onLongPressHold,
                modifier = Modifier.fillMaxSize(),
            )
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
                onLongPress = onLongPressHold,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (state.mode == MeterMode.REFLECTIVE && cameraReady) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassChip(
                    text = "Spot",
                    selected = state.spot,
                    enabled = state.supportsSpot,
                    onClick = { viewModel.setSpot(true) },
                )
                GlassChip(
                    text = "Average",
                    selected = !state.spot,
                    onClick = { viewModel.setSpot(false) },
                )
            }
        }
        GlassChip(
            text = if (state.mode == MeterMode.REFLECTIVE) "Incident" else "Reflective",
            selected = state.mode == MeterMode.INCIDENT,
            enabled = state.hasLightSensor,
            onClick = onToggleMode,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        )
        val hint = when {
            state.mode == MeterMode.INCIDENT && state.hasLightSensor -> "long-press to hold"
            state.mode == MeterMode.REFLECTIVE && cameraReady ->
                "tap to meter · long-press to hold"
            else -> null
        }
        if (hint != null) {
            Text(
                hint,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun IncidentGlass(
    state: MeterUiState,
    onLongPressHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPressHold() })
            }
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
private fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .height(28.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                else Color.Black.copy(alpha = 0.5f),
            )
            .then(
                if (selected) Modifier
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
            else Color.White.copy(alpha = 0.8f),
        )
    }
}

// ---- control deck ----

@Composable
private fun ControlDeck(
    state: MeterUiState,
    viewModel: MeterViewModel,
    onOpenFilmSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isoLabels = remember { Stops.ISOS.map { it.nominal } }
    val apertureLabels = remember { Stops.APERTURES.map { "f/${it.nominal}" } }
    val shutterLabels = remember { Stops.SHUTTERS.map { it.nominal } }
    val ecLabels = remember { (-9..9).map { formatEcThirds(it) } }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column {
            DragRuler(
                label = "ISO",
                values = isoLabels,
                selectedIndex = Stops.ISOS
                    .indexOfFirst { it.nominal == state.iso.nominal }
                    .coerceAtLeast(0),
                onSelect = { viewModel.setIso(Stops.ISOS[it]) },
            )
            DeckDivider()
            if (state.priority == Priority.APERTURE) {
                DragRuler(
                    label = "APERTURE",
                    values = apertureLabels,
                    selectedIndex = Stops.APERTURES
                        .indexOfFirst { it.nominal == state.aperture.nominal }
                        .coerceAtLeast(0),
                    onSelect = { viewModel.setAperture(Stops.APERTURES[it]) },
                )
            } else {
                DragRuler(
                    label = "SHUTTER",
                    values = shutterLabels,
                    selectedIndex = Stops.SHUTTERS
                        .indexOfFirst { it.nominal == state.shutter.nominal }
                        .coerceAtLeast(0),
                    onSelect = { viewModel.setShutter(Stops.SHUTTERS[it]) },
                )
            }
            DeckDivider()
            DragRuler(
                label = "EC",
                values = ecLabels,
                selectedIndex = state.ecThirds + 9,
                onSelect = { viewModel.setEcThirds(it - 9) },
                isFullStop = { (it - 9) % 3 == 0 },
            )
            DeckDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                DeckChip(
                    text = "A priority",
                    selected = state.priority == Priority.APERTURE,
                    onClick = { viewModel.setPriority(Priority.APERTURE) },
                )
                DeckChip(
                    text = "S priority",
                    selected = state.priority == Priority.SHUTTER,
                    onClick = { viewModel.setPriority(Priority.SHUTTER) },
                )
                DeckChip(
                    text = if (state.film.id == FilmStocks.NONE.id) "Film…" else state.film.name,
                    selected = state.film.id != FilmStocks.NONE.id,
                    onClick = onOpenFilmSheet,
                )
            }
        }
    }
}

@Composable
private fun DeckDivider() {
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun DeckChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---- action row ----

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ActionRow(
    state: MeterUiState,
    onOpenLog: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleHold: () -> Unit,
    onSave: () -> Unit,
    onSaveWithNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val holdEnabled = state.ev100 != null || state.isHeld
    val saveEnabled = state.ev100 != null
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedIconButton(onClick = onOpenLog, modifier = Modifier.size(52.dp)) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Reading log")
        }
        OutlinedIconButton(onClick = onOpenSettings, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
        }
        OutlinedButton(
            onClick = onToggleHold,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            enabled = holdEnabled,
            border = BorderStroke(
                1.dp,
                if (holdEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(if (state.isHeld) "Resume" else "Hold")
        }
        // Custom pill instead of Button: plain tap saves immediately,
        // long-press opens the save-with-note dialog.
        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(CircleShape)
                .background(
                    if (saveEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                )
                .combinedClickable(
                    enabled = saveEnabled,
                    onClick = onSave,
                    onLongClick = onSaveWithNote,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Save",
                style = MaterialTheme.typography.labelLarge,
                color = if (saveEnabled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}
