package it.danielebufarini.spesify.ui.screens.transactions

data class ExpenseEditorPrefill(
    val requestId: String,
    val amountMinor: Long,
    val description: String?,
    val categoryId: String?,
    val dateMillis: Long?
)
