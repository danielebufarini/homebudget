package it.homebudget.app.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

expect fun currentAppLanguageCode(): String

val AppStrings: Strings
    get() = if (isItalian) ItStrings else EnStrings

val LocalStrings = staticCompositionLocalOf<Strings> { EnStrings }

@Composable
fun ProvideAppStrings(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStrings provides AppStrings) {
        content()
    }
}

private val isItalian: Boolean
    get() = currentAppLanguageCode().startsWith("it", ignoreCase = true)

fun currentCurrencySymbol(): String = AppStrings.currencySymbol

interface Strings {
    val currencySymbol: String
    val add: String
    val addCategory: String
    val addExpense: String
    val addIncome: String
    val amount: String
    val back: String
    val byCategory: String
    val byDate: String
    val calendar: String
    val cancel: String
    val cashFlow: String
    val categories: String
    val category: String
    val categoryName: String
    val close: String
    val customCategory: String
    val dashboard: String
    val date: String
    val defaultCategory: String
    val delete: String
    val deleteCategory: String
    val deleteExpense: String
    val deleteIncome: String
    val deleteRecurringExpenseTitle: String
    val deleteRecurringIncomeTitle: String
    val description: String
    val editExpense: String
    val editIncome: String
    val enterValidAmount: String
    val expense: String
    val expenseDetails: String
    val expenses: String
    val expensesByCategory: String
    val highestDay: String
    val income: String
    val importCsv: String
    val installments: String
    val monthlySummary: String
    val csvImportFailed: String
    val csvImportNoRows: String
    val noExpensesForDay: String
    val noExpensesForMonth: String
    val noExpensesInPeriod: String
    val noIncomeForMonth: String
    val recurringMonthly: String
    val save: String
    val saveExpense: String
    val saving: String
    val selectCategory: String
    val selectDate: String
    val selectInstallments: String
    val shared: String
    val sharedExpense: String
    val sharedExpenses: String
    val singlePayment: String
    val thisInstanceOnly: String
    val topCategory: String
    val unableToDeleteCategory: String
    val unableToDeleteExpense: String
    val unableToDeleteIncome: String
    val unableToSaveExpense: String
    val unableToSaveIncome: String
    val unknownCategory: String
    val update: String
    val updateExpense: String
    val updateRecurringExpenseTitle: String
    val updateRecurringIncomeTitle: String
    val wholeSeries: String

    fun recurringExpenseInfo(years: Int): String
    fun recurringIncomeInfo(years: Int): String
    fun recurringExpenseSeriesInfo(): String
    fun recurringIncomeSeriesInfo(): String
    fun recurringExpenseActionMessage(isUpdate: Boolean): String
    fun recurringIncomeActionMessage(isUpdate: Boolean): String
    fun noExpensesForCategoryThisMonth(categoryName: String): String
    fun noSharedExpensesForMonth(): String
    fun installmentLabel(count: Int): String
    fun csvImportSuccess(importedCount: Int, skippedCount: Int): String
    fun categoryName(id: String, storedName: String, isCustom: Long): String
    fun fullMonthName(month: Int): String
    fun shortMonthName(month: Int): String
    fun shortWeekdayName(dayIndex: Int): String
}

private object ItStrings : Strings {
    override val currencySymbol = "€"
    override val add = "Aggiungi"
    override val addCategory = "Aggiungi categoria"
    override val addExpense = "Aggiungi spesa"
    override val addIncome = "Aggiungi entrata"
    override val amount = "Importo"
    override val back = "Indietro"
    override val byCategory = "Per categoria"
    override val byDate = "Per data"
    override val calendar = "Calendario"
    override val cancel = "Annulla"
    override val cashFlow = "Flusso di cassa"
    override val categories = "Categorie"
    override val category = "Categoria"
    override val categoryName = "Nome categoria"
    override val close = "Chiudi"
    override val customCategory = "Categoria personalizzata"
    override val dashboard = "Riepilogo"
    override val date = "Data"
    override val defaultCategory = "Categoria predefinita"
    override val delete = "Elimina"
    override val deleteCategory = "Elimina categoria"
    override val deleteExpense = "Elimina spesa"
    override val deleteIncome = "Elimina entrata"
    override val deleteRecurringExpenseTitle = "Eliminare la spesa ricorrente?"
    override val deleteRecurringIncomeTitle = "Eliminare l'entrata ricorrente?"
    override val description = "Descrizione"
    override val editExpense = "Modifica spesa"
    override val editIncome = "Modifica entrata"
    override val enterValidAmount = "Inserisci un importo valido maggiore di 0"
    override val expense = "Spesa"
    override val expenseDetails = "Dettagli spesa"
    override val expenses = "Spese"
    override val expensesByCategory = "Spese per categoria"
    override val highestDay = "Giorno più alto"
    override val income = "Entrate"
    override val importCsv = "Importa CSV"
    override val installments = "Rate"
    override val monthlySummary = "Riepilogo mensile"
    override val csvImportFailed = "Impossibile importare il file CSV"
    override val csvImportNoRows = "Nessuna riga importabile trovata nel file CSV"
    override val noExpensesForDay = "Nessuna spesa per questo giorno"
    override val noExpensesForMonth = "Nessuna spesa per questo mese"
    override val noExpensesInPeriod = "Nessuna spesa in questo periodo"
    override val noIncomeForMonth = "Nessuna entrata per questo mese"
    override val recurringMonthly = "Mensile ricorrente"
    override val save = "Salva"
    override val saveExpense = "Salva spesa"
    override val saving = "Salvataggio..."
    override val selectCategory = "Seleziona categoria"
    override val selectDate = "Seleziona una data"
    override val selectInstallments = "Seleziona rate"
    override val shared = "Condivise"
    override val sharedExpense = "Spesa condivisa"
    override val sharedExpenses = "Spese condivise"
    override val singlePayment = "Pagamento singolo"
    override val thisInstanceOnly = "Solo questa occorrenza"
    override val topCategory = "Categoria principale"
    override val unableToDeleteCategory = "Impossibile eliminare la categoria"
    override val unableToDeleteExpense = "Impossibile eliminare la spesa"
    override val unableToDeleteIncome = "Impossibile eliminare l'entrata"
    override val unableToSaveExpense = "Impossibile salvare la spesa"
    override val unableToSaveIncome = "Impossibile salvare l'entrata"
    override val unknownCategory = "Categoria sconosciuta"
    override val update = "Aggiorna"
    override val updateExpense = "Aggiorna spesa"
    override val updateRecurringExpenseTitle = "Aggiornare la spesa ricorrente?"
    override val updateRecurringIncomeTitle = "Aggiornare l'entrata ricorrente?"
    override val wholeSeries = "Tutta la serie"

    private val fullMonthNames = listOf(
        "Gennaio",
        "Febbraio",
        "Marzo",
        "Aprile",
        "Maggio",
        "Giugno",
        "Luglio",
        "Agosto",
        "Settembre",
        "Ottobre",
        "Novembre",
        "Dicembre"
    )
    private val shortMonthNames = listOf("Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic")
    private val shortWeekdayNames = listOf("Dom", "Lun", "Mar", "Mer", "Gio", "Ven", "Sab")

    override fun recurringExpenseInfo(years: Int): String =
        "Crea la stessa spesa ogni mese in questo giorno per i prossimi $years anni."

    override fun recurringIncomeInfo(years: Int): String =
        "Crea la stessa entrata ogni mese in questo giorno per i prossimi $years anni."

    override fun recurringExpenseSeriesInfo(): String =
        "Questa spesa fa parte di una serie mensile ricorrente."

    override fun recurringIncomeSeriesInfo(): String =
        "Questa entrata fa parte di una serie mensile ricorrente."

    override fun recurringExpenseActionMessage(isUpdate: Boolean): String =
        if (isUpdate) {
            "Vuoi aggiornare solo questa spesa o tutta la serie ricorrente?"
        } else {
            "Vuoi eliminare solo questa spesa o tutta la serie ricorrente?"
        }

    override fun recurringIncomeActionMessage(isUpdate: Boolean): String =
        if (isUpdate) {
            "Vuoi aggiornare solo questa entrata o tutta la serie ricorrente?"
        } else {
            "Vuoi eliminare solo questa entrata o tutta la serie ricorrente?"
        }

    override fun noExpensesForCategoryThisMonth(categoryName: String): String =
        "Nessuna spesa per $categoryName questo mese"

    override fun noSharedExpensesForMonth(): String =
        "Nessuna spesa condivisa per questo mese"

    override fun installmentLabel(count: Int): String =
        if (count == 1) singlePayment else "$count rate"

    override fun csvImportSuccess(importedCount: Int, skippedCount: Int): String =
        if (skippedCount == 0) {
            "Importate $importedCount voci dal CSV"
        } else {
            "Importate $importedCount voci dal CSV, saltate $skippedCount"
        }

    override fun categoryName(id: String, storedName: String, isCustom: Long): String =
        if (isCustom == 1L) {
            storedName
        } else {
            when (id) {
                "default_0" -> "Spese casa"
                "default_1" -> "Cibo"
                "default_2" -> "Bollette"
                "default_3" -> "Spese auto"
                "default_4" -> "Varie"
                else -> storedName
            }
        }

    override fun fullMonthName(month: Int): String = fullMonthNames[month - 1]

    override fun shortMonthName(month: Int): String = shortMonthNames[month - 1]

    override fun shortWeekdayName(dayIndex: Int): String = shortWeekdayNames[dayIndex]
}

private object EnStrings : Strings {
    override val currencySymbol = "$"
    override val add = "Add"
    override val addCategory = "Add Category"
    override val addExpense = "Add Expense"
    override val addIncome = "Add Income"
    override val amount = "Amount"
    override val back = "Back"
    override val byCategory = "By Category"
    override val byDate = "By Date"
    override val calendar = "Calendar"
    override val cancel = "Cancel"
    override val cashFlow = "Cash Flow"
    override val categories = "Categories"
    override val category = "Category"
    override val categoryName = "Category Name"
    override val close = "Close"
    override val customCategory = "Custom category"
    override val dashboard = "Dashboard"
    override val date = "Date"
    override val defaultCategory = "Default category"
    override val delete = "Delete"
    override val deleteCategory = "Delete category"
    override val deleteExpense = "Delete expense"
    override val deleteIncome = "Delete income"
    override val deleteRecurringExpenseTitle = "Delete recurring expense?"
    override val deleteRecurringIncomeTitle = "Delete recurring income?"
    override val description = "Description"
    override val editExpense = "Edit Expense"
    override val editIncome = "Edit Income"
    override val enterValidAmount = "Enter a valid amount greater than 0"
    override val expense = "Expense"
    override val expenseDetails = "Expense Details"
    override val expenses = "Expenses"
    override val expensesByCategory = "Expenses by category"
    override val highestDay = "Highest Day"
    override val income = "Income"
    override val importCsv = "Import CSV"
    override val installments = "Installments"
    override val monthlySummary = "Monthly Summary"
    override val csvImportFailed = "Unable to import the CSV file"
    override val csvImportNoRows = "No importable rows were found in the CSV file"
    override val noExpensesForDay = "No expenses for this day"
    override val noExpensesForMonth = "No expenses for this month"
    override val noExpensesInPeriod = "No expenses in this period"
    override val noIncomeForMonth = "No income for this month"
    override val recurringMonthly = "Recurring Monthly"
    override val save = "Save"
    override val saveExpense = "Save Expense"
    override val saving = "Saving..."
    override val selectCategory = "Select Category"
    override val selectDate = "Select a date"
    override val selectInstallments = "Select Installments"
    override val shared = "Shared"
    override val sharedExpense = "Shared Expense"
    override val sharedExpenses = "Shared Expenses"
    override val singlePayment = "Single payment"
    override val thisInstanceOnly = "This instance only"
    override val topCategory = "Top Category"
    override val unableToDeleteCategory = "Unable to delete category"
    override val unableToDeleteExpense = "Unable to delete expense"
    override val unableToDeleteIncome = "Unable to delete income"
    override val unableToSaveExpense = "Unable to save expense"
    override val unableToSaveIncome = "Unable to save income"
    override val unknownCategory = "Unknown category"
    override val update = "Update"
    override val updateExpense = "Update Expense"
    override val updateRecurringExpenseTitle = "Update recurring expense?"
    override val updateRecurringIncomeTitle = "Update recurring income?"
    override val wholeSeries = "Whole series"

    private val fullMonthNames = listOf(
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December"
    )
    private val shortMonthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val shortWeekdayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    override fun recurringExpenseInfo(years: Int): String =
        "Creates the same expense every month on this day for the next $years years."

    override fun recurringIncomeInfo(years: Int): String =
        "Creates the same income every month on this day for the next $years years."

    override fun recurringExpenseSeriesInfo(): String =
        "This expense is part of a recurring monthly series."

    override fun recurringIncomeSeriesInfo(): String =
        "This income is part of a recurring monthly series."

    override fun recurringExpenseActionMessage(isUpdate: Boolean): String =
        if (isUpdate) {
            "Do you want to update only this expense or the whole recurring series?"
        } else {
            "Do you want to delete only this expense or the whole recurring series?"
        }

    override fun recurringIncomeActionMessage(isUpdate: Boolean): String =
        if (isUpdate) {
            "Do you want to update only this income or the whole recurring series?"
        } else {
            "Do you want to delete only this income or the whole recurring series?"
        }

    override fun noExpensesForCategoryThisMonth(categoryName: String): String =
        "No expenses for $categoryName this month"

    override fun noSharedExpensesForMonth(): String =
        "No shared expenses for this month"

    override fun installmentLabel(count: Int): String =
        if (count == 1) singlePayment else "$count installments"

    override fun csvImportSuccess(importedCount: Int, skippedCount: Int): String =
        if (skippedCount == 0) {
            "Imported $importedCount items from CSV"
        } else {
            "Imported $importedCount items from CSV, skipped $skippedCount"
        }

    override fun categoryName(id: String, storedName: String, isCustom: Long): String =
        if (isCustom == 1L) {
            storedName
        } else {
            when (id) {
                "default_0" -> "Home expenses"
                "default_1" -> "Food"
                "default_2" -> "Bills"
                "default_3" -> "Car expenses"
                "default_4" -> "Miscellaneous"
                else -> storedName
            }
        }

    override fun fullMonthName(month: Int): String = fullMonthNames[month - 1]

    override fun shortMonthName(month: Int): String = shortMonthNames[month - 1]

    override fun shortWeekdayName(dayIndex: Int): String = shortWeekdayNames[dayIndex]
}
