package com.localbudget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.localbudget.ui.BudgetAppUi
import com.localbudget.ui.theme.BudgetTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Pending quick action from the widget or a launcher shortcut. */
    private val launchAction = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) launchAction.value = intent?.action
        setContent {
            BudgetTheme {
                BudgetAppUi(
                    launchAction = launchAction,
                    onLaunchActionHandled = { launchAction.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launchAction.value = intent.action
    }

    override fun onResume() {
        super.onResume()
        // Book any recurring bills / income that became due since the app was last opened.
        val container = container
        container.scope.launch { container.repository.processRecurring() }
    }

    companion object {
        const val ACTION_ADD_EXPENSE = "com.localbudget.action.ADD_EXPENSE"
        const val ACTION_ADD_INCOME = "com.localbudget.action.ADD_INCOME"
    }
}
