package it.danielebufarini.homebudget.data.csv.import

import it.danielebufarini.homebudget.data.PendingExpense
import it.danielebufarini.homebudget.data.PendingIncome
import it.danielebufarini.homebudget.data.csv.CsvImportStore
import it.danielebufarini.homebudget.data.csv.CsvImportedExpenseKey
import it.danielebufarini.homebudget.data.csv.CsvImportedIncomeKey
import it.danielebufarini.homebudget.data.csv.CsvImportedRecurringOccurrenceKey
import it.danielebufarini.homebudget.database.Category

internal class CsvImportState(
    val repository: CsvImportStore,
    val resolveCategoryName: (String, String) -> String,
    val categoriesById: MutableMap<String, Category>,
    val categoriesByNormalizedName: MutableMap<String, Category>,
    val existingExpenseKeys: MutableSet<CsvImportedExpenseKey>,
    val existingExpenseRecurringOccurrenceKeys: MutableSet<CsvImportedRecurringOccurrenceKey>,
    val existingIncomeKeys: MutableSet<CsvImportedIncomeKey>,
    val existingIncomeRecurringOccurrenceKeys: MutableSet<CsvImportedRecurringOccurrenceKey>
) {
    val expensesToInsert = mutableListOf<PendingExpense>()
    val incomesToInsert = mutableListOf<PendingIncome>()
    var skippedCount = 0
}
