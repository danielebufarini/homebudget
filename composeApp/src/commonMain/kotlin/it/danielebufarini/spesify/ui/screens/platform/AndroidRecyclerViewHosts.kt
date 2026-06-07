package it.danielebufarini.spesify.ui.screens.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import it.danielebufarini.spesify.database.Category

@Composable
internal expect fun AndroidCategoriesRecyclerView(
    categories: List<Category>,
    modifier: Modifier = Modifier,
    onDeleteCategory: (String) -> Unit,
    onEditCategory: (Category) -> Unit
)
