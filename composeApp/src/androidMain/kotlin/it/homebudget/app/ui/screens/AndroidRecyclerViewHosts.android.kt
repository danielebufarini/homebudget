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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.homebudget.app.data.formatAmount
import it.homebudget.app.data.sumBigInteger
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import kotlinx.datetime.toLocalDateTime

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
    val sections = remember(groupedExpenses, categoriesById, isGroupedByDate, expenseFallbackTitle) {
        groupedExpenses.map { (categoryName, categoryExpenses) ->
            AndroidGroupedExpenseSectionModel(
                id = categoryName,
                title = categoryName,
                totalAmountText = formatAmount(categoryExpenses.map { it.amount }.sumBigInteger()),
                rows = categoryExpenses
                    .map { expense ->
                        val expenseName = expense.description?.ifBlank { expenseFallbackTitle } ?: expenseFallbackTitle
                        val resolvedCategoryName = categoriesById[expense.categoryId]?.name ?: "Unknown category"
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

        this.sections = sections
        this.onOpenExpense = onOpenExpense
        this.onDeleteExpense = onDeleteExpense
        notifyDataSetChanged()
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
        this.categories = categories
        notifyDataSetChanged()
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
            PlatformCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(category.name)
                    Text(
                        text = if (category.isCustom == 1L) "Custom category" else "Default category",
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
                    .background(Color(0xFFFFF4F7))
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

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDeleteExpense(row.id)
                false
            } else {
                true
            }
        },
        positionalThreshold = { distance ->
            distance * 0.35f
        }
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
    val date = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        .date
    return "${date.year}-${(date.month.ordinal + 1).toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
}
