import Foundation

enum ICloudBackupStoreError: LocalizedError {
    case unavailable
    case notFound
    case unreadable

    var errorDescription: String? {
        switch self {
        case .unavailable:
            appLocalized("iCloud backup is unavailable on this device or account.")
        case .notFound:
            appLocalized("No cloud backup file was found.")
        case .unreadable:
            appLocalized("Unable to read the backup file")
        }
    }
}

enum ICloudBackupStore {
    private static let backupFileName = "homebudget-backup.json"
    private static let backupDirectoryName = "Data"

    static func writeBackupSync(text: String) throws {
        let backupURL = try backupFileURLSync()
        let data = Data(text.utf8)
        try data.write(to: backupURL, options: .atomic)
    }

    static func writeBackup(text: String) async throws {
        try await runOffMain {
            try writeBackupSync(text: text)
        }
    }

    static func readBackupSync() throws -> String {
        let backupURL = try backupFileURLSync()

        guard FileManager.default.fileExists(atPath: backupURL.path) else {
            throw ICloudBackupStoreError.notFound
        }

        let fileManager = FileManager.default
        if fileManager.isUbiquitousItem(at: backupURL) {
            try? fileManager.startDownloadingUbiquitousItem(at: backupURL)
            try waitUntilDownloadedSync(backupURL)
        }

        let data = try Data(contentsOf: backupURL)
        guard let text = String(data: data, encoding: .utf8) else {
            throw ICloudBackupStoreError.unreadable
        }
        return text
    }

    static func readBackup() async throws -> String {
        try await runOffMain {
            try readBackupSync()
        }
    }

    private static func backupFileURL() async throws -> URL {
        return try await runOffMain {
            try backupFileURLSync()
        }
    }

    private static func backupFileURLSync() throws -> URL {
        let fileManager = FileManager.default

        guard fileManager.ubiquityIdentityToken != nil else {
            throw ICloudBackupStoreError.unavailable
        }

        guard let containerURL = fileManager.url(forUbiquityContainerIdentifier: nil) else {
            throw ICloudBackupStoreError.unavailable
        }

        let backupDirectory = containerURL
            .appendingPathComponent(backupDirectoryName, isDirectory: true)
        try fileManager.createDirectory(
            at: backupDirectory,
            withIntermediateDirectories: true
        )

        return backupDirectory.appendingPathComponent(backupFileName, isDirectory: false)
    }

    private static func waitUntilDownloaded(_ url: URL) async throws {
        let timeout = Date().addingTimeInterval(10)

        while Date() < timeout {
            let values = try url.resourceValues(forKeys: [.ubiquitousItemDownloadingStatusKey])

            if values.ubiquitousItemDownloadingStatus == URLUbiquitousItemDownloadingStatus.current {
                return
            }

            try await Task.sleep(for: .milliseconds(250))
        }
    }

    private static func waitUntilDownloadedSync(_ url: URL) throws {
        let timeout = Date().addingTimeInterval(10)

        while Date() < timeout {
            let values = try url.resourceValues(forKeys: [.ubiquitousItemDownloadingStatusKey])

            if values.ubiquitousItemDownloadingStatus == URLUbiquitousItemDownloadingStatus.current {
                return
            }

            Thread.sleep(forTimeInterval: 0.25)
        }
    }

    private static func runOffMain<T>(
        _ operation: @escaping @Sendable () throws -> T
    ) async throws -> T {
        try await withCheckedThrowingContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                do {
                    continuation.resume(returning: try operation())
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }
}
