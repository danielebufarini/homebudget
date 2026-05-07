package it.homebudget.app.data.csv.import

import com.ionspin.kotlin.bignum.integer.BigInteger
import it.homebudget.app.data.PendingIncome
import it.homebudget.app.data.csv.CsvImportedIncomeKey
import it.homebudget.app.data.csv.ParsedUnifiedCsvRow
import it.homebudget.app.data.csv.buildImportedIncomeId
import it.homebudget.app.data.csv.normalizeDescription

internal object IncomeCsvRowImportHandler : CsvRowImportHandler {
    override suspend fun importRow(
        row: ParsedUnifiedCsvRow,
        rowIndex: Int,
        amount: BigInteger,
        itemDate: Long,
        state: CsvImportState
    ): Boolean {
        val incomeKey = CsvImportedIncomeKey(
            date = itemDate,
            amount = amount,
            description = normalizeDescription(row.description)
        )
        if (!state.existingIncomeKeys.add(incomeKey)) {
            return false
        }

        state.incomesToInsert += PendingIncome(
            id = buildImportedIncomeId(),
            amount = amount,
            date = itemDate,
            description = row.description?.takeIf { it.isNotBlank() },
            recurringSeriesId = row.buildRecurringSeriesId(rowIndex)
        )

        return true
    }
}
