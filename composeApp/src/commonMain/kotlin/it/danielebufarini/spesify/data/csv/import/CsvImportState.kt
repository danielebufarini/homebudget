package it.danielebufarini.spesify.data.csv.import

import it.danielebufarini.spesify.data.PendingExpense
import it.danielebufarini.spesify.data.PendingIncome
import it.danielebufarini.spesify.data.csv.CsvImportStore
import it.danielebufarini.spesify.data.csv.CsvImportedExpenseKey
import it.danielebufarini.spesify.data.csv.CsvImportedIncomeKey
import it.danielebufarini.spesify.data.csv.CsvImportedRecurringOccurrenceKey
import it.danielebufarini.spesify.database.Category

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
