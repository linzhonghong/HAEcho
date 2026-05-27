package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun SiriEdgeGlow(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!isActive) {
        Box(modifier = modifier) {
            content()
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "siri_glow_animation")
    
    // Smooth transition pulsing scale for breathing aura
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_scale"
    )

    // Breathing glow opacity
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_alpha"
    )

    val siriColors = listOf(
        Color(0xFF6C2AFA), // Neon Purple
        Color(0xFF00AAFF), // Ocean Blue
        Color(0xFF00FFCC), // Radiant Cyan
        Color(0xFFFF1177), // Pink Magenta
        Color(0xFFFFFF00), // Sunshine Yellow
        Color(0xFF6C2AFA)  // Neon Purple
    )

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Super deep neon background glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .graphicsLayer(
                    scaleX = auraScale,
                    scaleY = auraScale
                )
                .blur(32.dp)
                .border(
                    width = 24.dp,
                    brush = Brush.sweepGradient(siriColors),
                    shape = RoundedCornerShape(20.dp)
                )
                .alpha(auraAlpha * 0.4f)
        )

        // Mid-glow border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .blur(12.dp)
                .border(
                    width = 8.dp,
                    brush = Brush.sweepGradient(siriColors),
                    shape = RoundedCornerShape(16.dp)
                )
                .alpha(auraAlpha)
        )

        // Crisp inner glow frame
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .border(
                    width = 3.dp,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF2B86C5),
                            Color(0xFFFF3CAC),
                            Color(0xFF784BA0),
                            Color(0xFF2B86C5)
                        )
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
        )

        // Real screen content itself
        content()
    }
}
