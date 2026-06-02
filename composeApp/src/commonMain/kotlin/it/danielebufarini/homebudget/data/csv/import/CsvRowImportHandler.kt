package it.danielebufarini.homebudget.data.csv.import
import it.danielebufarini.homebudget.data.csv.ParsedUnifiedCsvRow

internal interface CsvRowImportHandler {
    suspend fun importRow(
        row: ParsedUnifiedCsvRow,
        rowIndex: Int,
        amount: Long,
        itemDate: Long,
        state: CsvImportState
    ): Boolean
}
