package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.EmeraldMuted
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun IslamicOrnamentDivider(
    modifier: Modifier = Modifier,
    color: Color = GoldAccent.copy(alpha = 0.5f),
    strokeWidth: Float = 2f
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        // Draw elegant center Islamic star (8 points)
        val radius = 10f
        val starCenter = Offset(width / 2, centerY)

        // Draw horizontal connecting lines
        drawLine(
            color = color.copy(alpha = 0.2f),
            start = Offset(0f, centerY),
            end = Offset(width / 2 - radius * 2, centerY),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = color.copy(alpha = 0.2f),
            start = Offset(width / 2 + radius * 2, centerY),
            end = Offset(width, centerY),
            strokeWidth = strokeWidth
        )

        // Draw central motif (two overlapping rotated squares)
        val path1 = Path().apply {
            moveTo(starCenter.x - radius, starCenter.y - radius)
            lineTo(starCenter.x + radius, starCenter.y - radius)
            lineTo(starCenter.x + radius, starCenter.y + radius)
            lineTo(starCenter.x - radius, starCenter.y + radius)
            close()
        }
        
        drawPath(path = path1, color = color, style = Stroke(width = strokeWidth))
        
        rotate(degrees = 45f, pivot = starCenter) {
            drawPath(path = path1, color = color, style = Stroke(width = strokeWidth))
        }

        // Little dots beside the star
        drawCircle(color = color, radius = 3f, center = Offset(width / 2 - radius * 3f, centerY))
        drawCircle(color = color, radius = 3f, center = Offset(width / 2 + radius * 3f, centerY))
    }
}

@Composable
fun IslamicStarStarIcon(
    modifier: Modifier = Modifier,
    color: Color = GoldAccent,
    strokeWidth: Float = 3f
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val radius = size.width / 2.5f

        val path = Path().apply {
            moveTo(cx - radius, cy - radius)
            lineTo(cx + radius, cy - radius)
            lineTo(cx + radius, cy + radius)
            lineTo(cx - radius, cy + radius)
            close()
        }

        drawPath(path = path, color = color, style = Stroke(width = strokeWidth))

        rotate(degrees = 45f, pivot = Offset(cx, cy)) {
            drawPath(path = path, color = color, style = Stroke(width = strokeWidth))
        }

        drawCircle(color = color, radius = radius / 3, center = Offset(cx, cy), style = Stroke(width = strokeWidth / 1.5f))
        drawCircle(color = color, radius = 2f, center = Offset(cx, cy))
    }
}

@Composable
fun ElegantBackgroundPattern(
    modifier: Modifier = Modifier,
    alpha: Float = 0.05f
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val stepX = 120.dp.toPx()
        val stepY = 120.dp.toPx()
        val width = size.width
        val height = size.height

        for (x in 0.. (width / stepX).toInt() + 1) {
            for (y in 0..(height / stepY).toInt() + 1) {
                val cx = x * stepX
                val cy = y * stepY
                
                // Draw decorative 8-pointed star in background
                val starRad = 15f
                val path = Path().apply {
                    moveTo(cx - starRad, cy - starRad)
                    lineTo(cx + starRad, cy - starRad)
                    lineTo(cx + starRad, cy + starRad)
                    lineTo(cx - starRad, cy + starRad)
                    close()
                }

                drawPath(path = path, color = GoldAccent.copy(alpha = alpha), style = Stroke(width = 1f))
                rotate(degrees = 45f, pivot = Offset(cx, cy)) {
                    drawPath(path = path, color = GoldAccent.copy(alpha = alpha), style = Stroke(width = 1f))
                }

                // Connect diagonal lines faintly
                drawLine(
                    color = GoldAccent.copy(alpha = alpha / 2),
                    start = Offset(cx - stepX/2, cy - stepY/2),
                    end = Offset(cx + stepX/2, cy + stepY/2),
                    strokeWidth = 1f
                )
            }
        }
    }
}
