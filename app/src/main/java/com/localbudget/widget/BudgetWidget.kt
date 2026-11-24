package com.localbudget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.localbudget.MainActivity
import com.localbudget.R
import com.localbudget.container
import com.localbudget.domain.Money
import com.localbudget.domain.Periods
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Home screen widget: this period's spending / remaining budget plus quick-add buttons. */
class BudgetWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val container = context.container
        container.scope.launch {
            try {
                // Also books due bills, so the widget stays correct even if the app isn't opened.
                val created = container.repository.processRecurring()
                if (created == 0) BudgetWidget.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}

object BudgetWidget {

    suspend fun updateAll(context: Context) = withContext(Dispatchers.IO) {
        val manager = AppWidgetManager.getInstance(context) ?: return@withContext
        val ids = manager.getAppWidgetIds(ComponentName(context, BudgetWidgetProvider::class.java))
        if (ids == null || ids.isEmpty()) return@withContext
        val views = buildViews(context)
        manager.updateAppWidget(ids, views)
    }

    private fun buildViews(context: Context): RemoteViews {
        val container = context.container
        val settings = container.settings.current
        val today = LocalDate.now()
        val period = Periods.containing(today, settings.periodStartDay)
        val totals = container.repository.totals(period.start, period.endInclusive)
        val currency = settings.currency
        val budget = settings.monthlyBudget

        val views = RemoteViews(context.packageName, R.layout.widget_budget)
        views.setTextViewText(R.id.widget_period, Periods.label(period))

        if (budget > 0) {
            val left = budget - totals.expense
            val daysLeft = period.daysLeft(today).coerceAtLeast(1)
            if (left >= 0) {
                views.setTextViewText(R.id.widget_headline, Money.format(left, currency))
                views.setTextViewText(R.id.widget_headline_label, context.getString(R.string.widget_left))
                views.setTextViewText(
                    R.id.widget_detail,
                    context.getString(
                        R.string.widget_detail_budget,
                        Money.format(totals.expense, currency),
                        Money.format(budget, currency),
                        Money.format(left / daysLeft, currency),
                    ),
                )
            } else {
                views.setTextViewText(R.id.widget_headline, Money.format(-left, currency))
                views.setTextViewText(R.id.widget_headline_label, context.getString(R.string.widget_over))
                views.setTextViewText(
                    R.id.widget_detail,
                    context.getString(
                        R.string.widget_detail_over,
                        Money.format(totals.expense, currency),
                        Money.format(budget, currency),
                    ),
                )
            }
            views.setViewVisibility(R.id.widget_progress, View.VISIBLE)
            val progress = ((totals.expense * 1000) / budget).coerceIn(0, 1000).toInt()
            views.setProgressBar(R.id.widget_progress, 1000, progress, false)
        } else {
            views.setTextViewText(R.id.widget_headline, Money.format(totals.expense, currency))
            views.setTextViewText(R.id.widget_headline_label, context.getString(R.string.widget_spent))
            views.setTextViewText(
                R.id.widget_detail,
                context.getString(
                    R.string.widget_detail_balance,
                    Money.format(totals.income, currency),
                    Money.format(totals.balance, currency),
                ),
            )
            views.setViewVisibility(R.id.widget_progress, View.GONE)
        }

        views.setOnClickPendingIntent(R.id.widget_root, launchIntent(context, null, 10))
        views.setOnClickPendingIntent(R.id.widget_add_expense, launchIntent(context, MainActivity.ACTION_ADD_EXPENSE, 11))
        views.setOnClickPendingIntent(R.id.widget_add_income, launchIntent(context, MainActivity.ACTION_ADD_INCOME, 12))
        return views
    }

    private fun launchIntent(context: Context, action: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action ?: Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
