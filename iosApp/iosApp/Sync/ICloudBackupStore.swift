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

/// The static interface remains available to the existing startup-restore caller.
enum ICloudBackupStore {
    static let archive = ICloudBackupArchive()

    static func writeBackup(text: String) async throws {
        try await archive.writeBackup(text: text)
    }

    static func readBackup() async throws -> String {
        try await archive.readBackup()
    }

    // Synchronous compatibility entry points. Application async paths use the cancellable methods
    // above; these methods intentionally block their caller while the dedicated I/O queue runs.
    static func writeBackupSync(text: String) throws {
        try archive.writeBackupSync(text: text)
    }

    static func readBackupSync() throws -> String {
        try archive.readBackupSync()
    }
}

/// Only the platform file access and download observations vary in tests. UTF-8 decoding, atomic
/// writes, cancellation checks and the actual read/write sequence are the production implementation.
struct ICloudBackupArchive: CloudBackupArchiving {
    private let resolveBackupURL: @Sendable () throws -> URL
    private let isUbiquitousItem: @Sendable (URL) -> Bool
    private let requestDownload: @Sendable (URL) throws -> Void
    private let isDownloaded: @Sendable (URL) throws -> Bool
    private let waitForNextCheck: @Sendable () async throws -> Void
    private let downloadTimeout: Duration

    init(
        resolveBackupURL: @escaping @Sendable () throws -> URL = Self.liveBackupFileURL,
        isUbiquitousItem: @escaping @Sendable (URL) -> Bool = { FileManager.default.isUbiquitousItem(at: $0) },
        requestDownload: @escaping @Sendable (URL) throws -> Void = { try FileManager.default.startDownloadingUbiquitousItem(at: $0) },
        isDownloaded: @escaping @Sendable (URL) throws -> Bool = {
            try $0.resourceValues(forKeys: [.ubiquitousItemDownloadingStatusKey])
                .ubiquitousItemDownloadingStatus == .current
        },
        waitForNextCheck: @escaping @Sendable () async throws -> Void = { try await Task.sleep(for: .milliseconds(250)) },
        downloadTimeout: Duration = .seconds(10)
    ) {
        self.resolveBackupURL = resolveBackupURL
        self.isUbiquitousItem = isUbiquitousItem
        self.requestDownload = requestDownload
        self.isDownloaded = isDownloaded
        self.waitForNextCheck = waitForNextCheck
        self.downloadTimeout = downloadTimeout
    }

    func writeBackup(text: String) async throws {
        try await FoundationFileIO.shared.run {
            let url = try resolveBackupURL()
            try Data(text.utf8).write(to: url, options: .atomic)
        }
    }

    func readBackup() async throws -> String {
        let url = try await FoundationFileIO.shared.run { try resolveBackupURL() }
        let needsDownload = try await FoundationFileIO.shared.run {
            guard FileManager.default.fileExists(atPath: url.path) else {
                throw ICloudBackupStoreError.notFound
            }
            return isUbiquitousItem(url)
        }
        if needsDownload {
            try await FoundationFileIO.shared.run { try requestDownload(url) }
            try await waitUntilDownloaded(url)
        }
        return try await FoundationFileIO.shared.run { try Self.readText(at: url) }
    }

    private func waitUntilDownloaded(_ url: URL) async throws {
        let clock = ContinuousClock()
        let deadline = clock.now.advanced(by: downloadTimeout)
        while clock.now < deadline {
            try Task.checkCancellation()
            if try await FoundationFileIO.shared.run({ try isDownloaded(url) }) { return }
            try await waitForNextCheck()
        }
        try Task.checkCancellation()
        // Do not read a stale local copy after the download deadline. Keep the existing localized
        // unreadable-file contract rather than introducing a new user-visible error vocabulary.
        throw ICloudBackupStoreError.unreadable
    }

    fileprivate func writeBackupSync(text: String) throws {
        try FoundationFileIO.shared.runSync {
            try Data(text.utf8).write(to: resolveBackupURL(), options: .atomic)
        }
    }

    fileprivate func readBackupSync() throws -> String {
        try FoundationFileIO.shared.runSync {
            let url = try resolveBackupURL()
            guard FileManager.default.fileExists(atPath: url.path) else {
                throw ICloudBackupStoreError.notFound
            }
            if isUbiquitousItem(url) {
                try requestDownload(url)
                let clock = ContinuousClock()
                let deadline = clock.now.advanced(by: downloadTimeout)
                while try !isDownloaded(url) {
                    guard clock.now < deadline else { throw ICloudBackupStoreError.unreadable }
                    Thread.sleep(forTimeInterval: 0.25)
                }
            }
            return try Self.readText(at: url)
        }
    }

    private static func readText(at url: URL) throws -> String {
        let data = try Data(contentsOf: url)
        guard let text = String(data: data, encoding: .utf8) else {
            throw ICloudBackupStoreError.unreadable
        }
        return text
    }

    private static func liveBackupFileURL() throws -> URL {
        let fileManager = FileManager.default
        guard fileManager.ubiquityIdentityToken != nil,
              let container = fileManager.url(forUbiquityContainerIdentifier: nil) else {
            throw ICloudBackupStoreError.unavailable
        }
        let directory = container.appendingPathComponent("Data", isDirectory: true)
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory.appendingPathComponent("spesify-backup.json", isDirectory: false)
    }
}
