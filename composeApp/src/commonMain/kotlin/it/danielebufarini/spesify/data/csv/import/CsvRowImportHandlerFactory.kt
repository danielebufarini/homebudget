package it.danielebufarini.spesify.data.csv.import

import it.danielebufarini.spesify.data.csv.CsvRowType

internal object CsvRowImportHandlerFactory {

    fun create(type: CsvRowType): CsvRowImportHandler {
        return when (type) {
            CsvRowType.Expense -> ExpenseCsvRowImportHandler
            CsvRowType.Income -> IncomeCsvRowImportHandler
        }
    }
}
