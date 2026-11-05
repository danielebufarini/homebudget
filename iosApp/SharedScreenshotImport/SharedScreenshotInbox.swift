import Foundation
import ImageIO
import UniformTypeIdentifiers

actor SharedScreenshotInbox {
    private let directory: URL

    init(containerURL: URL) {
        directory = containerURL.appendingPathComponent("SharedScreenshots", isDirectory: true)
    }

    static func appGroup() throws -> SharedScreenshotInbox {
        guard let container = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: "group.it.danielebufarini.spesify"
        ) else { throw SharedScreenshotError.containerUnavailable }
        return SharedScreenshotInbox(containerURL: container)
    }

    func store(_ data: Data) throws -> URL {
        try Task.checkCancellation()
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              CGImageSourceGetCount(source) > 0,
              let identifier = CGImageSourceGetType(source),
              let type = UTType(identifier as String), type.conforms(to: .image),
              let suffix = type.preferredFilenameExtension else {
            throw SharedScreenshotError.invalidImage
        }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let file = directory.appendingPathComponent("\(Date().timeIntervalSince1970)-\(UUID().uuidString).\(suffix)")
        try Task.checkCancellation()
        try data.write(to: file, options: .atomic)
        return file
    }

    func next() throws -> URL? {
        guard FileManager.default.fileExists(atPath: directory.path) else { return nil }
        return try FileManager.default.contentsOfDirectory(
            at: directory, includingPropertiesForKeys: nil, options: .skipsHiddenFiles
        )
        .filter { UTType(filenameExtension: $0.pathExtension)?.conforms(to: .image) == true }
        .sorted { $0.lastPathComponent < $1.lastPathComponent }
        .first
    }

    func remove(_ file: URL) throws {
        try FileManager.default.removeItem(at: file)
    }
}

enum SharedScreenshotError: Error {
    case containerUnavailable, expectedOneImage, invalidImage
}
