package com.sk150c.control.ui.components

import android.graphics.Paint as NativePaint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RotaryKnob(
    modifier: Modifier = Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float> = 0f..100f,
    size: Dp = 120.dp,
    unit: String = "",
    precision: Int = 2,
    step: Float? = null,
    showValue: Boolean = true
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val labelColor = onSurfaceColor.toArgb()

    Canvas(
        modifier = modifier
            .size(size)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val centerX = size.toPx() / 2
                    val centerY = size.toPx() / 2
                    val touchX = change.position.x
                    val touchY = change.position.y
                    
                    var angleDeg = Math.toDegrees(atan2(touchY - centerY, touchX - centerX).toDouble()).toFloat()
                    if (angleDeg < 0) angleDeg += 360f
                    
                    // We want the arc from 135 to 405 (45) degrees.
                    // The "dead zone" is 45 to 135.
                    
                    val relativeAngle = if (angleDeg >= 135f) {
                        angleDeg - 135f
                    } else if (angleDeg <= 45f) {
                        angleDeg + 225f
                    } else {
                        // In dead zone, snap to nearest
                        if (angleDeg > 90f) 0f else 270f
                    }
                    
                    var newValue = range.start + (relativeAngle / 270f) * (range.endInclusive - range.start)
                    
                    // Apply smart rounding if step is provided
                    if (step != null && step > 0) {
                        newValue = (Math.round(newValue / step) * step).toFloat()
                    }
                    
                    onValueChange(newValue.coerceIn(range))
                    change.consume()
                }
            }
    ) {
        val center = Offset(size.toPx() / 2, size.toPx() / 2)
        val fullRadius = size.toPx() / 2
        
        // 1. Dimensions (Refined for a smaller indicator)
        val trackWidth = 6.dp.toPx()
        val trackMargin = 2.dp.toPx() 
        val trackRadius = fullRadius - trackWidth / 2 - trackMargin
        val tickOuterRadius = trackRadius - trackWidth / 2 - 2.dp.toPx()
        val majorTickLength = 8.dp.toPx()
        val minorTickLength = 4.dp.toPx()
        val labelRadius = tickOuterRadius - majorTickLength - 16.dp.toPx()
        val knobRadius = labelRadius - 14.dp.toPx()
        val pointerWidth = 6.dp.toPx()

        // 2. Draw Track (Outer Ring)
        drawCircle(
            color = surfaceColor.copy(alpha = 0.3f),
            radius = trackRadius,
            center = center,
            style = Stroke(width = trackWidth)
        )
        
        val sweepAngle = ((value - range.start) / (range.endInclusive - range.start) * 270f)
        drawArc(
            color = primaryColor,
            startAngle = 135f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - trackRadius, center.y - trackRadius),
            size = Size(trackRadius * 2, trackRadius * 2),
            style = Stroke(width = trackWidth, cap = StrokeCap.Round)
        )

        // 3. Draw Ticks and Numeric Scale
        val totalRange = range.endInclusive - range.start
        val majorTickStep = if (totalRange <= 10f) 1f else 10f
        val minorTicksPerInterval = 5
        
        // We'll iterate through major points to draw labels, and then draw ticks separately for precision
        var tickVal = range.start
        while (tickVal <= range.endInclusive + 0.001f) {
            val progress = (tickVal - range.start) / totalRange
            val angleDeg = 135f + progress * 270f
            val angleRad = Math.toRadians(angleDeg.toDouble())
            
            // A tick is major if it's an integer value within our step
            val isMajor = (tickVal % majorTickStep).let { it < 0.001f || it > majorTickStep - 0.001f }
            
            val tLength = if (isMajor) majorTickLength else minorTickLength
            val startX = center.x + cos(angleRad).toFloat() * (tickOuterRadius - tLength)
            val startY = center.y + sin(angleRad).toFloat() * (tickOuterRadius - tLength)
            val endX = center.x + cos(angleRad).toFloat() * tickOuterRadius
            val endY = center.y + sin(angleRad).toFloat() * tickOuterRadius
            
            drawLine(
                color = onSurfaceColor.copy(alpha = if (isMajor) 0.6f else 0.3f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
            )
            
            // Draw label only for major ticks, and avoid drawing it too close to the end if it overlaps
            if (isMajor && tickVal <= range.endInclusive + 0.001f) {
                val lx = center.x + cos(angleRad).toFloat() * labelRadius
                val ly = center.y + sin(angleRad).toFloat() * labelRadius
                
                // Use round to avoid precision issues like 5.0 becoming 4
                val labelText = Math.round(tickVal.toDouble()).toInt().toString()
                
                drawContext.canvas.nativeCanvas.drawText(
                    labelText,
                    lx,
                    ly + 6.dp.toPx(),
                    NativePaint().apply {
                        color = labelColor
                        textSize = 14.sp.toPx()
                        textAlign = NativePaint.Align.CENTER
                        isAntiAlias = true
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                )
            }
            
            tickVal += majorTickStep / minorTicksPerInterval
        }

        // 4. Draw Knob Body
        // Shadow/Depth
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(onSurfaceColor.copy(alpha = 0.15f), Color.Transparent),
                center = center,
                radius = knobRadius * 1.05f
            ),
            radius = knobRadius * 1.05f,
            center = center
        )
        
        // Main knob face
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(surfaceColor, secondaryColor.copy(alpha = 0.8f)),
                start = Offset(center.x - knobRadius, center.y - knobRadius),
                end = Offset(center.x + knobRadius, center.y + knobRadius)
            ),
            radius = knobRadius,
            center = center
        )
        
        // Subtle inner ring for depth
        drawCircle(
            color = onSurfaceColor.copy(alpha = 0.05f),
            radius = knobRadius * 0.95f,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )

        // 5. Central Value Display
        if (showValue) {
            val formattedValue = "%.${precision}f".format(value)
            drawContext.canvas.nativeCanvas.apply {
                // Value
                drawText(
                    formattedValue,
                    center.x,
                    center.y - 2.dp.toPx(),
                    NativePaint().apply {
                        color = labelColor
                        textSize = 22.sp.toPx()
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textAlign = NativePaint.Align.CENTER
                        isAntiAlias = true
                    }
                )
                // Unit
                drawText(
                    unit,
                    center.x,
                    center.y + 14.dp.toPx(),
                    NativePaint().apply {
                        color = onSurfaceColor.copy(alpha = 0.6f).toArgb()
                        textSize = 11.sp.toPx()
                        textAlign = NativePaint.Align.CENTER
                        isAntiAlias = true
                    }
                )
            }
        }

        // 6. Draw Pointer
        val angleRad = Math.toRadians((135f + sweepAngle).toDouble())
        val pointerStartX = center.x + cos(angleRad).toFloat() * (knobRadius * 0.5f)
        val pointerEndX = center.x + cos(angleRad).toFloat() * (knobRadius * 0.9f)
        val pointerStartY = center.y + sin(angleRad).toFloat() * (knobRadius * 0.5f)
        val pointerEndY = center.y + sin(angleRad).toFloat() * (knobRadius * 0.9f)
        
        drawLine(
            color = primaryColor,
            start = Offset(pointerStartX, pointerStartY),
            end = Offset(pointerEndX, pointerEndY),
            strokeWidth = pointerWidth,
            cap = StrokeCap.Round
        )
    }
}
