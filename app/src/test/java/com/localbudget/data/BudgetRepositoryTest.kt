package com.localbudget.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.localbudget.domain.Frequency
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BudgetRepositoryTest {
    private lateinit var db: BudgetDatabase
    private lateinit var repo: BudgetRepository

    @Before fun setUp() {
        db = BudgetDatabase(ApplicationProvider.getApplicationContext(), name = null) // in-memory
        repo = BudgetRepository(db)
    }

    @After fun tearDown() = db.close()

    private fun d(s: String) = LocalDate.parse(s)
    private fun groceries() = repo.categories().first { it.name == "Groceries" }
    private fun salary() = repo.categories().first { it.name == "Salary" }

    @Test fun seedsDefaultCategories() {
        val categories = repo.categories()
        assertTrue(categories.any { it.type == EntryType.EXPENSE })
        assertTrue(categories.any { it.type == EntryType.INCOME })
    }

    @Test fun savesEntriesAndComputesTotals() = runBlocking {
        repo.saveEntry(Entry(type = EntryType.EXPENSE, amount = 1250, categoryId = groceries().id, note = "Milk", date = d("2026-11-03")))
        repo.saveEntry(Entry(type = EntryType.EXPENSE, amount = 750, categoryId = groceries().id, note = "", date = d("2026-11-04")))
        repo.saveEntry(Entry(type = EntryType.INCOME, amount = 300000, categoryId = salary().id, note = "", date = d("2026-11-01")))
        repo.saveEntry(Entry(type = EntryType.EXPENSE, amount = 999, categoryId = null, note = "", date = d("2026-10-31")))

        val totals = repo.totals(d("2026-11-01"), d("2026-11-30"))
        assertEquals(2000L, totals.expense)
        assertEquals(300000L, totals.income)

        val entries = repo.entries(d("2026-11-01"), d("2026-11-30"))
        assertEquals(3, entries.size)
        assertEquals("Groceries", entries.first().category?.name)

        val byCategory = repo.amountsByCategory(EntryType.EXPENSE, d("2026-11-01"), d("2026-11-30"))
        assertEquals(listOf(2000L), byCategory.map { it.amount })

        assertEquals(1, repo.search("milk").size)
    }

    @Test fun deletingCategoryKeepsEntries() = runBlocking {
        val cat = groceries()
        val id = repo.saveEntry(Entry(type = EntryType.EXPENSE, amount = 100, categoryId = cat.id, note = "", date = d("2026-11-03")))
        repo.deleteCategory(cat.id)
        val entry = repo.entry(id)!!
        assertNull(entry.categoryId)
        assertNull(entry.category)
    }

    @Test fun deleteAndRestoreEntry() = runBlocking {
        val id = repo.saveEntry(Entry(type = EntryType.EXPENSE, amount = 100, categoryId = null, note = "x", date = d("2026-11-03")))
        val entry = repo.entry(id)!!
        repo.deleteEntry(id)
        assertNull(repo.entry(id))
        repo.restoreEntry(entry)
        assertEquals("x", repo.entry(id)?.note)
    }

    @Test fun processesRecurringOnceAndCatchesUp() = runBlocking {
        repo.saveRecurring(
            Recurring(
                name = "Rent", type = EntryType.EXPENSE, amount = 90000, categoryId = null,
                frequency = Frequency.MONTHLY, every = 1, startDate = d("2026-09-01"), endDate = null,
                postedCount = 0, active = true,
            ),
        )
        assertEquals(3, repo.processRecurring(d("2026-11-24")))
        assertEquals(0, repo.processRecurring(d("2026-11-24"))) // idempotent
        val rent = repo.recurring().single()
        assertEquals(3, rent.postedCount)
        assertEquals(d("2026-12-01"), rent.nextDate)
        val entries = repo.entries(d("2026-01-01"), d("2026-12-31"))
        assertEquals(3, entries.size)
        assertTrue(entries.all { it.recurringId == rent.id && it.note == "Rent" })
        assertEquals(1, repo.processRecurring(d("2026-12-01")))
    }

    @Test fun pausedRecurringIsNotBooked() = runBlocking {
        repo.saveRecurring(
            Recurring(
                name = "Gym", type = EntryType.EXPENSE, amount = 3000, categoryId = null,
                frequency = Frequency.MONTHLY, every = 1, startDate = d("2026-09-01"), endDate = null,
                postedCount = 0, active = false,
            ),
        )
        assertEquals(0, repo.processRecurring(d("2026-11-24")))
    }

    @Test fun entryWithRepeatCreatesSeries() = runBlocking {
        repo.saveEntryWithRepeat(
            Entry(type = EntryType.EXPENSE, amount = 1299, categoryId = null, note = "Netflix", date = d("2026-10-15")),
            Frequency.MONTHLY, 1,
        )
        val series = repo.recurring().single()
        assertEquals("Netflix", series.name)
        assertEquals(d("2026-11-15"), series.nextDate)
        assertEquals(1, repo.processRecurring(d("2026-11-24")))
        assertEquals(2, repo.entryCount())
    }

    @Test fun backupRoundTrip() = runBlocking {
        repo.saveEntry(Entry(type = EntryType.EXPENSE, amount = 1250, categoryId = groceries().id, note = "Milk, \"fresh\"", date = d("2026-11-03")))
        repo.saveRecurring(
            Recurring(
                name = "Salary", type = EntryType.INCOME, amount = 300000, categoryId = salary().id,
                frequency = Frequency.MONTHLY, every = 1, startDate = d("2026-12-01"), endDate = null,
                postedCount = 0, active = true,
            ),
        )
        val settings = AppSettings(currency = "CHF", periodStartDay = 25, monthlyBudget = 150000)
        val json = Backup.toJson(repo.snapshot(), settings)

        val other = BudgetRepository(BudgetDatabase(ApplicationProvider.getApplicationContext(), name = null))
        val (snapshot, restoredSettings) = Backup.fromJson(json)
        other.replaceAll(snapshot)
        assertEquals(settings, restoredSettings)
        assertEquals(repo.snapshot().entries.map { it.copy(category = null) }, other.snapshot().entries.map { it.copy(category = null) })
        assertEquals(repo.categories(), other.categories())
        assertEquals(repo.recurring(), other.recurring())

        val csv = Backup.toCsv(repo.snapshot().entries)
        assertTrue(csv.contains("2026-11-03,expense,12.5,Groceries,\"Milk, \"\"fresh\"\"\""))
    }
}
