package it.homebudget.app.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters

@Database(
    entities = [
        Category::class,
        Expense::class,
        Income::class
    ],
    version = 2,
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
