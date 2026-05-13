package it.homebudget.app.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [
        Category::class,
        Expense::class,
        Income::class
    ],
    version = 3,
    exportSchema = true
)
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
