package it.homebudget.app.data
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.database.Income
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

private const val BACKUP_FORMAT = "homebudget_backup"
private const val BACKUP_VERSION = 3
const val BACKUP_FILE_NAME = "homebudget-backup.json"
const val CLOUD_BACKUP_DIRECTORY_NAME = "Data"

private val budgetBackupJson = Json {
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = false
}

data class BudgetBackupFile(
    val fileName: String,
    val content: String
)

data class BudgetBackupCounters(
    val categoriesCount: Int,
    val expensesCount: Int,
    val incomesCount: Int
)

@Serializable
private data class BudgetBackupSnapshot(
    val format: String,
    val version: Int,
    val createdAtEpochMillis: Long,
    val categories: List<BudgetBackupCategory>,
    val expenses: List<BudgetBackupExpense>,
    val incomes: List<BudgetBackupIncome>
)

@Serializable
private data class BudgetBackupCategory(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val categoryType: String,
    val isArchived: Boolean,
    val sortOrder: Long
)

@Serializable
private data class BudgetBackupExpense(
    val id: String,
    val amount: String,
    val date: Long,
    val categoryId: String,
    val description: String? = null,
    val isShared: Boolean,
    val recurringSeriesId: String? = null
)

@Serializable
private data class BudgetBackupIncome(
    val id: String,
    val amount: String,
    val date: Long,
    val categoryId: String? = null,
    val description: String? = null,
    val recurringSeriesId: String? = null
)

suspend fun exportBudgetBackup(repository: ExpenseRepository): BudgetBackupFile {
    val categories = repository.getAllCategoriesSnapshot()
    val expenses = repository.getAllExpensesSnapshot()
    val incomes = repository.getAllIncomesSnapshot()

    return withContext(Dispatchers.Default) {
        val snapshot = BudgetBackupSnapshot(
            format = BACKUP_FORMAT,
            version = BACKUP_VERSION,
            createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
            categories = categories.map(Category::toBackupModel),
            expenses = expenses.map(Expense::toBackupModel),
            incomes = incomes.map(Income::toBackupModel)
        )

        BudgetBackupFile(
            fileName = BACKUP_FILE_NAME,
            content = budgetBackupJson.encodeToString(BudgetBackupSnapshot.serializer(), snapshot)
        )
    }
}

suspend fun parseBudgetBackup(jsonText: String): BudgetBackupCounters {
    return withContext(Dispatchers.Default) {
        val snapshot = decodeBudgetBackupSnapshot(jsonText)
        BudgetBackupCounters(
            categoriesCount = snapshot.categories.size,
            expensesCount = snapshot.expenses.size,
            incomesCount = snapshot.incomes.size
        )
    }
}

suspend fun restoreBudgetBackup(
    repository: ExpenseRepository,
    jsonText: String
): BudgetBackupCounters {
    val snapshot = withContext(Dispatchers.Default) {
        decodeBudgetBackupSnapshot(jsonText)
    }

    repository.replaceAllData(
        categories = snapshot.categories.map { category ->
            RestoredCategory(
                id = category.id,
                name = category.name,
                icon = category.icon,
                color = category.color,
                categoryType = category.categoryType,
                isArchived = category.isArchived,
                sortOrder = category.sortOrder
            )
        },
        expenses = snapshot.expenses.map { expense ->
            PendingExpense(
                id = expense.id,
                amount = parseSerializedAmount(expense.amount)
                    ?: error("Backup expense ${expense.id} amount is out of Long range."),
                date = expense.date,
                categoryId = expense.categoryId,
                description = expense.description,
                isShared = expense.isShared,
                recurringSeriesId = expense.recurringSeriesId
            )
        },
        incomes = snapshot.incomes.map { income ->
            PendingIncome(
                id = income.id,
                amount = parseSerializedAmount(income.amount)
                    ?: error("Backup income ${income.id} amount is out of Long range."),
                date = income.date,
                categoryId = income.categoryId,
                description = income.description,
                recurringSeriesId = income.recurringSeriesId
            )
        }
    )

    return BudgetBackupCounters(
        categoriesCount = snapshot.categories.size,
        expensesCount = snapshot.expenses.size,
        incomesCount = snapshot.incomes.size
    )
}

private fun decodeBudgetBackupSnapshot(jsonText: String): BudgetBackupSnapshot {
    require(jsonText.isNotBlank()) { "Backup file is empty." }

    val snapshot = budgetBackupJson.decodeFromString(BudgetBackupSnapshot.serializer(), jsonText)
    require(snapshot.format == BACKUP_FORMAT) { "Unsupported backup format." }
    require(snapshot.version == BACKUP_VERSION) { "Unsupported backup version." }

    ensureUniqueIds(snapshot.categories, "category") { it.id }
    ensureUniqueIds(snapshot.expenses, "expense") { it.id }
    ensureUniqueIds(snapshot.incomes, "income") { it.id }

    val categoryIds = snapshot.categories.mapTo(linkedSetOf(), BudgetBackupCategory::id)
    snapshot.expenses.forEach { expense ->
        require(expense.categoryId in categoryIds) {
            "Expense ${expense.id} references unknown category ${expense.categoryId}."
        }
    }
    snapshot.incomes.forEach { income ->
        require(income.categoryId == null || income.categoryId in categoryIds) {
            "Income ${income.id} references unknown category ${income.categoryId}."
        }
    }

    return snapshot
}

private fun <T> ensureUniqueIds(
    items: List<T>,
    itemType: String,
    idSelector: (T) -> String
) {
    val duplicates = items.groupingBy(idSelector)
        .eachCount()
        .filterValues { it > 1 }
        .keys
    require(duplicates.isEmpty()) { "Duplicate $itemType ids: ${duplicates.joinToString(", ")}." }
}

private fun Category.toBackupModel() = BudgetBackupCategory(
    id = id,
    name = name,
    icon = icon,
    color = color,
    categoryType = categoryType,
    isArchived = isArchived == 1L,
    sortOrder = sortOrder
)

private fun Expense.toBackupModel() = BudgetBackupExpense(
    id = id,
    amount = amount.toString(),
    date = date,
    categoryId = categoryId,
    description = description,
    isShared = isShared == 1L,
    recurringSeriesId = recurringSeriesId
)

private fun Income.toBackupModel() = BudgetBackupIncome(
    id = id,
    amount = amount.toString(),
    date = date,
    categoryId = categoryId,
    description = description,
    recurringSeriesId = recurringSeriesId
)
