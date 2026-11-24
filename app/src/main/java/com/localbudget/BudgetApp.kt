package com.localbudget

import android.app.Application
import android.content.Context
import com.localbudget.data.BudgetDatabase
import com.localbudget.data.BudgetRepository
import com.localbudget.data.SettingsStore
import com.localbudget.widget.BudgetWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BudgetApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Simple manual dependency container shared by the UI and the widget. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val repository = BudgetRepository(BudgetDatabase(appContext))
    val settings = SettingsStore(appContext)

    init {
        val refreshWidget: () -> Unit = { scope.launch { BudgetWidget.updateAll(appContext) } }
        repository.onChanged = refreshWidget
        settings.onChanged = refreshWidget
    }
}

val Context.container: AppContainer get() = (applicationContext as BudgetApp).container
