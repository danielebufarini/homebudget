package it.danielebufarini.homebudget.ui.screens.expenses

private var activeIosExpenseEditorSaveHandler: (() -> Unit)? = null

actual fun setActiveIosExpenseEditorSaveHandler(handler: () -> Unit) {
    activeIosExpenseEditorSaveHandler = handler
}

actual fun clearActiveIosExpenseEditorSaveHandler() {
    activeIosExpenseEditorSaveHandler = null
}

fun performIosExpenseEditorSave() {
    activeIosExpenseEditorSaveHandler?.invoke()
}
