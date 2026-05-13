package it.homebudget.app.data.csv.import
import it.homebudget.app.data.csv.ParsedUnifiedCsvRow

internal interface CsvRowImportHandler {
    suspend fun importRow(
        row: ParsedUnifiedCsvRow,
        rowIndex: Int,
        amount: Long,
        itemDate: Long,
        state: CsvImportState
    ): Boolean
}
