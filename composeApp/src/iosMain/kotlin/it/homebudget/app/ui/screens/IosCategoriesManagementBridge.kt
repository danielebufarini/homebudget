package it.homebudget.app.ui.screens

private var activeIosCategoriesManagementAddHandler: (() -> Unit)? = null

actual fun setActiveIosCategoriesManagementAddHandler(handler: () -> Unit) {
    activeIosCategoriesManagementAddHandler = handler
}

actual fun clearActiveIosCategoriesManagementAddHandler() {
    activeIosCategoriesManagementAddHandler = null
}

fun performIosCategoriesManagementAdd() {
    activeIosCategoriesManagementAddHandler?.invoke()
}
