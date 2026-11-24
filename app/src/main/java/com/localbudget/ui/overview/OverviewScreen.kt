@file:OptIn(ExperimentalMaterial3Api::class)

package com.localbudget.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.localbudget.AppContainer
import com.localbudget.data.AppSettings
import com.localbudget.data.Category
import com.localbudget.data.Entry
import com.localbudget.data.EntryType
import com.localbudget.data.Recurring
import com.localbudget.data.Totals
import com.localbudget.domain.Money
import com.localbudget.domain.Periods
import com.localbudget.ui.appViewModel
import com.localbudget.ui.components.BudgetBar
import com.localbudget.ui.components.CategoryBadge
import com.localbudget.ui.components.EmptyState
import com.localbudget.ui.components.EntryRow
import com.localbudget.ui.components.LabeledAmount
import com.localbudget.ui.components.SectionHeader
import com.localbudget.ui.components.amountColor
import com.localbudget.ui.components.dueLabel
import com.localbudget.ui.components.signedAmount
import com.localbudget.ui.theme.LocalMoneyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class CategoryBudget(val category: Category, val spent: Long) {
    val budget: Long get() = category.budget ?: 0L
}

data class OverviewState(
    val loaded: Boolean = false,
    val currency: String = "EUR",
    val periodLabel: String = "",
    val totals: Totals = Totals(0, 0),
    val budget: Long = 0,
    val daysLeft: Int = 0,
    val categoryBudgets: List<CategoryBudget> = emptyList(),
    val upcoming: List<Recurring> = emptyList(),
    val recent: List<Entry> = emptyList(),
)

class OverviewViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<OverviewState> =
        combine(container.repository.version, container.settings.state) { _, settings -> load(settings) }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewState())

    private fun load(settings: AppSettings): OverviewState {
        val repo = container.repository
        val today = LocalDate.now()
        val period = Periods.containing(today, settings.periodStartDay)
        val spentByCategory = repo.amountsByCategory(EntryType.EXPENSE, period.start, period.endInclusive)
            .associate { it.category?.id to it.amount }
        val categoryBudgets = repo.categories()
            .filter { it.type == EntryType.EXPENSE && (it.budget ?: 0) > 0 }
            .map { CategoryBudget(it, spentByCategory[it.id] ?: 0L) }
            .sortedByDescending { it.spent.toDouble() / it.budget }
        val horizon = today.plusDays(30)
        val upcoming = repo.recurring()
            .filter { it.active && it.nextDate?.let { d -> !d.isAfter(horizon) } == true }
            .sortedBy { it.nextDate }
            .take(5)
        return OverviewState(
            loaded = true,
            currency = settings.currency,
            periodLabel = Periods.label(period),
            totals = repo.totals(period.start, period.endInclusive),
            budget = settings.monthlyBudget,
            daysLeft = period.daysLeft(today),
            categoryBudgets = categoryBudgets,
            upcoming = upcoming,
            recent = repo.recentEntries(8),
        )
    }
}

@Composable
fun OverviewScreen(
    onAddEntry: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onOpenRecurring: (Long) -> Unit,
    onShowHistory: () -> Unit,
    onShowBills: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = appViewModel { OverviewViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local Budget") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddEntry,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add") },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item { SummaryCard(state) }
            item { BudgetCard(state, onOpenSettings) }

            if (state.categoryBudgets.isNotEmpty()) {
                item { SectionHeader("Category budgets") }
                items(state.categoryBudgets, key = { "cb" + it.category.id }) { CategoryBudgetRow(it, state.currency) }
            }

            if (state.upcoming.isNotEmpty()) {
                item { SectionHeader("Upcoming", action = "All", onAction = onShowBills) }
                items(state.upcoming, key = { "r" + it.id }) { item ->
                    UpcomingRow(item, state.currency) { onOpenRecurring(item.id) }
                }
            }

            item { SectionHeader("Recent", action = if (state.recent.isEmpty()) null else "All", onAction = onShowHistory) }
            if (state.recent.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing here yet",
                        message = "Tap “Add” to record your first expense or income. Everything is stored only on this phone.",
                    )
                }
            } else {
                items(state.recent, key = { "e" + it.id }) { entry ->
                    EntryRow(entry, state.currency, onClick = { onOpenEntry(entry.id) }, showDate = true)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: OverviewState) {
    val colors = LocalMoneyColors.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                state.periodLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Text("Balance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                Money.format(state.totals.balance, state.currency),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                LabeledAmount(
                    label = "Income",
                    value = "+" + Money.format(state.totals.income, state.currency),
                    color = colors.income,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                LabeledAmount(
                    label = "Expenses",
                    value = "−" + Money.format(state.totals.expense, state.currency),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BudgetCard(state: OverviewState, onOpenSettings: () -> Unit) {
    if (state.budget <= 0) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onOpenSettings),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Set a monthly budget", style = MaterialTheme.typography.titleSmall)
                Text(
                    "See how much you can still spend per day. Tap to set one in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    val spent = state.totals.expense
    val left = state.budget - spent
    val fraction = spent.toFloat() / state.budget
    val over = LocalMoneyColors.current.over
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Monthly budget", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    if (left >= 0) "${Money.format(left, state.currency)} left" else "${Money.format(-left, state.currency)} over",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (left >= 0) MaterialTheme.colorScheme.primary else over,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            BudgetBar(fraction)
            Spacer(Modifier.height(8.dp))
            Row {
                Text(
                    "${Money.format(spent, state.currency)} of ${Money.format(state.budget, state.currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (left > 0 && state.daysLeft > 0) {
                    Text(
                        "${Money.format(left / state.daysLeft, state.currency)}/day · ${state.daysLeft} days left",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBudgetRow(item: CategoryBudget, currency: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(item.category, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(item.category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Money.formatShort(item.spent, currency)} / ${Money.formatShort(item.budget, currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.spent > item.budget) LocalMoneyColors.current.over else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            BudgetBar(item.spent.toFloat() / item.budget, height = 6.dp)
        }
    }
}

@Composable
private fun UpcomingRow(item: Recurring, currency: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryBadge(item.category)
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.nextDate?.let {
                Text(dueLabel(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            signedAmount(item.type, item.amount, currency),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = amountColor(item.type),
        )
    }
}
