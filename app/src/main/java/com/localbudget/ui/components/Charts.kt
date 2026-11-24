package com.localbudget.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background

data class Slice(val color: Color, val value: Long)

/** Ring chart; [center] is drawn in the middle (e.g. the total). */
@Composable
fun DonutChart(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    thickness: Dp = 26.dp,
    center: @Composable () -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = thickness.toPx()
            val inset = strokePx / 2
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(inset, inset)
            val total = slices.sumOf { it.value }.toFloat()
            if (total <= 0f) {
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokePx))
                return@Canvas
            }
            val gap = if (slices.count { it.value > 0 } > 1) 1.5f else 0f
            var start = -90f
            slices.forEach { slice ->
                val sweep = slice.value / total * 360f
                if (sweep > 0f) {
                    drawArc(
                        color = slice.color,
                        startAngle = start + gap / 2,
                        sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(strokePx, cap = StrokeCap.Butt),
                    )
                }
                start += sweep
            }
        }
        center()
    }
}

data class BarGroup(val label: String, val income: Long, val expense: Long, val highlighted: Boolean)

/** Side-by-side income/expense bars per period. */
@Composable
fun IncomeExpenseBars(
    groups: List<BarGroup>,
    incomeColor: Color,
    expenseColor: Color,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 140.dp,
) {
    val max = groups.maxOfOrNull { maxOf(it.income, it.expense) }?.coerceAtLeast(1) ?: 1
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(chartHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            groups.forEach { g ->
                Row(
                    modifier = Modifier.fillMaxHeight().weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Bar(g.income.toFloat() / max, incomeColor.copy(alpha = if (g.highlighted) 1f else 0.55f))
                    Spacer(Modifier.width(3.dp))
                    Bar(g.expense.toFloat() / max, expenseColor.copy(alpha = if (g.highlighted) 1f else 0.55f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            groups.forEach { g ->
                Text(
                    g.label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (g.highlighted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(
        Modifier
            .width(12.dp)
            .fillMaxHeight(fraction.coerceIn(0.01f, 1f))
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .background(color),
    )
}

/**
 * Cumulative spending over the period compared with an even "budget pace" line.
 * [cumulative] has one value per elapsed day.
 */
@Composable
fun PaceChart(
    cumulative: List<Long>,
    totalDays: Int,
    budget: Long,
    lineColor: Color,
    budgetColor: Color,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        val maxValue = maxOf(budget, cumulative.maxOrNull() ?: 0L).coerceAtLeast(1).toFloat() * 1.05f
        val w = size.width
        val h = size.height
        fun x(day: Int) = if (totalDays <= 1) 0f else day.toFloat() / (totalDays - 1) * w
        fun y(value: Long) = h - value / maxValue * h

        drawLine(gridColor, Offset(0f, h), Offset(w, h), strokeWidth = 1.dp.toPx())
        if (budget > 0) {
            drawLine(
                budgetColor,
                Offset(0f, y(0)),
                Offset(w, y(budget)),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
            )
        }
        if (cumulative.isNotEmpty()) {
            val path = Path().apply {
                moveTo(x(0), y(cumulative[0]))
                cumulative.forEachIndexed { index, value -> lineTo(x(index), y(value)) }
            }
            drawPath(path, lineColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(lineColor, 5.dp.toPx(), Offset(x(cumulative.lastIndex), y(cumulative.last())))
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
