package com.localbudget.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class BudgetDatabase(context: Context, name: String? = DB_NAME) :
    SQLiteOpenHelper(context, name, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                emoji TEXT NOT NULL,
                color INTEGER NOT NULL,
                type INTEGER NOT NULL,
                budget INTEGER,
                sort_order INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE recurring (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                type INTEGER NOT NULL,
                amount INTEGER NOT NULL,
                category_id INTEGER REFERENCES categories(id) ON DELETE SET NULL,
                frequency INTEGER NOT NULL,
                every INTEGER NOT NULL DEFAULT 1,
                start_date INTEGER NOT NULL,
                end_date INTEGER,
                posted_count INTEGER NOT NULL DEFAULT 0,
                active INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type INTEGER NOT NULL,
                amount INTEGER NOT NULL,
                category_id INTEGER REFERENCES categories(id) ON DELETE SET NULL,
                note TEXT NOT NULL DEFAULT '',
                date INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                recurring_id INTEGER REFERENCES recurring(id) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_entries_date ON entries(date)")
        db.execSQL("CREATE INDEX idx_entries_category ON entries(category_id)")
        db.execSQL("CREATE INDEX idx_entries_recurring ON entries(recurring_id)")
        seedCategories(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema migrations go here.
    }

    private fun seedCategories(db: SQLiteDatabase) {
        DefaultCategories.all.forEachIndexed { index, c ->
            db.insert("categories", null, ContentValues().apply {
                put("name", c.name)
                put("emoji", c.emoji)
                put("color", c.color)
                put("type", c.type.code)
                put("sort_order", index)
            })
        }
    }

    companion object {
        const val DB_NAME = "budget.db"
        const val VERSION = 1
    }
}

object DefaultCategories {
    private fun c(name: String, emoji: String, color: Long, type: EntryType = EntryType.EXPENSE) =
        Category(name = name, emoji = emoji, color = color.toInt(), type = type)

    val all = listOf(
        c("Groceries", "🛒", 0xFF4CAF50),
        c("Eating out", "🍔", 0xFFFF9800),
        c("Housing", "🏠", 0xFF795548),
        c("Transport", "🚌", 0xFF2196F3),
        c("Utilities", "💡", 0xFFFFC107),
        c("Subscriptions", "📱", 0xFF9C27B0),
        c("Shopping", "🛍️", 0xFFE91E63),
        c("Entertainment", "🎬", 0xFF673AB7),
        c("Health", "💊", 0xFFF44336),
        c("Travel", "✈️", 0xFF00BCD4),
        c("Gifts", "🎁", 0xFFFF5722),
        c("Other", "📦", 0xFF607D8B),
        c("Salary", "💼", 0xFF2E7D32, EntryType.INCOME),
        c("Side income", "💰", 0xFF00897B, EntryType.INCOME),
        c("Gifts received", "🎉", 0xFFAB47BC, EntryType.INCOME),
        c("Refunds", "↩️", 0xFF1E88E5, EntryType.INCOME),
        c("Other income", "📈", 0xFF546E7A, EntryType.INCOME),
    )

    /** Palette offered when creating/editing a category. */
    val palette: List<Int> = listOf(
        0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5, 0xFF2196F3,
        0xFF00BCD4, 0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFFFC107, 0xFFFF9800,
        0xFFFF5722, 0xFF795548, 0xFF607D8B, 0xFF2E7D32,
    ).map { it.toInt() }

    val emojis: List<String> = listOf(
        "🛒", "🍔", "☕", "🍺", "🏠", "🚌", "🚗", "⛽", "💡", "📱", "💻", "🛍️", "👕", "🎬",
        "🎮", "📚", "💊", "🏋️", "✈️", "🏖️", "🎁", "🐶", "👶", "🎓", "💇", "🔧", "🧾", "🏦",
        "💼", "💰", "📈", "↩️", "🎉", "📦",
    )
}
