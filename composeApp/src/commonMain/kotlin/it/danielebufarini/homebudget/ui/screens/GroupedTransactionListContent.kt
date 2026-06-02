package it.danielebufarini.homebudget.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.danielebufarini.homebudget.data.formatAmount
import it.danielebufarini.homebudget.ui.screens.categories.CategoryLabel
import it.danielebufarini.homebudget.ui.screens.common.ExpandableSectionChevron
import it.danielebufarini.homebudget.ui.screens.expenses.GroupingModeButtons
import it.danielebufarini.homebudget.ui.screens.platform.PlatformCard

internal data class GroupedTransactionSectionStyle(
    val containerColor: Color,
    val contentColor: Color,
    val textStyle: TextStyle,
    val iconTint: Color?,
    val chevronContainerColor: Color?,
    val chevronContentColor: Color?
)

internal data class GroupedTransactionSection<T>(
    val key: String,
    val title: String,
    val totalAmount: Long,
    val categoryId: String?,
    val categoryIconKey: String?,
    val items: List<T>
)

@Composable
internal fun <T> GroupedTransactionListContent(
    sections: List<GroupedTransactionSection<T>>,
    modifier: Modifier,
    groupingMode: ExpenseGroupingMode,
    onGroupingModeChange: (ExpenseGroupingMode) -> Unit,
    emptyStateText: String,
    currencySymbol: String,
    byCategoryLabel: String,
    byDateLabel: String,
    groupsExpandedByDefault: Boolean,
    sectionStyle: GroupedTransactionSectionStyle,
    showGroupingControls: Boolean = true,
    listContentPadding: PaddingValues = PaddingValues(0.dp),
    bottomControlsBottomPadding: Dp = 16.dp,
    loadMoreSearchResultsLabel: String = "",
    canLoadMoreSearchResults: Boolean = false,
    onLoadMoreSearchResults: () -> Unit = {},
    isLoading: Boolean = false,
    emptyStateCentered: Boolean = false,
    itemKey: (T) -> Any,
    rowContent: @Composable (T) -> Unit
) {
    Box(modifier = modifier) {
        GroupedTransactionList(
            sections = sections,
            groupingMode = groupingMode,
            modifier = Modifier.fillMaxSize(),
            emptyStateText = emptyStateText,
            currencySymbol = currencySymbol,
            groupsExpandedByDefault = groupsExpandedByDefault,
            sectionStyle = sectionStyle,
            contentPadding = listContentPadding,
            loadMoreSearchResultsLabel = loadMoreSearchResultsLabel,
            canLoadMoreSearchResults = canLoadMoreSearchResults,
            onLoadMoreSearchResults = onLoadMoreSearchResults,
            isLoading = isLoading,
            emptyStateCentered = emptyStateCentered,
            itemKey = itemKey,
            rowContent = rowContent
        )

        if (showGroupingControls) {
            GroupingModeButtons(
                groupingMode = groupingMode,
                onGroupingModeChange = onGroupingModeChange,
                byCategoryLabel = byCategoryLabel,
                byDateLabel = byDateLabel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomControlsBottomPadding)
            )
        }
    }
}

@Composable
private fun <T> GroupedTransactionList(
    sections: List<GroupedTransactionSection<T>>,
    groupingMode: ExpenseGroupingMode,
    modifier: Modifier,
    emptyStateText: String,
    currencySymbol: String,
    groupsExpandedByDefault: Boolean,
    sectionStyle: GroupedTransactionSectionStyle,
    contentPadding: PaddingValues,
    loadMoreSearchResultsLabel: String,
    canLoadMoreSearchResults: Boolean,
    onLoadMoreSearchResults: () -> Unit,
    isLoading: Boolean,
    emptyStateCentered: Boolean,
    itemKey: (T) -> Any,
    rowContent: @Composable (T) -> Unit
) {
    if (sections.isEmpty() && isLoading) {
        GroupedTransactionLoadingState(
            modifier = modifier.padding(contentPadding)
        )
        return
    }

    if (sections.isEmpty() && !canLoadMoreSearchResults && emptyStateCentered) {
        GroupedTransactionEmptyState(
            text = emptyStateText,
            modifier = modifier.padding(contentPadding)
        )
        return
    }

    val expandedState = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (sections.isEmpty()) {
            item(key = "empty-state") {
                PlatformCard {
                    Text(
                        text = emptyStateText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        sections.forEach { section ->
            item(key = section.key) {
                val expanded = expandedState[section.key] ?: groupsExpandedByDefault
                val chevronRotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    label = "GroupedTransactionSectionChevronRotation"
                )
                PlatformCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    GroupedTransactionSectionCard(
                        section = section,
                        groupingMode = groupingMode,
                        expanded = expanded,
                        chevronRotation = chevronRotation,
                        sectionStyle = sectionStyle,
                        currencySymbol = currencySymbol,
                        onToggleExpanded = {
                            expandedState[section.key] = !expanded
                        },
                        itemKey = itemKey,
                        rowContent = rowContent
                    )
                }
            }
        }

        if (canLoadMoreSearchResults) {
            item(key = "load-more-search-results") {
                Button(
                    onClick = onLoadMoreSearchResults,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(loadMoreSearchResultsLabel)
                }
            }
        }
    }
}

@Composable
private fun GroupedTransactionLoadingState(
    modifier: Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun GroupedTransactionEmptyState(
    text: String,
    modifier: Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        PlatformCard {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun <T> GroupedTransactionSectionCard(
    section: GroupedTransactionSection<T>,
    groupingMode: ExpenseGroupingMode,
    expanded: Boolean,
    chevronRotation: Float,
    sectionStyle: GroupedTransactionSectionStyle,
    currencySymbol: String,
    onToggleExpanded: () -> Unit,
    itemKey: (T) -> Any,
    rowContent: @Composable (T) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        GroupedTransactionSectionHeader(
            section = section,
            groupingMode = groupingMode,
            chevronRotation = chevronRotation,
            currencySymbol = currencySymbol,
            style = sectionStyle,
            onToggleExpanded = onToggleExpanded
        )
        if (expanded) {
            HorizontalDivider()
            section.items.forEach { item ->
                key(itemKey(item)) {
                    rowContent(item)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun <T> GroupedTransactionSectionHeader(
    section: GroupedTransactionSection<T>,
    groupingMode: ExpenseGroupingMode,
    chevronRotation: Float,
    currencySymbol: String,
    style: GroupedTransactionSectionStyle,
    onToggleExpanded: () -> Unit
) {
    val isGroupedByCategory = groupingMode == ExpenseGroupingMode.ByCategory

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = style.containerColor,
        contentColor = style.contentColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryLabel(
                iconKey = section.categoryIconKey.takeIf { isGroupedByCategory },
                showIcon = isGroupedByCategory,
                colorKey = section.categoryId.takeIf { isGroupedByCategory },
                text = section.title,
                modifier = Modifier.weight(1f),
                textStyle = style.textStyle,
                textColor = style.contentColor,
                iconTint = style.iconTint,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatAmount(section.totalAmount, currencySymbol),
                    style = style.textStyle,
                    color = style.contentColor,
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.width(8.dp))
                ExpandableSectionChevron(
                    rotation = chevronRotation,
                    containerColor = style.chevronContainerColor,
                    contentColor = style.chevronContentColor
                )
            }
        }
    }
}
