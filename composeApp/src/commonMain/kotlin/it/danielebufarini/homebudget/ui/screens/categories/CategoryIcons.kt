package it.danielebufarini.homebudget.ui.screens.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.icon
import homebudget.composeapp.generated.resources.icon_theme_food_and_dining
import homebudget.composeapp.generated.resources.icon_theme_general_and_hobbies
import homebudget.composeapp.generated.resources.icon_theme_health_and_wellness
import homebudget.composeapp.generated.resources.icon_theme_home_and_bills
import homebudget.composeapp.generated.resources.icon_theme_people_and_work
import homebudget.composeapp.generated.resources.icon_theme_transport_and_travel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal const val DEFAULT_CATEGORY_ICON_KEY = "category"

private val categoryIconColorPalette = listOf(
    Color(0xFFD65A5A),
    Color(0xFFC65A9E),
    Color(0xFF8B5CF6),
    Color(0xFF5B6EE1),
    Color(0xFF3B82F6),
    Color(0xFF0EA5A4),
    Color(0xFF22A06B),
    Color(0xFF84A63D),
    Color(0xFFF59E0B),
    Color(0xFFF97316)
)

private data class CategoryIconSection(
    val titleRes: StringResource,
    val iconKeys: List<String>
)

private val categoryIconSections = listOf(
    CategoryIconSection(
        titleRes = Res.string.icon_theme_home_and_bills,
        iconKeys = listOf("home", "receipt", "build")
    ),
    CategoryIconSection(
        titleRes = Res.string.icon_theme_food_and_dining,
        iconKeys = listOf("shopping_cart", "restaurant", "local_cafe", "cake")
    ),
    CategoryIconSection(
        titleRes = Res.string.icon_theme_transport_and_travel,
        iconKeys = listOf("directions_car", "directions_bus", "train", "local_taxi", "flight", "hotel", "beach_access")
    ),
    CategoryIconSection(
        titleRes = Res.string.icon_theme_health_and_wellness,
        iconKeys = listOf("local_hospital", "healing", "fitness_center", "spa")
    ),
    CategoryIconSection(
        titleRes = Res.string.icon_theme_people_and_work,
        iconKeys = listOf("person", "work", "school")
    ),
    CategoryIconSection(
        titleRes = Res.string.icon_theme_general_and_hobbies,
        iconKeys = listOf("pets", "category")
    )
)

private val categoryIconAliases = mapOf(
    "household_expenses" to "home",
    "food" to "shopping_cart",
    "car_expenses" to "directions_car",
    "travel" to "flight",
    "healthcare_expenses" to "local_hospital",
    "bills" to "receipt",
    "personal_expenses" to "person",
    "personal_expeses" to "person",
    "miscellaneous" to "category"
)

internal fun normalizeCategoryIconKey(iconKey: String?): String {
    val normalized = iconKey?.trim().orEmpty()
    if (normalized.isEmpty()) {
        return DEFAULT_CATEGORY_ICON_KEY
    }
    return categoryIconAliases[normalized] ?: normalized
}

internal fun stableCategoryColorIndex(categoryId: String?): Int {
    val key = categoryId?.trim().orEmpty()
    if (key.isEmpty()) {
        return 0
    }

    var hash = 0u
    key.forEach { character ->
        hash = hash * 31u + character.code.toUInt()
    }
    return (hash % categoryIconColorPalette.size.toUInt()).toInt()
}

@Composable
internal fun categoryIconTint(colorKey: String?): Color {
    return remember(colorKey) {
        categoryIconColorPalette[stableCategoryColorIndex(colorKey)]
    }
}

private fun categoryImageVector(iconKey: String?): ImageVector {
    return when (normalizeCategoryIconKey(iconKey)) {
        "home" -> Icons.Filled.Home
        "receipt" -> Icons.Filled.Receipt
        "build" -> Icons.Filled.Build
        "shopping_cart" -> Icons.Filled.ShoppingCart
        "restaurant" -> Icons.Filled.Restaurant
        "local_cafe" -> Icons.Filled.LocalCafe
        "cake" -> Icons.Filled.Cake
        "directions_car" -> Icons.Filled.DirectionsCar
        "directions_bus" -> Icons.Filled.DirectionsBus
        "train" -> Icons.Filled.Train
        "local_taxi" -> Icons.Filled.LocalTaxi
        "flight" -> Icons.Filled.Flight
        "hotel" -> Icons.Filled.Hotel
        "beach_access" -> Icons.Filled.BeachAccess
        "local_hospital" -> Icons.Filled.LocalHospital
        "healing" -> Icons.Filled.Healing
        "fitness_center" -> Icons.Filled.FitnessCenter
        "spa" -> Icons.Filled.Spa
        "person" -> Icons.Filled.Person
        "work" -> Icons.Filled.Work
        "school" -> Icons.Filled.School
        "pets" -> Icons.Filled.Pets
        else -> Icons.Filled.Category
    }
}

@Composable
internal fun CategoryIcon(
    iconKey: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    colorKey: String? = null,
    tint: Color? = null
) {
    val resolvedTint = tint ?: categoryIconTint(colorKey)
    Icon(
        imageVector = categoryImageVector(iconKey),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = resolvedTint
    )
}

@Composable
internal fun CategoryLabel(
    iconKey: String?,
    text: String,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    colorKey: String? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color? = null,
    iconSize: Dp = 18.dp,
    maxLines: Int = 1
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showIcon) {
            CategoryIcon(
                iconKey = iconKey,
                modifier = Modifier.size(iconSize),
                contentDescription = null,
                colorKey = colorKey,
                tint = iconTint
            )
        }
        Text(
            text = text,
            style = textStyle,
            color = textColor,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun CategoryIconPicker(
    selectedIconKey: String,
    onIconSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val iconLabel = stringResource(Res.string.icon)
    val normalizedSelectedIconKey = normalizeCategoryIconKey(selectedIconKey)
    val sections = remember { categoryIconSections }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = iconLabel,
            style = MaterialTheme.typography.labelLarge
        )

        sections.forEach { section ->
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(section.titleRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                section.iconKeys.chunked(4).forEach { rowIcons ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowIcons.forEach { iconKey ->
                            val isSelected = normalizedSelectedIconKey == normalizeCategoryIconKey(iconKey)
                            val previewTint = categoryIconTint(iconKey)
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clickable { onIconSelected(iconKey) }
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CategoryIcon(
                                        iconKey = iconKey,
                                        modifier = Modifier.size(22.dp),
                                        contentDescription = iconLabel,
                                        colorKey = iconKey,
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            previewTint
                                        }
                                    )
                                }
                            }
                        }

                        repeat(4 - rowIcons.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
