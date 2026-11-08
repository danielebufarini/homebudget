package it.danielebufarini.spesify

import android.view.WindowInsets
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdditionalKeyboardTests {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun categoryNameInSheetStaysAboveKeyboard() {
        awaitDashboard()
        compose.onNodeWithContentDescription("Dashboard").performClick()
        compose.onNodeWithText("Categories").performClick()
        compose.onNodeWithContentDescription("Add category").performClick()
        val field = compose.onNode(hasSetTextAction() and isFocused())
        field.performClick().performTextInput("Keyboard category")
        assertFocusedFieldVisible()
    }

    @Test
    fun dashboardAndCategorySearchStayAboveKeyboard() {
        awaitDashboard()
        compose.onNode(hasSetTextAction()).performClick().performTextInput("long query ".repeat(20))
        assertFocusedFieldVisible()
        compose.onNodeWithContentDescription("Dashboard").performClick()
        compose.onNodeWithText("Categories").performClick()
        compose.onNode(hasSetTextAction()).performClick().performTextInput("long category search ".repeat(12))
        assertFocusedFieldVisible()
    }

    @Test
    fun pastedDescriptionKeepsTextAndRemainsAboveKeyboard() {
        awaitDashboard()
        compose.onNodeWithContentDescription("Add Expense").performClick()
        val field = compose.onNode(hasSetTextAction() and hasText("Description"))
        field.performClick()
        val pasted = "Pasted description " + "long text ".repeat(30)
        compose.runOnIdle {
            compose.activity.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("description", pasted))
        }
        field.performSemanticsAction(SemanticsActions.PasteText) { it() }
        field.assertTextContains(pasted)
        assertFocusedFieldVisible()
    }

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

    private fun assertFocusedFieldVisible() {
        val decor = compose.activity.window.decorView
        compose.waitUntil(10_000) { decor.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
        compose.waitForIdle()
        val field = compose.onNode(hasSetTextAction() and isFocused())
        field.assertIsFocused()
        val bounds = field.fetchSemanticsNode().boundsInWindow
        val keyboardTop = decor.height - decor.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom
        assertTrue("field=$bounds keyboardTop=$keyboardTop", bounds.bottom <= keyboardTop)
    }
}
