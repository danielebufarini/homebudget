package it.homebudget.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import homebudget.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal const val DEFAULT_CATEGORY_ICON_KEY = "category"

private data class CategoryIconSection(
    val titleRes: StringResource,
    val iconKeys: List<String>
)

internal val PRELOADED_CATEGORY_ICON_KEYS = listOf(
    "home",
    "receipt",
    "build",
    "shopping_cart",
    "restaurant",
    "local_cafe",
    "cake",
    "directions_car",
    "directions_bus",
    "train",
    "local_taxi",
    "flight",
    "hotel",
    "beach_access",
    "local_hospital",
    "healing",
    "fitness_center",
    "spa",
    "person",
    "work",
    "school",
    "pets",
    "category"
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
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Icon(
        imageVector = categoryImageVector(iconKey),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}

@Composable
internal fun CategoryLabel(
    iconKey: String?,
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconSize: Dp = 18.dp,
    maxLines: Int = 1
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CategoryIcon(
            iconKey = iconKey,
            modifier = Modifier.size(iconSize),
            contentDescription = null,
            tint = iconTint
        )
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
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
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
