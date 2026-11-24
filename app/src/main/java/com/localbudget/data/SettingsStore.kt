package com.localbudget.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Currency
import java.util.Locale

data class AppSettings(
    val currency: String,
    /** Day of month on which a budget period starts (1–28), e.g. your payday. */
    val periodStartDay: Int,
    /** Overall monthly spending budget in cents, 0 = no budget. */
    val monthlyBudget: Long,
    /** Animation used when switching between screens. */
    val transition: ScreenTransition = ScreenTransition.NONE,
)

enum class ScreenTransition(val label: String) {
    NONE("Instant"),
    FADE("Fade"),
    SLIDE("Slide");

    companion object {
        fun of(name: String?): ScreenTransition = entries.firstOrNull { it.name == name } ?: NONE
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state
    val current: AppSettings get() = _state.value

    /** Called after settings change, e.g. to refresh the widget. */
    var onChanged: (() -> Unit)? = null

    private fun load() = AppSettings(
        currency = prefs.getString(KEY_CURRENCY, null) ?: defaultCurrency(),
        periodStartDay = prefs.getInt(KEY_START_DAY, 1),
        monthlyBudget = prefs.getLong(KEY_BUDGET, 0L),
        transition = ScreenTransition.of(prefs.getString(KEY_TRANSITION, null)),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val new = transform(_state.value)
        prefs.edit()
            .putString(KEY_CURRENCY, new.currency)
            .putInt(KEY_START_DAY, new.periodStartDay.coerceIn(1, 28))
            .putLong(KEY_BUDGET, new.monthlyBudget.coerceAtLeast(0))
            .putString(KEY_TRANSITION, new.transition.name)
            .apply()
        _state.value = new
        onChanged?.invoke()
    }

    /** The most recently used category per entry type, so the entry screen can preselect it. */
    fun lastCategory(type: EntryType): Long? =
        prefs.getLong(KEY_LAST_CATEGORY + type.code, -1L).takeIf { it > 0 }

    fun setLastCategory(type: EntryType, categoryId: Long?) {
        prefs.edit().putLong(KEY_LAST_CATEGORY + type.code, categoryId ?: -1L).apply()
    }

    private companion object {
        const val KEY_CURRENCY = "currency"
        const val KEY_START_DAY = "period_start_day"
        const val KEY_BUDGET = "monthly_budget"
        const val KEY_TRANSITION = "screen_transition"
        const val KEY_LAST_CATEGORY = "last_category_"

        fun defaultCurrency(): String =
            runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull() ?: "EUR"
    }
}
