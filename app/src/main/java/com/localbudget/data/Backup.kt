package com.localbudget.data

import com.localbudget.domain.Frequency
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate

/** JSON backup/restore and CSV export. Files are written wherever the user chooses on the device. */
object Backup {
    private const val FORMAT_VERSION = 1

    fun toJson(snapshot: BudgetRepository.Snapshot, settings: AppSettings): String {
        val root = JSONObject()
        root.put("app", "LocalBudget")
        root.put("formatVersion", FORMAT_VERSION)
        root.put("exportedAt", LocalDate.now().toString())
        root.put("settings", JSONObject().apply {
            put("currency", settings.currency)
            put("periodStartDay", settings.periodStartDay)
            put("monthlyBudget", settings.monthlyBudget)
        })
        root.put("categories", JSONArray().apply {
            snapshot.categories.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("emoji", c.emoji)
                    put("color", c.color)
                    put("type", c.type.code)
                    put("budget", c.budget ?: JSONObject.NULL)
                    put("sortOrder", c.sortOrder)
                })
            }
        })
        root.put("recurring", JSONArray().apply {
            snapshot.recurring.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("name", r.name)
                    put("type", r.type.code)
                    put("amount", r.amount)
                    put("categoryId", r.categoryId ?: JSONObject.NULL)
                    put("frequency", r.frequency.code)
                    put("every", r.every)
                    put("startDate", r.startDate.toString())
                    put("endDate", r.endDate?.toString() ?: JSONObject.NULL)
                    put("postedCount", r.postedCount)
                    put("active", r.active)
                })
            }
        })
        root.put("entries", JSONArray().apply {
            snapshot.entries.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id)
                    put("type", e.type.code)
                    put("amount", e.amount)
                    put("categoryId", e.categoryId ?: JSONObject.NULL)
                    put("note", e.note)
                    put("date", e.date.toString())
                    put("recurringId", e.recurringId ?: JSONObject.NULL)
                })
            }
        })
        return root.toString(2)
    }

    class InvalidBackupException(message: String) : Exception(message)

    fun fromJson(json: String): Pair<BudgetRepository.Snapshot, AppSettings?> {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw InvalidBackupException("This file is not a valid backup.")
        }
        if (root.optString("app") != "LocalBudget") throw InvalidBackupException("This file is not a Local Budget backup.")
        if (root.optInt("formatVersion", 0) > FORMAT_VERSION) {
            throw InvalidBackupException("This backup was made by a newer version of the app.")
        }

        fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else getLong(key)
        fun JSONObject.optDateOrNull(key: String): LocalDate? =
            if (isNull(key) || !has(key)) null else LocalDate.parse(getString(key))

        val categories = root.optJSONArray("categories").objects().map { o ->
            Category(
                id = o.getLong("id"),
                name = o.getString("name"),
                emoji = o.optString("emoji", "📦"),
                color = o.optInt("color", DefaultCategories.palette.first()),
                type = EntryType.of(o.optInt("type")),
                budget = o.optLongOrNull("budget"),
                sortOrder = o.optInt("sortOrder"),
            )
        }
        val recurring = root.optJSONArray("recurring").objects().map { o ->
            Recurring(
                id = o.getLong("id"),
                name = o.getString("name"),
                type = EntryType.of(o.optInt("type")),
                amount = o.getLong("amount"),
                categoryId = o.optLongOrNull("categoryId"),
                frequency = Frequency.of(o.optInt("frequency", Frequency.MONTHLY.code)),
                every = o.optInt("every", 1),
                startDate = LocalDate.parse(o.getString("startDate")),
                endDate = o.optDateOrNull("endDate"),
                postedCount = o.optInt("postedCount"),
                active = o.optBoolean("active", true),
            )
        }
        val entries = root.optJSONArray("entries").objects().map { o ->
            Entry(
                id = o.getLong("id"),
                type = EntryType.of(o.optInt("type")),
                amount = o.getLong("amount"),
                categoryId = o.optLongOrNull("categoryId"),
                note = o.optString("note", ""),
                date = LocalDate.parse(o.getString("date")),
                recurringId = o.optLongOrNull("recurringId"),
            )
        }
        val settings = root.optJSONObject("settings")?.let { s ->
            AppSettings(
                currency = s.optString("currency", "EUR"),
                periodStartDay = s.optInt("periodStartDay", 1).coerceIn(1, 28),
                monthlyBudget = s.optLong("monthlyBudget", 0L),
            )
        }
        return BudgetRepository.Snapshot(categories, entries, recurring) to settings
    }

    fun toCsv(entries: List<Entry>): String = buildString {
        appendLine("date,type,amount,category,note")
        entries.sortedWith(compareBy<Entry>({ it.date }, { it.id })).forEach { e ->
            append(e.date).append(',')
            append(if (e.type == EntryType.INCOME) "income" else "expense").append(',')
            append(BigDecimal.valueOf(e.amount).movePointLeft(2).toPlainString()).append(',')
            append(csv(e.category?.name ?: "")).append(',')
            append(csv(e.note)).append('\n')
        }
    }

    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
}
