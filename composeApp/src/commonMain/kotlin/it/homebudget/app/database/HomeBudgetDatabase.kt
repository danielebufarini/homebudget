package it.homebudget.app.database

import androidx.room.*

@Database(
    entities = [
        Category::class,
        Expense::class,
        Income::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(BigIntegerTypeConverters::class)
@ConstructedBy(HomeBudgetDatabaseConstructor::class)
abstract class HomeBudgetDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun incomeDao(): IncomeDao
}

@Suppress("KotlinNoActualForExpect")
expect object HomeBudgetDatabaseConstructor : RoomDatabaseConstructor<HomeBudgetDatabase> {
    override fun initialize(): HomeBudgetDatabase
}
