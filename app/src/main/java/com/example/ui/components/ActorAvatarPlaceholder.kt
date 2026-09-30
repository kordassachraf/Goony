package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.example.R

/**
 * Custom full-container Avatar Placeholder.
 * Renders the exact Heroicons outlined User icon inside a sleek dark themed background.
 */
@Composable
fun ActorAvatarPlaceholder(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF1E1E22),
    iconColor: Color = Color.White.copy(alpha = 0.65f)
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_actor_placeholder),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.fillMaxSize(0.60f) // increased to 60% of circle size for perfect prominence
        )
    }
}
