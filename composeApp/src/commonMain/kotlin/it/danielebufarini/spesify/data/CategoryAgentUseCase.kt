package it.danielebufarini.spesify.data

import it.danielebufarini.spesify.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.spesify.database.CATEGORY_TYPE_INCOME
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.database.DEFAULT_CATEGORY_COLOR

private const val DEFAULT_EXPENSE_CATEGORY_ICON = "category"
private const val DEFAULT_INCOME_CATEGORY_ICON = "attach_money"

data class AgentCategory(
    val id: String,
    val name: String,
    val kind: TransactionKind,
    val icon: String,
    val color: String,
    val isArchived: Boolean,
    val isInUse: Boolean
)

data class CategoryListResult(
    val status: String,
    val categories: List<AgentCategory>,
    val message: String
) {
    val isSuccess: Boolean get() = status == STATUS_SUCCESS

    companion object {
        const val STATUS_SUCCESS = "success"
        const val STATUS_FAILED = "failed"
    }
}

sealed interface CategoryCommandResult {
    val message: String?

    data class Success(
        val categoryId: String?,
        override val message: String
    ) : CategoryCommandResult

    data class NeedsConfirmation(
        override val message: String
    ) : CategoryCommandResult

    data class Failed(
        override val message: String
    ) : CategoryCommandResult
}

class CategoryAgentUseCase internal constructor(
    private val categoryRepository: CategoryManagementRepository
) {
    suspend fun listCategories(
        kind: TransactionKind? = null,
        includeArchived: Boolean = false
    ): CategoryListResult {
        return runCatching {
            categoryRepository.seedStarterCategoriesIfEmpty()
            val categories = categoryRepository.getAllCategoriesSnapshot()
                .filter { category ->
                    kind?.let { category.categoryType == it.toCategoryType() } ?: true
                }
                .filter { category -> includeArchived || category.isArchived != 1L }
                .sortedWith(compareBy<Category> { it.categoryType }.thenBy { it.sortOrder }.thenBy { it.name })
                .map { category ->
                    category.toAgentCategory(
                        isInUse = categoryRepository.isCategoryInUse(category.id)
                    )
                }
            CategoryListResult(
                status = CategoryListResult.STATUS_SUCCESS,
                categories = categories,
                message = categories.toCategoryListMessage(kind)
            )
        }.getOrElse { error ->
            CategoryListResult(
                status = CategoryListResult.STATUS_FAILED,
                categories = emptyList(),
                message = error.message ?: "Unable to list categories."
            )
        }
    }

    suspend fun addCategory(
        kind: TransactionKind,
        name: String,
        iconKey: String? = null,
        color: String? = null
    ): CategoryCommandResult {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return CategoryCommandResult.NeedsConfirmation("Please provide a category name.")
        }

        return runCatching {
            categoryRepository.seedStarterCategoriesIfEmpty()
            val existing = categoryRepository.getAllCategoriesSnapshot()
            val duplicate = existing.firstOrNull { category ->
                category.categoryType == kind.toCategoryType() &&
                    category.name.matchesCategoryQuery(trimmedName)
            }
            if (duplicate != null) {
                return@runCatching CategoryCommandResult.NeedsConfirmation(
                    "Category '$trimmedName' already exists as ${duplicate.name}."
                )
            }

            val id = IdGenerator.newId("agent-${kind.toExternalValue()}-category")
            categoryRepository.insertCategory(
                id = id,
                name = trimmedName,
                icon = iconKey?.trim()?.takeIf(String::isNotEmpty) ?: kind.defaultAgentCategoryIcon(),
                color = color?.trim()?.takeIf(String::isNotEmpty) ?: DEFAULT_CATEGORY_COLOR,
                categoryType = kind.toCategoryType(),
                isArchived = false,
                sortOrder = null
            )
            CategoryCommandResult.Success(
                categoryId = id,
                message = "Category '$trimmedName' added."
            )
        }.getOrElse { error ->
            CategoryCommandResult.Failed(error.message ?: "Unable to add the category.")
        }
    }

    suspend fun deleteCategory(
        kind: TransactionKind?,
        categoryNameOrId: String,
        moveToCategoryNameOrId: String
    ): CategoryCommandResult {
        val sourceQuery = categoryNameOrId.trim()
        val targetQuery = moveToCategoryNameOrId.trim()
        if (sourceQuery.isBlank()) {
            return CategoryCommandResult.NeedsConfirmation("Please specify the category to delete.")
        }
        if (targetQuery.isBlank()) {
            return CategoryCommandResult.NeedsConfirmation(
                "Please specify the category that should receive existing transactions."
            )
        }

        return runCatching {
            categoryRepository.seedStarterCategoriesIfEmpty()
            val categories = categoryRepository.getAllCategoriesSnapshot()
            val sourceResolution = resolveCategory(
                categories = categories,
                query = sourceQuery,
                kind = kind,
                role = "category to delete"
            )
            if (sourceResolution is AgentCategoryResolution.NeedsConfirmation) {
                return@runCatching CategoryCommandResult.NeedsConfirmation(sourceResolution.message)
            }
            val source = (sourceResolution as AgentCategoryResolution.Resolved).category
            val sourceKind = source.toTransactionKind()
                ?: return@runCatching CategoryCommandResult.Failed("Category '${source.name}' has an unsupported type.")

            val targetResolution = resolveCategory(
                categories = categories,
                query = targetQuery,
                kind = sourceKind,
                role = "replacement category"
            )
            if (targetResolution is AgentCategoryResolution.NeedsConfirmation) {
                return@runCatching CategoryCommandResult.NeedsConfirmation(targetResolution.message)
            }
            val target = (targetResolution as AgentCategoryResolution.Resolved).category

            if (source.id == target.id) {
                return@runCatching CategoryCommandResult.NeedsConfirmation(
                    "Choose a different category to receive transactions before deleting '${source.name}'."
                )
            }

            categoryRepository.reassignCategoryTransactions(
                sourceCategoryId = source.id,
                targetCategoryId = target.id
            )
            CategoryCommandResult.Success(
                categoryId = source.id,
                message = "Category '${source.name}' deleted and its transactions moved to '${target.name}'."
            )
        }.getOrElse { error ->
            CategoryCommandResult.Failed(error.message ?: "Unable to delete the category.")
        }
    }

    private fun resolveCategory(
        categories: List<Category>,
        query: String,
        kind: TransactionKind?,
        role: String
    ): AgentCategoryResolution {
        val activeCategories = categories.filter { it.isArchived != 1L }
        val scopedCategories = activeCategories.filter { category ->
            kind?.let { category.categoryType == it.toCategoryType() } ?: true
        }
        val matches = scopedCategories.filter { category ->
            category.id == query || category.name.matchesCategoryQuery(query)
        }
        return when (matches.size) {
            1 -> AgentCategoryResolution.Resolved(matches.first())
            in 2..Int.MAX_VALUE -> AgentCategoryResolution.NeedsConfirmation(
                "The $role '$query' is ambiguous. Use the category ID."
            )
            else -> {
                val wrongTypeMatches = activeCategories.filter { category ->
                    category.id == query || category.name.matchesCategoryQuery(query)
                }
                if (wrongTypeMatches.isNotEmpty() && kind != null) {
                    AgentCategoryResolution.NeedsConfirmation(
                        "Category '$query' exists, but it is not a ${kind.toExternalValue()} category."
                    )
                } else {
                    AgentCategoryResolution.NeedsConfirmation("Category '$query' was not found.")
                }
            }
        }
    }

    private sealed interface AgentCategoryResolution {
        data class Resolved(val category: Category) : AgentCategoryResolution
        data class NeedsConfirmation(val message: String) : AgentCategoryResolution
    }
}

internal fun TransactionKind.toCategoryType(): String {
    return when (this) {
        TransactionKind.Expense -> CATEGORY_TYPE_EXPENSE
        TransactionKind.Income -> CATEGORY_TYPE_INCOME
    }
}

internal fun TransactionKind.toExternalValue(): String {
    return when (this) {
        TransactionKind.Expense -> "expense"
        TransactionKind.Income -> "income"
    }
}

private fun TransactionKind.defaultAgentCategoryIcon(): String {
    return when (this) {
        TransactionKind.Expense -> DEFAULT_EXPENSE_CATEGORY_ICON
        TransactionKind.Income -> DEFAULT_INCOME_CATEGORY_ICON
    }
}

private fun Category.toTransactionKind(): TransactionKind? {
    return when (categoryType) {
        CATEGORY_TYPE_EXPENSE -> TransactionKind.Expense
        CATEGORY_TYPE_INCOME -> TransactionKind.Income
        else -> null
    }
}

private fun Category.toAgentCategory(isInUse: Boolean): AgentCategory {
    return AgentCategory(
        id = id,
        name = name,
        kind = toTransactionKind() ?: TransactionKind.Expense,
        icon = icon,
        color = color,
        isArchived = isArchived == 1L,
        isInUse = isInUse
    )
}

internal fun Category.matchesCategoryQuery(query: String): Boolean {
    return id == query || name.matchesCategoryQuery(query)
}

internal fun String.matchesCategoryQuery(query: String): Boolean {
    val normalizedName = normalizedAgentCategoryLookupKey()
    val normalizedQuery = query.normalizedAgentCategoryLookupKey()
    if (normalizedName == normalizedQuery) return true
    return compactAgentCategoryLookupKey() == query.compactAgentCategoryLookupKey()
}

internal fun String.normalizedAgentCategoryLookupKey(): String {
    return trim()
        .lowercase()
        .split(Regex("\\s+"))
        .joinToString(" ")
}

private fun String.compactAgentCategoryLookupKey(): String {
    return lowercase().filter(Char::isLetterOrDigit)
}

private fun List<AgentCategory>.toCategoryListMessage(kind: TransactionKind?): String {
    if (isEmpty()) {
        return kind?.let { "No active ${it.toExternalValue()} categories found." }
            ?: "No active categories found."
    }

    kind?.let { requestedKind ->
        return formatCategoryListGroup(
            title = when (requestedKind) {
                TransactionKind.Expense -> "Expense categories"
                TransactionKind.Income -> "Income categories"
            },
            categories = this
        )
    }

    val expenseCategories = filter { it.kind == TransactionKind.Expense }
    val incomeCategories = filter { it.kind == TransactionKind.Income }
    return listOfNotNull(
        expenseCategories.takeIf { it.isNotEmpty() }?.let {
            formatCategoryListGroup(
                title = "Expense categories",
                categories = it
            )
        },
        incomeCategories.takeIf { it.isNotEmpty() }?.let {
            formatCategoryListGroup(
                title = "Income categories",
                categories = it
            )
        }
    ).joinToString(separator = "\n")
}

private fun formatCategoryListGroup(
    title: String,
    categories: List<AgentCategory>
): String {
    return categories.joinToString(
        prefix = "$title (${categories.size}): ",
        separator = "; ",
        postfix = "."
    ) { category ->
        category.name
    }
}
