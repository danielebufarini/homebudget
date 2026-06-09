package it.danielebufarini.spesify.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import it.danielebufarini.spesify.data.AddTransactionCommand
import it.danielebufarini.spesify.data.AddTransactionResult
import it.danielebufarini.spesify.data.AddTransactionUseCase
import it.danielebufarini.spesify.data.CategoryAgentUseCase
import it.danielebufarini.spesify.data.CategoryCommandResult
import it.danielebufarini.spesify.data.CategoryListResult
import it.danielebufarini.spesify.data.FinancialQueryResult
import it.danielebufarini.spesify.data.FinancialQueryUseCase
import it.danielebufarini.spesify.data.TransactionCreationSource
import it.danielebufarini.spesify.data.TransactionKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

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
                description = description,
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

    private fun parseDateMillis(dateIso: String?, dateEpochMillis: Long?): Long? {
        dateEpochMillis?.takeIf { it > 0L }?.let { return it }
        val date = dateIso?.trim()?.takeIf(String::isNotEmpty) ?: return 0L
        return parseIsoDateMillis(date)
    }

    private fun parsePeriodMillis(startDateIso: String, endDateIso: String): Pair<Long, Long>? {
        val startMillis = parseIsoDateMillis(startDateIso) ?: return null
        val endMillis = parseIsoDateMillis(endDateIso) ?: return null
        if (endMillis < startMillis) return null
        return startMillis to endMillis
    }

    private fun parseIsoDateMillis(dateIso: String): Long? {
        return runCatching {
            LocalDate.parse(dateIso.trim())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    private fun parseAmountMinorUnits(amount: String): Long? {
        val cleaned = amount
            .trim()
            .filter { it.isDigit() || it == '.' || it == ',' || it == '-' }

        if (cleaned.isBlank() || cleaned.contains('-')) return null

        val normalized = normalizeAmount(cleaned) ?: return null
        val decimalAmount = runCatching { BigDecimal(normalized) }.getOrNull() ?: return null
        if (decimalAmount <= BigDecimal.ZERO) return null

        val scale = decimalAmount.stripTrailingZeros().scale().coerceAtLeast(0)
        if (scale > 2) return null

        return runCatching {
            decimalAmount
                .movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
        }.getOrNull()
    }

    private fun normalizeAmount(cleaned: String): String? {
        val lastDot = cleaned.lastIndexOf('.')
        val lastComma = cleaned.lastIndexOf(',')
        val hasDot = lastDot >= 0
        val hasComma = lastComma >= 0

        if (!hasDot && !hasComma) {
            return cleaned.takeIf { it.any(Char::isDigit) }
        }

        if (hasDot && hasComma) {
            val decimalSeparator = if (lastDot > lastComma) '.' else ','
            val groupingSeparator = if (decimalSeparator == '.') ',' else '.'
            return cleaned
                .replace(groupingSeparator.toString(), "")
                .replace(decimalSeparator, '.')
                .takeIf(::hasValidDecimalShape)
        }

        val separator = if (hasDot) '.' else ','
        val parts = cleaned.split(separator)
        if (parts.size > 2) {
            return parts
                .takeIf { groups -> groups.first().isNotEmpty() && groups.drop(1).all { it.length == 3 } }
                ?.joinToString(separator = "")
        }

        val whole = parts.getOrElse(0) { "" }
        val fraction = parts.getOrElse(1) { "" }
        return when (fraction.length) {
            1, 2 -> "$whole.$fraction".takeIf(::hasValidDecimalShape)
            3 -> "$whole$fraction".takeIf { it.any(Char::isDigit) }
            else -> null
        }
    }

    private fun hasValidDecimalShape(value: String): Boolean {
        val parts = value.split('.')
        return parts.size <= 2 &&
            parts.any { it.isNotEmpty() } &&
            parts.all { part -> part.all(Char::isDigit) }
    }
}

/**
 * Categories returned for agent-facing category discovery.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class CategoryListResponse(
    /** Result status: success or failed. */
    val status: String,
    /** Human-readable category list summary. */
    val message: String,
    /** Machine-readable JSON array string with id, name, kind, icon, color, isArchived, and isInUse fields. */
    val categoriesJson: String
) {
    companion object {
        fun failed(message: String): CategoryListResponse = CategoryListResponse(
            status = STATUS_FAILED,
            message = message,
            categoriesJson = "[]"
        )

        const val STATUS_SUCCESS = "success"
        const val STATUS_FAILED = "failed"
    }
}

/**
 * Result returned after adding or deleting a Spesify category.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class CategoryMutationResponse(
    /** Result status: success, needs_confirmation, or failed. */
    val status: String,
    /** Category ID when relevant. */
    val categoryId: String? = null,
    /** Human-readable message for the agent or assistant. */
    val message: String? = null,
    /** True when the agent should ask the user for more information before retrying. */
    val needsConfirmation: Boolean = false
) {
    companion object {
        fun needsConfirmation(message: String): CategoryMutationResponse = CategoryMutationResponse(
            status = STATUS_NEEDS_CONFIRMATION,
            message = message,
            needsConfirmation = true
        )

        const val STATUS_SUCCESS = "success"
        const val STATUS_NEEDS_CONFIRMATION = "needs_confirmation"
        const val STATUS_FAILED = "failed"
    }
}

/**
 * Result returned after trying to create a Spesify transaction.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AddTransactionResponse(
    /** Result status: created, needs_confirmation, or failed. */
    val status: String,
    /** Created transaction ID when status is created. */
    val transactionId: String? = null,
    /** Human-readable message for the agent or assistant. */
    val message: String? = null,
    /** True when the agent should ask the user for more information before retrying. */
    val needsConfirmation: Boolean = false
) {
    companion object {
        fun needsConfirmation(message: String): AddTransactionResponse = AddTransactionResponse(
            status = STATUS_NEEDS_CONFIRMATION,
            message = message,
            needsConfirmation = true
        )

        const val STATUS_CREATED = "created"
        const val STATUS_NEEDS_CONFIRMATION = "needs_confirmation"
        const val STATUS_FAILED = "failed"
    }
}

/**
 * Result returned for financial total and balance queries.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class FinancialAmountResponse(
    /** Result status: success or failed. */
    val status: String,
    /** Amount in Spesify minor currency units. */
    val amountMinorUnits: Long,
    /** Localized display value for the user, such as "$55.56" or "€55.56". */
    val displayAmount: String,
    /** Human-readable message for the agent or assistant. */
    val message: String? = null
) {
    companion object {
        fun failed(message: String): FinancialAmountResponse = FinancialAmountResponse(
            status = STATUS_FAILED,
            amountMinorUnits = 0L,
            displayAmount = formatMinorUnits(0L),
            message = message
        )

        const val STATUS_SUCCESS = "success"
        const val STATUS_FAILED = "failed"
    }
}

private fun CategoryListResult.toCategoryListResponse(): CategoryListResponse {
    return CategoryListResponse(
        status = status,
        message = message,
        categoriesJson = categories.toCategoryJson()
    )
}

private fun List<it.danielebufarini.spesify.data.AgentCategory>.toCategoryJson(): String {
    return joinToString(prefix = "[", separator = ",", postfix = "]") { category ->
        "{" +
            "\"id\":\"${category.id.escapeJson()}\"," +
            "\"name\":\"${category.name.escapeJson()}\"," +
            "\"kind\":\"${category.kind.toJsonKind()}\"," +
            "\"icon\":\"${category.icon.escapeJson()}\"," +
            "\"color\":\"${category.color.escapeJson()}\"," +
            "\"isArchived\":${category.isArchived}," +
            "\"isInUse\":${category.isInUse}" +
            "}"
    }
}

private fun TransactionKind.toJsonKind(): String {
    return when (this) {
        TransactionKind.Expense -> "expense"
        TransactionKind.Income -> "income"
    }
}

private fun String.escapeJson(): String {
    return buildString {
        for (char in this@escapeJson) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}

private fun CategoryCommandResult.toCategoryMutationResponse(): CategoryMutationResponse {
    return when (this) {
        is CategoryCommandResult.Success -> CategoryMutationResponse(
            status = CategoryMutationResponse.STATUS_SUCCESS,
            categoryId = categoryId,
            message = message,
            needsConfirmation = false
        )
        is CategoryCommandResult.NeedsConfirmation -> CategoryMutationResponse(
            status = CategoryMutationResponse.STATUS_NEEDS_CONFIRMATION,
            message = message,
            needsConfirmation = true
        )
        is CategoryCommandResult.Failed -> CategoryMutationResponse(
            status = CategoryMutationResponse.STATUS_FAILED,
            message = message,
            needsConfirmation = false
        )
    }
}

private fun AddTransactionResult.toAppFunctionResponse(): AddTransactionResponse {
    return when (this) {
        is AddTransactionResult.Created -> AddTransactionResponse(
            status = AddTransactionResponse.STATUS_CREATED,
            transactionId = transactionId,
            message = "Transaction added.",
            needsConfirmation = false
        )
        is AddTransactionResult.NeedsConfirmation -> AddTransactionResponse(
            status = AddTransactionResponse.STATUS_NEEDS_CONFIRMATION,
            message = message,
            needsConfirmation = true
        )
        is AddTransactionResult.Failed -> AddTransactionResponse(
            status = AddTransactionResponse.STATUS_FAILED,
            message = message,
            needsConfirmation = false
        )
    }
}

private fun FinancialQueryResult.toFinancialAmountResponse(
    successMessagePrefix: String
): FinancialAmountResponse {
    if (!isSuccess) {
        return FinancialAmountResponse.failed(message ?: "Unable to read the requested amount.")
    }
    val displayAmount = formatMinorUnits(amount)
    return FinancialAmountResponse(
        status = FinancialAmountResponse.STATUS_SUCCESS,
        amountMinorUnits = amount,
        displayAmount = displayAmount,
        message = "$successMessagePrefix: $displayAmount."
    )
}

private fun formatMinorUnits(amountMinorUnits: Long): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
    val amount = BigDecimal.valueOf(amountMinorUnits).movePointLeft(2)
    return formatter.format(amount)
}
