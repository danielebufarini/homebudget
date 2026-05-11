package it.homebudget.app.ui.screens

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.homebudget.app.database.Category

@Composable
internal actual fun AndroidCategoriesRecyclerView(
    categories: List<Category>,
    modifier: Modifier,
    onDeleteCategory: (String) -> Unit,
    onEditCategory: (Category) -> Unit
) {
    val compositionContext = rememberCompositionContext()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = CategoriesRecyclerAdapter(compositionContext)
                overScrollMode = View.OVER_SCROLL_NEVER
                itemAnimator = null
            }
        },
        update = { recyclerView ->
            (recyclerView.adapter as CategoriesRecyclerAdapter).submit(
                categories = categories,
                onDeleteCategory = onDeleteCategory,
                onEditCategory = onEditCategory
            )
        }
    )
}

private class CategoriesRecyclerAdapter(
    private val parentCompositionContext: androidx.compose.runtime.CompositionContext
) : RecyclerView.Adapter<ComposeViewHolder>() {
    private var categories: List<Category> = emptyList()
    private var onDeleteCategory: (String) -> Unit = {}
    private var onEditCategory: (Category) -> Unit = {}

    fun submit(
        categories: List<Category>,
        onDeleteCategory: (String) -> Unit,
        onEditCategory: (Category) -> Unit
    ) {
        val previousCategories = this.categories
        this.categories = categories
        this.onDeleteCategory = onDeleteCategory
        this.onEditCategory = onEditCategory

        DiffUtil.calculateDiff(
            CategoriesDiffCallback(
                oldCategories = previousCategories,
                newCategories = categories
            )
        ).dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComposeViewHolder {
        return ComposeViewHolder(
            composeView = ComposeView(parent.context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setParentCompositionContext(parentCompositionContext)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        )
    }

    override fun onBindViewHolder(holder: ComposeViewHolder, position: Int) {
        val category = categories[position]
        holder.composeView.setContent {
            if (category.isCustom == 1L) {
                val dismissState = rememberSwipeToDeleteBoxState(
                    itemId = category.id,
                    onDeleteItem = onDeleteCategory
                )

                Box(modifier = Modifier.padding(bottom = 12.dp)) {
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            if (dismissState.dismissDirection == SwipeToDismissBoxValue.Settled) {
                                Spacer(modifier = Modifier.fillMaxSize())
                            } else {
                                DeleteCategoryBackground()
                            }
                        }
                    ) {
                        CategoryListItem(
                            category = category,
                            onClick = { onEditCategory(category) }
                        )
                    }
                }
            } else {
                CategoryListItem(
                    category = category,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }

    override fun getItemCount(): Int = categories.size
}


@Composable
private fun CategoryListItem(
    category: Category,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Card(
        modifier = clickableModifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CategoryLabel(
                iconKey = category.icon,
                text = category.name,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge
            )

            if (category.isCustom == 1L) {
                Text(
                    text = "Custom",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DeleteCategoryBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.large
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(
            text = "Delete",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

private class ComposeViewHolder(
    val composeView: ComposeView
) : RecyclerView.ViewHolder(composeView)

private class CategoriesDiffCallback(
    private val oldCategories: List<Category>,
    private val newCategories: List<Category>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldCategories.size

    override fun getNewListSize(): Int = newCategories.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldCategories[oldItemPosition].id == newCategories[newItemPosition].id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldCategories[oldItemPosition] == newCategories[newItemPosition]
    }
}
