package io.github.ocpabdurrahim.fintrack.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Charts are drawn by hand (Canvas and plain boxes) instead of pulling in a
// chart library: three small charts do not justify the extra weight.

/** A ring chart. [slices] are (value, colour) pairs; [center] is drawn in the hole. */
@Composable
fun DonutChart(
    slices: List<Pair<Long, Color>>,
    modifier: Modifier = Modifier,
    thickness: Dp = 26.dp,
    center: @Composable () -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke),
            )
            val total = slices.sumOf { it.first }.toFloat()
            if (total > 0f) {
                val gap = if (slices.size > 1) 1.2f else 0f
                var start = -90f
                for ((value, color) in slices) {
                    val sweep = 360f * value / total * progress.value
                    if (sweep > gap) {
                        drawArc(
                            color = color,
                            startAngle = start + gap / 2f,
                            sweepAngle = sweep - gap,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(stroke),
                        )
                    }
                    start += sweep
                }
            }
        }
        center()
    }
}

data class BarGroup(val label: String, val values: List<Long>)

/** Side-by-side bars per group (e.g. income and expense per month). */
@Composable
fun GroupedBarChart(
    groups: List<BarGroup>,
    seriesColors: List<Color>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    barWidth: Dp = 12.dp,
    highlight: Int = -1,
) {
    val max = groups.maxOfOrNull { g -> g.values.maxOrNull() ?: 0L }?.takeIf { it > 0L } ?: 1L
    Row(modifier.fillMaxWidth()) {
        groups.forEachIndexed { index, group ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    Modifier.height(height),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    group.values.forEachIndexed { series, value ->
                        val fraction = (value.toFloat() / max).coerceIn(0f, 1f)
                        if (value > 0L) {
                            Box(
                                Modifier
                                    .width(barWidth)
                                    .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(seriesColors[series % seriesColors.size]),
                            )
                        } else {
                            Spacer(Modifier.width(barWidth))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    group.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (index == highlight) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == highlight) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One thin bar per day of a month; the biggest day is drawn solid. */
@Composable
fun DailyBars(values: List<Long>, color: Color, modifier: Modifier = Modifier, height: Dp = 96.dp) {
    val max = values.maxOrNull()?.takeIf { it > 0L } ?: 1L
    val peak = values.indexOf(values.maxOrNull() ?: 0L).takeIf { (values.maxOrNull() ?: 0L) > 0L } ?: -1
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(height),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { i, value ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (value > 0L) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.62f)
                                .fillMaxHeight((value.toFloat() / max).coerceIn(0.03f, 1f))
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(if (i == peak) color else color.copy(alpha = 0.5f)),
                        )
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            val style = MaterialTheme.typography.labelSmall
            val tint = MaterialTheme.colorScheme.onSurfaceVariant
            Text("1", style = style, color = tint)
            Spacer(Modifier.weight(1f))
            Text("15", style = style, color = tint)
            Spacer(Modifier.weight(1f))
            Text("${values.size}", style = style, color = tint)
        }
    }
}
