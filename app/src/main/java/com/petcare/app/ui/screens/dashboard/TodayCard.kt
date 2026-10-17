package com.petcare.app.ui.screens.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petcare.app.ui.theme.Spacing

private const val RING_ANIMATION_MILLIS = 700
private const val MAX_PET_BARS = 4

/** Today's care at a glance: one ring for the household, one bar per pet. */
@Composable
fun TodayCard(progress: TodayProgress, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProgressRing(progress)
            Spacer(Modifier.size(Spacing.medium))
            Column(Modifier.weight(1f)) {
                Text(
                    text = when {
                        progress.allDone -> "All done for today"
                        progress.remaining == 1 -> "1 task left today"
                        else -> "${progress.remaining} tasks left today"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(Spacing.small))
                val palette = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.secondary
                )
                progress.perPet.take(MAX_PET_BARS).forEachIndexed { index, pet ->
                    PetBar(pet, palette[index % palette.size])
                }
                val hidden = progress.perPet.size - MAX_PET_BARS
                if (hidden > 0) {
                    Text(
                        "+$hidden more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressRing(progress: TodayProgress) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(progress.fraction) {
        sweep.animateTo(progress.fraction, tween(RING_ANIMATION_MILLIS, easing = FastOutSlowInEasing))
    }
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val brush = Brush.sweepGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary)
    )
    val description = "${progress.done} of ${progress.total} tasks done today"
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(104.dp)
            .semantics { contentDescription = description }
    ) {
        Canvas(Modifier.size(104.dp)) {
            val strokeWidth = 10.dp.toPx()
            val inset = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(strokeWidth)
            )
            if (sweep.value > 0f) {
                drawArc(
                    brush = brush,
                    startAngle = -90f,
                    sweepAngle = 360f * sweep.value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${progress.done}/${progress.total}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text("done", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PetBar(pet: PetProgress, color: Color) {
    val fraction = if (pet.total == 0) 0f else pet.done.toFloat() / pet.total
    val animated = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        animated.animateTo(fraction, tween(RING_ANIMATION_MILLIS, easing = FastOutSlowInEasing))
    }
    Column(Modifier.padding(bottom = Spacing.small)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                pet.pet.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text(
                "${pet.done} of ${pet.total} done",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { animated.value },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}
