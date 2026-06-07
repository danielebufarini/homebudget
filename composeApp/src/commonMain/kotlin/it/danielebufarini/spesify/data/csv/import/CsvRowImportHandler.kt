package it.danielebufarini.spesify.data.csv.import
import it.danielebufarini.spesify.data.csv.ParsedUnifiedCsvRow

internal interface CsvRowImportHandler {
    suspend fun importRow(
        row: ParsedUnifiedCsvRow,
        rowIndex: Int,
        amount: Long,
        itemDate: Long,
        state: CsvImportState
    ): Boolean
}
