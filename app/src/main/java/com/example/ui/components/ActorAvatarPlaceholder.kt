package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.min

/**
 * Custom full-container Avatar Placeholder matching the user's uploaded avatar image.
 * Features a dark grey background with a solid dark silhouette consisting of a
 * centered head circle and a wide shoulder dome stretching across the bottom ("full screen mode").
 */
@Composable
fun ActorAvatarPlaceholder(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF3F3F3F),
    silhouetteColor: Color = Color(0xFF121212)
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val minDim = min(w, h)

        if (minDim <= 0f) return@Canvas

        // 1. Fill solid dark grey background
        drawRect(color = backgroundColor)

        // 2. Head circle
        val headRadius = minDim * 0.21f
        val headCenter = Offset(x = w / 2f, y = h * 0.38f)
        drawCircle(
            color = silhouetteColor,
            radius = headRadius,
            center = headCenter
        )

        // 3. Wide Shoulder Dome (full-screen arch spanning bottom width)
        val shoulderRadius = minDim * 0.46f
        val shoulderCenter = Offset(x = w / 2f, y = h * 1.05f)
        drawCircle(
            color = silhouetteColor,
            radius = shoulderRadius,
            center = shoulderCenter
        )
    }
}
