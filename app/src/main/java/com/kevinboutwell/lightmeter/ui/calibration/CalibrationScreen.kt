package com.kevinboutwell.lightmeter.ui.calibration

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinboutwell.lightmeter.core.Stops
import com.kevinboutwell.lightmeter.data.CalibrationSource
import com.kevinboutwell.lightmeter.ui.components.DeckCard
import com.kevinboutwell.lightmeter.ui.components.Hairline
import com.kevinboutwell.lightmeter.ui.components.ScreenHeader
import com.kevinboutwell.lightmeter.ui.meter.DragRuler
import com.kevinboutwell.lightmeter.ui.meter.ReflectiveViewfinder
import java.util.Locale

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

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader(
                title = if (isReflective) "Calibrate · Reflective" else "Calibrate · Incident",
                onBack = onBack,
            )

            ReferenceCard(state = state, viewModel = viewModel)
            Spacer(Modifier.height(16.dp))
            ThisPhoneCard(
                state = state,
                isReflective = isReflective,
                hasCameraPermission = hasCameraPermission.value,
                viewModel = viewModel,
            )
            Spacer(Modifier.height(16.dp))
            SamplesCard(state = state, viewModel = viewModel)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---- step 1: reference ----

@Composable
private fun ReferenceCard(state: CalibrationUiState, viewModel: CalibrationViewModel) {
    StepCard(number = 1, label = "Reference") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CalChip(
                text = "Trusted meter",
                selected = state.source == CalibrationSource.REFERENCE_METER,
                onClick = { viewModel.setSource(CalibrationSource.REFERENCE_METER) },
            )
            Spacer(Modifier.width(8.dp))
            CalChip(
                text = "Sunny 16",
                selected = state.source == CalibrationSource.SUNNY_16,
                onClick = { viewModel.setSource(CalibrationSource.SUNNY_16) },
            )
            Spacer(Modifier.weight(1f))
            if (state.source == CalibrationSource.REFERENCE_METER) {
                CalChip(
                    text = "EV",
                    selected = state.entryMode == ReferenceEntry.EV_DIRECT,
                    onClick = { viewModel.setEntryMode(ReferenceEntry.EV_DIRECT) },
                )
                Spacer(Modifier.width(8.dp))
                CalChip(
                    text = "Camera",
                    selected = state.entryMode == ReferenceEntry.CAMERA_SETTINGS,
                    onClick = { viewModel.setEntryMode(ReferenceEntry.CAMERA_SETTINGS) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            state.source == CalibrationSource.SUNNY_16 -> Text(
                "On a clear day, mid-morning to mid-afternoon, meter a front-lit " +
                    "subject. The reference is EV 15 at ISO 100 — no equipment needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            state.entryMode == ReferenceEntry.EV_DIRECT -> {
                Text(
                    "Meter the same evenly-lit scene (ideally a gray card) with a meter " +
                        "or camera you trust, and enter its reading.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.evText,
                    onValueChange = viewModel::setEvText,
                    label = { Text("Trusted EV (at ISO 100)") },
                    singleLine = true,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            else -> {
                DragRuler(
                    label = "ISO",
                    values = IsoLabels,
                    selectedIndex = Stops.ISOS
                        .indexOfFirst { it.nominal == state.refIso.nominal }
                        .coerceAtLeast(0),
                    onSelect = { viewModel.setRefIso(Stops.ISOS[it]) },
                )
                Hairline()
                DragRuler(
                    label = "APERTURE",
                    values = ApertureLabels,
                    selectedIndex = Stops.APERTURES
                        .indexOfFirst { it.nominal == state.refAperture.nominal }
                        .coerceAtLeast(0),
                    onSelect = { viewModel.setRefAperture(Stops.APERTURES[it]) },
                )
                Hairline()
                DragRuler(
                    label = "SHUTTER",
                    values = ShutterLabels,
                    selectedIndex = Stops.SHUTTERS
                        .indexOfFirst { it.nominal == state.refShutter.nominal }
                        .coerceAtLeast(0),
                    onSelect = { viewModel.setRefShutter(Stops.SHUTTERS[it]) },
                )
                state.trustedEv100?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        String.format(Locale.US, "= EV %.1f at ISO 100", it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ---- step 2: this phone ----

@Composable
private fun ThisPhoneCard(
    state: CalibrationUiState,
    isReflective: Boolean,
    hasCameraPermission: Boolean,
    viewModel: CalibrationViewModel,
) {
    StepCard(
        number = 2,
        label = "This phone",
        trailing = {
            Box(
                Modifier
                    .size(6.dp)
                    .background(
                        if (state.liveEvRaw != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    ),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "live",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        if (isReflective) {
            if (hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .aspectRatio(3f)
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    ReflectiveViewfinder(
                        spotEnabled = false,
                        onBind = viewModel::bindCamera,
                        onMeterAt = {},
                        modifier = Modifier.fillMaxSize(),
                        fillBox = true,
                    )
                }
            } else {
                Text(
                    "Camera permission is needed — grant it from the meter screen first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else {
            Text(
                "Point the light sensor at the light, from the subject's position.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                state.liveEvRaw?.let { String.format(Locale.US, "EV %.1f", it) } ?: "EV —",
                style = MaterialTheme.typography.displaySmall
                    .copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onSurface,
            )
            val trusted = state.trustedEv100
            val live = state.liveEvRaw
            if (trusted != null && live != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    String.format(Locale.US, "Δ %s", formatSignedEv(trusted - live)),
                    style = MaterialTheme.typography.titleMedium
                        .copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
    }
}

// ---- step 3: samples ----

@Composable
private fun SamplesCard(state: CalibrationUiState, viewModel: CalibrationViewModel) {
    StepCard(
        number = 3,
        label = "Samples",
        trailing = {
            if (state.samples.isNotEmpty()) {
                TextButton(onClick = viewModel::clearSamples) { Text("Clear") }
                Spacer(Modifier.width(4.dp))
            }
            OutlinedButton(
                onClick = viewModel::addSample,
                enabled = state.trustedEv100 != null && state.liveEvRaw != null,
                modifier = Modifier.height(36.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("Add sample")
            }
        },
    ) {
        if (state.samples.isEmpty()) {
            Text(
                "Add a sample when both readings are of the same scene. More samples " +
                    "at different light levels give a better offset.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.samples.forEach { sample ->
                        SampleChip(formatSignedEv(sample))
                    }
                }
                Text(
                    if (state.samples.size == 1) "1 sample" else "${state.samples.size} samples",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.proposedOffset?.let { offset ->
            Spacer(Modifier.height(16.dp))
            Hairline()
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${formatSignedEv(offset)} EV",
                        style = VerdictStyle,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        when {
                            offset > 0 -> String.format(
                                Locale.US, "your phone reads %.1f EV low (dark)", offset,
                            )
                            offset < 0 -> String.format(
                                Locale.US, "your phone reads %.1f EV hot (bright)", -offset,
                            )
                            else -> "your phone matches the reference"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Button(
                    onClick = viewModel::apply,
                    enabled = !state.applied,
                    modifier = Modifier.height(48.dp),
                ) {
                    Text(if (state.applied) "Applied ✓" else "Apply")
                }
            }
            if (state.bigOffsetWarning) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "That's a large offset — double-check the reference reading before applying.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (state.spreadWarning) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Samples disagree by more than ⅔ stop — this phone's meter may be " +
                        "non-linear, or one sample was off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

// ---- shared pieces ----

/**
 * Deck card with a numbered step header: 24dp amber-container circle plus a
 * letterspaced caps label, optional trailing content on the header row.
 * Step content children pad themselves horizontally so full-bleed rows
 * (drag rulers, hairlines) can reach the card edge.
 */
@Composable
private fun StepCard(
    number: Int,
    label: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    DeckCard(modifier = modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$number",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelMedium
                        .copy(letterSpacing = 1.5.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                trailing?.invoke()
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/** Selectable deck chip, 32dp tall, radius 8dp. */
@Composable
private fun CalChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .height(32.dp)
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

/** Read-only sample chip, e.g. "+0.5". */
@Composable
private fun SampleChip(text: String) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SampleChipBg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatSignedEv(value: Double): String = when {
    value >= 0.05 -> String.format(Locale.US, "+%.1f", value)
    value <= -0.05 -> String.format(Locale.US, "−%.1f", -value)
    else -> "0.0"
}

private val IsoLabels = Stops.ISOS.map { it.nominal }
private val ApertureLabels = Stops.APERTURES.map { "f/${it.nominal}" }
private val ShutterLabels = Stops.SHUTTERS.map { it.nominal }
private val SampleChipBg = Color(0xFF232328)

/** Offset verdict: 20sp Medium tnum. */
private val VerdictStyle = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 20.sp,
    fontFeatureSettings = "tnum",
)
