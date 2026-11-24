package com.localbudget.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.localbudget.MainActivity
import com.localbudget.data.EntryType
import com.localbudget.ui.bills.BillsScreen
import com.localbudget.ui.bills.RecurringEditorScreen
import com.localbudget.ui.entry.EntryScreen
import com.localbudget.ui.history.HistoryScreen
import com.localbudget.ui.overview.OverviewScreen
import com.localbudget.ui.settings.CategoriesScreen
import com.localbudget.ui.settings.SettingsScreen
import com.localbudget.ui.stats.StatsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Routes {
    const val OVERVIEW = "overview"
    const val HISTORY = "history"
    const val STATS = "stats"
    const val BILLS = "bills"
    const val SETTINGS = "settings"
    const val CATEGORIES = "categories"
    const val ENTRY = "entry?id={id}&type={type}"
    const val RECURRING = "recurring?id={id}"

    fun entry(id: Long = 0L, type: EntryType = EntryType.EXPENSE) = "entry?id=$id&type=${type.code}"
    fun recurring(id: Long = 0L) = "recurring?id=$id"
}

/** Shows snackbars that survive navigating away from the screen that triggered them. */
class Messenger(private val scope: CoroutineScope, val host: SnackbarHostState) {
    fun show(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        scope.launch(Dispatchers.Main.immediate) {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}

val LocalMessenger = staticCompositionLocalOf<Messenger> { error("No Messenger provided") }

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.OVERVIEW, "Overview", Icons.Default.Home),
    TopLevel(Routes.HISTORY, "History", Icons.AutoMirrored.Filled.List),
    TopLevel(Routes.STATS, "Stats", Icons.Default.PieChart),
    TopLevel(Routes.BILLS, "Recurring", Icons.Default.Autorenew),
)

@Composable
fun BudgetAppUi(launchAction: StateFlow<String?>, onLaunchActionHandled: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messenger = remember(scope, snackbar) { Messenger(scope, snackbar) }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottomBar = route == null || topLevel.any { it.route == route }

    val action by launchAction.collectAsStateWithLifecycle()
    LaunchedEffect(action) {
        when (action) {
            MainActivity.ACTION_ADD_EXPENSE -> nav.navigate(Routes.entry(type = EntryType.EXPENSE))
            MainActivity.ACTION_ADD_INCOME -> nav.navigate(Routes.entry(type = EntryType.INCOME))
        }
        if (action != null) onLaunchActionHandled()
    }

    CompositionLocalProvider(LocalMessenger provides messenger) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            snackbarHost = {
                // On tab screens, keep the snackbar above the floating "Add" button so it never covers it.
                SnackbarHost(
                    snackbar,
                    modifier = if (showBottomBar) Modifier.padding(bottom = 80.dp) else Modifier.navigationBarsPadding(),
                )
            },
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        topLevel.forEach { item ->
                            NavigationBarItem(
                                selected = route == item.route,
                                onClick = { nav.navigateTopLevel(item.route) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Routes.OVERVIEW,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            ) {
                composable(Routes.OVERVIEW) {
                    OverviewScreen(
                        onAddEntry = { nav.navigate(Routes.entry()) },
                        onOpenEntry = { nav.navigate(Routes.entry(id = it)) },
                        onOpenRecurring = { nav.navigate(Routes.recurring(it)) },
                        onShowHistory = { nav.navigateTopLevel(Routes.HISTORY) },
                        onShowBills = { nav.navigateTopLevel(Routes.BILLS) },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        onAddEntry = { nav.navigate(Routes.entry()) },
                        onOpenEntry = { nav.navigate(Routes.entry(id = it)) },
                    )
                }
                composable(Routes.STATS) {
                    StatsScreen()
                }
                composable(Routes.BILLS) {
                    BillsScreen(
                        onAdd = { nav.navigate(Routes.recurring()) },
                        onOpen = { nav.navigate(Routes.recurring(it)) },
                    )
                }
                composable(
                    Routes.ENTRY,
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("type") { type = NavType.IntType; defaultValue = EntryType.EXPENSE.code },
                    ),
                ) { entry ->
                    EntryScreen(
                        entryId = entry.arguments?.getLong("id") ?: 0L,
                        initialType = EntryType.of(entry.arguments?.getInt("type") ?: 0),
                        onDone = { nav.popBackStack() },
                    )
                }
                composable(
                    Routes.RECURRING,
                    arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
                ) { entry ->
                    RecurringEditorScreen(
                        recurringId = entry.arguments?.getLong("id") ?: 0L,
                        onDone = { nav.popBackStack() },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { nav.popBackStack() },
                        onOpenCategories = { nav.navigate(Routes.CATEGORIES) },
                    )
                }
                composable(Routes.CATEGORIES) {
                    CategoriesScreen(onBack = { nav.popBackStack() })
                }
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
