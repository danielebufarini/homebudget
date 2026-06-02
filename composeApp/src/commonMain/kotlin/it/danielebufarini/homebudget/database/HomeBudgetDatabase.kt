package it.danielebufarini.homebudget.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [
        Category::class,
        Expense::class,
        Income::class,
        ExpenseSearchFts::class,
        IncomeSearchFts::class
    ],
    version = 7,
    exportSchema = true
)
@ConstructedBy(HomeBudgetDatabaseConstructor::class)
abstract class HomeBudgetDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun incomeDao(): IncomeDao
    abstract fun searchIndexDao(): SearchIndexDao
}

@Suppress("KotlinNoActualForExpect")
expect object HomeBudgetDatabaseConstructor : RoomDatabaseConstructor<HomeBudgetDatabase> {
    override fun initialize(): HomeBudgetDatabase
}
