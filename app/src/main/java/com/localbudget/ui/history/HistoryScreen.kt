@file:OptIn(ExperimentalMaterial3Api::class)

package com.localbudget.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.localbudget.AppContainer
import com.localbudget.data.Entry
import com.localbudget.data.EntryType
import com.localbudget.domain.Money
import com.localbudget.domain.Periods
import com.localbudget.ui.LocalMessenger
import com.localbudget.ui.appViewModel
import com.localbudget.ui.components.EmptyState
import com.localbudget.ui.components.EntryRow
import com.localbudget.ui.components.PeriodSwitcher
import com.localbudget.ui.components.friendlyDate
import com.localbudget.ui.theme.LocalMoneyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DayGroup(val date: LocalDate, val entries: List<Entry>) {
    val net: Long get() = entries.sumOf { if (it.type == EntryType.INCOME) it.amount else -it.amount }
}

data class HistoryState(
    val loaded: Boolean = false,
    val currency: String = "EUR",
    val periodLabel: String = "",
    val searching: Boolean = false,
    val query: String = "",
    val filter: EntryType? = null,
    val days: List<DayGroup> = emptyList(),
    val income: Long = 0,
    val expense: Long = 0,
)

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val offset = MutableStateFlow(0)
    private val searching = MutableStateFlow(false)
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<EntryType?>(null)

    val state: StateFlow<HistoryState> = combine(
        combine(repo.version, container.settings.state) { _, s -> s },
        offset, searching, query, filter,
    ) { settings, offset, searching, query, filter ->
        val period = Periods.relative(LocalDate.now(), settings.periodStartDay, offset)
        val all = if (searching && query.isNotBlank()) {
            repo.search(query)
        } else {
            repo.entries(period.start, period.endInclusive)
        }
        val shown = if (filter == null) all else all.filter { it.type == filter }
        HistoryState(
            loaded = true,
            currency = settings.currency,
            periodLabel = Periods.label(period),
            searching = searching,
            query = query,
            filter = filter,
            days = shown.groupBy { it.date }.map { (date, entries) -> DayGroup(date, entries) },
            income = shown.filter { it.type == EntryType.INCOME }.sumOf { it.amount },
            expense = shown.filter { it.type == EntryType.EXPENSE }.sumOf { it.amount },
        )
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun shift(by: Int) = offset.update { it + by }
    fun resetPeriod() { offset.value = 0 }
    fun setSearching(on: Boolean) {
        searching.value = on
        if (!on) query.value = ""
    }
    fun setQuery(text: String) { query.value = text }
    fun setFilter(type: EntryType?) { filter.value = type }

    fun delete(entry: Entry, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repo.deleteEntry(entry.id)
            onDeleted()
        }
    }

    fun restore(entry: Entry) {
        container.scope.launch { repo.restoreEntry(entry) }
    }
}

@Composable
fun HistoryScreen(onAddEntry: () -> Unit, onOpenEntry: (Long) -> Unit) {
    val vm = appViewModel { HistoryViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current

    Scaffold(
        topBar = {
            if (state.searching) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                TopAppBar(
                    title = {
                        TextField(
                            value = state.query,
                            onValueChange = vm::setQuery,
                            placeholder = { Text("Search notes and categories") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { vm.setSearching(false) }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("History") },
                    actions = {
                        IconButton(onClick = { vm.setSearching(true) }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddEntry) { Icon(Icons.Default.Add, contentDescription = "Add entry") }
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            if (!state.searching) {
                item {
                    PeriodSwitcher(
                        label = state.periodLabel,
                        onPrevious = { vm.shift(-1) },
                        onNext = { vm.shift(1) },
                        onReset = { vm.resetPeriod() },
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = state.filter == null, onClick = { vm.setFilter(null) }, label = { Text("All") })
                    FilterChip(
                        selected = state.filter == EntryType.EXPENSE,
                        onClick = { vm.setFilter(EntryType.EXPENSE) },
                        label = { Text("Expenses") },
                    )
                    FilterChip(
                        selected = state.filter == EntryType.INCOME,
                        onClick = { vm.setFilter(EntryType.INCOME) },
                        label = { Text("Income") },
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        "In  +" + Money.format(state.income, state.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalMoneyColors.current.income,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Out  −" + Money.format(state.expense, state.currency),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (state.days.isEmpty()) {
                item {
                    EmptyState(
                        title = if (state.searching) "No matches" else "No entries",
                        message = if (state.searching) {
                            "Try a different word."
                        } else {
                            "Nothing recorded in this period yet."
                        },
                    )
                }
            }

            state.days.forEach { day ->
                item(key = "d" + day.date.toEpochDay()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            friendlyDate(day.date),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Money.format(day.net, state.currency, showPlus = true),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                items(day.entries, key = { "e" + it.id }) { entry ->
                    SwipeToDelete(onDelete = {
                        vm.delete(entry) {
                            messenger.show("Entry deleted", "Undo") { vm.restore(entry) }
                        }
                    }) {
                        EntryRow(entry, state.currency, onClick = { onOpenEntry(entry.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) { content() }
    }
}
