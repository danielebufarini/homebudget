package it.homebudget.app.ui.screens.categories.management

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Work
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.categories_usage_plural
import homebudget.composeapp.generated.resources.categories_usage_single
import it.homebudget.app.data.IdGenerator
import it.homebudget.app.database.CATEGORY_TYPE_EXPENSE
import it.homebudget.app.database.CATEGORY_TYPE_INCOME
import it.homebudget.app.database.Category
import it.homebudget.app.database.DEFAULT_CATEGORY_COLOR
import it.homebudget.app.database.Expense
import it.homebudget.app.database.Income
import org.jetbrains.compose.resources.stringResource

@Immutable
internal data class CategoryUiModel(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorHex: String,
    val categoryType: String,
    val isArchived: Boolean,
    val transactionCount: Int,
    val accent: Color,
) {
    companion object {
        fun newEmpty(): CategoryUiModel =
            CategoryUiModel(
                id = "",
                name = "",
                iconKey = "category",
                colorHex = DEFAULT_CATEGORY_COLOR,
                categoryType = CATEGORY_TYPE_EXPENSE,
                isArchived = false,
                transactionCount = 0,
                accent = CategoryAccentPalette.first(),
            )
    }
}

internal enum class CategoryFilter {
    All,
    Expense,
    Income,
    Archived,
}

@Immutable
internal data class EditorIconOption(
    val key: String,
    val icon: ImageVector,
)

internal val EditorIconOptions = listOf(
    EditorIconOption("shopping_cart", Icons.Rounded.ShoppingCart),
    EditorIconOption("restaurant", Icons.Rounded.Restaurant),
    EditorIconOption("flight", Icons.Rounded.Flight),
    EditorIconOption("pets", Icons.Rounded.Pets),
    EditorIconOption("local_hospital", Icons.Rounded.LocalHospital),
    EditorIconOption("home", Icons.Rounded.Home),
    EditorIconOption("directions_car", Icons.Rounded.DirectionsCar),
    EditorIconOption("work", Icons.Rounded.Work),
    EditorIconOption("coffee", Icons.Rounded.Coffee),
    EditorIconOption("savings", Icons.Rounded.Savings),
    EditorIconOption("person", Icons.Rounded.Person),
    EditorIconOption("category", Icons.Rounded.Category),
)

internal fun buildCategoryUiModels(
    categories: List<Category>,
    expenses: List<Expense>,
    incomes: List<Income>,
): List<CategoryUiModel> {
    val expensesByCategory = expenses.groupBy { it.categoryId }
    val incomesByCategory = incomes
        .filter { !it.categoryId.isNullOrBlank() }
        .groupBy { it.categoryId.orEmpty() }

    return categories.map { category ->
        val accent = category.color.toColorOrDefault()
        CategoryUiModel(
            id = category.id,
            name = category.name,
            iconKey = category.icon,
            colorHex = category.color,
            categoryType = category.categoryType,
            isArchived = category.isArchived == 1L,
            transactionCount = if (category.categoryType == CATEGORY_TYPE_INCOME) {
                incomesByCategory[category.id].orEmpty().size
            } else {
                expensesByCategory[category.id].orEmpty().size
            },
            accent = accent,
        )
    }
}

@Composable
internal fun CategoryUiModel.usageLabel(): String {
    return if (transactionCount == 1) {
        stringResource(Res.string.categories_usage_single, transactionCount)
    } else {
        stringResource(Res.string.categories_usage_plural, transactionCount)
    }
}

internal fun iconForKey(iconKey: String): ImageVector {
    return when (iconKey) {
        "shopping_cart" -> Icons.Rounded.ShoppingCart
        "restaurant" -> Icons.Rounded.Restaurant
        "flight" -> Icons.Rounded.Flight
        "pets" -> Icons.Rounded.Pets
        "local_hospital" -> Icons.Rounded.LocalHospital
        "health" -> Icons.Rounded.HealthAndSafety
        "home" -> Icons.Rounded.Home
        "directions_car" -> Icons.Rounded.DirectionsCar
        "work" -> Icons.Rounded.Work
        "coffee" -> Icons.Rounded.Coffee
        "savings" -> Icons.Rounded.Savings
        "person" -> Icons.Rounded.Person
        else -> Icons.Rounded.Category
    }
}

internal fun buildCategoryId(): String = IdGenerator.newId("category")

internal val AccentPurple = Color(0xFF6F45E9)
internal val DeleteRed = Color(0xFFE53935)

internal val CategoryAccentPalette = listOf(
    Color(0xFF2FA66A),
    Color(0xFF6F45E9),
    Color(0xFF2388D9),
    Color(0xFFE46C42),
    Color(0xFFD63871),
    Color(0xFF009688),
    Color(0xFFE19A15),
    Color(0xFF8D6E63),
)

internal val CategoryAccentPaletteHex = listOf(
    "#2FA66A",
    "#6F45E9",
    "#2388D9",
    "#E46C42",
    "#D63871",
    "#009688",
    "#E19A15",
    "#8D6E63",
)

internal fun String.toColorOrDefault(): Color {
    val normalized = removePrefix("#")
    if (normalized.length != 6 && normalized.length != 8) {
        return AccentPurple
    }
    val value = normalized.toLongOrNull(16) ?: return AccentPurple
    return if (normalized.length == 6) {
        Color((0xFF000000 or value).toInt())
    } else {
        Color(value.toInt())
    }
}

@Immutable
internal data class CategoriesPalette(
    val isDark: Boolean,
    val background: Brush,
    val glassSurface: Color,
    val glassStrong: Color,
    val glassSurfaceSoft: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val divider: Color,
    val iconSurface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val sheetBackground: Color,
)

@Composable
internal fun rememberCategoriesPalette(): CategoriesPalette {
    val darkTheme = isSystemInDarkTheme()

    return remember(darkTheme) {
        if (darkTheme) {
            CategoriesPalette(
                isDark = true,
                background = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B1020),
                        Color(0xFF131A2E),
                        Color(0xFF1A2133),
                    ),
                ),
                glassSurface = Color(0xD9242C3F),
                glassStrong = Color(0xCC1E2638),
                glassSurfaceSoft = Color(0x992D374D),
                cardSurface = Color(0xEE1A2233),
                cardBorder = Color(0x6654657E),
                divider = Color(0x335A6980),
                iconSurface = Color(0xA62D374D),
                textPrimary = Color(0xFFF5F7FC),
                textSecondary = Color(0xFFC0C8D9),
                textMuted = Color(0xFF8E99AF),
                sheetBackground = Color(0xFF12192A),
            )
        } else {
            CategoriesPalette(
                isDark = false,
                background = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF9F9FF),
                        Color(0xFFF4F7FF),
                        Color(0xFFFFFFFF),
                    ),
                ),
                glassSurface = Color.White.copy(alpha = 0.76f),
                glassStrong = Color.White.copy(alpha = 0.70f),
                glassSurfaceSoft = Color.White.copy(alpha = 0.68f),
                cardSurface = Color.White.copy(alpha = 0.94f),
                cardBorder = Color(0xFFE6EAF4),
                divider = Color(0xFFE9EDF5),
                iconSurface = Color.White.copy(alpha = 0.70f),
                textPrimary = Color(0xFF15172E),
                textSecondary = Color(0xFF687083),
                textMuted = Color(0xFF8B91A1),
                sheetBackground = Color(0xFFF4F6FB),
            )
        }
    }
}
