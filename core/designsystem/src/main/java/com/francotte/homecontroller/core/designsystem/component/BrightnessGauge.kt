package com.francotte.homecontroller.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Convertit une position verticale du doigt dans la jauge en pourcentage (0–100).
 * Haut = 100, bas = 0. Fonction pure, testable.
 */
fun brightnessFromOffset(pointerY: Float, gaugeHeightPx: Float): Int =
    if (gaugeHeightPx <= 0f) 0
    else (((gaugeHeightPx - pointerY) / gaugeHeightPx) * 100f).roundToInt().coerceIn(0, 100)

/**
 * Jauge verticale remplie depuis le bas. Manipulation directe : la position du doigt fixe le
 * niveau. Pendant le glissement → [onValueChange] (affichage) ; à la levée du doigt →
 * [onValueChangeFinished] (envoi). Atome de design system : ne prend qu'un pourcentage (Int).
 */
@Composable
fun BrightnessGauge(
    percent: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var heightPx by remember { mutableFloatStateOf(0f) }
    var latest by remember { mutableIntStateOf(percent) }
    val fillColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(36.dp))
            .background(trackColor)
            .onSizeChanged { heightPx = it.height.toFloat() }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        latest = brightnessFromOffset(offset.y, heightPx)
                        onValueChange(latest)
                    },
                    onVerticalDrag = { change, _ ->
                        latest = brightnessFromOffset(change.position.y, heightPx)
                        onValueChange(latest)
                    },
                    onDragEnd = { onValueChangeFinished(latest) }
                )
            }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(percent / 100f)
                .align(Alignment.BottomCenter)
                .background(fillColor)
        )
    }
}
