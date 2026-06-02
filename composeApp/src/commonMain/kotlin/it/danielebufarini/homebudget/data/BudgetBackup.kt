package it.danielebufarini.homebudget.data

import it.danielebufarini.homebudget.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.homebudget.database.Category
import it.danielebufarini.homebudget.database.DEFAULT_CATEGORY_COLOR
import it.danielebufarini.homebudget.database.Expense
import it.danielebufarini.homebudget.database.Income
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

private const val BACKUP_FORMAT = "homebudget_backup"
private const val BACKUP_VERSION = 4
private const val MIN_SUPPORTED_BACKUP_VERSION = 1
const val BACKUP_FILE_NAME = "homebudget-backup.json"
const val CLOUD_BACKUP_DIRECTORY_NAME = "Data"

private val budgetBackupJson = Json {
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

data class BudgetBackupFile(
    val fileName: String,
    val content: String
)

data class BudgetBackupCounters(
    val categoriesCount: Int,
    val expensesCount: Int,
    val incomesCount: Int,
    val createdAtEpochMillis: Long? = null,
    val version: Int? = null
)

@Serializable
private data class BudgetBackupSnapshot(
    val format: String,
    val version: Int,
    val createdAtEpochMillis: Long = 0L,
    val checksumSha256: String? = null,
    val categories: List<BudgetBackupCategory>,
    val expenses: List<BudgetBackupExpense>,
    val incomes: List<BudgetBackupIncome>
)

@Serializable
private data class BudgetBackupChecksumPayload(
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
    val color: String = DEFAULT_CATEGORY_COLOR,
    val categoryType: String = CATEGORY_TYPE_EXPENSE,
    val isArchived: Boolean = false,
    val sortOrder: Long = 0L
)

@Serializable
private data class BudgetBackupExpense(
    val id: String,
    val amount: String,
    val date: Long,
    val categoryId: String,
    val description: String? = null,
    val isShared: Boolean = false,
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
        val snapshot = buildBudgetBackupSnapshot(
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
        decodeBudgetBackupSnapshot(jsonText).toCounters()
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
        categories = snapshot.categories.mapIndexed { index, category ->
            RestoredCategory(
                id = category.id,
                name = category.name,
                icon = category.icon,
                color = category.color,
                categoryType = category.categoryType,
                isArchived = category.isArchived,
                sortOrder = if (snapshot.version < 3) index.toLong() else category.sortOrder
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

    return snapshot.toCounters()
}

private fun buildBudgetBackupSnapshot(
    createdAtEpochMillis: Long,
    categories: List<BudgetBackupCategory>,
    expenses: List<BudgetBackupExpense>,
    incomes: List<BudgetBackupIncome>
): BudgetBackupSnapshot {
    val checksumPayload = BudgetBackupChecksumPayload(
        format = BACKUP_FORMAT,
        version = BACKUP_VERSION,
        createdAtEpochMillis = createdAtEpochMillis,
        categories = categories,
        expenses = expenses,
        incomes = incomes
    )
    val checksumSource = budgetBackupJson.encodeToString(
        BudgetBackupChecksumPayload.serializer(),
        checksumPayload
    )

    return BudgetBackupSnapshot(
        format = BACKUP_FORMAT,
        version = BACKUP_VERSION,
        createdAtEpochMillis = createdAtEpochMillis,
        checksumSha256 = sha256Hex(checksumSource.encodeToByteArray()),
        categories = categories,
        expenses = expenses,
        incomes = incomes
    )
}

private fun decodeBudgetBackupSnapshot(jsonText: String): BudgetBackupSnapshot {
    require(jsonText.isNotBlank()) { "Backup file is empty." }

    val snapshot = budgetBackupJson.decodeFromString(BudgetBackupSnapshot.serializer(), jsonText)
    require(snapshot.format == BACKUP_FORMAT) { "Unsupported backup format." }
    require(snapshot.version in MIN_SUPPORTED_BACKUP_VERSION..BACKUP_VERSION) {
        "Unsupported backup version ${snapshot.version}."
    }
    verifyChecksumIfPresent(snapshot)

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

private fun verifyChecksumIfPresent(snapshot: BudgetBackupSnapshot) {
    val expectedChecksum = snapshot.checksumSha256 ?: return
    val checksumPayload = BudgetBackupChecksumPayload(
        format = snapshot.format,
        version = snapshot.version,
        createdAtEpochMillis = snapshot.createdAtEpochMillis,
        categories = snapshot.categories,
        expenses = snapshot.expenses,
        incomes = snapshot.incomes
    )
    val checksumSource = budgetBackupJson.encodeToString(
        BudgetBackupChecksumPayload.serializer(),
        checksumPayload
    )
    val actualChecksum = sha256Hex(checksumSource.encodeToByteArray())
    require(actualChecksum.equals(expectedChecksum, ignoreCase = true)) {
        "Backup integrity check failed."
    }
}

private fun BudgetBackupSnapshot.toCounters() = BudgetBackupCounters(
    categoriesCount = categories.size,
    expensesCount = expenses.size,
    incomesCount = incomes.size,
    createdAtEpochMillis = createdAtEpochMillis.takeIf { it > 0L },
    version = version
)

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

private fun sha256Hex(input: ByteArray): String = Sha256.digest(input).joinToString("") { byte ->
    (byte.toInt() and 0xff).toString(16).padStart(2, '0')
}
