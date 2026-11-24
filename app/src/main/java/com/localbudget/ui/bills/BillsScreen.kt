@file:OptIn(ExperimentalMaterial3Api::class)

package com.localbudget.ui.bills

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.localbudget.AppContainer
import com.localbudget.data.EntryType
import com.localbudget.data.Recurring
import com.localbudget.domain.Money
import com.localbudget.domain.Recurrence
import com.localbudget.ui.appViewModel
import com.localbudget.ui.components.CategoryBadge
import com.localbudget.ui.components.EmptyState
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

data class BillsState(
    val loaded: Boolean = false,
    val currency: String = "EUR",
    val expenses: List<Recurring> = emptyList(),
    val income: List<Recurring> = emptyList(),
) {
    val monthlyCosts: Long get() = expenses.filter { it.active }.sumOf { it.monthlyEquivalent }
    val monthlyIncome: Long get() = income.filter { it.active }.sumOf { it.monthlyEquivalent }
}

class BillsViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository

    val state: StateFlow<BillsState> = combine(repo.version, container.settings.state) { _, settings ->
        val all = repo.recurring().sortedWith(
            compareBy<Recurring>({ !it.active }, { it.nextDate ?: java.time.LocalDate.MAX }),
        )
        BillsState(
            loaded = true,
            currency = settings.currency,
            expenses = all.filter { it.type == EntryType.EXPENSE },
            income = all.filter { it.type == EntryType.INCOME },
        )
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillsState())
}

@Composable
fun BillsScreen(onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val vm = appViewModel { BillsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Recurring") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.testTag("add_recurring"),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New") },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item { SummaryCard(state) }
            if (state.expenses.isEmpty() && state.income.isEmpty()) {
                item {
                    EmptyState(
                        title = "No recurring items",
                        message = "Add rent, subscriptions, insurance or your salary. " +
                            "They are booked automatically on their due date, so you never have to enter them again.",
                    )
                }
            }
            if (state.expenses.isNotEmpty()) {
                item { SectionHeader("Bills & subscriptions") }
                items(state.expenses, key = { it.id }) { RecurringRow(it, state.currency) { onOpen(it.id) } }
            }
            if (state.income.isNotEmpty()) {
                item { SectionHeader("Income") }
                items(state.income, key = { it.id }) { RecurringRow(it, state.currency) { onOpen(it.id) } }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: BillsState) {
    val colors = LocalMoneyColors.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Per month (approx.)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                LabeledAmount(
                    "Fixed costs",
                    Money.format(state.monthlyCosts, state.currency),
                    MaterialTheme.colorScheme.onSecondaryContainer,
                    MaterialTheme.typography.titleMedium,
                    Modifier.weight(1f),
                )
                LabeledAmount(
                    "Fixed income",
                    Money.format(state.monthlyIncome, state.currency),
                    colors.income,
                    MaterialTheme.typography.titleMedium,
                    Modifier.weight(1f),
                )
            }
            if (state.monthlyIncome > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Left after fixed costs: " + Money.format(state.monthlyIncome - state.monthlyCosts, state.currency),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun RecurringRow(item: Recurring, currency: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (item.active) 1f else 0.5f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryBadge(item.category)
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val next = item.nextDate
            val status = when {
                !item.active -> "Paused"
                next == null -> "Ended"
                else -> "Next: " + dueLabel(next)
            }
            Text(
                Recurrence.describe(item.frequency, item.every) + " · " + status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            signedAmount(item.type, item.amount, currency),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = amountColor(item.type),
        )
    }
}
