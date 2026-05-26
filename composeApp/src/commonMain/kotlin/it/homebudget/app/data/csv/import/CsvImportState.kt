package it.homebudget.app.data.csv.import

import it.homebudget.app.data.PendingExpense
import it.homebudget.app.data.PendingIncome
import it.homebudget.app.data.csv.CsvImportStore
import it.homebudget.app.data.csv.CsvImportedExpenseKey
import it.homebudget.app.data.csv.CsvImportedIncomeKey
import it.homebudget.app.database.Category

internal class CsvImportState(
    val repository: CsvImportStore,
    val resolveCategoryName: (String, String) -> String,
    val categoriesById: MutableMap<String, Category>,
    val categoriesByNormalizedName: MutableMap<String, Category>,
    val existingExpenseKeys: MutableSet<CsvImportedExpenseKey>,
    val existingIncomeKeys: MutableSet<CsvImportedIncomeKey>
) {
    val expensesToInsert = mutableListOf<PendingExpense>()
    val incomesToInsert = mutableListOf<PendingIncome>()
    var skippedCount = 0
}
