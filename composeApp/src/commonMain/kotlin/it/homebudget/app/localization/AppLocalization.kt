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
    val defaultCategory0 = stringResource(Res.string.category_default_0)
    val defaultCategory1 = stringResource(Res.string.category_default_1)
    val defaultCategory2 = stringResource(Res.string.category_default_2)
    val defaultCategory3 = stringResource(Res.string.category_default_3)
    val defaultCategory4 = stringResource(Res.string.category_default_4)

    return remember(
        defaultCategory0,
        defaultCategory1,
        defaultCategory2,
        defaultCategory3,
        defaultCategory4
    ) {
        mapOf(
            "default_0" to defaultCategory0,
            "default_1" to defaultCategory1,
            "default_2" to defaultCategory2,
            "default_3" to defaultCategory3,
            "default_4" to defaultCategory4
        )
    }
}

private suspend fun loadDefaultCategoryNamesById(): Map<String, String> {
    return mapOf(
        "default_0" to getString(Res.string.category_default_0),
        "default_1" to getString(Res.string.category_default_1),
        "default_2" to getString(Res.string.category_default_2),
        "default_3" to getString(Res.string.category_default_3),
        "default_4" to getString(Res.string.category_default_4)
    )
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
