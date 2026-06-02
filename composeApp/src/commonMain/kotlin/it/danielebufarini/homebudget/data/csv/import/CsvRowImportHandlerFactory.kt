package it.danielebufarini.homebudget.data.csv.import

import it.danielebufarini.homebudget.data.csv.CsvRowType

internal object CsvRowImportHandlerFactory {

    fun create(type: CsvRowType): CsvRowImportHandler {
        return when (type) {
            CsvRowType.Expense -> ExpenseCsvRowImportHandler
            CsvRowType.Income -> IncomeCsvRowImportHandler
        }
    }
}
