package it.danielebufarini.spesify

import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipe
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class DashboardInteractionTests {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun awaitDashboard() {
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasContentDescription("Add Expense") or hasText("Skip")).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodes(hasText("Skip")).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Skip").performClick()
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Add Expense").fetchSemanticsNodes().size == 1
        }
    }

    private fun selectCard(expand: String) {
        compose.onNodeWithTag("dashboard_compact_card").performScrollTo()
        repeat(3) {
            if (compose.onAllNodesWithContentDescription(expand).fetchSemanticsNodes().isNotEmpty()) return
            compose.onNodeWithTag("dashboard_pager").performTouchInput { swipeLeft() }
        }
        compose.onNodeWithContentDescription(expand).assertIsDisplayed()
    }

    @Test
    fun categoryAndRecentCardsGrowFromTheirBoundsAndReturnToTheSamePage() {
        awaitDashboard()
        listOf("top categories", "recent transactions").forEach { name ->
            selectCard("Expand $name")
            val compact = compose.onNodeWithTag("dashboard_compact_card").fetchSemanticsNode().boundsInRoot
            capture("$name-compact", compact.toString())
            compose.mainClock.autoAdvance = false
            compose.onNodeWithContentDescription("Expand $name").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(120)
            compose.waitForIdle()
            val middle = compose.onNodeWithTag("dashboard_expanded_card").fetchSemanticsNode().boundsInRoot
            capture("$name-middle", middle.toString())
            compose.onNodeWithContentDescription("Collapse $name").performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.mainClock.advanceTimeBy(80)
            compose.onNodeWithContentDescription("Expand $name").performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.mainClock.advanceTimeBy(2_000)
            compose.waitForIdle()
            val expanded = compose.onNodeWithTag("dashboard_expanded_card").fetchSemanticsNode().boundsInRoot
            capture("$name-expanded", expanded.toString())
            assertTrue("compact=$compact middle=$middle expanded=$expanded", middle.top < compact.top && middle.top > expanded.top)
            assertTrue("compact=$compact middle=$middle expanded=$expanded", middle.height > compact.height && middle.height < expanded.height)
            compose.onNodeWithContentDescription("Collapse $name").performClick()
            compose.mainClock.advanceTimeBy(2_000)
            compose.mainClock.autoAdvance = true
            compose.onNodeWithContentDescription("Expand $name").assertIsDisplayed()
            val restored = compose.onNodeWithTag("dashboard_compact_card").fetchSemanticsNode().boundsInRoot
            assertEquals(compact.top, restored.top, 1f)
            assertEquals(compact.height, restored.height, 1f)
        }
    }

    @Test
    fun titleSwipeRejectsShortHorizontalDownwardAndCancelledDrags() {
        awaitDashboard()
        selectCard("Expand top categories")
        val wasPinned = compose.onAllNodesWithContentDescription("Pinned dashboard card").fetchSemanticsNodes().isNotEmpty()
        val pin = if (wasPinned) "Pinned dashboard card" else "Pin dashboard card"
        val changedPin = if (wasPinned) "Pin dashboard card" else "Pinned dashboard card"
        compose.onNodeWithContentDescription(pin).performClick()
        compose.onNodeWithContentDescription(changedPin).assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Expand top categories").assertIsDisplayed()
        val threshold = compose.activity.resources.displayMetrics.density * 48
        val title = compose.onNodeWithTag("dashboard_card_title")
        title.performTouchInput { swipe(center, center + Offset(0f, -threshold / 2)) }
        compose.onNodeWithContentDescription("Collapse top categories").assertDoesNotExist()
        title.performTouchInput { swipe(center, center + Offset(threshold * 2, -threshold)) }
        compose.onNodeWithContentDescription("Collapse top categories").assertDoesNotExist()
        title.performTouchInput { swipe(center, center + Offset(0f, threshold * 2)) }
        compose.onNodeWithContentDescription("Collapse top categories").assertDoesNotExist()
        title.performTouchInput {
            down(center)
            moveBy(Offset(0f, -threshold * 2))
            cancel()
        }
        compose.onNodeWithContentDescription("Collapse top categories").assertDoesNotExist()
        title.performTouchInput { swipe(center, center + Offset(0f, -threshold * 2)) }
        compose.onNodeWithContentDescription("Collapse top categories").assertIsDisplayed()
        compose.onNodeWithContentDescription("Collapse top categories").performClick()
        compose.onNodeWithContentDescription("Expand top categories").assertIsDisplayed()
    }

    @Test
    fun balanceRotationRetainsActivityAndReturnsToTheSelectedCard() {
        awaitDashboard()
        selectCard("Expand cumulative balance chart")
        val activity = compose.activity
        repeat(3) {
            compose.onNodeWithContentDescription("Expand cumulative balance chart").performClick()
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            assertSame(activity, compose.activity)
            capture("balance-landscape-$it")
            compose.onNodeWithContentDescription("Collapse cumulative balance chart").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            assertSame(activity, compose.activity)
            compose.onNodeWithContentDescription("Expand cumulative balance chart").assertIsDisplayed()
            capture("balance-portrait-$it")
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Expand cumulative balance chart").assertIsDisplayed()
    }

    @Test
    fun groupingCapsulesKeepExclusiveSelectionInExpensesAndIncome() {
        awaitDashboard()
        compose.onNodeWithText("Expenses").performClick()
        val category = compose.onNodeWithContentDescription("View transactions by Category")
        val date = compose.onNodeWithContentDescription("View transactions by Date")
        category.assertIsSelected()
        date.assertIsNotSelected().performClick()
        date.assertIsSelected()
        category.assertIsNotSelected()
        val density = compose.activity.resources.displayMetrics.density
        val categoryBounds = category.fetchSemanticsNode().boundsInRoot
        val dateBounds = date.fetchSemanticsNode().boundsInRoot
        assertTrue(categoryBounds.height >= 48 * density)
        assertTrue(dateBounds.left > categoryBounds.right)
        compose.onNodeWithText("Income").performClick()
        date.assertIsSelected()
        category.performClick().assertIsSelected()
        date.assertIsNotSelected()
        capture("grouping-income")
    }

    private fun capture(name: String, geometry: String = "") {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "home-ui-evidence")
        directory.mkdirs()
        File(directory, "$name.txt").writeText(geometry)
        File(directory, "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
