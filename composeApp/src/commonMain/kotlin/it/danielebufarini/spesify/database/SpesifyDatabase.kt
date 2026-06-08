package it.danielebufarini.spesify.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [
        Category::class,
        Expense::class,
        Income::class,
        RecurringTransactionRule::class,
        ExpenseSearchFts::class,
        IncomeSearchFts::class
    ],
    version = 8,
    exportSchema = true
)
@ConstructedBy(SpesifyDatabaseConstructor::class)
abstract class SpesifyDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun incomeDao(): IncomeDao
    abstract fun recurringTransactionRuleDao(): RecurringTransactionRuleDao
    abstract fun searchIndexDao(): SearchIndexDao
}

@Suppress("KotlinNoActualForExpect")
expect object SpesifyDatabaseConstructor : RoomDatabaseConstructor<SpesifyDatabase> {
    override fun initialize(): SpesifyDatabase
}
