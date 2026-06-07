package it.danielebufarini.spesify.data.csv.import

import it.danielebufarini.spesify.data.PendingExpense
import it.danielebufarini.spesify.data.buildRecurringMonthlyExpenses
import it.danielebufarini.spesify.data.csv.CsvImportedExpenseKey
import it.danielebufarini.spesify.data.csv.asImportKey
import it.danielebufarini.spesify.data.csv.buildImportedExpenseId

internal class ImportedRecurringExpenseSeriesCompleter :
    ImportedRecurringSeriesCompleter<PendingExpense, CsvImportedExpenseKey>() {

    override fun recurringSeriesIdOf(item: PendingExpense): String? = item.recurringSeriesId

    override fun dateOf(item: PendingExpense): Long = item.date

    override fun importKeyOf(item: PendingExpense): CsvImportedExpenseKey = item.asImportKey()

    override fun buildRecurringMonthlyItems(
        latestImportedOccurrence: PendingExpense,
        recurringSeriesId: String,
        occurrences: Int
    ): List<PendingExpense> {
        return buildRecurringMonthlyExpenses(
            amount = latestImportedOccurrence.amount,
            firstDate = latestImportedOccurrence.date,
            categoryId = latestImportedOccurrence.categoryId,
            description = latestImportedOccurrence.description,
            isShared = latestImportedOccurrence.isShared,
            recurringSeriesId = recurringSeriesId,
            idProvider = ::buildImportedExpenseId,
            occurrences = occurrences
        )
    }
}
