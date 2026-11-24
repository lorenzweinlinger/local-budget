package com.localbudget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbudget.data.Category
import com.localbudget.data.Entry
import com.localbudget.data.EntryType
import com.localbudget.domain.Money
import com.localbudget.ui.theme.LocalMoneyColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun amountColor(type: EntryType): Color =
    if (type == EntryType.INCOME) LocalMoneyColors.current.income else MaterialTheme.colorScheme.onSurface

fun signedAmount(type: EntryType, cents: Long, currency: String): String =
    if (type == EntryType.INCOME) "+" + Money.format(cents, currency) else "−" + Money.format(cents, currency)

@Composable
fun CategoryBadge(category: Category?, size: Dp = 40.dp) {
    val color = category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = category?.emoji ?: "❔",
            fontSize = (size.value * 0.48f).sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun EntryRow(entry: Entry, currency: String, onClick: () -> Unit, showDate: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(entry.category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.category?.name ?: "Uncategorized",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = buildList {
                if (showDate) add(friendlyDate(entry.date))
                if (entry.note.isNotBlank()) add(entry.note)
            }.joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (entry.recurringId != null) {
            Icon(
                Icons.Default.Autorenew,
                contentDescription = "Recurring",
                modifier = Modifier.size(16.dp).padding(end = 4.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = signedAmount(entry.type, entry.amount, currency),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = amountColor(entry.type),
        )
    }
}

@Composable
fun PeriodSwitcher(label: String, onPrevious: () -> Unit, onNext: () -> Unit, onReset: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) { Icon(Icons.Default.ChevronLeft, contentDescription = "Previous period") }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (onReset != null) {
                TextButton(onClick = onReset) { Text(label, style = MaterialTheme.typography.titleMedium) }
            } else {
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 12.dp))
            }
        }
        IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, contentDescription = "Next period") }
    }
}

@Composable
fun SectionHeader(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        } else {
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Progress bar that turns red when [fraction] exceeds 1. */
@Composable
fun BudgetBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val over = fraction > 1f
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(height).clip(CircleShape),
        color = if (over) LocalMoneyColors.current.over else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
fun LabeledAmount(label: String, value: String, color: Color, style: TextStyle, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = style, color = color, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

fun friendlyDate(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    today.plusDays(1) -> "Tomorrow"
    else -> if (date.year == today.year) {
        date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    } else {
        date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}

fun dueLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days < 0 -> "Overdue"
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days < 7 -> "In $days days"
        else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "d MMM" else "d MMM yyyy"))
    }
}
