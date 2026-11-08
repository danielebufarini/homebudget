package it.danielebufarini.spesify

import android.view.WindowInsets
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performImeAction
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class EditorKeyboardTests {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun expenseDescriptionStaysAboveKeyboardWhileTypingAndAfterReopening() {
        checkDescription(income = false)
    }

    @Test
    fun incomeDescriptionStaysAboveKeyboardWhileTypingAndAfterReopening() {
        checkDescription(income = true)
    }

    private fun checkDescription(income: Boolean) {
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasContentDescription("Add Expense") or hasText("Skip")).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodes(hasText("Skip")).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Skip").performClick()
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Add Expense").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithContentDescription("Add Expense").performClick()
        if (income) compose.onNodeWithText("Income", substring = false).performClick()
        val field = compose.onNode(hasSetTextAction() and hasText("Description"))
        field.performScrollTo().performClick()
        val decor = compose.activity.window.decorView
        compose.waitUntil(10_000) { decor.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
        field.performTextInput("Description " + "long text ".repeat(25))
        compose.waitForIdle()
        field.assertIsFocused()
        val location = IntArray(2)
        compose.runOnIdle { decor.getLocationOnScreen(location) }
        val bounds = field.fetchSemanticsNode().boundsInWindow
        val keyboardTop = location[1] + decor.height - decor.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom
        val evidence = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "keyboard-${if (income) "income" else "expense"}.txt")
        evidence.writeText("field=$bounds keyboardTop=$keyboardTop decorHeight=${decor.height}\n")
        evidence.resolveSibling(evidence.nameWithoutExtension + ".png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertTrue(evidence.readText(), bounds.bottom + location[1] <= keyboardTop)
        field.performImeAction()
        compose.waitUntil(10_000) { !decor.rootWindowInsets.isVisible(WindowInsets.Type.ime()) }
        field.performClick()
        compose.waitUntil(10_000) { decor.rootWindowInsets.isVisible(WindowInsets.Type.ime()) }
        field.performTextInput(" reopened")
        field.assertIsFocused()
        val reopened = field.fetchSemanticsNode().boundsInWindow
        val reopenedTop = location[1] + decor.height - decor.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom
        assertTrue("field=$reopened keyboardTop=$reopenedTop", reopened.bottom + location[1] <= reopenedTop)
    }
}
