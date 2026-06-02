package it.danielebufarini.homebudget.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.csv_export_failed
import homebudget.composeapp.generated.resources.csv_import_failed
import homebudget.composeapp.generated.resources.csv_import_no_rows
import homebudget.composeapp.generated.resources.csv_import_success
import homebudget.composeapp.generated.resources.csv_import_success_with_skipped
import homebudget.composeapp.generated.resources.unable_to_save_expense
import it.danielebufarini.homebudget.database.Category
import org.jetbrains.compose.resources.getString

@Composable
fun rememberCategoryNameResolver(): (String, String) -> String {
    return remember {
        buildCategoryNameResolver()
    }
}

@Composable
fun localizedCategoryName(category: Category): String {
    val resolveCategoryName = rememberCategoryNameResolver()
    return resolveCategoryName(category.id, category.name)
}

suspend fun loadCategoryNameResolver(): (String, String) -> String {
    return buildCategoryNameResolver()
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

private fun buildCategoryNameResolver(): (String, String) -> String = { _, storedName -> storedName }
