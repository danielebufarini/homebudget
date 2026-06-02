package it.danielebufarini.homebudget.data.csv.import

import it.danielebufarini.homebudget.data.PendingIncome
import it.danielebufarini.homebudget.data.buildRecurringMonthlyIncomes
import it.danielebufarini.homebudget.data.csv.CsvImportedIncomeKey
import it.danielebufarini.homebudget.data.csv.asImportKey
import it.danielebufarini.homebudget.data.csv.buildImportedIncomeId

internal class ImportedRecurringIncomeSeriesCompleter :
    ImportedRecurringSeriesCompleter<PendingIncome, CsvImportedIncomeKey>() {

    override fun recurringSeriesIdOf(item: PendingIncome): String? = item.recurringSeriesId

    override fun dateOf(item: PendingIncome): Long = item.date

    override fun importKeyOf(item: PendingIncome): CsvImportedIncomeKey = item.asImportKey()

    override fun buildRecurringMonthlyItems(
        latestImportedOccurrence: PendingIncome,
        recurringSeriesId: String,
        occurrences: Int
    ): List<PendingIncome> {
        return buildRecurringMonthlyIncomes(
            amount = latestImportedOccurrence.amount,
            firstDate = latestImportedOccurrence.date,
            description = latestImportedOccurrence.description,
            categoryId = latestImportedOccurrence.categoryId,
            recurringSeriesId = recurringSeriesId,
            idProvider = ::buildImportedIncomeId,
            occurrences = occurrences
        )
    }
}
