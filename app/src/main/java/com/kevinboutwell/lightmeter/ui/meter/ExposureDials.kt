package com.kevinboutwell.lightmeter.ui.meter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.kevinboutwell.lightmeter.core.StopValue

/**
 * A compact stepper dial: label on top, big value, up/down arrows. Steps
 * through a third-stop table by index.
 */
@Composable
fun StepperDial(
    label: String,
    values: List<StopValue>,
    selected: StopValue,
    onSelect: (StopValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val index = values.indexOfFirst { it.nominal == selected.nominal }.coerceAtLeast(0)
    StepperColumn(
        label = label,
        valueText = selected.nominal,
        onStepUp = { if (index < values.lastIndex) onSelect(values[index + 1]) },
        onStepDown = { if (index > 0) onSelect(values[index - 1]) },
        canStepUp = enabled && index < values.lastIndex,
        canStepDown = enabled && index > 0,
        modifier = modifier,
    )
}

/** Stepper over exposure-compensation thirds: -9..+9 shown as "-1⅓" etc. */
@Composable
fun EcDial(
    ecThirds: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    StepperColumn(
        label = "EC",
        valueText = formatEcThirds(ecThirds),
        onStepUp = { onChange(ecThirds + 1) },
        onStepDown = { onChange(ecThirds - 1) },
        canStepUp = ecThirds < 9,
        canStepDown = ecThirds > -9,
        modifier = modifier,
    )
}

fun formatEcThirds(thirds: Int): String {
    if (thirds == 0) return "0"
    val sign = if (thirds > 0) "+" else "−"
    val whole = kotlin.math.abs(thirds) / 3
    val frac = when (kotlin.math.abs(thirds) % 3) {
        1 -> "⅓"
        2 -> "⅔"
        else -> ""
    }
    val wholeText = if (whole > 0) "$whole" else ""
    return "$sign$wholeText$frac"
}

@Composable
private fun StepperColumn(
    label: String,
    valueText: String,
    onStepUp: () -> Unit,
    onStepDown: () -> Unit,
    canStepUp: Boolean,
    canStepDown: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(76.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StepButton(Icons.Filled.KeyboardArrowUp, enabled = canStepUp, onClick = onStepUp)
        Text(
            valueText,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
        StepButton(Icons.Filled.KeyboardArrowDown, enabled = canStepDown, onClick = onStepDown)
    }
}

@Composable
private fun StepButton(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
fun DialsRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        content()
    }
}
