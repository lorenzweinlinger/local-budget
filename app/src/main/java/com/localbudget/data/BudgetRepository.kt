package com.localbudget.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.localbudget.domain.Frequency
import com.localbudget.domain.Recurrence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Single source of truth for all budget data. Everything lives in a local SQLite database.
 *
 * Reads are plain blocking functions (call them from a background thread, e.g. inside [observe]);
 * writes are suspend functions that bump [version] so that observers re-query.
 */
class BudgetRepository(private val db: BudgetDatabase) {

    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version

    /** Called after every write, e.g. to refresh the home screen widget. */
    var onChanged: (() -> Unit)? = null

    private val recurringMutex = Mutex()

    /** Re-runs [query] on a background thread whenever data changes. */
    fun <T> observe(query: () -> T): Flow<T> = _version.map { query() }.flowOn(Dispatchers.IO)

    private fun changed() {
        _version.update { it + 1 }
        onChanged?.invoke()
    }

    private val read: SQLiteDatabase get() = db.readableDatabase
    private val write: SQLiteDatabase get() = db.writableDatabase

    // region Categories

    fun categories(): List<Category> =
        read.rawQuery("SELECT $CATEGORY_COLUMNS FROM categories c ORDER BY c.type, c.sort_order, c.id", null)
            .use { cursor -> cursor.mapRows { readCategory(it, 0)!! } }

    suspend fun saveCategory(category: Category): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("name", category.name.trim())
            put("emoji", category.emoji)
            put("color", category.color)
            put("type", category.type.code)
            if (category.budget != null && category.budget > 0) put("budget", category.budget) else putNull("budget")
        }
        val id = if (category.id == 0L) {
            val nextOrder = read.rawQuery("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM categories", null)
                .use { if (it.moveToFirst()) it.getInt(0) else 0 }
            values.put("sort_order", nextOrder)
            write.insertOrThrow("categories", null, values)
        } else {
            write.update("categories", values, "id = ?", arrayOf(category.id.toString()))
            category.id
        }
        changed()
        id
    }

    /** Deletes a category. Its entries are kept and become "Uncategorized". */
    suspend fun deleteCategory(id: Long) = withContext(Dispatchers.IO) {
        write.delete("categories", "id = ?", arrayOf(id.toString()))
        changed()
    }

    fun categoryUsage(id: Long): Int =
        read.rawQuery("SELECT COUNT(*) FROM entries WHERE category_id = ?", arrayOf(id.toString()))
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    // endregion

    // region Entries

    fun entries(from: LocalDate, to: LocalDate): List<Entry> = queryEntries(
        "WHERE e.date BETWEEN ? AND ? ORDER BY e.date DESC, e.id DESC",
        arrayOf(from.toEpochDay().toString(), to.toEpochDay().toString()),
    )

    fun recentEntries(limit: Int): List<Entry> = queryEntries(
        "WHERE e.date <= ? ORDER BY e.date DESC, e.id DESC LIMIT $limit",
        arrayOf(LocalDate.now().toEpochDay().toString()),
    )

    /** Searches notes and category names across all time. */
    fun search(text: String, limit: Int = 300): List<Entry> {
        val pattern = "%" + text.trim().replace("%", "").replace("_", "") + "%"
        return queryEntries(
            "WHERE e.note LIKE ? OR c.name LIKE ? ORDER BY e.date DESC, e.id DESC LIMIT $limit",
            arrayOf(pattern, pattern),
        )
    }

    fun entry(id: Long): Entry? = queryEntries("WHERE e.id = ?", arrayOf(id.toString())).firstOrNull()

    fun entryCount(): Int = read.rawQuery("SELECT COUNT(*) FROM entries", null)
        .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun queryEntries(clause: String, args: Array<String>): List<Entry> {
        val sql = """
            SELECT e.id, e.type, e.amount, e.category_id, e.note, e.date, e.recurring_id, $CATEGORY_COLUMNS
            FROM entries e LEFT JOIN categories c ON c.id = e.category_id
            $clause
        """.trimIndent()
        return read.rawQuery(sql, args).use { cursor ->
            cursor.mapRows {
                Entry(
                    id = it.getLong(0),
                    type = EntryType.of(it.getInt(1)),
                    amount = it.getLong(2),
                    categoryId = it.getLongOrNull(3),
                    note = it.getString(4) ?: "",
                    date = LocalDate.ofEpochDay(it.getLong(5)),
                    recurringId = it.getLongOrNull(6),
                    category = readCategory(it, 7),
                )
            }
        }
    }

    suspend fun saveEntry(entry: Entry): Long = withContext(Dispatchers.IO) {
        val id = upsertEntry(write, entry)
        changed()
        id
    }

    suspend fun deleteEntry(id: Long) = withContext(Dispatchers.IO) {
        write.delete("entries", "id = ?", arrayOf(id.toString()))
        changed()
    }

    /** Re-inserts a deleted entry with its original id (used for "Undo"). */
    suspend fun restoreEntry(entry: Entry) = withContext(Dispatchers.IO) {
        upsertEntry(write, entry, keepId = true)
        changed()
    }

    private fun upsertEntry(db: SQLiteDatabase, entry: Entry, keepId: Boolean = false): Long {
        val values = ContentValues().apply {
            put("type", entry.type.code)
            put("amount", entry.amount)
            if (entry.categoryId != null) put("category_id", entry.categoryId) else putNull("category_id")
            put("note", entry.note.trim())
            put("date", entry.date.toEpochDay())
            if (entry.recurringId != null) put("recurring_id", entry.recurringId) else putNull("recurring_id")
        }
        return if (entry.id == 0L || keepId) {
            if (keepId && entry.id != 0L) values.put("id", entry.id)
            values.put("created_at", System.currentTimeMillis())
            db.insertWithOnConflict("entries", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } else {
            db.update("entries", values, "id = ?", arrayOf(entry.id.toString()))
            entry.id
        }
    }

    // endregion

    // region Aggregates

    fun totals(from: LocalDate, to: LocalDate): Totals {
        var income = 0L
        var expense = 0L
        read.rawQuery(
            "SELECT type, SUM(amount) FROM entries WHERE date BETWEEN ? AND ? GROUP BY type",
            arrayOf(from.toEpochDay().toString(), to.toEpochDay().toString()),
        ).use { c ->
            while (c.moveToNext()) {
                if (EntryType.of(c.getInt(0)) == EntryType.INCOME) income = c.getLong(1) else expense = c.getLong(1)
            }
        }
        return Totals(income, expense)
    }

    /** Sum per category, largest first. Entries without a category are grouped under a null category. */
    fun amountsByCategory(type: EntryType, from: LocalDate, to: LocalDate): List<CategoryAmount> {
        val categories = categories().associateBy { it.id }
        return read.rawQuery(
            """
            SELECT category_id, SUM(amount) AS total FROM entries
            WHERE type = ? AND date BETWEEN ? AND ?
            GROUP BY category_id ORDER BY total DESC
            """.trimIndent(),
            arrayOf(type.code.toString(), from.toEpochDay().toString(), to.toEpochDay().toString()),
        ).use { c ->
            c.mapRows { CategoryAmount(it.getLongOrNull(0)?.let(categories::get), it.getLong(1)) }
        }
    }

    /** Expense totals per day (only days that have expenses). */
    fun dailyExpenses(from: LocalDate, to: LocalDate): Map<LocalDate, Long> = read.rawQuery(
        "SELECT date, SUM(amount) FROM entries WHERE type = 0 AND date BETWEEN ? AND ? GROUP BY date",
        arrayOf(from.toEpochDay().toString(), to.toEpochDay().toString()),
    ).use { c ->
        buildMap { while (c.moveToNext()) put(LocalDate.ofEpochDay(c.getLong(0)), c.getLong(1)) }
    }

    // endregion

    // region Recurring

    fun recurring(): List<Recurring> {
        val sql = """
            SELECT r.id, r.name, r.type, r.amount, r.category_id, r.frequency, r.every, r.start_date,
                   r.end_date, r.posted_count, r.active, $CATEGORY_COLUMNS
            FROM recurring r LEFT JOIN categories c ON c.id = r.category_id
            ORDER BY r.type, r.name COLLATE NOCASE
        """.trimIndent()
        return read.rawQuery(sql, null).use { cursor ->
            cursor.mapRows {
                Recurring(
                    id = it.getLong(0),
                    name = it.getString(1),
                    type = EntryType.of(it.getInt(2)),
                    amount = it.getLong(3),
                    categoryId = it.getLongOrNull(4),
                    frequency = Frequency.of(it.getInt(5)),
                    every = it.getInt(6),
                    startDate = LocalDate.ofEpochDay(it.getLong(7)),
                    endDate = it.getLongOrNull(8)?.let(LocalDate::ofEpochDay),
                    postedCount = it.getInt(9),
                    active = it.getInt(10) == 1,
                    category = readCategory(it, 11),
                )
            }
        }
    }

    fun recurring(id: Long): Recurring? = recurring().firstOrNull { it.id == id }

    suspend fun saveRecurring(item: Recurring): Long = withContext(Dispatchers.IO) {
        val id = upsertRecurring(write, item)
        changed()
        id
    }

    private fun upsertRecurring(db: SQLiteDatabase, item: Recurring, keepId: Boolean = false): Long {
        val values = ContentValues().apply {
            put("name", item.name.trim())
            put("type", item.type.code)
            put("amount", item.amount)
            if (item.categoryId != null) put("category_id", item.categoryId) else putNull("category_id")
            put("frequency", item.frequency.code)
            put("every", item.every.coerceAtLeast(1))
            put("start_date", item.startDate.toEpochDay())
            if (item.endDate != null) put("end_date", item.endDate.toEpochDay()) else putNull("end_date")
            put("posted_count", item.postedCount)
            put("active", if (item.active) 1 else 0)
        }
        return if (item.id == 0L || keepId) {
            if (keepId && item.id != 0L) values.put("id", item.id)
            db.insertWithOnConflict("recurring", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } else {
            db.update("recurring", values, "id = ?", arrayOf(item.id.toString()))
            item.id
        }
    }

    /** Deletes a recurring item. Entries it already created are kept. */
    suspend fun deleteRecurring(id: Long) = withContext(Dispatchers.IO) {
        write.delete("recurring", "id = ?", arrayOf(id.toString()))
        changed()
    }

    /**
     * Creates a new entry and, optionally, a recurring item starting with it.
     * The entry counts as the first occurrence of the series.
     */
    suspend fun saveEntryWithRepeat(entry: Entry, frequency: Frequency, every: Int): Long =
        withContext(Dispatchers.IO) {
            val w = write
            w.beginTransaction()
            val entryId = try {
                val name = entry.note.ifBlank { entry.category?.name ?: "Recurring" }
                val recurringId = upsertRecurring(
                    w,
                    Recurring(
                        name = name, type = entry.type, amount = entry.amount, categoryId = entry.categoryId,
                        frequency = frequency, every = every, startDate = entry.date, endDate = null,
                        postedCount = 1, active = true,
                    ),
                )
                val id = upsertEntry(w, entry.copy(recurringId = recurringId))
                w.setTransactionSuccessful()
                id
            } finally {
                w.endTransaction()
            }
            changed()
            entryId
        }

    /**
     * Books every recurring occurrence that is due on or before [today].
     * Safe to call often (app start, widget update); returns the number of entries created.
     */
    suspend fun processRecurring(today: LocalDate = LocalDate.now()): Int = withContext(Dispatchers.IO) {
        recurringMutex.withLock {
            var created = 0
            val w = write
            w.beginTransaction()
            try {
                for (item in recurring()) {
                    if (!item.active) continue
                    val due = Recurrence.dueOccurrences(
                        item.startDate, item.frequency, item.every, item.postedCount, item.endDate, today,
                    )
                    if (due.isEmpty()) continue
                    for (date in due) {
                        upsertEntry(
                            w,
                            Entry(
                                type = item.type, amount = item.amount, categoryId = item.categoryId,
                                note = item.name, date = date, recurringId = item.id,
                            ),
                        )
                    }
                    w.update(
                        "recurring",
                        ContentValues().apply { put("posted_count", item.postedCount + due.size) },
                        "id = ?",
                        arrayOf(item.id.toString()),
                    )
                    created += due.size
                }
                w.setTransactionSuccessful()
            } finally {
                w.endTransaction()
            }
            if (created > 0) changed()
            created
        }
    }

    // endregion

    // region Backup

    data class Snapshot(
        val categories: List<Category>,
        val entries: List<Entry>,
        val recurring: List<Recurring>,
    )

    fun snapshot(): Snapshot = Snapshot(
        categories = categories(),
        entries = queryEntries("ORDER BY e.date, e.id", emptyArray()),
        recurring = recurring(),
    )

    /** Replaces ALL data with [snapshot]. */
    suspend fun replaceAll(snapshot: Snapshot) = withContext(Dispatchers.IO) {
        val w = write
        w.beginTransaction()
        try {
            w.delete("entries", null, null)
            w.delete("recurring", null, null)
            w.delete("categories", null, null)
            snapshot.categories.forEach { c ->
                w.insertOrThrow("categories", null, ContentValues().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("emoji", c.emoji)
                    put("color", c.color)
                    put("type", c.type.code)
                    if (c.budget != null) put("budget", c.budget) else putNull("budget")
                    put("sort_order", c.sortOrder)
                })
            }
            val categoryIds = snapshot.categories.map { it.id }.toSet()
            snapshot.recurring.forEach { r ->
                upsertRecurring(w, r.copy(categoryId = r.categoryId?.takeIf { it in categoryIds }), keepId = true)
            }
            val recurringIds = snapshot.recurring.map { it.id }.toSet()
            snapshot.entries.forEach { e ->
                upsertEntry(
                    w,
                    e.copy(
                        categoryId = e.categoryId?.takeIf { it in categoryIds },
                        recurringId = e.recurringId?.takeIf { it in recurringIds },
                    ),
                    keepId = true,
                )
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        changed()
    }

    // endregion

    private companion object {
        const val CATEGORY_COLUMNS = "c.id, c.name, c.emoji, c.color, c.type, c.budget, c.sort_order"

        fun readCategory(c: Cursor, offset: Int): Category? {
            if (c.isNull(offset)) return null
            return Category(
                id = c.getLong(offset),
                name = c.getString(offset + 1),
                emoji = c.getString(offset + 2),
                color = c.getInt(offset + 3),
                type = EntryType.of(c.getInt(offset + 4)),
                budget = c.getLongOrNull(offset + 5),
                sortOrder = c.getInt(offset + 6),
            )
        }

        fun Cursor.getLongOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)

        inline fun <T> Cursor.mapRows(transform: (Cursor) -> T): List<T> {
            val result = ArrayList<T>(count.coerceAtLeast(0))
            while (moveToNext()) result += transform(this)
            return result
        }
    }
}
