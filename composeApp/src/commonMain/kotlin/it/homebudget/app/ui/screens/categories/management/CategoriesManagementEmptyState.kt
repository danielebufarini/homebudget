package it.homebudget.app.ui.screens.categories.management

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.add_category
import homebudget.composeapp.generated.resources.categories_empty_all
import homebudget.composeapp.generated.resources.categories_empty_all_description
import homebudget.composeapp.generated.resources.categories_empty_archived
import homebudget.composeapp.generated.resources.categories_empty_archived_description
import homebudget.composeapp.generated.resources.categories_empty_expense
import homebudget.composeapp.generated.resources.categories_empty_expense_description
import homebudget.composeapp.generated.resources.categories_empty_income
import homebudget.composeapp.generated.resources.categories_empty_income_description
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EmptyCategoriesCard(
    selectedFilter: CategoryFilter,
    onAdd: () -> Unit,
) {
    val palette = rememberCategoriesPalette()
    val addCategoryLabel = stringResource(Res.string.add_category)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        shape = RoundedCornerShape(32.dp),
        color = palette.glassSurface,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyCategoriesIcon()
            Spacer(Modifier.height(16.dp))
            Text(
                text = selectedFilter.emptyTitle(),
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
            )
            Text(
                text = selectedFilter.emptyDescription(),
                color = palette.textSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(addCategoryLabel)
            }
        }
    }
}

@Composable
private fun EmptyCategoriesIcon() {
    Surface(
        modifier = Modifier.size(78.dp),
        shape = RoundedCornerShape(28.dp),
        color = AccentPurple.copy(alpha = 0.12f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Category,
                contentDescription = null,
                tint = AccentPurple,
                modifier = Modifier.size(38.dp),
            )
        }
    }
}

@Composable
private fun CategoryFilter.emptyTitle() = when (this) {
    CategoryFilter.All -> stringResource(Res.string.categories_empty_all)
    CategoryFilter.Expense -> stringResource(Res.string.categories_empty_expense)
    CategoryFilter.Income -> stringResource(Res.string.categories_empty_income)
    CategoryFilter.Archived -> stringResource(Res.string.categories_empty_archived)
}

@Composable
private fun CategoryFilter.emptyDescription() = when (this) {
    CategoryFilter.All -> stringResource(Res.string.categories_empty_all_description)
    CategoryFilter.Expense -> stringResource(Res.string.categories_empty_expense_description)
    CategoryFilter.Income -> stringResource(Res.string.categories_empty_income_description)
    CategoryFilter.Archived -> stringResource(Res.string.categories_empty_archived_description)
}
