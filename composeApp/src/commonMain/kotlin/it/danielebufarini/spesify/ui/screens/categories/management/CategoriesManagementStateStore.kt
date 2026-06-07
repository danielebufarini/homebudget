package it.danielebufarini.spesify.ui.screens.categories.management

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Stable
internal class CategoriesManagementStateStore private constructor(
    query: String,
    filter: CategoryFilter,
    sortAscending: Boolean,
) {
    constructor() : this(
        query = "",
        filter = CategoryFilter.All,
        sortAscending = true,
    )

    var query by mutableStateOf(query)
        private set

    var selectedFilter by mutableStateOf(filter)
        private set

    var sortAscending by mutableStateOf(sortAscending)
        private set

    var editorTarget by mutableStateOf<CategoryUiModel?>(null)
        private set

    var deleteTarget by mutableStateOf<CategoryUiModel?>(null)
        private set

    var moveTarget by mutableStateOf<CategoryUiModel?>(null)
        private set

    fun updateQuery(value: String) {
        query = value
    }

    fun selectFilter(filter: CategoryFilter) {
        selectedFilter = filter
    }

    fun toggleSort() {
        sortAscending = !sortAscending
    }

    fun startAdd() {
        editorTarget = CategoryUiModel.newEmpty()
    }

    fun startEdit(category: CategoryUiModel) {
        editorTarget = category
    }

    fun dismissEditor() {
        editorTarget = null
    }

    fun requestDelete(category: CategoryUiModel) {
        deleteTarget = category
    }

    fun requestDeleteFromEditor(category: CategoryUiModel) {
        if (category.id.isBlank()) return

        deleteTarget = category
        editorTarget = null
    }

    fun dismissDelete() {
        deleteTarget = null
    }

    fun startMoveFromDelete(category: CategoryUiModel) {
        moveTarget = category
        deleteTarget = null
    }

    fun dismissMove() {
        moveTarget = null
    }

    fun clearAfterSave() {
        editorTarget = null
    }

    companion object {
        val Saver: Saver<CategoriesManagementStateStore, List<Any>> = Saver(
            save = { state ->
                listOf(
                    state.query,
                    state.selectedFilter.name,
                    state.sortAscending,
                )
            },
            restore = { restored ->
                CategoriesManagementStateStore(
                    query = restored.getOrNull(0) as? String ?: "",
                    filter = categoryFilterOrDefault(restored.getOrNull(1) as? String),
                    sortAscending = restored.getOrNull(2) as? Boolean ?: true,
                )
            },
        )
    }
}

@Composable
internal fun rememberCategoriesManagementStateStore(): CategoriesManagementStateStore =
    rememberSaveable(saver = CategoriesManagementStateStore.Saver) {
        CategoriesManagementStateStore()
    }

private fun categoryFilterOrDefault(name: String?): CategoryFilter =
    runCatching {
        if (name == null) CategoryFilter.All else CategoryFilter.valueOf(name)
    }.getOrDefault(CategoryFilter.All)
