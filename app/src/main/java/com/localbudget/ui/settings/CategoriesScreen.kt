@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.localbudget.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localbudget.data.Category
import com.localbudget.data.DefaultCategories
import com.localbudget.data.EntryType
import com.localbudget.domain.Money
import com.localbudget.ui.LocalMessenger
import com.localbudget.ui.appContainer
import com.localbudget.ui.components.CategoryBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val container = appContainer()
    val repo = container.repository
    val categories by remember { repo.observe { repo.categories() } }.collectAsStateWithLifecycle(emptyList())
    val settings by container.settings.state.collectAsStateWithLifecycle()
    var type by rememberSaveable { mutableStateOf(EntryType.EXPENSE) }
    var editing by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = Category(
                    name = "",
                    emoji = if (type == EntryType.EXPENSE) "📦" else "💰",
                    color = DefaultCategories.palette.random(),
                    type = type,
                )
            }) { Icon(Icons.Default.Add, contentDescription = "Add category") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                    listOf(EntryType.EXPENSE to "Expenses", EntryType.INCOME to "Income").forEachIndexed { i, (t, label) ->
                        SegmentedButton(
                            selected = type == t,
                            onClick = { type = t },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
            }
            items(categories.filter { it.type == type }, key = { it.id }) { c ->
                ListItem(
                    modifier = Modifier.clickable { editing = c },
                    leadingContent = { CategoryBadge(c) },
                    headlineContent = { Text(c.name) },
                    supportingContent = if ((c.budget ?: 0L) > 0L) {
                        { Text("Budget ${Money.format(c.budget ?: 0L, settings.currency)} / month") }
                    } else {
                        null
                    },
                )
            }
        }
    }

    editing?.let { category ->
        CategoryDialog(
            initial = category,
            currency = settings.currency,
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun CategoryDialog(initial: Category, currency: String, onDismiss: () -> Unit) {
    val container = appContainer()
    val messenger = LocalMessenger.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var color by remember { mutableIntStateOf(initial.color) }
    var budget by remember { mutableStateOf(initial.budget?.let(Money::toInput) ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var usage by remember { mutableIntStateOf(0) }
    val isNew = initial.id == 0L

    LaunchedEffect(initial.id) {
        if (!isNew) usage = withContext(Dispatchers.IO) { container.repository.categoryUsage(initial.id) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${initial.name}”?") },
            text = {
                Text(
                    if (usage > 0) {
                        "$usage entries use this category. They are kept and shown as “Uncategorized”."
                    } else {
                        "This category isn't used by any entries."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        container.repository.deleteCategory(initial.id)
                        messenger.show("Category deleted")
                        onDismiss()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New category" else "Edit category") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryBadge(initial.copy(emoji = emoji, color = color), size = 48.dp)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(30) },
                        label = { Text("Name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DefaultCategories.emojis.forEach { e ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (e == emoji) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 20.sp, textAlign = TextAlign.Center) }
                    }
                }
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { v -> if (v.isNotBlank()) emoji = v.trim().take(8) },
                    label = { Text("…or type any emoji") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Color", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DefaultCategories.palette.forEach { c ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(
                                    if (c == color) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { color = c },
                        )
                    }
                }
                if (initial.type == EntryType.EXPENSE) {
                    OutlinedTextField(
                        value = budget,
                        onValueChange = { v -> if (v.isEmpty() || v.matches(Regex("""\d{0,9}([.,]\d{0,2})?"""))) budget = v },
                        label = { Text("Monthly budget (optional)") },
                        prefix = { Text(Money.symbol(currency) + " ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!isNew) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete category", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    scope.launch {
                        container.repository.saveCategory(
                            initial.copy(
                                name = name.trim(),
                                emoji = emoji,
                                color = color,
                                budget = Money.parse(budget)?.takeIf { it > 0 },
                            ),
                        )
                        onDismiss()
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
