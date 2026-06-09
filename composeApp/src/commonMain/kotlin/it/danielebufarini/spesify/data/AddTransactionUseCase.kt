package it.danielebufarini.spesify.data

import it.danielebufarini.spesify.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.spesify.database.CATEGORY_TYPE_INCOME
import it.danielebufarini.spesify.database.Category
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

enum class TransactionKind {
    Expense,
    Income;

    companion object {
        fun fromExternalValue(value: String): TransactionKind? {
            return when (value.trim().lowercase()) {
                "expense", "spesa" -> Expense
                "income", "entrata" -> Income
                else -> null
            }
        }
    }
}

enum class TransactionCreationSource {
    Manual,
    IosAppIntent,
    AndroidAppFunction
}

data class AddTransactionCommand(
    val kind: TransactionKind,
    val amount: Long,
    val categoryName: String? = null,
    val description: String? = null,
    val dateMillis: Long? = null,
    val source: TransactionCreationSource = TransactionCreationSource.Manual
)

sealed interface AddTransactionResult {
    val message: String?

    data class Created(
        val transactionId: String,
        val kind: TransactionKind,
        val amount: Long,
        val dateMillis: Long,
        val categoryId: String?,
        override val message: String? = null
    ) : AddTransactionResult

    data class NeedsConfirmation(
        override val message: String
    ) : AddTransactionResult

    data class Failed(
        override val message: String
    ) : AddTransactionResult
}

class AddTransactionUseCase internal constructor(
    private val categoryRepository: CategoryManagementRepository,
    private val transactionWriteRepository: TransactionWriteRepository
) {
    suspend fun execute(command: AddTransactionCommand): AddTransactionResult {
        if (command.amount <= 0L) {
            return AddTransactionResult.NeedsConfirmation("Please provide a positive amount.")
        }

        val dateMillis = normalizeTransactionDate(command.dateMillis)
        val description = command.description?.trim()?.takeIf(String::isNotEmpty)

        return runCatching {
            val categories = loadCategories()
            when (command.kind) {
                TransactionKind.Expense -> createExpense(
                    command = command,
                    dateMillis = dateMillis,
                    description = description,
                    categories = categories
                )
                TransactionKind.Income -> createIncome(
                    command = command,
                    dateMillis = dateMillis,
                    description = description,
                    categories = categories
                )
            }
        }.getOrElse { error ->
            AddTransactionResult.Failed(error.message ?: "Unable to add the transaction.")
        }
    }

    private suspend fun loadCategories(): List<Category> {
        categoryRepository.seedStarterCategoriesIfEmpty()
        return categoryRepository.getAllCategoriesSnapshot()
    }

    private suspend fun createExpense(
        command: AddTransactionCommand,
        dateMillis: Long,
        description: String?,
        categories: List<Category>
    ): AddTransactionResult {
        val category = resolveCategory(
            rawCategory = command.categoryName,
            categories = categories,
            categoryType = CATEGORY_TYPE_EXPENSE,
            missingMessage = "Please specify an expense category."
        ) ?: return AddTransactionResult.NeedsConfirmation("Please specify an expense category.")

        if (category is CategoryResolution.NeedsConfirmation) {
            return AddTransactionResult.NeedsConfirmation(category.message)
        }
        val resolvedCategory = (category as CategoryResolution.Resolved).category
        val id = buildAgentTransactionId(command.kind, command.source)
        transactionWriteRepository.insertExpenses(
            listOf(
                PendingExpense(
                    id = id,
                    amount = command.amount,
                    date = dateMillis,
                    categoryId = resolvedCategory.id,
                    description = description,
                    isShared = false,
                    recurringSeriesId = null
                )
            )
        )
        return AddTransactionResult.Created(
            transactionId = id,
            kind = command.kind,
            amount = command.amount,
            dateMillis = dateMillis,
            categoryId = resolvedCategory.id
        )
    }

    private suspend fun createIncome(
        command: AddTransactionCommand,
        dateMillis: Long,
        description: String?,
        categories: List<Category>
    ): AddTransactionResult {
        val categoryResolution = resolveCategory(
            rawCategory = command.categoryName,
            categories = categories,
            categoryType = CATEGORY_TYPE_INCOME,
            missingMessage = null
        )
        if (categoryResolution is CategoryResolution.NeedsConfirmation) {
            return AddTransactionResult.NeedsConfirmation(categoryResolution.message)
        }
        val categoryId = (categoryResolution as? CategoryResolution.Resolved)?.category?.id
        val id = buildAgentTransactionId(command.kind, command.source)
        transactionWriteRepository.insertIncomes(
            listOf(
                PendingIncome(
                    id = id,
                    amount = command.amount,
                    date = dateMillis,
                    description = description,
                    recurringSeriesId = null,
                    categoryId = categoryId
                )
            )
        )
        return AddTransactionResult.Created(
            transactionId = id,
            kind = command.kind,
            amount = command.amount,
            dateMillis = dateMillis,
            categoryId = categoryId
        )
    }

    private fun resolveCategory(
        rawCategory: String?,
        categories: List<Category>,
        categoryType: String,
        missingMessage: String?
    ): CategoryResolution? {
        val categoryQuery = rawCategory?.trim()?.takeIf(String::isNotEmpty)
            ?: return missingMessage?.let(CategoryResolution::NeedsConfirmation)
        val activeCategories = categories.filter { category ->
            category.categoryType == categoryType && category.isArchived != 1L
        }

        val idMatches = activeCategories.filter { it.id == categoryQuery }
        if (idMatches.size == 1) return CategoryResolution.Resolved(idMatches.first())
        if (idMatches.size > 1) {
            return CategoryResolution.NeedsConfirmation("Category '$categoryQuery' is ambiguous.")
        }

        val normalizedQuery = categoryQuery.normalizedCategoryLookupKey()
        val nameMatches = activeCategories.filter { category ->
            category.name.normalizedCategoryLookupKey() == normalizedQuery
        }
        return when (nameMatches.size) {
            0 -> CategoryResolution.NeedsConfirmation("Category '$categoryQuery' was not found.")
            1 -> CategoryResolution.Resolved(nameMatches.first())
            else -> CategoryResolution.NeedsConfirmation("Category '$categoryQuery' is ambiguous.")
        }
    }

    private fun normalizeTransactionDate(dateMillis: Long?): Long {
        val timeZone = TimeZone.currentSystemDefault()
        val instant = dateMillis
            ?.takeIf { it > 0L }
            ?.let(Instant::fromEpochMilliseconds)
            ?: Clock.System.now()
        val localDate = instant.toLocalDateTime(timeZone).date
        return localDate.atStartOfDayIn(timeZone).toEpochMilliseconds()
    }

    private fun buildAgentTransactionId(
        kind: TransactionKind,
        source: TransactionCreationSource
    ): String {
        val sourcePrefix = when (source) {
            TransactionCreationSource.Manual -> "manual"
            TransactionCreationSource.IosAppIntent -> "ios-intent"
            TransactionCreationSource.AndroidAppFunction -> "android-appfunction"
        }
        val kindPrefix = when (kind) {
            TransactionKind.Expense -> "expense"
            TransactionKind.Income -> "income"
        }
        return IdGenerator.newId("$sourcePrefix-$kindPrefix")
    }

    private sealed interface CategoryResolution {
        data class Resolved(val category: Category) : CategoryResolution
        data class NeedsConfirmation(val message: String) : CategoryResolution
    }
}

private fun String.normalizedCategoryLookupKey(): String =
    trim()
        .lowercase()
        .split(Regex("\\s+"))
        .joinToString(" ")
