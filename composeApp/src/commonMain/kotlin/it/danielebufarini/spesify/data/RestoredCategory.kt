package it.danielebufarini.spesify.data

import it.danielebufarini.spesify.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.spesify.database.DEFAULT_CATEGORY_COLOR

data class RestoredCategory(
    val id: String,
    val name: String,
    val icon: String,
    val color: String = DEFAULT_CATEGORY_COLOR,
    val categoryType: String = CATEGORY_TYPE_EXPENSE,
    val isArchived: Boolean = false,
    val sortOrder: Long = 0L
)
