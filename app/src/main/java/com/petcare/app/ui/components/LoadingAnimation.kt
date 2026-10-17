package com.petcare.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Keeps a visible arc even when Android's animation scale is set to zero. */
@Composable
internal fun LoadingAnimation() {
    val transition = rememberInfiniteTransition(label = "Background loading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label = "Loading ring rotation"
    )
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "Loading paw pulse"
    )
    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier.size(88.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            .testTag("loading-animation")
            .semantics {
                contentDescription = "Loading in background"
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(76.dp).graphicsLayer { rotationZ = rotation }) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke))
            drawArc(ringColor, -90f, 250f, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Icon(
            Icons.Filled.Pets,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(30.dp).graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        )
    }
}