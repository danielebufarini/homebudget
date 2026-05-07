package it.homebudget.app.data.csv.import

import com.ionspin.kotlin.bignum.integer.BigInteger
import it.homebudget.app.data.csv.ParsedUnifiedCsvRow

internal interface CsvRowImportHandler {
    suspend fun importRow(
        row: ParsedUnifiedCsvRow,
        rowIndex: Int,
        amount: BigInteger,
        itemDate: Long,
        state: CsvImportState
    ): Boolean
}
