package it.homebudget.app.data

import com.ionspin.kotlin.bignum.integer.toBigInteger
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.database.Income
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

private const val BACKUP_FORMAT = "homebudget_backup"
private const val BACKUP_VERSION = 1

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

data class BudgetBackupPreview(
    val categoriesCount: Int,
    val expensesCount: Int,
    val incomesCount: Int
)

data class BudgetBackupRestoreResult(
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
    val isCustom: Boolean
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
    val description: String? = null,
    val recurringSeriesId: String? = null
)

suspend fun exportBudgetBackup(repository: ExpenseRepository): BudgetBackupFile {
    val snapshot = BudgetBackupSnapshot(
        format = BACKUP_FORMAT,
        version = BACKUP_VERSION,
        createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
        categories = repository.getAllCategoriesSnapshot().map(Category::toBackupModel),
        expenses = repository.getAllExpensesSnapshot().map(Expense::toBackupModel),
        incomes = repository.getAllIncomesSnapshot().map(Income::toBackupModel)
    )

    return BudgetBackupFile(
        fileName = buildBudgetBackupFileName(snapshot.createdAtEpochMillis),
        content = budgetBackupJson.encodeToString(BudgetBackupSnapshot.serializer(), snapshot)
    )
}

fun parseBudgetBackup(jsonText: String): BudgetBackupPreview {
    val snapshot = decodeBudgetBackupSnapshot(jsonText)
    return BudgetBackupPreview(
        categoriesCount = snapshot.categories.size,
        expensesCount = snapshot.expenses.size,
        incomesCount = snapshot.incomes.size
    )
}

suspend fun restoreBudgetBackup(
    repository: ExpenseRepository,
    jsonText: String
): BudgetBackupRestoreResult {
    val snapshot = decodeBudgetBackupSnapshot(jsonText)

    repository.replaceAllData(
        categories = snapshot.categories.map { category ->
            RestoredCategory(
                id = category.id,
                name = category.name,
                icon = category.icon,
                isCustom = category.isCustom
            )
        },
        expenses = snapshot.expenses.map { expense ->
            PendingExpense(
                id = expense.id,
                amount = expense.amount.toBigInteger(),
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
                amount = income.amount.toBigInteger(),
                date = income.date,
                description = income.description,
                recurringSeriesId = income.recurringSeriesId
            )
        }
    )

    return BudgetBackupRestoreResult(
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

private fun buildBudgetBackupFileName(createdAtEpochMillis: Long): String {
    val date = kotlin.time.Instant.fromEpochMilliseconds(createdAtEpochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return "homebudget-backup-${date.year}-${date.month.ordinal.plus(1).toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}.json"
}

private fun Category.toBackupModel() = BudgetBackupCategory(
    id = id,
    name = name,
    icon = icon,
    isCustom = isCustom == 1L
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
    description = description,
    recurringSeriesId = recurringSeriesId
)
