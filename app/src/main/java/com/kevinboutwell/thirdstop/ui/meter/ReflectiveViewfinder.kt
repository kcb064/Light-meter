package com.kevinboutwell.thirdstop.ui.meter

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Camera preview with tap-to-spot-meter. Coordinate transforms are handled by
 * [PreviewView.getMeteringPointFactory], which accounts for rotation, crop and
 * scale type.
 */
@Composable
fun ReflectiveViewfinder(
    spotEnabled: Boolean,
    onBind: (androidx.lifecycle.LifecycleOwner, PreviewView) -> Unit,
    onMeterAt: (androidx.camera.core.MeteringPoint) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit = {},
    /** Incremental pinch scale (>1 zooms in) — see [detectTransformGestures]. */
    onPinch: (Float) -> Unit = {},
    fillBox: Boolean = false,
) {
    val context = LocalContext.current
    // Inside a NavHost this is the back-stack entry: binding the camera to it
    // means CameraX releases the camera automatically when the screen stops,
    // with no manual unbind (which races across screen transitions).
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            // FILL crops to cover boxes much wider than the sensor frame
            // (the calibration strip); FIT letterboxes for the meter screen.
            scaleType = if (fillBox) PreviewView.ScaleType.FILL_CENTER
            else PreviewView.ScaleType.FIT_CENTER
        }
    }
    var reticle by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(previewView) {
        onBind(lifecycleOwner, previewView)
    }
    // Clear the reticle when leaving spot mode.
    LaunchedEffect(spotEnabled) {
        if (!spotEnabled) reticle = null
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(spotEnabled) {
                    detectTapGestures(
                        onLongPress = { onLongPress() },
                        onTap = { offset ->
                            if (spotEnabled) {
                                reticle = offset
                                val point = previewView.meteringPointFactory
                                    .createPoint(offset.x, offset.y, SPOT_SIZE)
                                onMeterAt(point)
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (zoomChange != 1f) onPinch(zoomChange)
                    }
                },
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = if (spotEnabled) reticle else null
            if (center != null) {
                drawCircle(
                    color = Color(0xFFFFB74D),
                    radius = 48f,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
                )
                drawCircle(
                    color = Color(0xFFFFB74D),
                    radius = 4f,
                    center = center,
                )
            }
        }
    }
}

// Metering region size as a fraction of the frame — roughly a spot meter's angle.
private const val SPOT_SIZE = 0.1f
