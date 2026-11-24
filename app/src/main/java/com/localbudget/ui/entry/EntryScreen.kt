@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.localbudget.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.localbudget.AppContainer
import com.localbudget.data.Category
import com.localbudget.data.Entry
import com.localbudget.data.EntryType
import com.localbudget.domain.Frequency
import com.localbudget.domain.Keypad
import com.localbudget.domain.Money
import com.localbudget.domain.Recurrence
import com.localbudget.ui.LocalMessenger
import com.localbudget.ui.appContainer
import com.localbudget.ui.appViewModel
import com.localbudget.ui.components.DatePickerFor
import com.localbudget.ui.components.amountColor
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

data class EntryForm(
    val loaded: Boolean = false,
    val id: Long = 0,
    val type: EntryType = EntryType.EXPENSE,
    val input: String = "",
    val categoryId: Long? = null,
    val note: String = "",
    val date: LocalDate = LocalDate.now(),
    val repeat: Frequency? = null,
    val recurringId: Long? = null,
) {
    val isNew: Boolean get() = id == 0L
    val amount: Long get() = Keypad.toCents(input)
}

class EntryViewModel(
    private val container: AppContainer,
    entryId: Long,
    initialType: EntryType,
) : ViewModel() {
    private val repo = container.repository
    private var original: Entry? = null

    private val _form = MutableStateFlow(EntryForm(type = initialType))
    val form: StateFlow<EntryForm> = _form

    val categories: StateFlow<List<Category>> = repo.observe { repo.categories() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            val existing = if (entryId > 0) withContext(Dispatchers.IO) { repo.entry(entryId) } else null
            original = existing
            _form.value = if (existing != null) {
                EntryForm(
                    loaded = true,
                    id = existing.id,
                    type = existing.type,
                    input = Money.toInput(existing.amount),
                    categoryId = existing.categoryId,
                    note = existing.note,
                    date = existing.date,
                    recurringId = existing.recurringId,
                )
            } else {
                EntryForm(loaded = true, type = initialType, categoryId = container.settings.lastCategory(initialType))
            }
        }
    }

    fun press(key: Char) = _form.update { it.copy(input = Keypad.press(it.input, key)) }
    fun setType(type: EntryType) = _form.update {
        if (it.type == type) it else it.copy(type = type, categoryId = container.settings.lastCategory(type))
    }
    fun setCategory(id: Long) = _form.update { it.copy(categoryId = id) }
    fun setNote(note: String) = _form.update { it.copy(note = note.take(200)) }
    fun setDate(date: LocalDate) = _form.update { it.copy(date = date) }
    fun setRepeat(repeat: Frequency?) = _form.update { it.copy(repeat = repeat) }

    /** The category that will be saved: the chosen one if valid for the type, else the first of that type. */
    fun effectiveCategory(form: EntryForm, categories: List<Category>): Category? {
        val ofType = categories.filter { it.type == form.type }
        return ofType.firstOrNull { it.id == form.categoryId } ?: ofType.firstOrNull()
    }

    suspend fun save(): Entry? {
        val f = _form.value
        if (f.amount <= 0) return null
        val category = effectiveCategory(f, categories.value)
        val entry = Entry(
            id = f.id,
            type = f.type,
            amount = f.amount,
            categoryId = category?.id,
            note = f.note.trim(),
            date = f.date,
            recurringId = f.recurringId,
            category = category,
        )
        val id = if (f.isNew && f.repeat != null) {
            repo.saveEntryWithRepeat(entry, f.repeat, 1)
        } else {
            repo.saveEntry(entry)
        }
        container.settings.setLastCategory(f.type, category?.id)
        return entry.copy(id = id)
    }

    fun delete(onDeleted: (Entry) -> Unit) {
        val entry = original ?: return
        viewModelScope.launch {
            repo.deleteEntry(entry.id)
            onDeleted(entry)
        }
    }
}

@Composable
fun EntryScreen(entryId: Long, initialType: EntryType, onDone: () -> Unit) {
    val vm = appViewModel { EntryViewModel(it, entryId, initialType) }
    val form by vm.form.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    if (!form.loaded) return
    val currency = container.settings.current.currency
    val selected = vm.effectiveCategory(form, categories)

    val save: () -> Unit = {
        if (!saving) {
            saving = true
            scope.launch {
                val saved = vm.save()
                if (saved != null) {
                    val what = if (saved.type == EntryType.INCOME) "Income" else "Expense"
                    val amount = Money.format(saved.amount, currency)
                    if (form.isNew) {
                        messenger.show("$what of $amount added", "Undo") {
                            container.scope.launch { container.repository.deleteEntry(saved.id) }
                        }
                    } else {
                        messenger.show("$what updated")
                    }
                    onDone()
                } else {
                    saving = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.isNew) "New entry" else "Edit entry") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Default.Close, contentDescription = "Close") }
                },
                actions = {
                    if (!form.isNew) {
                        IconButton(onClick = {
                            vm.delete { deleted ->
                                messenger.show("Entry deleted", "Undo") {
                                    container.scope.launch { container.repository.restoreEntry(deleted) }
                                }
                                onDone()
                            }
                        }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(EntryType.EXPENSE to "Expense", EntryType.INCOME to "Income").forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = form.type == type,
                        onClick = { vm.setType(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        modifier = Modifier.testTag("type_${type.name.lowercase()}"),
                    ) { Text(label) }
                }
            }

            Text(
                text = (if (form.type == EntryType.INCOME) "+" else "−") + Money.symbol(currency) + " " + form.input.ifEmpty { "0" },
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (form.input.isEmpty()) MaterialTheme.colorScheme.outline else amountColor(form.type),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("amount"),
            )

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.filter { it.type == form.type }.forEach { category ->
                        FilterChip(
                            selected = category.id == selected?.id,
                            onClick = { vm.setCategory(category.id) },
                            label = { Text("${category.emoji}  ${category.name}") },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { showDatePicker = true },
                        label = { Text(friendlyDate(form.date)) },
                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    )
                    if (form.isNew) RepeatChip(form.repeat, vm::setRepeat)
                }
                if (form.recurringId != null) {
                    Text(
                        "Created by a recurring item. Changes here only affect this entry.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = form.note,
                    onValueChange = vm::setNote,
                    placeholder = { Text("Note (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().testTag("note"),
                )
            }

            if (!WindowInsets.isImeVisible) {
                NumberPad(onKey = vm::press, modifier = Modifier.padding(top = 12.dp))
            }
            Button(
                onClick = save,
                enabled = form.amount > 0 && !saving,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(56.dp).testTag("save"),
            ) {
                Text(
                    when {
                        !form.isNew -> "Save changes"
                        form.type == EntryType.INCOME -> "Save income"
                        else -> "Save expense"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }

    if (showDatePicker) {
        DatePickerFor(form.date, onDismiss = { showDatePicker = false }, onPick = vm::setDate)
    }
}

@Composable
private fun RepeatChip(repeat: Frequency?, onChange: (Frequency?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(if (repeat == null) "Doesn't repeat" else Recurrence.describe(repeat, 1)) },
            leadingIcon = { Icon(Icons.Default.Autorenew, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Doesn't repeat") }, onClick = { onChange(null); open = false })
            Frequency.entries.forEach { f ->
                DropdownMenuItem(text = { Text(Recurrence.describe(f, 1)) }, onClick = { onChange(f); open = false })
            }
        }
    }
}

@Composable
private fun NumberPad(onKey: (Char) -> Unit, modifier: Modifier = Modifier) {
    val rows = listOf("123", "456", "789", ".0" + Keypad.BACKSPACE)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Surface(
                        onClick = { onKey(key) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f).height(54.dp).testTag("key_$key"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (key == Keypad.BACKSPACE) {
                                Icon(Icons.Outlined.Backspace, contentDescription = "Delete digit")
                            } else {
                                Text(key.toString(), style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
