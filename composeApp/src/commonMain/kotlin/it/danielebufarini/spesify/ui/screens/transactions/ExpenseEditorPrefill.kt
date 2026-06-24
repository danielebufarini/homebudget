@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package it.danielebufarini.spesify.ui.screens.transactions

import kotlin.native.ObjCName

data class ExpenseEditorPrefill(
    val requestId: String,
    val amountMinor: Long,
    @property:ObjCName(swiftName = "descriptionText")
    val description: String?,
    val categoryId: String?,
    val dateMillis: Long?
)
