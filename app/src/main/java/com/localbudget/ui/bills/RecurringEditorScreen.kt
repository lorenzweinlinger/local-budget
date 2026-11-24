@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.localbudget.ui.bills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.localbudget.AppContainer
import com.localbudget.data.Category
import com.localbudget.data.EntryType
import com.localbudget.data.Recurring
import com.localbudget.domain.Frequency
import com.localbudget.domain.Money
import com.localbudget.domain.Recurrence
import com.localbudget.ui.LocalMessenger
import com.localbudget.ui.appContainer
import com.localbudget.ui.appViewModel
import com.localbudget.ui.components.DatePickerFor
import com.localbudget.ui.components.friendlyDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class RecurringForm(
    val loaded: Boolean = false,
    val name: String = "",
    val amountText: String = "",
    val type: EntryType = EntryType.EXPENSE,
    val categoryId: Long? = null,
    val frequency: Frequency = Frequency.MONTHLY,
    val every: Int = 1,
    val nextDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val active: Boolean = true,
) {
    val amount: Long get() = Money.parse(amountText) ?: 0L
    val valid: Boolean get() = name.isNotBlank() && amount > 0
}

class RecurringEditorViewModel(private val container: AppContainer, private val id: Long) : ViewModel() {
    private val repo = container.repository
    private var original: Recurring? = null

    private val _form = MutableStateFlow(RecurringForm())
    val form: StateFlow<RecurringForm> = _form

    val categories: StateFlow<List<Category>> = repo.observe { repo.categories() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isNew: Boolean get() = id == 0L

    init {
        viewModelScope.launch {
            val existing = if (id > 0) withContext(Dispatchers.IO) { repo.recurring(id) } else null
            original = existing
            _form.value = if (existing != null) {
                RecurringForm(
                    loaded = true,
                    name = existing.name,
                    amountText = Money.toInput(existing.amount),
                    type = existing.type,
                    categoryId = existing.categoryId,
                    frequency = existing.frequency,
                    every = existing.every,
                    nextDate = existing.nextDate ?: LocalDate.now(),
                    endDate = existing.endDate,
                    active = existing.active,
                )
            } else {
                RecurringForm(loaded = true)
            }
        }
    }

    fun update(transform: (RecurringForm) -> RecurringForm) = _form.update(transform)

    fun effectiveCategory(form: RecurringForm, categories: List<Category>): Category? {
        val ofType = categories.filter { it.type == form.type }
        return ofType.firstOrNull { it.id == form.categoryId } ?: ofType.firstOrNull()
    }

    suspend fun save(): Boolean {
        val f = _form.value
        if (!f.valid) return false
        val old = original
        // If the schedule changed, restart the series from the chosen next date.
        val scheduleChanged = old == null || old.frequency != f.frequency || old.every != f.every || old.nextDate != f.nextDate
        val item = Recurring(
            id = id,
            name = f.name.trim(),
            type = f.type,
            amount = f.amount,
            categoryId = effectiveCategory(f, categories.value)?.id,
            frequency = f.frequency,
            every = f.every,
            startDate = if (scheduleChanged) f.nextDate else old!!.startDate,
            endDate = f.endDate,
            postedCount = if (scheduleChanged) 0 else old!!.postedCount,
            active = f.active,
        )
        repo.saveRecurring(item)
        // Book it right away if the first date is today or in the past.
        repo.processRecurring()
        return true
    }

    suspend fun delete() {
        if (id > 0) repo.deleteRecurring(id)
    }
}

@Composable
fun RecurringEditorScreen(recurringId: Long, onDone: () -> Unit) {
    val vm = appViewModel { RecurringEditorViewModel(it, recurringId) }
    val form by vm.form.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    val currency = appContainer().settings.current.currency
    val scope = rememberCoroutineScope()
    var pickNext by rememberSaveable { mutableStateOf(false) }
    var pickEnd by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    if (!form.loaded) return
    val selected = vm.effectiveCategory(form, categories)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "New recurring item" else "Edit recurring item") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Default.Close, contentDescription = "Close") } },
                actions = {
                    if (!vm.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(EntryType.EXPENSE to "Expense", EntryType.INCOME to "Income").forEachIndexed { i, (type, label) ->
                    SegmentedButton(
                        selected = form.type == type,
                        onClick = { vm.update { it.copy(type = type, categoryId = null) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> vm.update { it.copy(name = v.take(60)) } },
                label = { Text("Name") },
                placeholder = { Text(if (form.type == EntryType.EXPENSE) "e.g. Rent, Netflix, Gym" else "e.g. Salary") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.amountText,
                onValueChange = { v ->
                    if (v.isEmpty() || v.matches(Regex("""\d{0,9}([.,]\d{0,2})?"""))) vm.update { it.copy(amountText = v) }
                },
                label = { Text("Amount") },
                prefix = { Text(Money.symbol(currency) + " ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Category", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.filter { it.type == form.type }.forEach { c ->
                    FilterChip(
                        selected = c.id == selected?.id,
                        onClick = { vm.update { it.copy(categoryId = c.id) } },
                        label = { Text("${c.emoji}  ${c.name}") },
                    )
                }
            }

            Text("Schedule", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Every", modifier = Modifier.padding(end = 8.dp))
                IconButton(onClick = { vm.update { it.copy(every = (it.every - 1).coerceAtLeast(1)) } }) {
                    Icon(Icons.Default.Remove, contentDescription = "Less often")
                }
                Text(form.every.toString(), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { vm.update { it.copy(every = (it.every + 1).coerceAtMost(52)) } }) {
                    Icon(Icons.Default.Add, contentDescription = "More")
                }
                Spacer(Modifier.width(8.dp))
                FrequencyPicker(form.frequency, form.every) { f -> vm.update { it.copy(frequency = f) } }
            }
            Text(
                Recurrence.describe(form.frequency, form.every),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Next date", modifier = Modifier.width(96.dp))
                AssistChip(
                    onClick = { pickNext = true },
                    label = { Text(friendlyDate(form.nextDate)) },
                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ends", modifier = Modifier.width(96.dp))
                AssistChip(
                    onClick = { pickEnd = true },
                    label = { Text(form.endDate?.let(::friendlyDate) ?: "Never") },
                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                )
                if (form.endDate != null) {
                    TextButton(onClick = { vm.update { it.copy(endDate = null) } }) { Text("Clear") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Active", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Paused items are not booked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = form.active, onCheckedChange = { v -> vm.update { it.copy(active = v) } })
            }
            Text(
                "An entry is added automatically on each due date. If the next date is in the past, " +
                    "the missed entries are added as well.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    scope.launch {
                        if (vm.save()) {
                            messenger.show(if (vm.isNew) "Recurring item added" else "Recurring item saved")
                            onDone()
                        }
                    }
                },
                enabled = form.valid,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Save") }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (pickNext) {
        DatePickerFor(form.nextDate, onDismiss = { pickNext = false }) { d -> vm.update { it.copy(nextDate = d) } }
    }
    if (pickEnd) {
        DatePickerFor(form.endDate ?: form.nextDate.plusYears(1), onDismiss = { pickEnd = false }) { d ->
            vm.update { it.copy(endDate = d) }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${form.name}”?") },
            text = { Text("No more entries will be created. Entries that were already booked are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        vm.delete()
                        messenger.show("Recurring item deleted")
                        onDone()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FrequencyPicker(frequency: Frequency, every: Int, onChange: (Frequency) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val plural = every > 1
    fun label(f: Frequency) = when (f) {
        Frequency.WEEKLY -> if (plural) "weeks" else "week"
        Frequency.MONTHLY -> if (plural) "months" else "month"
        Frequency.YEARLY -> if (plural) "years" else "year"
    }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(label(frequency))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Frequency.entries.forEach { f ->
                DropdownMenuItem(text = { Text(label(f)) }, onClick = { onChange(f); open = false })
            }
        }
    }
}
