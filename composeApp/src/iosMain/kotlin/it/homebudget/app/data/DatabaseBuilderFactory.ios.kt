@file:OptIn(ExperimentalForeignApi::class)

package it.homebudget.app.data

import androidx.room.Room
import androidx.room.RoomDatabase
import it.homebudget.app.database.HomeBudgetDatabase
import it.homebudget.app.database.addHomeBudgetMigrations
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual class DatabaseBuilderFactory {
    actual fun createBuilder(): RoomDatabase.Builder<HomeBudgetDatabase> {
        return Room.databaseBuilder<HomeBudgetDatabase>(
            name = databasePath()
        ).addHomeBudgetMigrations()
    }

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

    private fun databasePath(): String {
        val documentsPath = documentDirectory()
        val crossPlatformDatabaseDirectory = "$documentsPath/app/databases"
        ensureDirectory(crossPlatformDatabaseDirectory)

        val preferredDatabasePath = "$crossPlatformDatabaseDirectory/homebudget-room.db"
        migrateLegacyDatabaseIfNeeded(
            legacyDatabasePath = "$documentsPath/homebudget-room.db",
            preferredDatabasePath = preferredDatabasePath
        )
        return preferredDatabasePath
    }

    private fun ensureDirectory(path: String) {
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
    }

    private fun migrateLegacyDatabaseIfNeeded(
        legacyDatabasePath: String,
        preferredDatabasePath: String
    ) {
        val fileManager = NSFileManager.defaultManager
        if (fileManager.fileExistsAtPath(preferredDatabasePath) || !fileManager.fileExistsAtPath(legacyDatabasePath)) {
            return
        }

        moveIfPresent(legacyDatabasePath, preferredDatabasePath)
        moveIfPresent("$legacyDatabasePath-wal", "$preferredDatabasePath-wal")
        moveIfPresent("$legacyDatabasePath-shm", "$preferredDatabasePath-shm")
    }

    private fun moveIfPresent(sourcePath: String, destinationPath: String) {
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(sourcePath) || fileManager.fileExistsAtPath(destinationPath)) {
            return
        }
        fileManager.moveItemAtPath(
            srcPath = sourcePath,
            toPath = destinationPath,
            error = null
        )
    }
}
