package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Apple iOS / macOS style 12-bar radial Activity Indicator Spinner.
 * Matches the uploaded progress-loading-bar vector image.
 * Uses 12 pill/capsule bars with decaying alpha, stepped 12-tick rotation.
 */
@Composable
fun SmoothProgressIndicator(
    modifier: Modifier = Modifier.size(32.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color.Transparent,
    strokeWidth: Dp = 3.dp,
    durationMillis: Int = 900
) {
    val transition = rememberInfiniteTransition(label = "ios_spinner_transition")
    val rawRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ios_spinner_rotation"
    )

    // Stepped 12-tick rotation (30 degrees per step) matching iOS UIActivityIndicatorView
    val rotation = (rawRotation / 30f).toInt() * 30f

    // 12 bars decay alpha curve (head to tail) matching Apple's activity indicator
    val barAlphas = remember {
        floatArrayOf(
            1.00f, 0.88f, 0.75f, 0.62f, 0.50f, 0.38f,
            0.28f, 0.20f, 0.14f, 0.10f, 0.07f, 0.04f
        )
    }

    Canvas(
        modifier = modifier.progressSemantics()
    ) {
        val sizeMin = size.minDimension
        if (sizeMin <= 0f) return@Canvas

        val centerPt = center
        val outerRadius = sizeMin * 0.46f
        val innerRadius = sizeMin * 0.25f
        val calculatedBarWidth = (strokeWidth.toPx()).coerceAtLeast(sizeMin * 0.09f)

        rotate(degrees = rotation, pivot = centerPt) {
            for (i in 0 until 12) {
                val angleDeg = i * 30f
                val rad = Math.toRadians(angleDeg.toDouble())
                val cosVal = cos(rad).toFloat()
                val sinVal = sin(rad).toFloat()

                val start = Offset(
                    x = centerPt.x + innerRadius * cosVal,
                    y = centerPt.y + innerRadius * sinVal
                )
                val end = Offset(
                    x = centerPt.x + outerRadius * cosVal,
                    y = centerPt.y + outerRadius * sinVal
                )

                val alpha = barAlphas[i].coerceIn(0.04f, 1f)

                drawLine(
                    color = color.copy(alpha = alpha),
                    start = start,
                    end = end,
                    strokeWidth = calculatedBarWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
