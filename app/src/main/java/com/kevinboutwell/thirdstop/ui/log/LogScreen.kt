package com.kevinboutwell.thirdstop.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinboutwell.thirdstop.core.FilmStocks
import com.kevinboutwell.thirdstop.core.Stops
import com.kevinboutwell.thirdstop.data.ReadingEntity
import com.kevinboutwell.thirdstop.ui.components.DeckCard
import com.kevinboutwell.thirdstop.ui.components.Hairline
import com.kevinboutwell.thirdstop.ui.components.ScreenHeader
import com.kevinboutwell.thirdstop.ui.components.TagChip
import com.kevinboutwell.thirdstop.ui.meter.formatEcThirds
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@Composable
fun LogScreen(
    onBack: () -> Unit,
    onApplyToMeter: (ReadingEntity) -> Unit,
    viewModel: LogViewModel = viewModel(factory = LogViewModel.Factory),
) {
    val readings by viewModel.readings.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val onDelete: (ReadingEntity) -> Unit = { reading ->
        viewModel.delete(reading)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Reading deleted",
                actionLabel = "Undo",
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ScreenHeader(
                title = "Reading log",
                onBack = onBack,
                meta = when (readings.size) {
                    0 -> null
                    1 -> "1 reading"
                    else -> "${readings.size} readings"
                },
            )
            if (readings.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "No saved readings yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val zone = ZoneId.systemDefault()
                val groups = readings.groupBy {
                    Instant.ofEpochMilli(it.timestampEpochMs).atZone(zone).toLocalDate()
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    groups.forEach { (day, dayReadings) ->
                        item(key = "day-$day") {
                            Text(
                                dayLabel(day),
                                style = MaterialTheme.typography.labelMedium
                                    .copy(letterSpacing = 1.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    start = 20.dp, top = 12.dp, bottom = 8.dp,
                                ),
                            )
                        }
                        item(key = "card-$day") {
                            DeckCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                                dayReadings.forEachIndexed { index, reading ->
                                    key(reading.id) {
                                        if (index > 0) Hairline()
                                        DismissibleReadingRow(
                                            reading = reading,
                                            onApply = {
                                                haptics.performHapticFeedback(
                                                    HapticFeedbackType.LongPress,
                                                )
                                                onApplyToMeter(reading)
                                            },
                                            onDelete = { onDelete(reading) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun dayLabel(day: LocalDate): String =
    if (day == LocalDate.now()) "TODAY"
    else day.format(DateTimeFormatter.ofPattern("EEE MMM d", Locale.getDefault())).uppercase()

/** Swipe left reveals an error-tinted trash and deletes; tap re-applies. */
@Composable
private fun DismissibleReadingRow(
    reading: ReadingEntity,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to MaterialTheme.colorScheme.error.copy(alpha = 0f),
                            1f to MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                        ),
                    )
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete reading",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        ReadingRow(
            reading = reading,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onApply),
        )
    }
}

@Composable
private fun ReadingRow(reading: ReadingEntity, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "f/${reading.apertureNominal} · ${reading.shutterNominal} · ${reading.isoNominal}",
                style = MaterialTheme.typography.headlineMedium
                    .copy(fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                DateFormat.getTimeInstance(DateFormat.SHORT)
                    .format(Date(reading.timestampEpochMs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TagChip(String.format(Locale.US, "EV %.1f", reading.ev100))
            TagChip(
                when (reading.mode) {
                    "INCIDENT" -> "incident"
                    "REFLECTIVE_SPOT" -> "spot"
                    else -> "average"
                },
            )
            if (reading.ecThirds != 0) {
                TagChip("EC ${formatEcThirds(reading.ecThirds)}")
            }
            reading.filmId?.let { filmId ->
                val film = FilmStocks.byId(filmId)
                val corrected = reading.correctedShutterSec
                if (corrected != null) {
                    TagChip("${film.name} · corr. ${Stops.formatSeconds(corrected)}", amber = true)
                } else {
                    TagChip(film.name)
                }
            }
        }
        reading.note?.let { note ->
            Spacer(Modifier.height(8.dp))
            Text(
                "“$note”",
                style = MaterialTheme.typography.bodySmall
                    .copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
