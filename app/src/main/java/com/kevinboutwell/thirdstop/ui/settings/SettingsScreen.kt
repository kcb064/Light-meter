package com.kevinboutwell.thirdstop.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinboutwell.thirdstop.data.CalibrationSource
import com.kevinboutwell.thirdstop.data.ModeCalibration
import com.kevinboutwell.thirdstop.ui.components.DeckCard
import com.kevinboutwell.thirdstop.ui.components.ScreenHeader
import com.kevinboutwell.thirdstop.ui.meter.DragRuler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCalibrate: (reflective: Boolean) -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader(title = "Settings", onBack = onBack)
            Text(
                "Phone meters vary between devices by up to ±0.7 EV. Match this " +
                    "meter to a trusted meter or sunny 16, or fine-tune by hand.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(16.dp))

            CalibrationCard(
                title = "Reflective",
                subtitle = "camera",
                calibration = settings.reflective,
                onOffsetChange = { viewModel.setManualOffset(true, it) },
                onWizard = { onCalibrate(true) },
            )
            Spacer(Modifier.height(16.dp))
            CalibrationCard(
                title = "Incident",
                subtitle = "light sensor",
                calibration = settings.incident,
                onOffsetChange = { viewModel.setManualOffset(false, it) },
                onWizard = { onCalibrate(false) },
            )

            Spacer(Modifier.height(16.dp))
            DeckCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "ABOUT",
                        style = MaterialTheme.typography.labelMedium
                            .copy(letterSpacing = 1.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Reciprocity data is encoded from manufacturer datasheets as " +
                            "approximations. Verify against the current datasheet for " +
                            "critical work. All readings stay on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CalibrationCard(
    title: String,
    subtitle: String,
    calibration: ModeCalibration,
    onOffsetChange: (Double) -> Unit,
    onWizard: () -> Unit,
) {
    // Offset in tenths of an EV on the ruler, −2.0..+2.0.
    val tenths = (calibration.offsetEv * 10).roundToInt().coerceIn(-20, 20)
    val rulerLabels = remember { (-20..20).map(::formatTenths) }

    DeckCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${formatTenths(tenths)} EV",
                    style = OffsetValueStyle,
                    color = if (tenths != 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            DragRuler(
                label = null,
                values = rulerLabels,
                selectedIndex = tenths + 20,
                onSelect = { onOffsetChange((it - 20) / 10.0) },
                isFullStop = { it == 20 },
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    provenanceText(calibration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onWizard,
                    modifier = Modifier.height(36.dp),
                ) {
                    Text("Wizard")
                }
            }
        }
    }
}

private fun provenanceText(calibration: ModeCalibration): String =
    calibration.calibratedAtEpochMs?.let { at ->
        val date = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(at))
        val source = when (calibration.source) {
            CalibrationSource.REFERENCE_METER -> "reference meter"
            CalibrationSource.SUNNY_16 -> "sunny 16"
            CalibrationSource.MANUAL -> "manual offset"
            null -> "unknown"
        }
        "Calibrated $date via $source"
    } ?: "Not calibrated yet"

private fun formatTenths(tenths: Int): String = when {
    tenths > 0 -> String.format(Locale.US, "+%.1f", tenths / 10.0)
    tenths < 0 -> String.format(Locale.US, "−%.1f", -tenths / 10.0)
    else -> "0.0"
}

/** Big right-aligned offset value in the card header: 22sp Medium tnum. */
private val OffsetValueStyle = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 22.sp,
    fontFeatureSettings = "tnum",
)
