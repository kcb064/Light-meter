package com.kevinboutwell.lightmeter.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinboutwell.lightmeter.data.CalibrationSource
import com.kevinboutwell.lightmeter.data.ModeCalibration
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCalibrate: (reflective: Boolean) -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            Text(
                "Calibration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Phone meters vary between devices by up to ±0.7 EV. Use the wizard to " +
                    "match this meter to a trusted meter or the sunny-16 rule, or fine-tune " +
                    "the offset by hand.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            CalibrationSection(
                title = "Reflective (camera)",
                calibration = settings.reflective,
                onOffsetChange = { viewModel.setManualOffset(true, it) },
                onCalibrate = { onCalibrate(true) },
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            CalibrationSection(
                title = "Incident (light sensor)",
                calibration = settings.incident,
                onOffsetChange = { viewModel.setManualOffset(false, it) },
                onCalibrate = { onCalibrate(false) },
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(
                "About",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Reciprocity data is encoded from manufacturer datasheets as " +
                    "approximations. Verify against the current datasheet for critical " +
                    "work. All readings stay on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalibrationSection(
    title: String,
    calibration: ModeCalibration,
    onOffsetChange: (Double) -> Unit,
    onCalibrate: () -> Unit,
) {
    // Local slider position in tenths of an EV; committed on release.
    var tenths by remember(calibration.offsetEv) {
        mutableStateOf((calibration.offsetEv * 10).roundToInt())
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = tenths.toFloat(),
                onValueChange = { tenths = it.roundToInt() },
                onValueChangeFinished = { onOffsetChange(tenths / 10.0) },
                valueRange = -20f..20f,
                steps = 39,
                modifier = Modifier.weight(1f),
            )
            Text(
                String.format(Locale.US, "%+.1f EV", tenths / 10.0),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        val calibratedText = calibration.calibratedAtEpochMs?.let { at ->
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))
            val source = when (calibration.source) {
                CalibrationSource.REFERENCE_METER -> "reference meter"
                CalibrationSource.SUNNY_16 -> "sunny 16"
                CalibrationSource.MANUAL -> "manual"
                null -> "unknown"
            }
            "Calibrated $date via $source"
        } ?: "Not calibrated yet"
        Text(
            calibratedText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onCalibrate, modifier = Modifier.padding(top = 8.dp)) {
            Text("Calibration wizard")
        }
    }
}
