@file:OptIn(ExperimentalMaterial3Api::class)

package com.localbudget.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.localbudget.AppContainer
import com.localbudget.data.CategoryAmount
import com.localbudget.data.EntryType
import com.localbudget.data.Totals
import com.localbudget.domain.Money
import com.localbudget.domain.Periods
import com.localbudget.ui.appViewModel
import com.localbudget.ui.components.BarGroup
import com.localbudget.ui.components.BudgetBar
import com.localbudget.ui.components.CategoryBadge
import com.localbudget.ui.components.DonutChart
import com.localbudget.ui.components.EmptyState
import com.localbudget.ui.components.IncomeExpenseBars
import com.localbudget.ui.components.LegendDot
import com.localbudget.ui.components.PaceChart
import com.localbudget.ui.components.PeriodSwitcher
import com.localbudget.ui.components.Slice
import com.localbudget.ui.theme.LocalMoneyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

data class StatsState(
    val loaded: Boolean = false,
    val currency: String = "EUR",
    val periodLabel: String = "",
    val mode: EntryType = EntryType.EXPENSE,
    val totals: Totals = Totals(0, 0),
    val byCategory: List<CategoryAmount> = emptyList(),
    val history: List<BarGroup> = emptyList(),
    val cumulative: List<Long> = emptyList(),
    val totalDays: Int = 30,
    val daysElapsed: Int = 0,
    val budget: Long = 0,
)

class StatsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val offset = MutableStateFlow(0)
    private val mode = MutableStateFlow(EntryType.EXPENSE)

    val state: StateFlow<StatsState> = combine(repo.version, container.settings.state, offset, mode) { _, settings, offset, mode ->
        val today = LocalDate.now()
        val period = Periods.relative(today, settings.periodStartDay, offset)
        val history = (-5..0).map { i ->
            val p = Periods.relative(today, settings.periodStartDay, offset + i)
            val t = repo.totals(p.start, p.endInclusive)
            BarGroup(Periods.shortLabel(p), t.income, t.expense, highlighted = i == 0)
        }
        val elapsed = period.daysElapsed(today)
        val daily = repo.dailyExpenses(period.start, period.endInclusive)
        var running = 0L
        val cumulative = (0 until elapsed).map { day ->
            running += daily[period.start.plusDays(day.toLong())] ?: 0L
            running
        }
        StatsState(
            loaded = true,
            currency = settings.currency,
            periodLabel = Periods.label(period),
            mode = mode,
            totals = repo.totals(period.start, period.endInclusive),
            byCategory = repo.amountsByCategory(mode, period.start, period.endInclusive),
            history = history,
            cumulative = cumulative,
            totalDays = period.days,
            daysElapsed = elapsed,
            budget = settings.monthlyBudget,
        )
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    fun shift(by: Int) = offset.update { it + by }
    fun reset() { offset.value = 0 }
    fun setMode(type: EntryType) { mode.value = type }
}

@Composable
fun StatsScreen() {
    val vm = appViewModel { StatsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Statistics") }) }) { padding ->
        if (!state.loaded) return@Scaffold
        val total = state.byCategory.sumOf { it.amount }
        val fallback = MaterialTheme.colorScheme.outline
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                PeriodSwitcher(state.periodLabel, onPrevious = { vm.shift(-1) }, onNext = { vm.shift(1) }, onReset = vm::reset)
            }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    listOf(EntryType.EXPENSE to "Expenses", EntryType.INCOME to "Income").forEachIndexed { i, (type, label) ->
                        SegmentedButton(
                            selected = state.mode == type,
                            onClick = { vm.setMode(type) },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
            }
            item {
                StatsCard {
                    DonutChart(
                        slices = state.byCategory.map { Slice(it.category?.let { c -> Color(c.color) } ?: fallback, it.amount) },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (state.mode == EntryType.EXPENSE) "Spent" else "Earned",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                Money.format(total, state.currency),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    if (state.byCategory.isEmpty()) {
                        EmptyState("No data", "Nothing recorded for this period.")
                    }
                }
            }
            items(state.byCategory, key = { "c" + (it.category?.id ?: -1) }) { item ->
                CategoryLine(item, total, state.currency)
            }

            if (state.mode == EntryType.EXPENSE && state.daysElapsed > 0) {
                item {
                    StatsCard(title = "Spending pace") {
                        val colors = LocalMoneyColors.current
                        PaceChart(
                            cumulative = state.cumulative,
                            totalDays = state.totalDays,
                            budget = state.budget,
                            lineColor = MaterialTheme.colorScheme.primary,
                            budgetColor = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            LegendDot(MaterialTheme.colorScheme.primary, "Spent so far")
                            if (state.budget > 0) LegendDot(MaterialTheme.colorScheme.outline, "Even budget pace")
                        }
                        Spacer(Modifier.height(8.dp))
                        val perDay = state.totals.expense / state.daysElapsed.coerceAtLeast(1)
                        Text(
                            "Average ${Money.format(perDay, state.currency)} per day" +
                                if (state.daysElapsed < state.totalDays) {
                                    " · projected ${Money.format(perDay * state.totalDays, state.currency)} this period"
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.budget > 0 && perDay * state.totalDays > state.budget) colors.over else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                val colors = LocalMoneyColors.current
                StatsCard(title = "Last 6 periods") {
                    IncomeExpenseBars(
                        groups = state.history,
                        incomeColor = colors.income,
                        expenseColor = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendDot(colors.income, "Income")
                        LegendDot(MaterialTheme.colorScheme.primary, "Expenses")
                    }
                }
            }

            item {
                StatsCard(title = "Summary") {
                    SummaryLine("Income", "+" + Money.format(state.totals.income, state.currency))
                    SummaryLine("Expenses", "−" + Money.format(state.totals.expense, state.currency))
                    SummaryLine("Balance", Money.format(state.totals.balance, state.currency, showPlus = true))
                    if (state.totals.income > 0) {
                        val rate = state.totals.balance * 100.0 / state.totals.income
                        SummaryLine("Savings rate", "%.0f%%".format(rate))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 12.dp))
            }
            content()
        }
    }
}

@Composable
private fun CategoryLine(item: CategoryAmount, total: Long, currency: String) {
    val budget = item.category?.budget ?: 0L
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(item.category, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.category?.name ?: "Uncategorized",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(Money.format(item.amount, currency), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            val pct = if (total > 0) item.amount * 100.0 / total else 0.0
            Text(
                "%.0f%%".format(pct) + if (budget > 0) " · budget ${Money.formatShort(budget, currency)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (budget > 0) {
                Spacer(Modifier.height(4.dp))
                BudgetBar(item.amount.toFloat() / budget, height = 6.dp)
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
