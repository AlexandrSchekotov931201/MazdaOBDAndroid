@file:Suppress("MagicNumber")

package car.mazda.obd.android.ui.compose

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun MazdaStyleTachometer(
    modifier: Modifier = Modifier,
    rpm: Int,
    maxRpm: Int = 8000,
    redlineFromRpm: Int = 6500,
    // Mazda-like: дуга больше сверху, не полный круг
    startAngleDeg: Float = 200f,
    sweepAngleDeg: Float = 220f,
    needleWidth: Dp = 3.dp,
) {
    val clamped = rpm.coerceIn(0, maxRpm)
    val progress = clamped.toFloat() / maxRpm.toFloat()

    val animatedProgress by animateFloatAsState(targetValue = progress, label = "rpm_progress")

    val textColor = MaterialTheme.colorScheme.onSurface

    val localDensity = LocalDensity.current

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val minDim = size.minDimension

            val outerRadius = minDim * 0.46f
            val arcStrokeW = minDim * 0.06f
            val arcStroke = Stroke(width = arcStrokeW, cap = StrokeCap.Round)

            fun degToPoint(deg: Float, radius: Float): Offset {
                val rad = Math.toRadians(deg.toDouble())
                return Offset(
                    x = center.x + cos(rad).toFloat() * radius,
                    y = center.y + sin(rad).toFloat() * radius
                )
            }

            // ---- background plate (subtle inner gradient)
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color(0xFF0B0F14),
                    0.65f to Color(0xFF0B0F14),
                    1.0f to Color(0xFF05070A)
                ),
                radius = outerRadius * 1.10f,
                center = center
            )
            // thin inner ring
            drawCircle(
                color = Color(0xFF111827),
                radius = outerRadius * 1.02f,
                center = center,
                style = Stroke(width = minDim * 0.01f)
            )

            // Arc geometry
            val arcSize = Size(outerRadius * 2f, outerRadius * 2f)
            val arcTopLeft = Offset(center.x - outerRadius, center.y - outerRadius)

            // ---- base arc
            drawArc(
                color = Color(0xFF1F2937),
                startAngle = startAngleDeg,
                sweepAngle = sweepAngleDeg,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = arcStroke
            )

            // ---- redline segment
            val redStartT = (redlineFromRpm.toFloat() / maxRpm.toFloat()).coerceIn(0f, 1f)
            val redStartAngle = startAngleDeg + sweepAngleDeg * redStartT
            val redSweep = sweepAngleDeg * (1f - redStartT)

            if (redSweep > 0.5f) {
                drawArc(
                    color = Color(0xFFEF4444),
                    startAngle = redStartAngle,
                    sweepAngle = redSweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = arcStroke
                )
            }

            // ---- active progress arc (white-ish like Mazda needle trail)
            drawArc(
                color = Color(0xFFE5E7EB),
                startAngle = startAngleDeg,
                sweepAngle = sweepAngleDeg * animatedProgress,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = arcStrokeW * 0.35f, cap = StrokeCap.Round)
            )

            // ---- ticks (major & minor) + numbers 1..8
            val majorMax = maxRpm / 1000 // 8
            val majorTicks = majorMax
            val minorPerMajor = 4 // Mazda-ish: много мелких

            val tickOuter = outerRadius * 1.00f
            val tickInnerMajor = outerRadius * 0.86f
            val tickInnerMinor = outerRadius * 0.90f

            val totalMinor = majorTicks * minorPerMajor
            for (i in 0..totalMinor) {
                val t = i.toFloat() / totalMinor.toFloat()
                val a = startAngleDeg + sweepAngleDeg * t

                val isMajor = i % minorPerMajor == 0
                val p1 = degToPoint(a, tickOuter)
                val p2 = degToPoint(a, if (isMajor) tickInnerMajor else tickInnerMinor)

                val tickColor = when {
                    // подчёркиваем красную зону: деления там чуть краснее
                    (t >= redStartT) -> if (isMajor) Color(0xFFF87171) else Color(0xFFEF4444)
                    else -> if (isMajor) Color(0xFFF9FAFB) else Color(0xFF9CA3AF)
                }

                drawLine(
                    color = tickColor,
                    start = p1,
                    end = p2,
                    strokeWidth = if (isMajor) minDim * 0.010f else minDim * 0.005f,
                    cap = StrokeCap.Round
                )

                // numbers at major ticks (1..majorTicks), but show 0 optionally skip
                if (isMajor) {
                    val majorValue = (i / minorPerMajor) // 0..8
                    if (majorValue != 0) {
                        val labelRadius = outerRadius * 0.73f
                        val lp = degToPoint(a, labelRadius)

                        // Draw text via nativeCanvas (simple + sharp)
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                textAlign = android.graphics.Paint.Align.CENTER
                                textSize = minDim * 0.07f
                                color = android.graphics.Color.argb(
                                    230,
                                    229, 231, 235 // ~E5E7EB
                                )
                                typeface = android.graphics.Typeface.create(
                                    android.graphics.Typeface.SANS_SERIF,
                                    android.graphics.Typeface.BOLD
                                )
                            }
                            // small vertical correction for baseline
                            drawText(
                                majorValue.toString(),
                                lp.x,
                                lp.y + paint.textSize * 0.35f,
                                paint
                            )
                        }
                    }
                }
            }

            // ---- needle
            val needleAngle = startAngleDeg + sweepAngleDeg * animatedProgress
            val needleStart = degToPoint(needleAngle, outerRadius * 0.18f)
            val needleEnd = degToPoint(needleAngle, outerRadius * 0.83f)

            // main needle (bright)
            drawLine(
                color = Color(0xFFF9FAFB),
                start = needleStart,
                end = needleEnd,
                strokeWidth = with(localDensity) { needleWidth.toPx() },
                cap = StrokeCap.Round
            )
            // needle tip accent (Mazda-like warm tint)
            val tipStart = degToPoint(needleAngle, outerRadius * 0.68f)
            drawLine(
                color = Color(0xFFFBBF24),
                start = tipStart,
                end = needleEnd,
                strokeWidth = with(localDensity) { (needleWidth * 1.15f).toPx() },
                cap = StrokeCap.Round
            )

            // ---- center hub
            drawCircle(color = Color(0xFF0B0F14), radius = minDim * 0.07f, center = center)
            drawCircle(color = Color(0xFF374151), radius = minDim * 0.045f, center = center)
            drawCircle(color = Color(0xFF111827), radius = minDim * 0.028f, center = center)

            // ---- small caption under center: x1000 r/min
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = minDim * 0.045f
                    color = android.graphics.Color.argb(170, 156, 163, 175) // ~9CA3AF alpha
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.SANS_SERIF,
                        android.graphics.Typeface.NORMAL
                    )
                }
                drawText("x1000 r/min", center.x, center.y + outerRadius * 0.34f, paint)
            }
        }

        // Big RPM number (center) like modern clusters
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = clamped.toString(),
                color = textColor,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "RPM",
                color = textColor.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
fun MazdaTachPreviewIdle() {
    MaterialTheme {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            MazdaStyleTachometer(rpm = 850, modifier = Modifier.size(320.dp))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
fun MazdaTachPreviewCruise() {
    MaterialTheme {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            MazdaStyleTachometer(rpm = 2800, modifier = Modifier.size(320.dp))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
fun MazdaTachPreviewRedline() {
    MaterialTheme {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            MazdaStyleTachometer(rpm = 7200, modifier = Modifier.size(320.dp))
        }
    }
}
