package it.homebudget.app.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import homebudget.composeapp.generated.resources.*
import it.homebudget.app.database.Category
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun rememberCategoryNameResolver(): (String, String, Long) -> String {
    val defaultCategoryNamesById = rememberDefaultCategoryNamesById()
    return remember(defaultCategoryNamesById) {
        buildCategoryNameResolver(defaultCategoryNamesById)
    }
}

@Composable
fun localizedCategoryName(category: Category): String {
    val resolveCategoryName = rememberCategoryNameResolver()
    return resolveCategoryName(category.id, category.name, category.isCustom)
}

suspend fun loadCategoryNameResolver(): (String, String, Long) -> String {
    return buildCategoryNameResolver(loadDefaultCategoryNamesById())
}

suspend fun csvImportNoRowsMessage(): String = getString(Res.string.csv_import_no_rows)

suspend fun csvImportFailedMessage(): String = getString(Res.string.csv_import_failed)

suspend fun csvExportFailedMessage(): String = getString(Res.string.csv_export_failed)

suspend fun unableToSaveExpenseMessage(): String = getString(Res.string.unable_to_save_expense)

suspend fun csvImportSuccessMessage(importedCount: Int, skippedCount: Int): String {
    return if (skippedCount == 0) {
        getString(Res.string.csv_import_success).formatResourceArgs(importedCount)
    } else {
        getString(Res.string.csv_import_success_with_skipped)
            .formatResourceArgs(importedCount, skippedCount)
    }
}

internal fun String.formatResourceArgs(vararg args: Any): String {
    var formatted = this
    args.forEachIndexed { index, arg ->
        formatted = formatted.replace("%${index + 1}\\$[sd]".toRegex(), arg.toString())
    }
    return formatted
}

@Composable
private fun rememberDefaultCategoryNamesById(): Map<String, String> {
    val defaultCategoryNames = listOf(
        stringResource(Res.string.category_default_0),
        stringResource(Res.string.category_default_1),
        stringResource(Res.string.category_default_2),
        stringResource(Res.string.category_default_3),
        stringResource(Res.string.category_default_4),
        stringResource(Res.string.category_default_5),
        stringResource(Res.string.category_default_6),
        stringResource(Res.string.category_default_7),
        stringResource(Res.string.category_default_8)
    )

    return remember(defaultCategoryNames) {
        defaultCategoryNames.mapIndexed { index, name ->
            "default_$index" to name
        }.toMap()
    }
}

private suspend fun loadDefaultCategoryNamesById(): Map<String, String> {
    return listOf(
        getString(Res.string.category_default_0),
        getString(Res.string.category_default_1),
        getString(Res.string.category_default_2),
        getString(Res.string.category_default_3),
        getString(Res.string.category_default_4),
        getString(Res.string.category_default_5),
        getString(Res.string.category_default_6),
        getString(Res.string.category_default_7),
        getString(Res.string.category_default_8)
    ).mapIndexed { index, name ->
        "default_$index" to name
    }.toMap()
}

private fun buildCategoryNameResolver(
    defaultCategoryNamesById: Map<String, String>
): (String, String, Long) -> String = { id, storedName, isCustom ->
    if (isCustom == 1L) {
        storedName
    } else {
        defaultCategoryNamesById[id] ?: storedName
    }
}
