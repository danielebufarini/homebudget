package it.homebudget.app.ui.screens

import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.homebudget.app.data.formatAmount
import it.homebudget.app.data.sumBigInteger
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.localization.LocalStrings
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
internal actual fun AndroidGroupedExpensesRecyclerView(
    groupedExpenses: List<Pair<String, List<Expense>>>,
    categoriesById: Map<String, Category>,
    isGroupedByDate: Boolean,
    modifier: Modifier,
    emptyStateText: String,
    expenseFallbackTitle: String,
    groupsExpandedByDefault: Boolean,
    onOpenExpense: (String) -> Unit,
    onDeleteExpense: ((String) -> Unit)?
) {
    val compositionContext = rememberCompositionContext()
    val strings = LocalStrings.current
    val sections = remember(groupedExpenses, categoriesById, isGroupedByDate, expenseFallbackTitle, strings) {
        groupedExpenses.map { (categoryName, categoryExpenses) ->
            AndroidGroupedExpenseSectionModel(
                id = categoryName,
                title = categoryName,
                totalAmountText = formatAmount(categoryExpenses.map { it.amount }.sumBigInteger()),
                rows = categoryExpenses
                    .map { expense ->
                        val expenseName = expense.description?.ifBlank { expenseFallbackTitle } ?: expenseFallbackTitle
                        val resolvedCategoryName = categoriesById[expense.categoryId]
                            ?.let { strings.categoryName(it.id, it.name, it.isCustom) }
                            ?: strings.unknownCategory
                        AndroidGroupedExpenseRowModel(
                            id = expense.id,
                            title = if (isGroupedByDate) resolvedCategoryName else expenseName,
                            subtitleText = if (isGroupedByDate) expenseName else formatDate(epochMillis = expense.date),
                            amountText = formatAmount(expense.amount)
                        )
                    }
            )
        }
    }

    if (sections.isEmpty()) {
        PlatformCard(modifier = modifier) {
            Text(
                text = emptyStateText,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = GroupedExpensesRecyclerAdapter(compositionContext)
                overScrollMode = View.OVER_SCROLL_NEVER
                itemAnimator = null
            }
        },
        update = { recyclerView ->
            (recyclerView.adapter as GroupedExpensesRecyclerAdapter).submit(
                sections = sections,
                groupsExpandedByDefault = groupsExpandedByDefault,
                onOpenExpense = onOpenExpense,
                onDeleteExpense = onDeleteExpense
            )
        }
    )
}

@Composable
internal actual fun AndroidCategoriesRecyclerView(
    categories: List<Category>,
    modifier: Modifier
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
            (recyclerView.adapter as CategoriesRecyclerAdapter).submit(categories)
        }
    )
}

private data class AndroidGroupedExpenseSectionModel(
    val id: String,
    val title: String,
    val totalAmountText: String,
    val rows: List<AndroidGroupedExpenseRowModel>
)

private data class AndroidGroupedExpenseRowModel(
    val id: String,
    val title: String,
    val subtitleText: String,
    val amountText: String
)

private class GroupedExpensesRecyclerAdapter(
    private val parentCompositionContext: CompositionContext
) : RecyclerView.Adapter<ComposeViewHolder>() {
    private var sections: List<AndroidGroupedExpenseSectionModel> = emptyList()
    private var expandedSectionIds = mutableSetOf<String>()
    private var onOpenExpense: (String) -> Unit = {}
    private var onDeleteExpense: ((String) -> Unit)? = null

    fun submit(
        sections: List<AndroidGroupedExpenseSectionModel>,
        groupsExpandedByDefault: Boolean,
        onOpenExpense: (String) -> Unit,
        onDeleteExpense: ((String) -> Unit)?
    ) {
        val incomingIds = sections.mapTo(linkedSetOf()) { it.id }
        expandedSectionIds = expandedSectionIds
            .filterTo(mutableSetOf()) { it in incomingIds }
            .apply {
                incomingIds.forEach { sectionId ->
                    if (sectionId !in this && groupsExpandedByDefault) {
                        add(sectionId)
                    }
                }
            }

        val previousSections = this.sections
        this.sections = sections
        this.onOpenExpense = onOpenExpense
        this.onDeleteExpense = onDeleteExpense

        DiffUtil.calculateDiff(
            GroupedExpensesDiffCallback(
                oldSections = previousSections,
                newSections = sections
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
        val section = sections[position]
        holder.composeView.setContent {
            AndroidGroupedExpenseSectionCard(
                section = section,
                expanded = section.id in expandedSectionIds,
                onToggleExpanded = {
                    if (section.id in expandedSectionIds) {
                        expandedSectionIds.remove(section.id)
                    } else {
                        expandedSectionIds.add(section.id)
                    }
                    notifyItemChanged(position)
                },
                onOpenExpense = onOpenExpense,
                onDeleteExpense = onDeleteExpense
            )
        }
    }

    override fun getItemCount(): Int = sections.size
}

private class CategoriesRecyclerAdapter(
    private val parentCompositionContext: CompositionContext
) : RecyclerView.Adapter<ComposeViewHolder>() {
    private var categories: List<Category> = emptyList()

    fun submit(categories: List<Category>) {
        val previousCategories = this.categories
        this.categories = categories

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
            val strings = LocalStrings.current

            PlatformCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strings.categoryName(category.id, category.name, category.isCustom))
                    Text(
                        text = if (category.isCustom == 1L) strings.customCategory else strings.defaultCategory,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    override fun getItemCount(): Int = categories.size
}

private class ComposeViewHolder(
    val composeView: ComposeView
) : RecyclerView.ViewHolder(composeView)

private class GroupedExpensesDiffCallback(
    private val oldSections: List<AndroidGroupedExpenseSectionModel>,
    private val newSections: List<AndroidGroupedExpenseSectionModel>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldSections.size

    override fun getNewListSize(): Int = newSections.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldSections[oldItemPosition].id == newSections[newItemPosition].id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldSections[oldItemPosition] == newSections[newItemPosition]
    }
}

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

@Composable
private fun AndroidGroupedExpenseSectionCard(
    section: AndroidGroupedExpenseSectionModel,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenExpense: (String) -> Unit,
    onDeleteExpense: ((String) -> Unit)?
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "RecyclerViewSectionChevronRotation"
    )

    PlatformCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = section.title,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = section.totalAmountText,
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse section" else "Expand section",
                    modifier = Modifier.rotate(rotationAngle),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider()
                    section.rows.forEach { row ->
                        key(row.id) {
                            AndroidGroupedExpenseRow(
                                row = row,
                                onOpenExpense = onOpenExpense,
                                onDeleteExpense = onDeleteExpense
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AndroidGroupedExpenseRow(
    row: AndroidGroupedExpenseRowModel,
    onOpenExpense: (String) -> Unit,
    onDeleteExpense: ((String) -> Unit)?
) {
    if (onDeleteExpense == null) {
        ExpenseListItemRow(
            title = row.title,
            subtitleText = row.subtitleText,
            amountText = row.amountText,
            subtitleFontSizeOffsetSp = -2,
            onClick = { onOpenExpense(row.id) }
        )
        return
    }

    val dismissState = rememberExpenseSwipeToDeleteBoxState(
        itemId = row.id,
        onDeleteExpense = onDeleteExpense
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            DeleteExpenseBackground()
        }
    ) {
        ExpenseListItemRow(
            title = row.title,
            subtitleText = row.subtitleText,
            amountText = row.amountText,
            subtitleFontSizeOffsetSp = -2,
            onClick = { onOpenExpense(row.id) }
        )
    }
}

private fun formatDate(epochMillis: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return "${date.year}-${(date.month.ordinal + 1).toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
}
