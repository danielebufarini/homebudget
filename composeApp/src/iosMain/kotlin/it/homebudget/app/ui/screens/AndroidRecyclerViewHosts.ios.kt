package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import it.homebudget.app.database.Category

@Composable
internal actual fun AndroidCategoriesRecyclerView(
    categories: List<Category>,
    modifier: Modifier,
    onDeleteCategory: (String) -> Unit,
    onEditCategory: (Category) -> Unit
) = Unit
