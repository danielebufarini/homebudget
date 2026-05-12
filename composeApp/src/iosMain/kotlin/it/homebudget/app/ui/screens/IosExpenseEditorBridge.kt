package it.homebudget.app.ui.screens

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
