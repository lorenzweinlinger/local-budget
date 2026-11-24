package com.localbudget.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.localbudget.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Drives the real app UI: add an expense with the number pad and see it on every screen. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun addExpenseAndBrowseScreens() {
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Nothing here yet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("add_entry").performClick()

        compose.onNodeWithTag("save").assertIsNotEnabled()
        listOf('4', '2', '.', '5').forEach { compose.onNodeWithTag("key_$it").performClick() }
        compose.onNodeWithTag("amount").assertTextContains("42.5", substring = true)
        compose.onNodeWithText("🍔  Eating out").performClick()
        compose.onNodeWithTag("note").performTextInput("Pizza")
        compose.onNodeWithTag("save").assertIsEnabled().performClick()
        compose.waitForIdle()

        // Back on the overview: the entry shows up in "Recent".
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Today · Pizza").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Eating out").assertExists()

        // Add an income via the type toggle.
        compose.onNodeWithTag("add_entry").performClick()
        compose.onNodeWithTag("type_income").performClick()
        listOf('1', '0', '0').forEach { compose.onNodeWithTag("key_$it").performClick() }
        compose.onNodeWithTag("save").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Salary").fetchSemanticsNodes().isNotEmpty() }

        // Visit every tab to make sure they render.
        compose.onNodeWithText("History").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Stats").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Statistics").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Recurring").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("No recurring items").fetchSemanticsNodes().isNotEmpty() }

        // Create a recurring bill.
        compose.onNodeWithText("New").performClick()
        compose.onNodeWithText("Name").performTextInput("Rent")
        compose.onNodeWithText("Amount").performTextInput("900")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Bills & subscriptions").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodesWithText("Rent").fetchSemanticsNodes().isNotEmpty())

        // Settings opens from the overview.
        compose.onNodeWithText("Overview").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Set a monthly budget").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Export backup").fetchSemanticsNodes().isNotEmpty() }
    }
}
