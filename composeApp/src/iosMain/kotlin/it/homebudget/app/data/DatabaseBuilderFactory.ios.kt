package it.homebudget.app.data

import androidx.room.Room
import androidx.room.RoomDatabase
import it.homebudget.app.database.HomeBudgetDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual class DatabaseBuilderFactory {
    @OptIn(ExperimentalForeignApi::class)
    actual fun createBuilder(): RoomDatabase.Builder<HomeBudgetDatabase> {
        return Room.databaseBuilder<HomeBudgetDatabase>(
            name = documentDirectory() + "/homebudget-room.db"
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun documentDirectory(): String {
        val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null
        )
        return requireNotNull(documentDirectory?.path)
    }
}
