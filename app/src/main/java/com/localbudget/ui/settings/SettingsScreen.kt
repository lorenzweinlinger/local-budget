@file:OptIn(ExperimentalMaterial3Api::class)

package com.localbudget.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localbudget.AppContainer
import com.localbudget.data.Backup
import com.localbudget.data.ScreenTransition
import com.localbudget.domain.Money
import com.localbudget.ui.LocalMessenger
import com.localbudget.ui.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenCategories: () -> Unit) {
    val container = appContainer()
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val scope = rememberCoroutineScope()

    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch(Dispatchers.Main.immediate) {
            val result = runCatching { exportBackup(context, container, uri) }
            messenger.show(if (result.isSuccess) "Backup saved" else "Could not save backup")
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch(Dispatchers.Main.immediate) {
            val result = runCatching { exportCsv(context, container, uri) }
            messenger.show(if (result.isSuccess) "CSV exported" else "Could not export CSV")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsGroup("Budget")
            SettingsRow(
                icon = Icons.Default.Savings,
                title = "Monthly budget",
                value = if (settings.monthlyBudget > 0) Money.format(settings.monthlyBudget, settings.currency) else "Not set",
            ) { dialog = "budget" }
            SettingsRow(
                icon = Icons.Default.CalendarMonth,
                title = "Budget month starts on",
                value = if (settings.periodStartDay == 1) "1st of the month" else "Day ${settings.periodStartDay} (e.g. payday)",
            ) { dialog = "startDay" }
            SettingsRow(
                icon = Icons.Default.Paid,
                title = "Currency",
                value = currencyName(settings.currency),
            ) { dialog = "currency" }
            SettingsRow(
                icon = Icons.Default.Category,
                title = "Categories",
                value = "Add, edit and set per-category budgets",
                onClick = onOpenCategories,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SettingsGroup("Appearance")
            SettingsRow(
                icon = Icons.Default.Animation,
                title = "Screen transitions",
                value = settings.transition.label,
            ) { dialog = "transition" }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SettingsGroup("Your data")
            SettingsRow(Icons.Default.Upload, "Export backup", "Save everything to a JSON file") {
                exportLauncher.launch("local-budget-backup-${LocalDate.now()}.json")
            }
            SettingsRow(Icons.Default.Download, "Restore backup", "Replace all data with a backup file") {
                importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
            }
            SettingsRow(Icons.Default.TableChart, "Export CSV", "All entries, for spreadsheets") {
                csvLauncher.launch("local-budget-${LocalDate.now()}.csv")
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListItem(
                leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                headlineContent = { Text("Private by design") },
                supportingContent = {
                    Text(
                        "This app has no internet permission. All data is stored only on this device. " +
                            "Use “Export backup” from time to time to keep a copy, e.g. before switching phones.",
                    )
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    when (dialog) {
        "budget" -> BudgetDialog(
            initial = settings.monthlyBudget,
            currency = settings.currency,
            onDismiss = { dialog = null },
            onSave = { value -> container.settings.update { it.copy(monthlyBudget = value) }; dialog = null },
        )
        "startDay" -> StartDayDialog(
            initial = settings.periodStartDay,
            onDismiss = { dialog = null },
            onSave = { day -> container.settings.update { it.copy(periodStartDay = day) }; dialog = null },
        )
        "transition" -> TransitionDialog(
            selected = settings.transition,
            onDismiss = { dialog = null },
            onSelect = { t -> container.settings.update { it.copy(transition = t) }; dialog = null },
        )
        "currency" -> CurrencyDialog(
            onDismiss = { dialog = null },
            onSelect = { code -> container.settings.update { it.copy(currency = code) }; dialog = null },
        )
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Restore backup?") },
            text = { Text("All current entries, categories and recurring items will be replaced by the backup. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch(Dispatchers.Main.immediate) {
                        val result = runCatching { importBackup(context, container, uri) }
                        messenger.show(
                            result.fold(
                                onSuccess = { "Backup restored ($it entries)" },
                                onFailure = { (it as? Backup.InvalidBackupException)?.message ?: "Could not read the backup file" },
                            ),
                        )
                    }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
}

private suspend fun exportBackup(context: Context, container: AppContainer, uri: Uri) = withContext(Dispatchers.IO) {
    val json = Backup.toJson(container.repository.snapshot(), container.settings.current)
    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
}

private suspend fun exportCsv(context: Context, container: AppContainer, uri: Uri) = withContext(Dispatchers.IO) {
    val csv = Backup.toCsv(container.repository.snapshot().entries)
    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(csv.toByteArray()) }
}

private suspend fun importBackup(context: Context, container: AppContainer, uri: Uri): Int = withContext(Dispatchers.IO) {
    val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
    val (snapshot, settings) = Backup.fromJson(text)
    container.repository.replaceAll(snapshot)
    if (settings != null) container.settings.update { settings }
    snapshot.entries.size
}

private fun currencyName(code: String): String =
    runCatching { "$code · ${Currency.getInstance(code).getDisplayName(Locale.getDefault())}" }.getOrDefault(code)

@Composable
private fun SettingsGroup(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
    )
}

@Composable
private fun BudgetDialog(initial: Long, currency: String, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (initial > 0) Money.toInput(initial) else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly budget") },
        text = {
            Column {
                Text(
                    "How much do you want to spend at most per month? Leave empty for no budget.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> if (v.isEmpty() || v.matches(Regex("""\d{0,9}([.,]\d{0,2})?"""))) text = v },
                    prefix = { Text(Money.symbol(currency) + " ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(Money.parse(text) ?: 0L) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StartDayDialog(initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var day by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget month starts on") },
        text = {
            Column {
                Text(
                    "If you get paid on e.g. the 25th, start your budget month on that day.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items((1..28).toList()) { d ->
                        Row(
                            Modifier.fillMaxWidth().clickable { day = d }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = day == d, onClick = { day = d })
                            Text(if (d == 1) "1st (calendar month)" else "Day $d")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(day) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CurrencyDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember {
        Currency.getAvailableCurrencies()
            .map { it.currencyCode to it.getDisplayName(Locale.getDefault()) }
            .sortedBy { it.first }
    }
    val common = listOf("EUR", "USD", "GBP", "CHF", "JPY", "CAD", "AUD", "SEK", "NOK", "DKK", "PLN", "CZK", "INR", "CNY")
    val shown = if (query.isBlank()) {
        all.filter { it.first in common }.sortedBy { common.indexOf(it.first) } + all.filter { it.first !in common }
    } else {
        all.filter { it.first.contains(query, true) || it.second.contains(query, true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Currency") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.first }) { (code, name) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(code) }.padding(vertical = 12.dp),
                        ) {
                            Text(code, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(56.dp))
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TransitionDialog(selected: ScreenTransition, onDismiss: () -> Unit, onSelect: (ScreenTransition) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Screen transitions") },
        text = {
            Column {
                ScreenTransition.entries.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(t) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = t == selected, onClick = { onSelect(t) })
                        Column {
                            Text(t.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when (t) {
                                    ScreenTransition.NONE -> "Switch screens immediately"
                                    ScreenTransition.FADE -> "Short cross-fade"
                                    ScreenTransition.SLIDE -> "Editors slide in from the side"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
