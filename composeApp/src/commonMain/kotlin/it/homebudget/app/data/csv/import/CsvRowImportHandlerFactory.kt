package it.homebudget.app.data.csv.import

import it.homebudget.app.data.csv.CsvRowType

internal object CsvRowImportHandlerFactory {

    fun create(type: CsvRowType): CsvRowImportHandler {
        return when (type) {
            CsvRowType.Expense -> ExpenseCsvRowImportHandler
            CsvRowType.Income -> IncomeCsvRowImportHandler
        }
    }
}
