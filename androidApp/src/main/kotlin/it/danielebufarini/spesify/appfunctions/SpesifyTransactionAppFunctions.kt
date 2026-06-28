package it.danielebufarini.spesify.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.service.AppFunction
import it.danielebufarini.spesify.data.AddTransactionCommand
import it.danielebufarini.spesify.data.AddTransactionUseCase
import it.danielebufarini.spesify.data.CategoryAgentUseCase
import it.danielebufarini.spesify.data.FinancialQueryUseCase
import it.danielebufarini.spesify.data.TransactionCreationSource
import it.danielebufarini.spesify.data.TransactionKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

/**
 * Agent-facing transaction functions exposed by Spesify.
 */
class SpesifyTransactionAppFunctions {
    private val addTransactionUseCase: AddTransactionUseCase by lazy {
        GlobalContext.get().get<AddTransactionUseCase>()
    }
    private val financialQueryUseCase: FinancialQueryUseCase by lazy {
        GlobalContext.get().get<FinancialQueryUseCase>()
    }
    private val categoryAgentUseCase: CategoryAgentUseCase by lazy {
        GlobalContext.get().get<CategoryAgentUseCase>()
    }

    /**
     * Adds a non-recurring expense or income transaction to Spesify.
     *
     * Use this function only when the user asked to record a transaction. The amount is expressed
     * in normal user-facing currency format, for example "$55.56", "55.56", or "55,56".
     * Spesify converts this value internally to minor currency units before saving it, so callers
     * must not ask users to provide cents as an integer. The first version does not support
     * installments, recurring transactions, or shared expenses. If the transaction is an expense,
     * provide a category name or category ID so Spesify can attach the required category.
     *
     * @param appFunctionContext The execution context supplied by Android.
     * @param kind Transaction type. Use "expense" for spending and "income" for money received.
     * @param amount Standard user-facing amount, such as "$55.56", "55.56", or "55,56".
     * @param categoryName Optional category name or category ID. Required for expenses because Spesify expenses need a category.
     * @param description Optional human-readable note to save with the transaction.
     * @param dateIso Optional ISO local date in yyyy-MM-dd format. Omit it to use today's date.
     * @param dateEpochMillis Optional epoch milliseconds date. If supplied, this takes precedence over dateIso.
     * @return A structured result describing whether the transaction was created or needs user confirmation.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addTransaction(
        appFunctionContext: AppFunctionContext,
        kind: String,
        amount: String,
        categoryName: String? = null,
        description: String? = null,
        dateIso: String? = null,
        dateEpochMillis: Long? = null
    ): AddTransactionResponse = withContext(Dispatchers.IO) {
        val transactionKind = TransactionKind.fromExternalValue(kind)
            ?: return@withContext AddTransactionResponse.needsConfirmation("Please choose expense or income.")
        val amountMinorUnits = parseAmountMinorUnits(amount)
            ?: return@withContext AddTransactionResponse.needsConfirmation(
                "Please provide a valid amount, for example 55.56."
            )
        val dateMillis = parseDateMillis(dateIso = dateIso, dateEpochMillis = dateEpochMillis)
            ?: return@withContext AddTransactionResponse.needsConfirmation(
                "The date was not valid. Use ISO format yyyy-MM-dd or omit it to use today."
            )

        val result = addTransactionUseCase.execute(
            AddTransactionCommand(
                kind = transactionKind,
                amount = amountMinorUnits,
                categoryName = categoryName,
                note = description,
                dateMillis = dateMillis,
                source = TransactionCreationSource.AndroidAppFunction
            )
        )
        result.toAppFunctionResponse()
    }

    /**
     * Returns the total expenses for the current calendar month.
     *
     * Use this when the user asks how much they have spent this month. The returned amount is both
     * an integer minor-unit value and a localized display string.
     *
     * @param appFunctionContext The execution context supplied by Android.
     * @return The current month's expense total.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getCurrentMonthExpenseTotal(
        appFunctionContext: AppFunctionContext
    ): FinancialAmountResponse = withContext(Dispatchers.IO) {
        financialQueryUseCase.getCurrentMonthExpensesTotal().toFinancialAmountResponse(
            successMessagePrefix = "Current month expenses"
        )
    }

    /**
     * Returns the total expenses for a specific calendar month.
     *
     * Use this when the user asks for expenses in a month such as May 2026. Month must be 1 to 12.
     *
     * @param appFunctionContext The execution context supplied by Android.
     * @param year Four-digit year to query, for example 2026.
     * @param month Calendar month to query. January is 1 and December is 12.
     * @return The expense total for the requested month.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getExpenseTotalForMonth(
        appFunctionContext: AppFunctionContext,
        year: Int,
        month: Int
    ): FinancialAmountResponse = withContext(Dispatchers.IO) {
        financialQueryUseCase.getExpensesTotalForMonth(
            year = year,
            month = month
        ).toFinancialAmountResponse(
            successMessagePrefix = "Expenses for ${year}-${month.toString().padStart(2, '0')}"
        )
    }

    /**
     * Returns the total expenses for an inclusive date period.
     *
     * Use this when the user asks for spending between two dates. Dates must use ISO yyyy-MM-dd.
     * Both the start and end dates are included in the total.
     *
     * @param appFunctionContext The execution context supplied by Android.
     * @param startDateIso Inclusive start date in ISO yyyy-MM-dd format.
     * @param endDateIso Inclusive end date in ISO yyyy-MM-dd format.
     * @return The expense total for the requested period.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getExpenseTotalForPeriod(
        appFunctionContext: AppFunctionContext,
        startDateIso: String,
        endDateIso: String
    ): FinancialAmountResponse = withContext(Dispatchers.IO) {
        val bounds = parsePeriodMillis(startDateIso = startDateIso, endDateIso = endDateIso)
            ?: return@withContext FinancialAmountResponse.failed(
                "Please provide valid ISO dates in yyyy-MM-dd format and make sure the end date is not before the start date."
            )
        financialQueryUseCase.getExpensesTotalForPeriod(
            startDateMillis = bounds.first,
            endDateMillis = bounds.second
        ).toFinancialAmountResponse(
            successMessagePrefix = "Expenses from ${startDateIso} to ${endDateIso}"
        )
    }

    /**
     * Returns the total income for the current calendar month.
     *
     * Use this when the user asks how much income they have recorded this month.
     *
     * @param appFunctionContext The execution context supplied by Android.
     * @return The current month's income total.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getCurrentMonthIncomeTotal(
        appFunctionContext: AppFunctionContext
    ): FinancialAmountResponse = withContext(Dispatchers.IO) {
        financialQueryUseCase.getCurrentMonthIncomeTotal().toFinancialAmountResponse(
            successMessagePrefix = "Current month income"
        )
    }

    /**
     * Returns the total income for a specific calendar month.
     *
     * Use this when the user asks for income in a month such as May 2026. Month must be 1 to 12.
     *
     * @param appFunctionContext The execution context supplied by Android.
     * @param year Four-digit year to query, for example 2026.
     * @param month Calendar month to query. January is 1 and December is 12.
     * @return The income total for the requested month.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getIncomeTotalForMonth(
        appFunctionContext: AppFunctionContext,
        year: Int,
        month: Int
    ): FinancialAmountResponse = withContext(Dispatchers.IO) {
        financialQueryUseCase.getIncomeTotalForMonth(
            year = year,
            month = month
        ).toFinancialAmountResponse(
            successMessagePrefix = "Income for ${year}-${month.toString().padStart(2, '0')}"
        )
    }

    /**
     * Returns the total income for an inclusive date period.
     *
     * Use this when the user asks for income between two dates. Dates must use ISO yyyy-MM-dd.
     * Both the start and end dates are included in the total.
     *
     * @param appFunctionContext The execution context supplied by Android.
     * @param startDateIso Inclusive start date in ISO yyyy-MM-dd format.
     * @param endDateIso Inclusive end date in ISO yyyy-MM-dd format.
     * @return The income total for the requested period.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getIncomeTotalForPeriod(
        appFunctionContext: AppFunctionContext,
        startDateIso: String,
        endDateIso: String
    ): FinancialAmountResponse = withContext(Dispatchers.IO) {
        val bounds = parsePeriodMillis(startDateIso = startDateIso, endDateIso = endDateIso)
            ?: return@withContext FinancialAmountResponse.failed(
                "Please provide valid ISO dates in yyyy-MM-dd format and make sure the end date is not before the start date."
            )
        financialQueryUseCase.getIncomeTotalForPeriod(
            startDateMillis = bounds.first,
            endDateMillis = bounds.second
        ).toFinancialAmountResponse(
            successMessagePrefix = "Income from ${startDateIso} to ${endDateIso}"
        )
    }

    /**
     * Returns the current balance recorded in Spesify.
     *
     * The current balance matches the dashboard cumulative balance through the current calendar month.
     *
     * @param appFunctionContext The execution context supplied by Android.
     * @return The current balance.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getCurrentBalance(
        appFunctionContext: AppFunctionContext
    ): FinancialAmountResponse = withContext(Dispatchers.IO) {
        financialQueryUseCase.getCurrentBalance().toFinancialAmountResponse(
            successMessagePrefix = "Current balance"
        )
    }

    /**
     * Lists existing Spesify categories for agents and assistants.
     *
     * Use this before adding a transaction when the user refers to a category and the caller needs
     * an exact category name or ID. The optional kind parameter filters categories to "expense"
     * or "income". Archived categories are intentionally hidden from this agent-facing list.
     *
     * @param appFunctionContext The execution context supplied by Android.
     * @param kind Optional category type filter. Use "expense" or "income". Omit to list all active categories.
     * @return Active categories matching the requested filter.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listCategories(
        appFunctionContext: AppFunctionContext,
        kind: String? = null
    ): CategoryListResponse = withContext(Dispatchers.IO) {
        val transactionKind = kind
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(TransactionKind::fromExternalValue)
            ?: if (kind.isNullOrBlank()) null else return@withContext CategoryListResponse.failed(
                "Please choose expense or income, or omit the category type."
            )

        categoryAgentUseCase.listCategories(kind = transactionKind).toCategoryListResponse()
    }

    /**
     * Adds a new category explicitly requested by the user.
     *
     * This is the safe way for agents to create categories. The addTransaction function does not
     * create categories implicitly, because agents can infer or hallucinate category names.
     *
     * @param appFunctionContext The execution context supplied by Android.
     * @param kind Category type. Use "expense" for expense categories and "income" for income categories.
     * @param name Category name to create.
     * @param iconKey Optional Spesify icon key. Omit to use a sensible default.
     * @param color Optional color hex value. Omit to use the default category color.
     * @return A structured result describing whether the category was created or needs user confirmation.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addCategory(
        appFunctionContext: AppFunctionContext,
        kind: String,
        name: String,
        iconKey: String? = null,
        color: String? = null
    ): CategoryMutationResponse = withContext(Dispatchers.IO) {
        val transactionKind = TransactionKind.fromExternalValue(kind)
            ?: return@withContext CategoryMutationResponse.needsConfirmation("Please choose expense or income.")

        categoryAgentUseCase.addCategory(
            kind = transactionKind,
            name = name,
            iconKey = iconKey,
            color = color
        ).toCategoryMutationResponse()
    }

    /**
     * Deletes a category after moving existing transactions to another category.
     *
     * The replacement category is always required for the agent-facing delete operation so user
     * data is never orphaned. The replacement must have the same category type as the deleted
     * category.
     *
     * @param appFunctionContext The execution context supplied by Android.
     * @param kind Category type. Use "expense" or "income".
     * @param categoryName Category name or ID to delete.
     * @param moveToCategoryName Category name or ID that should receive transactions using the deleted category.
     * @return A structured result describing whether the category was deleted or needs user confirmation.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun deleteCategory(
        appFunctionContext: AppFunctionContext,
        kind: String,
        categoryName: String,
        moveToCategoryName: String
    ): CategoryMutationResponse = withContext(Dispatchers.IO) {
        val transactionKind = TransactionKind.fromExternalValue(kind)
            ?: return@withContext CategoryMutationResponse.needsConfirmation("Please choose expense or income.")

        categoryAgentUseCase.deleteCategory(
            kind = transactionKind,
            categoryNameOrId = categoryName,
            moveToCategoryNameOrId = moveToCategoryName
        ).toCategoryMutationResponse()
    }
}
