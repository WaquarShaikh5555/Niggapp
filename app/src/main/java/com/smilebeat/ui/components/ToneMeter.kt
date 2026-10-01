package com.smilebeat.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smilebeat.detection.ToneTriggerController
import com.smilebeat.ui.theme.NeonCyan
import com.smilebeat.ui.theme.NeonPurple
import com.smilebeat.ui.theme.NeonRed
import com.smilebeat.ui.theme.TextPrimary
import com.smilebeat.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ToneMeter(
    toneScore: Float?,
    triggerState: ToneTriggerController.TriggerState,
    threshold: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val isVibing = triggerState == ToneTriggerController.TriggerState.TRIGGERED

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isVibing) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isVibing) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val radius = diameter / 2
            val center = Offset(size.width / 2, size.height / 2)

            // Background track
            drawCircle(
                color = Color(0xFF1E1E2A),
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Glow effect for vibing
            if (isVibing) {
                drawCircle(
                    color = NeonPurple.copy(alpha = 0.15f),
                    radius = radius * pulseScale + 20.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = NeonRed.copy(alpha = 0.10f),
                    radius = radius * pulseScale + 35.dp.toPx(),
                    center = center
                )
            }

            // Progress arc based on tone score
            val score = toneScore ?: 0.5f
            // Invert so darker (low score) shows more progress toward trigger?
            // Actually we want meter to show current tone: 0 dark, 1 bright
            val sweep = 270f * score.coerceIn(0f, 1f)
            // Gradient colors based on score
            val progressColor = when {
                score <= threshold -> NeonRed
                score <= threshold + 0.15f -> Color(0xFFFF8A00)
                else -> NeonCyan
            }

            // Draw progress
            drawArc(
                color = progressColor,
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(diameter, diameter),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Threshold indicator
            val thresholdAngle = 135f + 270f * threshold
            val rad = Math.toRadians(thresholdAngle.toDouble())
            val tx = center.x + radius * cos(rad).toFloat()
            val ty = center.y + radius * sin(rad).toFloat()
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = 4.dp.toPx(),
                center = Offset(tx, ty)
            )

            // Small tick marks
            for (i in 0..10) {
                val angle = 135f + 27f * i
                val r = Math.toRadians(angle.toDouble())
                val inner = radius - 6.dp.toPx()
                val outer = radius + 6.dp.toPx()
                val x1 = center.x + inner * cos(r).toFloat()
                val y1 = center.y + inner * sin(r).toFloat()
                val x2 = center.x + outer * cos(r).toFloat()
                val y2 = center.y + outer * sin(r).toFloat()
                drawLine(
                    color = Color.White.copy(alpha = if (i % 2 == 0) 0.3f else 0.1f),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (toneScore != null) String.format("%.2f", toneScore) else "--",
                color = TextPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (triggerState) {
                    ToneTriggerController.TriggerState.IDLE -> "SEARCHING"
                    ToneTriggerController.TriggerState.NORMAL -> "NORMAL RANGE"
                    ToneTriggerController.TriggerState.ANALYZING -> "ANALYZING"
                    ToneTriggerController.TriggerState.TRIGGERED -> "DARK TONE"
                    ToneTriggerController.TriggerState.COOLDOWN -> "COOLDOWN"
                },
                color = when (triggerState) {
                    ToneTriggerController.TriggerState.TRIGGERED -> NeonRed
                    ToneTriggerController.TriggerState.NORMAL -> NeonCyan
                    else -> TextSecondary
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            if (toneScore != null) {
                Text(
                    text = "THRESH ${String.format("%.2f", threshold)}",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}
